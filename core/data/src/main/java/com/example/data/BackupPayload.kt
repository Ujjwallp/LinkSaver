package com.example.data

import com.example.autofill.CredentialAppMetadata
import com.example.autofill.CredentialMetadataStore
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class BackupAttachment(
    val fileName: String,
    val mimeType: String,
    val base64Data: String,
    val checksumSha256: String
)

data class BackupManifest(
    val appVersion: String,
    val schemaVersion: Int,
    val linkCount: Int,
    val profileCount: Int,
    val attachmentCount: Int,
    val linksSha256: String,
    val profilesSha256: String,
    val totalPayloadSha256: String
)

data class BackupPayload(
    val links: List<LinkEntity>,
    val profiles: List<LocalUser>,
    val customCategories: List<String>,
    val neverSaveList: List<String> = emptyList(),
    val credentialAppMetadata: List<CredentialAppMetadata> = emptyList(),
    val attachments: List<BackupAttachment> = emptyList(),
    val isDarkTheme: Boolean,
    val autoBackupEnabled: Boolean,
    val schemaVersion: Int = 3,
    val appVersion: String = "1.0",
    val backupTime: Long = System.currentTimeMillis()
) {
    fun calculateManifest(): BackupManifest {
        val linksStr = links.joinToString("|") { "${it.id}:${it.url}:${it.title}:${it.userEmail}" }
        val profilesStr = profiles.joinToString("|") { "${it.email}:${it.name}" }

        val linksSha = sha256Hex(linksStr)
        val profilesSha = sha256Hex(profilesStr)
        val totalSha = sha256Hex("$linksSha:$profilesSha:$backupTime:$schemaVersion")

        return BackupManifest(
            appVersion = appVersion,
            schemaVersion = schemaVersion,
            linkCount = links.size,
            profileCount = profiles.size,
            attachmentCount = attachments.size,
            linksSha256 = linksSha,
            profilesSha256 = profilesSha,
            totalPayloadSha256 = totalSha
        )
    }

    fun toJsonString(): String {
        val root = JSONObject()
        root.put("backupTime", backupTime)
        root.put("schemaVersion", schemaVersion)
        root.put("appVersion", appVersion)
        root.put("isDarkTheme", isDarkTheme)
        root.put("autoBackupEnabled", autoBackupEnabled)

        // Manifest
        val manifest = calculateManifest()
        val mobj = JSONObject()
        mobj.put("appVersion", manifest.appVersion)
        mobj.put("schemaVersion", manifest.schemaVersion)
        mobj.put("linkCount", manifest.linkCount)
        mobj.put("profileCount", manifest.profileCount)
        mobj.put("attachmentCount", manifest.attachmentCount)
        mobj.put("linksSha256", manifest.linksSha256)
        mobj.put("profilesSha256", manifest.profilesSha256)
        mobj.put("totalPayloadSha256", manifest.totalPayloadSha256)
        root.put("manifest", mobj)

        // Custom Categories
        val categoriesArray = JSONArray()
        customCategories.forEach { categoriesArray.put(it) }
        root.put("customCategories", categoriesArray)

        // Never Save List
        val neverSaveArray = JSONArray()
        neverSaveList.forEach { neverSaveArray.put(it) }
        root.put("neverSaveList", neverSaveArray)

        // Credential App Metadata (package, SHA-256 signers, linked apps/websites, password history)
        val credMetaArray = JSONArray()
        credentialAppMetadata.forEach { meta ->
            credMetaArray.put(CredentialMetadataStore.toJson(meta))
        }
        root.put("credentialAppMetadata", credMetaArray)

        // Links
        val linksArray = JSONArray()
        links.forEach { link ->
            val robj = JSONObject()
            robj.put("id", link.id)
            robj.put("url", link.url)
            robj.put("title", link.title)
            robj.put("description", link.description)
            robj.put("timestamp", link.timestamp)
            robj.put("category", link.category)
            robj.put("isFavorite", link.isFavorite)
            robj.put("note", link.note)
            robj.put("userEmail", link.userEmail)
            robj.put("openCount", link.openCount)
            linksArray.put(robj)
        }
        root.put("links", linksArray)

        // Profiles
        val profilesArray = JSONArray()
        profiles.forEach { user ->
            val pobj = JSONObject()
            pobj.put("email", user.email)
            pobj.put("name", user.name)
            pobj.put("passcode", user.passcode)
            pobj.put("passcodeType", user.passcodeType)
            pobj.put("biometricAllowed", user.biometricAllowed)
            pobj.put("lockEnabled", user.lockEnabled)
            profilesArray.put(pobj)
        }
        root.put("profiles", profilesArray)

        // Attachments
        val attachmentsArray = JSONArray()
        attachments.forEach { att ->
            val aobj = JSONObject()
            aobj.put("fileName", att.fileName)
            aobj.put("mimeType", att.mimeType)
            aobj.put("base64Data", att.base64Data)
            aobj.put("checksumSha256", att.checksumSha256)
            attachmentsArray.put(aobj)
        }
        root.put("attachments", attachmentsArray)

        return root.toString()
    }

    companion object {
        fun fromJsonString(jsonStr: String): BackupPayload {
            val root = JSONObject(jsonStr)
            val backupTime = root.optLong("backupTime", System.currentTimeMillis())
            val schemaVersion = root.optInt("schemaVersion", 3)
            val appVersion = root.optString("appVersion", "1.0")
            val isDarkTheme = root.optBoolean("isDarkTheme", true)
            val autoBackupEnabled = root.optBoolean("autoBackupEnabled", false)

            val customCategories = mutableListOf<String>()
            val categoriesArray = root.optJSONArray("customCategories")
            if (categoriesArray != null) {
                for (i in 0 until categoriesArray.length()) {
                    customCategories.add(categoriesArray.getString(i))
                }
            }

            val neverSaveList = mutableListOf<String>()
            val neverArray = root.optJSONArray("neverSaveList")
            if (neverArray != null) {
                for (i in 0 until neverArray.length()) {
                    neverSaveList.add(neverArray.getString(i))
                }
            }

            val credentialMetaList = mutableListOf<CredentialAppMetadata>()
            val credMetaArray = root.optJSONArray("credentialAppMetadata")
            if (credMetaArray != null) {
                for (i in 0 until credMetaArray.length()) {
                    val mObj = credMetaArray.optJSONObject(i) ?: continue
                    credentialMetaList.add(CredentialMetadataStore.fromJson(mObj))
                }
            }

            val linksList = mutableListOf<LinkEntity>()
            val linksArray = root.optJSONArray("links")
            if (linksArray != null) {
                for (i in 0 until linksArray.length()) {
                    val obj = linksArray.getJSONObject(i)
                    linksList.add(
                        LinkEntity(
                            id = obj.optInt("id", 0),
                            url = obj.optString("url", ""),
                            title = obj.optString("title", ""),
                            description = obj.optString("description", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            category = obj.optString("category", "📁 Uncategorized"),
                            isFavorite = obj.optBoolean("isFavorite", false),
                            note = obj.optString("note", ""),
                            userEmail = obj.optString("userEmail", ""),
                            openCount = obj.optInt("openCount", 0)
                        )
                    )
                }
            }

            val profilesList = mutableListOf<LocalUser>()
            val profilesArray = root.optJSONArray("profiles")
            if (profilesArray != null) {
                for (i in 0 until profilesArray.length()) {
                    val obj = profilesArray.getJSONObject(i)
                    profilesList.add(
                        LocalUser(
                            email = obj.optString("email", ""),
                            name = obj.optString("name", ""),
                            passcode = obj.optString("passcode", ""),
                            passcodeType = obj.optString("passcodeType", "FINGERPRINT"),
                            biometricAllowed = obj.optBoolean("biometricAllowed", false),
                            lockEnabled = obj.optBoolean("lockEnabled", false)
                        )
                    )
                }
            }

            val attachmentsList = mutableListOf<BackupAttachment>()
            val attachmentsArray = root.optJSONArray("attachments")
            if (attachmentsArray != null) {
                for (i in 0 until attachmentsArray.length()) {
                    val obj = attachmentsArray.getJSONObject(i)
                    attachmentsList.add(
                        BackupAttachment(
                            fileName = obj.optString("fileName", ""),
                            mimeType = obj.optString("mimeType", ""),
                            base64Data = obj.optString("base64Data", ""),
                            checksumSha256 = obj.optString("checksumSha256", "")
                        )
                    )
                }
            }

            return BackupPayload(
                links = linksList,
                profiles = profilesList,
                customCategories = customCategories,
                neverSaveList = neverSaveList,
                credentialAppMetadata = credentialMetaList,
                attachments = attachmentsList,
                isDarkTheme = isDarkTheme,
                autoBackupEnabled = autoBackupEnabled,
                schemaVersion = schemaVersion,
                appVersion = appVersion,
                backupTime = backupTime
            )
        }

        private fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
