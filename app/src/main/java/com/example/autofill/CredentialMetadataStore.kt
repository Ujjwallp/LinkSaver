package com.example.autofill

import android.content.Context
import com.example.security.RecordEncryption
import com.example.security.SecureStorage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encrypted persistent store for native-app autofill metadata (`appPackage`, `appSigners`, `appLabel`),
 * user-confirmed `linkedApps` and `linkedWebsites`, and encrypted `passwordHistory` (with 1-tap Undo).
 *
 * Also supports full export/import via [BackupPayload] so offline backups preserve all app identities,
 * linked apps/websites, and password history across device restores.
 */
class CredentialMetadataStore(private val context: Context) {

    private val prefs = SecureStorage.getEncryptedPrefs(
        context.applicationContext,
        "linksaver_credential_app_metadata_encrypted"
    )

    fun getMetadata(credentialId: Int): CredentialAppMetadata {
        if (credentialId <= 0) return CredentialAppMetadata(credentialId = credentialId)
        val raw = prefs.getString(keyForId(credentialId), null)
            ?: return CredentialAppMetadata(credentialId = credentialId)
        return try {
            fromJson(JSONObject(raw), credentialId)
        } catch (_: Exception) {
            CredentialAppMetadata(credentialId = credentialId)
        }
    }

    fun saveMetadata(metadata: CredentialAppMetadata) {
        if (metadata.credentialId <= 0) return
        val json = toJson(metadata).toString()
        prefs.edit().putString(keyForId(metadata.credentialId), json).apply()
    }

    fun deleteMetadata(credentialId: Int) {
        if (credentialId <= 0) return
        prefs.edit().remove(keyForId(credentialId)).apply()
    }

    /**
     * Records or updates the primary Android application identity for a credential saved from an app.
     */
    fun saveAppIdentity(
        credentialId: Int,
        appPackage: String,
        appSigners: Set<String>,
        appLabel: String
    ): CredentialAppMetadata {
        val normPkg = AppIdentityVerifier.normalizePackageName(appPackage)
        val normSigners = AppIdentityVerifier.normalizeSignerDigests(appSigners)
        val cleanLabel = appLabel.trim().ifBlank { normPkg }
        val current = getMetadata(credentialId)
        val updated = current.copy(
            appPackage = normPkg,
            appSigners = normSigners,
            appLabel = cleanLabel
        )
        saveMetadata(updated)
        return updated
    }

    /**
     * Explicitly confirms and attaches a linked Android application (package + SHA-256 signers)
     * to a credential. Called ONLY after explicit user confirmation.
     */
    fun addConfirmedLinkedApp(
        credentialId: Int,
        packageName: String,
        signersSha256: Set<String>,
        label: String
    ): CredentialAppMetadata {
        val normPkg = AppIdentityVerifier.normalizePackageName(packageName)
        if (normPkg.isBlank()) return getMetadata(credentialId)
        val normSigners = AppIdentityVerifier.normalizeSignerDigests(signersSha256)
        val cleanLabel = label.trim().ifBlank { normPkg }
        val current = getMetadata(credentialId)
        val filtered = current.linkedApps.filterNot {
            AppIdentityVerifier.isSamePackage(it.packageName, normPkg)
        }
        val newEntry = LinkedAppIdentity(
            packageName = normPkg,
            signersSha256 = normSigners,
            label = cleanLabel,
            confirmedAt = System.currentTimeMillis()
        )
        val updated = current.copy(linkedApps = filtered + newEntry)
        saveMetadata(updated)
        return updated
    }

    fun removeLinkedApp(credentialId: Int, packageName: String): CredentialAppMetadata {
        val normPkg = AppIdentityVerifier.normalizePackageName(packageName)
        val current = getMetadata(credentialId)
        val updated = current.copy(
            linkedApps = current.linkedApps.filterNot {
                AppIdentityVerifier.isSamePackage(it.packageName, normPkg)
            }
        )
        saveMetadata(updated)
        return updated
    }

    /**
     * Explicitly confirms and attaches a registrable website domain to a credential.
     * Called ONLY after explicit user confirmation.
     */
    fun addConfirmedLinkedWebsite(
        credentialId: Int,
        domainOrUrl: String
    ): CredentialAppMetadata {
        val normDomain = AppIdentityVerifier.normalizeRegistrableDomain(domainOrUrl)
        if (normDomain.isBlank()) return getMetadata(credentialId)
        val current = getMetadata(credentialId)
        if (current.linkedWebsites.any { it.equals(normDomain, ignoreCase = true) }) {
            return current
        }
        val updated = current.copy(
            linkedWebsites = (current.linkedWebsites + normDomain).distinct()
        )
        saveMetadata(updated)
        return updated
    }

    fun removeLinkedWebsite(credentialId: Int, domainOrUrl: String): CredentialAppMetadata {
        val normDomain = AppIdentityVerifier.normalizeRegistrableDomain(domainOrUrl)
        val current = getMetadata(credentialId)
        val updated = current.copy(
            linkedWebsites = current.linkedWebsites.filterNot {
                it.equals(normDomain, ignoreCase = true)
            }
        )
        saveMetadata(updated)
        return updated
    }

    /**
     * Appends an encrypted historical password before updating a credential's password,
     * keeping up to the 10 most recent entries so the user can audit or Undo.
     */
    fun recordPasswordHistory(
        credentialId: Int,
        previousPlainPassword: String,
        userEmail: String
    ): CredentialAppMetadata {
        if (previousPlainPassword.isBlank()) return getMetadata(credentialId)
        val encrypted = RecordEncryption.encrypt(
            context = context,
            plaintext = previousPlainPassword,
            recordId = credentialId,
            userEmail = userEmail
        )
        val current = getMetadata(credentialId)
        val newEntry = PasswordHistoryEntry(
            encryptedPassword = encrypted,
            changedAt = System.currentTimeMillis()
        )
        val updatedHistory = (listOf(newEntry) + current.passwordHistory).take(10)
        val updated = current.copy(passwordHistory = updatedHistory)
        saveMetadata(updated)
        return updated
    }

    /**
     * Pops the most recent historical password, decrypts it for restoration, and removes it from history.
     * Returns null if there is no password history.
     */
    fun popMostRecentHistoricalPassword(
        credentialId: Int,
        userEmail: String,
        isVaultUnlocked: Boolean = true
    ): String? {
        val current = getMetadata(credentialId)
        val latest = current.passwordHistory.firstOrNull() ?: return null
        val decrypted = RecordEncryption.decrypt(
            context = context,
            storedValue = latest.encryptedPassword,
            recordId = credentialId,
            userEmail = userEmail,
            isUnlocked = isVaultUnlocked
        )
        if (decrypted.isBlank()) return null
        val updated = current.copy(passwordHistory = current.passwordHistory.drop(1))
        saveMetadata(updated)
        return decrypted
    }

    /**
     * Decrypts all historical passwords on-demand for viewing in the Credential Detail screen.
     */
    fun getDecryptedPasswordHistory(
        credentialId: Int,
        userEmail: String,
        isVaultUnlocked: Boolean = true
    ): List<Pair<String, Long>> {
        if (!isVaultUnlocked) return emptyList()
        val current = getMetadata(credentialId)
        return current.passwordHistory.mapNotNull { entry ->
            val plain = RecordEncryption.decrypt(
                context = context,
                storedValue = entry.encryptedPassword,
                recordId = credentialId,
                userEmail = userEmail,
                isUnlocked = true
            )
            if (plain.isNotBlank()) Pair(plain, entry.changedAt) else null
        }
    }

    fun getAllMetadataMap(): Map<Int, CredentialAppMetadata> {
        val result = mutableMapOf<Int, CredentialAppMetadata>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(KEY_PREFIX) && value is String) {
                val id = key.removePrefix(KEY_PREFIX).toIntOrNull() ?: continue
                try {
                    result[id] = fromJson(JSONObject(value), id)
                } catch (_: Exception) {
                }
            }
        }
        return result
    }

    fun restoreAllMetadata(items: List<CredentialAppMetadata>, replaceExisting: Boolean) {
        val editor = prefs.edit()
        if (replaceExisting) {
            for (key in prefs.all.keys) {
                if (key.startsWith(KEY_PREFIX)) {
                    editor.remove(key)
                }
            }
        }
        for (item in items) {
            if (item.credentialId > 0) {
                editor.putString(keyForId(item.credentialId), toJson(item).toString())
            }
        }
        editor.apply()
    }

    fun clearAll() {
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(KEY_PREFIX)) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    companion object {
        private const val KEY_PREFIX = "cred_app_meta_"

        private fun keyForId(id: Int): String = "$KEY_PREFIX$id"

        fun toJson(meta: CredentialAppMetadata): JSONObject {
            val obj = JSONObject()
            obj.put("credentialId", meta.credentialId)
            obj.put("appPackage", meta.appPackage)
            obj.put("appLabel", meta.appLabel)

            val signersArr = JSONArray()
            meta.appSigners.forEach { signersArr.put(it) }
            obj.put("appSigners", signersArr)

            val linkedAppsArr = JSONArray()
            meta.linkedApps.forEach { app ->
                val aObj = JSONObject()
                aObj.put("packageName", app.packageName)
                aObj.put("label", app.label)
                aObj.put("confirmedAt", app.confirmedAt)
                val sArr = JSONArray()
                app.signersSha256.forEach { sArr.put(it) }
                aObj.put("signersSha256", sArr)
                linkedAppsArr.put(aObj)
            }
            obj.put("linkedApps", linkedAppsArr)

            val websitesArr = JSONArray()
            meta.linkedWebsites.forEach { websitesArr.put(it) }
            obj.put("linkedWebsites", websitesArr)

            val historyArr = JSONArray()
            meta.passwordHistory.forEach { h ->
                val hObj = JSONObject()
                hObj.put("encryptedPassword", h.encryptedPassword)
                hObj.put("changedAt", h.changedAt)
                historyArr.put(hObj)
            }
            obj.put("passwordHistory", historyArr)
            return obj
        }

        fun fromJson(obj: JSONObject, fallbackId: Int = 0): CredentialAppMetadata {
            val id = obj.optInt("credentialId", fallbackId)
            val appPackage = AppIdentityVerifier.normalizePackageName(obj.optString("appPackage", ""))
            val appLabel = obj.optString("appLabel", "")

            val signers = mutableSetOf<String>()
            val signersArr = obj.optJSONArray("appSigners")
            if (signersArr != null) {
                for (i in 0 until signersArr.length()) {
                    val s = signersArr.optString(i, "")
                    if (s.isNotBlank()) signers.add(s)
                }
            }

            val linkedApps = mutableListOf<LinkedAppIdentity>()
            val linkedAppsArr = obj.optJSONArray("linkedApps")
            if (linkedAppsArr != null) {
                for (i in 0 until linkedAppsArr.length()) {
                    val aObj = linkedAppsArr.optJSONObject(i) ?: continue
                    val pkg = AppIdentityVerifier.normalizePackageName(aObj.optString("packageName", ""))
                    if (pkg.isBlank()) continue
                    val sSet = mutableSetOf<String>()
                    val sArr = aObj.optJSONArray("signersSha256")
                    if (sArr != null) {
                        for (j in 0 until sArr.length()) {
                            val s = sArr.optString(j, "")
                            if (s.isNotBlank()) sSet.add(s)
                        }
                    }
                    linkedApps.add(
                        LinkedAppIdentity(
                            packageName = pkg,
                            signersSha256 = AppIdentityVerifier.normalizeSignerDigests(sSet),
                            label = aObj.optString("label", pkg).ifBlank { pkg },
                            confirmedAt = aObj.optLong("confirmedAt", 0L)
                        )
                    )
                }
            }

            val linkedWebsites = mutableListOf<String>()
            val websitesArr = obj.optJSONArray("linkedWebsites")
            if (websitesArr != null) {
                for (i in 0 until websitesArr.length()) {
                    val d = AppIdentityVerifier.normalizeRegistrableDomain(websitesArr.optString(i, ""))
                    if (d.isNotBlank()) linkedWebsites.add(d)
                }
            }

            val history = mutableListOf<PasswordHistoryEntry>()
            val historyArr = obj.optJSONArray("passwordHistory")
            if (historyArr != null) {
                for (i in 0 until historyArr.length()) {
                    val hObj = historyArr.optJSONObject(i) ?: continue
                    val enc = hObj.optString("encryptedPassword", "")
                    if (enc.isNotBlank()) {
                        history.add(
                            PasswordHistoryEntry(
                                encryptedPassword = enc,
                                changedAt = hObj.optLong("changedAt", 0L)
                            )
                        )
                    }
                }
            }

            return CredentialAppMetadata(
                credentialId = id,
                appPackage = appPackage,
                appSigners = AppIdentityVerifier.normalizeSignerDigests(signers),
                appLabel = appLabel,
                linkedApps = linkedApps,
                linkedWebsites = linkedWebsites.distinct(),
                passwordHistory = history
            )
        }
    }
}
