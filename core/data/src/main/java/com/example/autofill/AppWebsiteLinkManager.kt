package com.example.autofill

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.data.LinkEntity
import com.example.security.RecordEncryption
import com.example.security.SecureStorage
import org.json.JSONObject
import java.util.Locale

/**
 * Entry from the read-only bundled static asset `known_app_domain_pairs.json`.
 * Used ONLY to suggest user-confirmed links; never used to auto-fill or auto-link.
 */
@Immutable
data class KnownAppDomainPair(
    val appPackage: String,
    val appLabel: String,
    val domain: String
)

/**
 * User decision when prompted: "Also use this login for [App/Website]?"
 */
enum class LinkConfirmationChoice {
    /** Link the app and website now (requires user confirmation). */
    YES_LINK_NOW,
    /** Do not link now and ignore this suggestion in the future. */
    NO_IGNORE_SUGGESTION,
    /** Do not link right now, but keep offering the suggestion next time ("Always ask"). */
    ALWAYS_ASK
}

/**
 * Represents a candidate link suggestion between an Android app and a website credential.
 */
@Immutable
data class AppWebsiteLinkSuggestion(
    val existingCredentialId: Int,
    val existingTitle: String,
    val username: String,
    val targetAppPackage: String,
    val targetAppLabel: String,
    val targetAppSigners: Set<String>,
    val targetDomain: String,
    val promptMessage: String,
    val fromBundledCatalog: Boolean
)

/**
 * Manages offline, user-confirmed linking between Android apps (`package + SHA-256 signature`)
 * and websites (`registrable domain`), plus the read-only bundled suggestion catalog.
 *
 * Security Invariants (Step 8B):
 * 1. NEVER links an app and a website automatically.
 * 2. The bundled `known_app_domain_pairs.json` catalog is used ONLY to suggest a link to the user,
 *    NEVER to auto-fill credentials across unconfirmed app/domain boundaries.
 * 3. Users can ignore/remove suggestions at any time.
 */
class AppWebsiteLinkManager(
    private val context: Context,
    private val metadataStore: CredentialMetadataStore = CredentialMetadataStore(context)
) {

    private val prefs = SecureStorage.getEncryptedPrefs(
        context.applicationContext,
        "linksaver_app_web_links_prefs_encrypted"
    )

    /**
     * Loads the read-only bundled catalog of well-known app↔domain pairs from `assets/known_app_domain_pairs.json`.
     */
    fun loadBundledCatalog(): List<KnownAppDomainPair> {
        return try {
            val jsonText = context.assets.open(ASSET_FILE_NAME).bufferedReader().use { it.readText() }
            parseCatalogJson(jsonText)
        } catch (_: Exception) {
            DEFAULT_BUNDLED_PAIRS
        }
    }

    /**
     * Returns known domains suggested for [appPackage] from the static catalog, excluding ignored pairs.
     * Used strictly for UI suggestions, never for automatic filling.
     */
    fun getCatalogSuggestedDomainsForApp(appPackage: String): List<KnownAppDomainPair> {
        val normPkg = AppIdentityVerifier.normalizePackageName(appPackage)
        if (normPkg.isBlank()) return emptyList()
        val ignored = getIgnoredSuggestionKeys()
        return loadBundledCatalog().filter { pair ->
            AppIdentityVerifier.isSamePackage(pair.appPackage, normPkg) &&
                !ignored.contains(suggestionKey(normPkg, pair.domain))
        }
    }

    /**
     * Returns known apps suggested for [domainOrUrl] from the static catalog, excluding ignored pairs.
     * Used strictly for UI suggestions, never for automatic filling.
     */
    fun getCatalogSuggestedAppsForDomain(domainOrUrl: String): List<KnownAppDomainPair> {
        val normDomain = AppIdentityVerifier.normalizeRegistrableDomain(domainOrUrl)
        if (normDomain.isBlank()) return emptyList()
        val ignored = getIgnoredSuggestionKeys()
        return loadBundledCatalog().filter { pair ->
            pair.domain.equals(normDomain, ignoreCase = true) &&
                !ignored.contains(suggestionKey(pair.appPackage, normDomain))
        }
    }

    /**
     * When saving a login from an app (or a website), checks if a credential with the SAME username
     * already exists for a website (or an app) and builds a user-confirmation prompt:
     * `"Also use this login for [App/Website]?"`
     *
     * Never links automatically.
     */
    fun findCrossLinkSuggestionsOnSave(
        allCredentials: List<LinkEntity>,
        savingUsername: String,
        savingAppPackage: String,
        savingAppLabel: String,
        savingAppSigners: Set<String>,
        savingWebsiteDomain: String,
        isVaultUnlocked: Boolean = true
    ): List<AppWebsiteLinkSuggestion> {
        val cleanUser = savingUsername.trim().lowercase(Locale.US)
        if (cleanUser.isBlank()) return emptyList()

        val normSavingPkg = AppIdentityVerifier.normalizePackageName(savingAppPackage)
        val normSavingDomain = AppIdentityVerifier.normalizeRegistrableDomain(savingWebsiteDomain)
        val ignored = getIgnoredSuggestionKeys()
        val catalog = loadBundledCatalog()
        val suggestions = mutableListOf<AppWebsiteLinkSuggestion>()

        for (cred in allCredentials) {
            val isPass = cred.category == "🔑 Passwords" || cred.description == "SECURE_PASSWORD"
            if (!isPass) continue

            val existingUser = extractUsername(cred, isVaultUnlocked).trim().lowercase(Locale.US)
            if (existingUser != cleanUser) continue

            val meta = metadataStore.getMetadata(cred.id)
            val existingDomain = if (cred.url.startsWith("app://", ignoreCase = true)) {
                ""
            } else {
                AppIdentityVerifier.normalizeRegistrableDomain(cred.url)
            }
            val existingPkg = meta.appPackage.ifBlank {
                if (cred.url.startsWith("app://", ignoreCase = true)) {
                    AppIdentityVerifier.normalizePackageName(cred.url)
                } else ""
            }

            // Case 1: Saving from an Android app, and a website credential with the same username exists
            if (normSavingPkg.isNotBlank() && existingDomain.isNotBlank()) {
                val alreadyLinked = AppIdentityVerifier.isSamePackage(existingPkg, normSavingPkg) ||
                    meta.linkedApps.any { AppIdentityVerifier.isSamePackage(it.packageName, normSavingPkg) }
                val key = suggestionKey(normSavingPkg, existingDomain)
                if (!alreadyLinked && !ignored.contains(key)) {
                    val isCatalogMatch = catalog.any {
                        AppIdentityVerifier.isSamePackage(it.appPackage, normSavingPkg) &&
                            it.domain.equals(existingDomain, ignoreCase = true)
                    }
                    val readableTarget = existingDomain
                    suggestions.add(
                        AppWebsiteLinkSuggestion(
                            existingCredentialId = cred.id,
                            existingTitle = cred.title.ifBlank { existingDomain },
                            username = savingUsername.trim(),
                            targetAppPackage = normSavingPkg,
                            targetAppLabel = savingAppLabel.ifBlank { normSavingPkg },
                            targetAppSigners = savingAppSigners,
                            targetDomain = existingDomain,
                            promptMessage = "Also use this login for $readableTarget?",
                            fromBundledCatalog = isCatalogMatch
                        )
                    )
                }
            }

            // Case 2: Saving from a website, and an Android app credential with the same username exists
            if (normSavingDomain.isNotBlank() && existingPkg.isNotBlank()) {
                val alreadyLinked = existingDomain.equals(normSavingDomain, ignoreCase = true) ||
                    meta.linkedWebsites.any { it.equals(normSavingDomain, ignoreCase = true) }
                val key = suggestionKey(existingPkg, normSavingDomain)
                if (!alreadyLinked && !ignored.contains(key)) {
                    val isCatalogMatch = catalog.any {
                        AppIdentityVerifier.isSamePackage(it.appPackage, existingPkg) &&
                            it.domain.equals(normSavingDomain, ignoreCase = true)
                    }
                    val readableApp = meta.appLabel.ifBlank { existingPkg }
                    suggestions.add(
                        AppWebsiteLinkSuggestion(
                            existingCredentialId = cred.id,
                            existingTitle = cred.title.ifBlank { readableApp },
                            username = savingUsername.trim(),
                            targetAppPackage = existingPkg,
                            targetAppLabel = readableApp,
                            targetAppSigners = meta.appSigners,
                            targetDomain = normSavingDomain,
                            promptMessage = "Also use this login for $readableApp?",
                            fromBundledCatalog = isCatalogMatch
                        )
                    )
                }
            }
        }

        // Prioritize catalog-backed suggestions first, then other same-username matches
        return suggestions.sortedByDescending { it.fromBundledCatalog }
    }

    /**
     * Applies the user's explicit decision (`YES_LINK_NOW`, `NO_IGNORE_SUGGESTION`, or `ALWAYS_ASK`)
     * for a [AppWebsiteLinkSuggestion].
     */
    fun applyUserLinkDecision(
        suggestion: AppWebsiteLinkSuggestion,
        choice: LinkConfirmationChoice,
        newlySavedCredentialId: Int? = null
    ) {
        when (choice) {
            LinkConfirmationChoice.YES_LINK_NOW -> {
                if (suggestion.targetAppPackage.isNotBlank()) {
                    metadataStore.addConfirmedLinkedApp(
                        credentialId = suggestion.existingCredentialId,
                        packageName = suggestion.targetAppPackage,
                        signersSha256 = suggestion.targetAppSigners,
                        label = suggestion.targetAppLabel
                    )
                }
                if (suggestion.targetDomain.isNotBlank()) {
                    metadataStore.addConfirmedLinkedWebsite(
                        credentialId = suggestion.existingCredentialId,
                        domainOrUrl = suggestion.targetDomain
                    )
                }
                if (newlySavedCredentialId != null && newlySavedCredentialId > 0) {
                    if (suggestion.targetAppPackage.isNotBlank()) {
                        metadataStore.addConfirmedLinkedApp(
                            credentialId = newlySavedCredentialId,
                            packageName = suggestion.targetAppPackage,
                            signersSha256 = suggestion.targetAppSigners,
                            label = suggestion.targetAppLabel
                        )
                    }
                    if (suggestion.targetDomain.isNotBlank()) {
                        metadataStore.addConfirmedLinkedWebsite(
                            credentialId = newlySavedCredentialId,
                            domainOrUrl = suggestion.targetDomain
                        )
                    }
                }
            }
            LinkConfirmationChoice.NO_IGNORE_SUGGESTION -> {
                ignoreSuggestion(suggestion.targetAppPackage, suggestion.targetDomain)
            }
            LinkConfirmationChoice.ALWAYS_ASK -> {
                // Leave unlinked and do not add to ignore list so future saves will ask again
            }
        }
    }

    /**
     * Checks whether a credential matches a requested website domain (either via its primary URL
     * or via a user-confirmed `linkedWebsites` entry).
     *
     * Note: Bundled catalog suggestions are NEVER used here; only explicit user-confirmed links match.
     */
    fun isCredentialLinkedToDomain(
        credential: LinkEntity,
        requestedDomain: String,
        metadata: CredentialAppMetadata = metadataStore.getMetadata(credential.id)
    ): Boolean {
        val normReq = AppIdentityVerifier.normalizeRegistrableDomain(requestedDomain)
        if (normReq.isBlank()) return false

        if (!credential.url.startsWith("app://", ignoreCase = true)) {
            val primaryDomain = AppIdentityVerifier.normalizeRegistrableDomain(credential.url)
            if (primaryDomain.equals(normReq, ignoreCase = true)) {
                return true
            }
        }

        return metadata.linkedWebsites.any {
            AppIdentityVerifier.normalizeRegistrableDomain(it).equals(normReq, ignoreCase = true)
        }
    }

    fun ignoreSuggestion(appPackage: String, domain: String) {
        val key = suggestionKey(appPackage, domain)
        if (key.isBlank()) return
        val current = getIgnoredSuggestionKeys().toMutableSet()
        current.add(key)
        prefs.edit().putStringSet(KEY_IGNORED_SUGGESTIONS, current).apply()
    }

    fun removeIgnoredSuggestion(appPackage: String, domain: String) {
        val key = suggestionKey(appPackage, domain)
        val current = getIgnoredSuggestionKeys().toMutableSet()
        current.remove(key)
        prefs.edit().putStringSet(KEY_IGNORED_SUGGESTIONS, current).apply()
    }

    fun clearAllIgnoredSuggestions() {
        prefs.edit().remove(KEY_IGNORED_SUGGESTIONS).apply()
    }

    fun getIgnoredSuggestionKeys(): Set<String> {
        return prefs.getStringSet(KEY_IGNORED_SUGGESTIONS, emptySet()) ?: emptySet()
    }

    private fun suggestionKey(appPackage: String, domain: String): String {
        val pkg = AppIdentityVerifier.normalizePackageName(appPackage)
        val dom = AppIdentityVerifier.normalizeRegistrableDomain(domain)
        if (pkg.isBlank() || dom.isBlank()) return ""
        return "$pkg|$dom"
    }

    private fun extractUsername(link: LinkEntity, isUnlocked: Boolean): String {
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
            val beforeDelim = raw.substringBefore("|||").trim()
            if (beforeDelim.isNotBlank()) return beforeDelim
        }
        if (link.title.contains("(") && link.title.contains(")")) {
            return link.title.substringBefore("(").trim()
        }
        return ""
    }

    companion object {
        const val ASSET_FILE_NAME = "known_app_domain_pairs.json"
        private const val KEY_IGNORED_SUGGESTIONS = "ignored_app_domain_suggestions"

        val DEFAULT_BUNDLED_PAIRS = listOf(
            KnownAppDomainPair("com.netflix.mediaclient", "Netflix", "netflix.com"),
            KnownAppDomainPair("com.instagram.android", "Instagram", "instagram.com"),
            KnownAppDomainPair("com.github.android", "GitHub", "github.com"),
            KnownAppDomainPair("com.spotify.music", "Spotify", "spotify.com"),
            KnownAppDomainPair("com.reddit.frontpage", "Reddit", "reddit.com"),
            KnownAppDomainPair("com.discord", "Discord", "discord.com"),
            KnownAppDomainPair("com.linkedin.android", "LinkedIn", "linkedin.com"),
            KnownAppDomainPair("com.amazon.mshop.android.shopping", "Amazon Shopping", "amazon.com"),
            KnownAppDomainPair("com.dropbox.android", "Dropbox", "dropbox.com"),
            KnownAppDomainPair("com.slack", "Slack", "slack.com"),
            KnownAppDomainPair("com.pinterest", "Pinterest", "pinterest.com"),
            KnownAppDomainPair("tv.twitch.android.app", "Twitch", "twitch.tv")
        )

        fun parseCatalogJson(jsonText: String): List<KnownAppDomainPair> {
            val root = JSONObject(jsonText)
            val arr = root.optJSONArray("pairs") ?: return DEFAULT_BUNDLED_PAIRS
            val result = mutableListOf<KnownAppDomainPair>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val pkg = AppIdentityVerifier.normalizePackageName(obj.optString("appPackage", ""))
                val label = obj.optString("appLabel", pkg).ifBlank { pkg }
                val domain = AppIdentityVerifier.normalizeRegistrableDomain(obj.optString("domain", ""))
                if (pkg.isNotBlank() && domain.isNotBlank()) {
                    result.add(KnownAppDomainPair(pkg, label, domain))
                }
            }
            return if (result.isEmpty()) DEFAULT_BUNDLED_PAIRS else result
        }
    }
}
