package com.example.autofill

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.runtime.Immutable
import java.security.MessageDigest
import java.util.Locale

/**
 * Represents a user-confirmed linked Android application identity.
 */
@Immutable
data class LinkedAppIdentity(
    val packageName: String,
    val signersSha256: Set<String>,
    val label: String,
    val confirmedAt: Long = System.currentTimeMillis()
)

/**
 * Encrypted historical password entry for a credential so updates can be audited or undone.
 */
@Immutable
data class PasswordHistoryEntry(
    val encryptedPassword: String,
    val changedAt: Long
)

/**
 * Extended offline metadata for a saved credential supporting native Android apps,
 * publisher certificate pinning, user-confirmed linked apps/websites, and encrypted password history.
 */
@Immutable
data class CredentialAppMetadata(
    val credentialId: Int,
    val appPackage: String = "",
    val appSigners: Set<String> = emptySet(),
    val appLabel: String = "",
    val linkedApps: List<LinkedAppIdentity> = emptyList(),
    val linkedWebsites: List<String> = emptyList(),
    val passwordHistory: List<PasswordHistoryEntry> = emptyList()
)

/**
 * Result of verifying an app's package name and signing certificates against a saved credential.
 */
enum class AppSignatureMatchResult {
    /** Package and all current SHA-256 signing certificates match the saved record. */
    EXACT_MATCH,
    /** Package matches, but the signing certificate SHA-256 changed (different publisher / key). Never auto-fill! */
    SIGNATURE_CHANGED_WARNING,
    /** Package does not match the credential's primary appPackage or any user-confirmed linkedApps. */
    PACKAGE_MISMATCH
}

/**
 * Safe, package-visibility-resilient display info for an Android application.
 */
data class ResolvedAppDisplayInfo(
    val packageName: String,
    val displayLabel: String,
    val icon: Drawable? = null,
    val resolvedFromPackageManager: Boolean = false
)

/**
 * Handles native Android application identity normalization, SHA-256 signing certificate
 * computation and verification, debug/beta variant separation, and safe PackageManager lookups
 * without requiring QUERY_ALL_PACKAGES.
 */
object AppIdentityVerifier {

    private val PACKAGE_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")

    /**
     * Normalizes an Android package name:
     * - Trims whitespace
     * - Strips any `app://` or `android://` prefix and trailing path
     * - Lowercases using Locale.US
     *
     * Note: Debug/beta/staging variants (e.g. `com.app.beta`, `com.app.debug`) are preserved as
     * distinct package identifiers and are NEVER collapsed into the production package name.
     */
    fun normalizePackageName(rawPackage: String): String {
        val cleaned = rawPackage.trim()
            .removePrefix("app://")
            .removePrefix("android://")
            .substringBefore('/')
            .substringBefore(':')
            .trim()
            .lowercase(Locale.US)
        return cleaned
    }

    /**
     * Returns true if [rawPackage] looks like a valid multi-segment Android package name.
     */
    fun isValidPackageName(rawPackage: String): Boolean {
        val norm = normalizePackageName(rawPackage)
        return norm.isNotBlank() && PACKAGE_REGEX.matches(norm)
    }

    /**
     *Normalizes a registrable domain name for website matching / linking.
     */
    fun normalizeRegistrableDomain(rawDomainOrUrl: String): String {
        val clean = rawDomainOrUrl.trim()
            .lowercase(Locale.US)
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore(':')
            .substringBefore('?')
            .trim()
        if (clean.isBlank() || clean.startsWith("app://")) return ""
        val parts = clean.split('.').filter { it.isNotBlank() }
        if (parts.size <= 2) return clean
        // Keep last two labels (or 3 for common multi-part ccTLDs like .co.uk, .com.au, .co.in)
        val secondLast = parts[parts.size - 2]
        val last = parts.last()
        val multiPartCcTlds = setOf("co", "com", "org", "net", "gov", "edu", "ac")
        return if (last.length == 2 && secondLast in multiPartCcTlds && parts.size >= 3) {
            parts.takeLast(3).joinToString(".")
        } else {
            parts.takeLast(2).joinToString(".")
        }
    }

    /**
     * Checks whether two package names refer to the exact same normalized package.
     * Debug/beta variants (e.g. `com.example.app` vs `com.example.app.beta` or `com.example.app.debug`)
     * return FALSE unless explicitly linked by the user via `linkedApps`.
     */
    fun isSamePackage(pkgA: String, pkgB: String): Boolean {
        val normA = normalizePackageName(pkgA)
        val normB = normalizePackageName(pkgB)
        if (normA.isBlank() || normB.isBlank()) return false
        return normA == normB
    }

    /**
     * Normalizes a set of SHA-256 certificate digests to lowercase 64-char hex strings without colons.
     */
    fun normalizeSignerDigests(signers: Collection<String>): Set<String> {
        return signers.mapNotNull { raw ->
            val cleaned = raw.trim().replace(":", "").replace(" ", "").lowercase(Locale.US)
            if (cleaned.isNotEmpty()) cleaned else null
        }.toSortedSet()
    }

    /**
     * Computes the SHA-256 hex digest of raw certificate bytes.
     */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Retrieves all current signing certificate SHA-256 digests for [packageName].
     * Safe against Android 11+ package visibility restrictions (returns emptySet if blocked).
     */
    @Suppress("DEPRECATION")
    fun getAppSignersSha256(
        context: Context,
        packageName: String,
        packageManagerOverride: PackageManager? = null
    ): Set<String> {
        val normPkg = normalizePackageName(packageName)
        if (normPkg.isBlank()) return emptySet()
        val pm = packageManagerOverride ?: context.packageManager
        return try {
            val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = pm.getPackageInfo(normPkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = pkgInfo.signingInfo
                if (signingInfo == null) {
                    emptyArray()
                } else if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners ?: emptyArray()
                } else {
                    val history = signingInfo.signingCertificateHistory
                    if (!history.isNullOrEmpty()) {
                        arrayOf(history.last())
                    } else {
                        signingInfo.apkContentsSigners ?: emptyArray()
                    }
                }
            } else {
                val pkgInfo = pm.getPackageInfo(normPkg, PackageManager.GET_SIGNATURES)
                pkgInfo.signatures ?: emptyArray()
            }

            signatures.mapNotNull { sig ->
                val bytes = sig.toByteArray()
                if (bytes.isNotEmpty()) sha256Hex(bytes) else null
            }.toSortedSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /**
     * Resolves an application's human-readable label and icon safely without QUERY_ALL_PACKAGES.
     * On Android 11+, if package visibility blocks the lookup, catches the exception and falls back
     * to showing the normalized package name and null icon (which renders a deterministic letter avatar).
     */
    fun resolveAppDisplayInfo(
        context: Context,
        rawPackageName: String,
        packageManagerOverride: PackageManager? = null
    ): ResolvedAppDisplayInfo {
        val normPkg = normalizePackageName(rawPackageName)
        if (normPkg.isBlank()) {
            return ResolvedAppDisplayInfo(
                packageName = "",
                displayLabel = "Unknown App",
                icon = null,
                resolvedFromPackageManager = false
            )
        }
        val pm = packageManagerOverride ?: context.packageManager
        return try {
            val appInfo = pm.getApplicationInfo(normPkg, 0)
            val labelCharSeq = pm.getApplicationLabel(appInfo)
            val label = labelCharSeq.toString().trim().ifBlank { normPkg }
            val icon = try {
                pm.getApplicationIcon(appInfo)
            } catch (_: Exception) {
                null
            }
            ResolvedAppDisplayInfo(
                packageName = normPkg,
                displayLabel = label,
                icon = icon,
                resolvedFromPackageManager = true
            )
        } catch (_: Exception) {
            // Android 11+ package visibility blocked lookup or package not installed:
            // fall back cleanly to the normalized package name.
            ResolvedAppDisplayInfo(
                packageName = normPkg,
                displayLabel = normPkg,
                icon = null,
                resolvedFromPackageManager = false
            )
        }
    }

    /**
     * Verifies a requesting app's [requestedPackage] and [currentSigners] against a credential's
     * stored [CredentialAppMetadata] (checking both the primary `appPackage` and user-confirmed `linkedApps`).
     *
     * Rules (Step 8A.1 & 8A.3):
     * - Package must match normalized string equality (beta/debug variants are distinct unless in `linkedApps`).
     * - All current signing certificates must match the saved SHA-256 set.
     * - If the package matches but the signing certificate changed (or is missing when saved signers exist),
     *   returns [AppSignatureMatchResult.SIGNATURE_CHANGED_WARNING] so the service NEVER fills automatically
     *   and instead shows the publisher-change warning entry requiring explicit confirmation after auth.
     */
    fun verifyAppIdentity(
        requestedPackage: String,
        currentSigners: Set<String>,
        metadata: CredentialAppMetadata
    ): AppSignatureMatchResult {
        val normReqPkg = normalizePackageName(requestedPackage)
        if (normReqPkg.isBlank()) return AppSignatureMatchResult.PACKAGE_MISMATCH
        val normCurrentSigners = normalizeSignerDigests(currentSigners)

        // 1. Check primary appPackage
        if (isSamePackage(metadata.appPackage, normReqPkg)) {
            val savedSigners = normalizeSignerDigests(metadata.appSigners)
            return if (savedSigners.isNotEmpty() && normCurrentSigners == savedSigners) {
                AppSignatureMatchResult.EXACT_MATCH
            } else {
                // Saved signers exist and don't match, or no saved signers were pinned yet
                AppSignatureMatchResult.SIGNATURE_CHANGED_WARNING
            }
        }

        // 2. Check user-confirmed linkedApps
        val matchingLinkedApps = metadata.linkedApps.filter { isSamePackage(it.packageName, normReqPkg) }
        if (matchingLinkedApps.isNotEmpty()) {
            for (linked in matchingLinkedApps) {
                val savedSigners = normalizeSignerDigests(linked.signersSha256)
                if (savedSigners.isNotEmpty() && normCurrentSigners == savedSigners) {
                    return AppSignatureMatchResult.EXACT_MATCH
                }
            }
            return AppSignatureMatchResult.SIGNATURE_CHANGED_WARNING
        }

        return AppSignatureMatchResult.PACKAGE_MISMATCH
    }
}
