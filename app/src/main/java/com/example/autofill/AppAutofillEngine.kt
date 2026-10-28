package com.example.autofill

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.data.LinkDao
import com.example.data.LinkEntity
import com.example.security.RecordEncryption
import java.util.Locale

/**
 * Represents a matched credential ready for autofill or publisher-mismatch warning.
 */
data class AutofillCandidateMatch(
    val entity: LinkEntity,
    val metadata: CredentialAppMetadata,
    val username: String,
    val displayTitle: String,
    val signatureStatus: AppSignatureMatchResult,
    val currentAppSigners: Set<String> = emptySet()
)

sealed class AutofillFillPlan {
    object BlockedByNeverSave : AutofillFillPlan()
    object NoEligibleFields : AutofillFillPlan()
    object NoMatchingCredentials : AutofillFillPlan()
    data class Ready(
        val verifiedMatches: List<AutofillCandidateMatch>,
        val signatureWarningMatches: List<AutofillCandidateMatch>,
        val isAppRequest: Boolean,
        val targetTitle: String
    ) : AutofillFillPlan()
}

sealed class AutofillSaveOutcome {
    object SkippedNeverSave : AutofillSaveOutcome()
    object SkippedBlankPassword : AutofillSaveOutcome()
    object RejectedConfirmPasswordMismatch : AutofillSaveOutcome()
    data class AlreadyUpToDate(val credentialId: Int) : AutofillSaveOutcome()
    data class SavedNew(
        val credentialId: Int,
        val displayTitle: String,
        val crossLinkSuggestions: List<AppWebsiteLinkSuggestion>
    ) : AutofillSaveOutcome()
    data class UpdatedExisting(
        val credentialId: Int,
        val displayTitle: String,
        val canUndo: Boolean,
        val crossLinkSuggestions: List<AppWebsiteLinkSuggestion>
    ) : AutofillSaveOutcome()
}

/**
 * Core offline autofill decision and persistence engine for native Android apps and browsers.
 *
 * Enforces:
 * - Package + SHA-256 certificate verification (8A.1)
 * - Beta/debug variant isolation unless explicitly linked by user (8A.3)
 * - Publisher-mismatch warning ("This app was updated by a different publisher, tap to review") (8A.1)
 * - Safe app label resolution with package-name fallback (8A.2)
 * - User-confirmed app↔website links only; bundled map is used strictly for suggestions (8B.1, 8B.2)
 * - Sign-up confirm-password matching validation (8D.4)
 * - Password update with encrypted history and 1-tap Undo (8D.2)
 * - Never-save list enforcement by package and domain (8D.1)
 */
@RequiresApi(Build.VERSION_CODES.O)
class AppAutofillEngine(
    private val context: Context,
    private val neverSaveManager: NeverSaveManager = NeverSaveManager(context),
    private val metadataStore: CredentialMetadataStore = CredentialMetadataStore(context),
    private val linkManager: AppWebsiteLinkManager = AppWebsiteLinkManager(context, metadataStore)
) {

    fun evaluateFillRequest(
        parsed: ParsedAutofillStructure,
        allLinks: List<LinkEntity>,
        currentAppSignersOverride: Set<String>? = null,
        appLabelOverride: String? = null,
        isVaultUnlocked: Boolean = true
    ): AutofillFillPlan {
        // 1. Check Never-Save list by both domain and app package
        if (neverSaveManager.isNeverSaveTarget(parsed.domainHost, parsed.packageName)) {
            return AutofillFillPlan.BlockedByNeverSave
        }

        // 2. Do not offer fill on non-login screens or when fields opt out
        if (!parsed.shouldOfferFill) {
            return AutofillFillPlan.NoEligibleFields
        }

        val isWeb = parsed.isWebRequest && parsed.webDomain.isNotBlank()
        val normPkg = AppIdentityVerifier.normalizePackageName(parsed.packageName)
        val currentSigners = currentAppSignersOverride
            ?: if (!isWeb && normPkg.isNotBlank()) {
                AppIdentityVerifier.getAppSignersSha256(context, normPkg)
            } else {
                emptySet()
            }

        val resolvedTitle = if (isWeb) {
            parsed.webDomain
        } else {
            appLabelOverride ?: AppIdentityVerifier.resolveAppDisplayInfo(context, normPkg).displayLabel
        }

        val verifiedMatches = mutableListOf<AutofillCandidateMatch>()
        val warningMatches = mutableListOf<AutofillCandidateMatch>()

        val passwordRecords = allLinks.filter { link ->
            link.category == "🔑 Passwords" || link.description == "SECURE_PASSWORD"
        }

        for (cred in passwordRecords) {
            val meta = metadataStore.getMetadata(cred.id)
            val username = extractUsername(cred, isVaultUnlocked)

            if (isWeb) {
                // Website fill: match primary URL domain or user-confirmed linkedWebsites
                if (linkManager.isCredentialLinkedToDomain(cred, parsed.webDomain, meta)) {
                    verifiedMatches.add(
                        AutofillCandidateMatch(
                            entity = cred,
                            metadata = meta,
                            username = username,
                            displayTitle = cred.title.ifBlank { parsed.webDomain },
                            signatureStatus = AppSignatureMatchResult.EXACT_MATCH
                        )
                    )
                }
            } else if (normPkg.isNotBlank()) {
                // Native Android App fill: require package AND SHA-256 signing certificate match
                val effectiveMeta = if (meta.appPackage.isBlank() && cred.url.startsWith("app://", ignoreCase = true)) {
                    meta.copy(appPackage = AppIdentityVerifier.normalizePackageName(cred.url))
                } else {
                    meta
                }
                val matchResult = AppIdentityVerifier.verifyAppIdentity(
                    requestedPackage = normPkg,
                    currentSigners = currentSigners,
                    metadata = effectiveMeta
                )
                when (matchResult) {
                    AppSignatureMatchResult.EXACT_MATCH -> {
                        verifiedMatches.add(
                            AutofillCandidateMatch(
                                entity = cred,
                                metadata = effectiveMeta,
                                username = username,
                                displayTitle = effectiveMeta.appLabel.ifBlank { cred.title.ifBlank { resolvedTitle } },
                                signatureStatus = AppSignatureMatchResult.EXACT_MATCH,
                                currentAppSigners = currentSigners
                            )
                        )
                    }
                    AppSignatureMatchResult.SIGNATURE_CHANGED_WARNING -> {
                        warningMatches.add(
                            AutofillCandidateMatch(
                                entity = cred,
                                metadata = effectiveMeta,
                                username = username,
                                displayTitle = effectiveMeta.appLabel.ifBlank { cred.title.ifBlank { resolvedTitle } },
                                signatureStatus = AppSignatureMatchResult.SIGNATURE_CHANGED_WARNING,
                                currentAppSigners = currentSigners
                            )
                        )
                    }
                    AppSignatureMatchResult.PACKAGE_MISMATCH -> {
                        // Do not match; bundled catalog is strictly for suggestions, never auto-fill
                    }
                }
            }
        }

        if (verifiedMatches.isEmpty() && warningMatches.isEmpty()) {
            return AutofillFillPlan.NoMatchingCredentials
        }

        return AutofillFillPlan.Ready(
            verifiedMatches = verifiedMatches.take(5),
            signatureWarningMatches = warningMatches.take(3),
            isAppRequest = !isWeb,
            targetTitle = resolvedTitle
        )
    }

    /**
     * Processes a user-confirmed save request from either a native Android app or a browser/WebView.
     */
    suspend fun processSaveRequest(
        parsed: ParsedAutofillStructure,
        dao: LinkDao,
        userEmail: String = "",
        currentAppSignersOverride: Set<String>? = null,
        appLabelOverride: String? = null,
        isVaultUnlocked: Boolean = true
    ): AutofillSaveOutcome {
        // 1. Enforce Never-Save list by domain and package
        if (neverSaveManager.isNeverSaveTarget(parsed.domainHost, parsed.packageName)) {
            return AutofillSaveOutcome.SkippedNeverSave
        }

        // 2. Reject mismatched sign-up confirm passwords (8D.4)
        if (parsed.hasConfirmPasswordMismatch) {
            return AutofillSaveOutcome.RejectedConfirmPasswordMismatch
        }

        val username = parsed.usernameValue.trim()
        val password = parsed.passwordValue
        if (password.isBlank()) {
            return AutofillSaveOutcome.SkippedBlankPassword
        }

        val isWeb = parsed.isWebRequest && parsed.webDomain.isNotBlank()
        val normPkg = AppIdentityVerifier.normalizePackageName(parsed.packageName)
        val normDomain = if (isWeb) AppIdentityVerifier.normalizeRegistrableDomain(parsed.webDomain) else ""

        val appSigners = if (!isWeb && normPkg.isNotBlank()) {
            val raw = currentAppSignersOverride ?: AppIdentityVerifier.getAppSignersSha256(context, normPkg)
            AppIdentityVerifier.normalizeSignerDigests(raw)
        } else {
            emptySet()
        }

        val appLabel = if (!isWeb && normPkg.isNotBlank()) {
            appLabelOverride?.trim()?.takeIf { it.isNotBlank() }
                ?: AppIdentityVerifier.resolveAppDisplayInfo(context, normPkg).displayLabel
        } else {
            ""
        }

        val canonicalUrl = if (isWeb) {
            "https://$normDomain"
        } else {
            "app://$normPkg"
        }

        val readableTitle = if (isWeb) {
            if (username.isNotBlank()) "$normDomain ($username)" else normDomain
        } else {
            val baseLabel = appLabel.ifBlank { normPkg }
            baseLabel
        }

        val allLinks = dao.getAllLinksSnapshot()
        val existingMatch = findExistingCredentialForTarget(
            allLinks = allLinks,
            isWeb = isWeb,
            normPkg = normPkg,
            normDomain = normDomain,
            username = username,
            isVaultUnlocked = isVaultUnlocked
        )

        if (existingMatch != null) {
            val existingPass = decryptPassword(existingMatch, isVaultUnlocked)
            if (existingPass == password) {
                // Keep app metadata up-to-date
                if (!isWeb && normPkg.isNotBlank()) {
                    metadataStore.saveAppIdentity(
                        credentialId = existingMatch.id,
                        appPackage = normPkg,
                        appSigners = appSigners,
                        appLabel = appLabel.ifBlank { normPkg }
                    )
                }
                return AutofillSaveOutcome.AlreadyUpToDate(existingMatch.id)
            }

            // Record encrypted password history before updating so Undo works (Step 8D.2)
            if (existingPass.isNotBlank()) {
                metadataStore.recordPasswordHistory(
                    credentialId = existingMatch.id,
                    previousPlainPassword = existingPass,
                    userEmail = existingMatch.userEmail.ifBlank { userEmail }
                )
            }

            val rawNote = "$username|||$password"
            val encNote = RecordEncryption.encrypt(
                context = context,
                plaintext = rawNote,
                recordId = existingMatch.id,
                userEmail = existingMatch.userEmail.ifBlank { userEmail }
            )
            val updatedEntity = existingMatch.copy(
                title = readableTitle,
                note = encNote,
                timestamp = System.currentTimeMillis()
            )
            dao.updateLink(updatedEntity)

            if (!isWeb && normPkg.isNotBlank()) {
                metadataStore.saveAppIdentity(
                    credentialId = existingMatch.id,
                    appPackage = normPkg,
                    appSigners = appSigners,
                    appLabel = appLabel.ifBlank { normPkg }
                )
            }

            val suggestions = linkManager.findCrossLinkSuggestionsOnSave(
                allCredentials = dao.getAllLinksSnapshot(),
                savingUsername = username,
                savingAppPackage = if (!isWeb) normPkg else "",
                savingAppLabel = appLabel,
                savingAppSigners = appSigners,
                savingWebsiteDomain = normDomain,
                isVaultUnlocked = isVaultUnlocked
            )

            return AutofillSaveOutcome.UpdatedExisting(
                credentialId = existingMatch.id,
                displayTitle = readableTitle,
                canUndo = existingPass.isNotBlank(),
                crossLinkSuggestions = suggestions
            )
        } else {
            val rawNote = "$username|||$password"
            val initialEntity = LinkEntity(
                url = canonicalUrl,
                title = readableTitle,
                description = "SECURE_PASSWORD",
                category = "🔑 Passwords",
                note = rawNote,
                userEmail = userEmail,
                timestamp = System.currentTimeMillis()
            )
            val insertedId = dao.insertLink(initialEntity).toInt()
            val encNote = RecordEncryption.encrypt(
                context = context,
                plaintext = rawNote,
                recordId = insertedId,
                userEmail = userEmail
            )
            dao.updateLink(initialEntity.copy(id = insertedId, note = encNote))

            if (!isWeb && normPkg.isNotBlank()) {
                metadataStore.saveAppIdentity(
                    credentialId = insertedId,
                    appPackage = normPkg,
                    appSigners = appSigners,
                    appLabel = appLabel.ifBlank { normPkg }
                )
            }

            val suggestions = linkManager.findCrossLinkSuggestionsOnSave(
                allCredentials = dao.getAllLinksSnapshot(),
                savingUsername = username,
                savingAppPackage = if (!isWeb) normPkg else "",
                savingAppLabel = appLabel,
                savingAppSigners = appSigners,
                savingWebsiteDomain = normDomain,
                isVaultUnlocked = isVaultUnlocked
            )

            return AutofillSaveOutcome.SavedNew(
                credentialId = insertedId,
                displayTitle = readableTitle,
                crossLinkSuggestions = suggestions
            )
        }
    }

    /**
     * Undoes the most recent password update for [credentialId], restoring the previous
     * encrypted password from [CredentialMetadataStore].
     */
    suspend fun undoLastPasswordUpdate(
        credentialId: Int,
        dao: LinkDao,
        isVaultUnlocked: Boolean = true
    ): Boolean {
        val current = dao.getLinkById(credentialId) ?: return false
        val previousPlain = metadataStore.popMostRecentHistoricalPassword(
            credentialId = credentialId,
            userEmail = current.userEmail,
            isVaultUnlocked = isVaultUnlocked
        ) ?: return false

        val username = extractUsername(current, isVaultUnlocked)
        val rawNote = "$username|||$previousPlain"
        val encNote = RecordEncryption.encrypt(
            context = context,
            plaintext = rawNote,
            recordId = credentialId,
            userEmail = current.userEmail
        )
        dao.updateLink(current.copy(note = encNote, timestamp = System.currentTimeMillis()))
        return true
    }

    private fun findExistingCredentialForTarget(
        allLinks: List<LinkEntity>,
        isWeb: Boolean,
        normPkg: String,
        normDomain: String,
        username: String,
        isVaultUnlocked: Boolean
    ): LinkEntity? {
        val cleanUser = username.trim().lowercase(Locale.US)
        for (link in allLinks) {
            val isPass = link.category == "🔑 Passwords" || link.description == "SECURE_PASSWORD"
            if (!isPass) continue

            val existingUser = extractUsername(link, isVaultUnlocked).trim().lowercase(Locale.US)
            if (cleanUser.isNotBlank() && existingUser.isNotBlank() && existingUser != cleanUser) {
                continue
            }

            val meta = metadataStore.getMetadata(link.id)
            if (isWeb && normDomain.isNotBlank()) {
                val linkDomain = AppIdentityVerifier.normalizeRegistrableDomain(link.url)
                if (linkDomain.equals(normDomain, ignoreCase = true)) {
                    return link
                }
            } else if (!isWeb && normPkg.isNotBlank()) {
                val linkPkg = meta.appPackage.ifBlank {
                    if (link.url.startsWith("app://", ignoreCase = true)) {
                        AppIdentityVerifier.normalizePackageName(link.url)
                    } else ""
                }
                if (AppIdentityVerifier.isSamePackage(linkPkg, normPkg)) {
                    return link
                }
            }
        }
        return null
    }

    fun extractUsername(link: LinkEntity, isUnlocked: Boolean = true): String {
        if (link.note.isNotBlank()) {
            val raw = if (RecordEncryption.isEncrypted(link.note)) {
                RecordEncryption.decrypt(
                    context = context,
                    storedValue = link.note,
                    recordId = link.id,
                    userEmail = link.userEmail,
                    isUnlocked = isUnlocked
                )
            } else {
                link.note
            }
            if (raw.contains("|||")) {
                return raw.substringBefore("|||").trim()
            }
        }
        if (link.title.contains("(") && link.title.contains(")")) {
            return link.title.substringAfter("(").substringBefore(")").trim()
        }
        return link.userEmail
    }

    fun decryptPassword(link: LinkEntity, isUnlocked: Boolean = true): String {
        if (link.note.isBlank()) return ""
        val raw = if (RecordEncryption.isEncrypted(link.note)) {
            RecordEncryption.decrypt(
                context = context,
                storedValue = link.note,
                recordId = link.id,
                userEmail = link.userEmail,
                isUnlocked = isUnlocked
            )
        } else {
            link.note
        }
        return if (raw.contains("|||")) {
            raw.substringAfter("|||")
        } else {
            raw
        }
    }
}
