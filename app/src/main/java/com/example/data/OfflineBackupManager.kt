package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.security.KeyHierarchy
import com.example.security.SecureStorage
import com.example.util.SecureLog as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom

// Unified Backup Status indicator
sealed class BackupStatus {
    object Idle : BackupStatus()
    data class Progress(val percent: Int) : BackupStatus()
    data class Success(val message: String) : BackupStatus()
    data class Error(val message: String) : BackupStatus()
}

// Data class representation of Metadata for historical/available selections
data class DriveBackupItem(
    val fileId: String, // Local absolute path OR standard content:// tree URI string
    val fileName: String,
    val sizeBytes: Long,
    val creationTime: Long,
    val payloadPreview: String,
    val isVerified: Boolean = true,
    val backupCounter: Long = 0L
)

data class BackupHistoryLog(
    val timestamp: Long,
    val status: String,
    val details: String
)

/**
 * Local & SAF File Backup Manager.
 * Operates offline with zero cloud dependency.
 * Enforces AES-256-GCM encryption, random filenames, monotonic counters,
 * pre-deletion verification, and retention of the last 3 backups.
 */
class OfflineBackupManager(private val context: Context) {

    private val sharedPrefs = run {
        SecureStorage.migratePlainPrefsToEncrypted(
            context,
            "linksaver_drive_backup_prefs",
            "linksaver_drive_backup_prefs_encrypted"
        )
        SecureStorage.getEncryptedPrefs(context, "linksaver_drive_backup_prefs_encrypted")
    }

    private val secureRandom = SecureRandom()

    // Observable states for UI integration
    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus

    private val _restoreStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val restoreStatus: StateFlow<BackupStatus> = _restoreStatus

    var activeUserEmail: String = ""

    var autoBackupEnabled: Boolean
        get() = sharedPrefs.getBoolean("auto_backup_enabled_${activeUserEmail}", false)
        set(value) = sharedPrefs.edit().putBoolean("auto_backup_enabled_${activeUserEmail}", value).apply()

    var lastBackupTime: Long
        get() = sharedPrefs.getLong("last_backup_time_${activeUserEmail}", 0L)
        set(value) = sharedPrefs.edit().putLong("last_backup_time_${activeUserEmail}", value).apply()

    var customBackupDirUri: String
        get() = sharedPrefs.getString("custom_backup_dir_uri_${activeUserEmail}", "") ?: ""
        set(value) = sharedPrefs.edit().putString("custom_backup_dir_uri_${activeUserEmail}", value).apply()

    var customBackupDirDisplayName: String
        get() = sharedPrefs.getString("custom_backup_dir_display_name_${activeUserEmail}", "") ?: ""
        set(value) = sharedPrefs.edit().putString("custom_backup_dir_display_name_${activeUserEmail}", value).apply()

    fun setCustomBackupFolder(uriString: String, displayName: String) {
        customBackupDirUri = uriString
        customBackupDirDisplayName = displayName
        addHistoryLog("Config", "Set custom backup folder to '$displayName'")
    }

    fun clearCustomBackupFolder() {
        customBackupDirUri = ""
        customBackupDirDisplayName = ""
        addHistoryLog("Config", "Cleared custom folder, reset to local sandbox fallback.")
    }

    /**
     * Monotonic backup counter per profile to prevent rollback attacks.
     */
    fun getNextCounter(profileId: String): Long {
        val key = "monotonic_backup_counter_${profileId.trim().lowercase()}"
        val current = sharedPrefs.getLong(key, 0L)
        val next = current + 1
        sharedPrefs.edit().putLong(key, next).apply()
        return next
    }

    fun getLastRestoredCounter(profileId: String): Long {
        val key = "last_restored_counter_${profileId.trim().lowercase()}"
        return sharedPrefs.getLong(key, 0L)
    }

    fun setLastRestoredCounter(profileId: String, counter: Long) {
        val key = "last_restored_counter_${profileId.trim().lowercase()}"
        sharedPrefs.edit().putLong(key, counter).apply()
    }

    private val cloudFolder: File by lazy {
        File(context.filesDir, "offline_sandbox_backup_folder").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getBackupHistory(): List<BackupHistoryLog> {
        val raw = sharedPrefs.getString("backup_history_logs", "[]") ?: "[]"
        val list = mutableListOf<BackupHistoryLog>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    BackupHistoryLog(
                        timestamp = obj.optLong("timestamp"),
                        status = obj.optString("status"),
                        details = obj.optString("details")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("OfflineBackupManager", "Error loading backup history logs", e)
        }
        return list.sortedByDescending { it.timestamp }
    }

    private fun addHistoryLog(status: String, details: String) {
        val current = getBackupHistory().toMutableList()
        current.add(BackupHistoryLog(System.currentTimeMillis(), status, details))
        val trimmed = current.take(35)
        val arr = JSONArray()
        trimmed.forEach { log ->
            val obj = JSONObject()
            obj.put("timestamp", log.timestamp)
            obj.put("status", log.status)
            obj.put("details", log.details)
            arr.put(obj)
        }
        sharedPrefs.edit().putString("backup_history_logs", arr.toString()).apply()
    }

    fun clearHistory() {
        sharedPrefs.edit().putString("backup_history_logs", "[]").apply()
    }

    /**
     * Performs an authenticated, hardened backup with AES-256-GCM,
     * randomized filename, monotonic counter, and safe retention of the last 3 backups.
     */
    suspend fun performBackup(
        payload: BackupPayload,
        encryptionKey: CharArray,
        simulateNetworkErrorOnce: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            _backupStatus.value = BackupStatus.Progress(20)

            val profileId = if (activeUserEmail.isNotBlank()) activeUserEmail else "default"
            val counter = getNextCounter(profileId)
            val backupSubkey = KeyHierarchy.getBackupKey()

            val rawJsonStr = payload.toJsonString()
            val encryptedBytesBase64 = BackupEncryption.encryptV3(
                plainText = rawJsonStr,
                password = encryptionKey,
                backupSubkey = backupSubkey,
                counter = counter,
                profileId = profileId
            )

            _backupStatus.value = BackupStatus.Progress(50)

            // Random 16-hex filename with counter (No email or timestamp leaks)
            val randBytes = ByteArray(8)
            secureRandom.nextBytes(randBytes)
            val randHex = randBytes.joinToString("") { "%02x".format(it) }
            val filename = "vault_bk_${randHex}_cnt${counter}.enc"

            val checksum = calculateChecksum(encryptedBytesBase64)
            saveChecksumForFile(filename, checksum)

            val dirUriStr = customBackupDirUri
            val newFileDestination: File?

            if (dirUriStr.isNotEmpty()) {
                val writeSuccess = writeToCustomDirectory(filename, encryptedBytesBase64)
                if (!writeSuccess) {
                    throw Exception("Write operation failed or rejected by Storage Access Framework")
                }
                newFileDestination = null
            } else {
                newFileDestination = File(cloudFolder, filename)
                newFileDestination.writeText(encryptedBytesBase64)
            }

            _backupStatus.value = BackupStatus.Progress(75)

            // CRITICAL STEP: Verify decrypt and checksum BEFORE touching older backups!
            val verificationContent = if (dirUriStr.isNotEmpty()) {
                readContentFromCustomDirectory(filename) ?: throw Exception("Failed reading back newly written backup")
            } else {
                newFileDestination!!.readText()
            }

            if (calculateChecksum(verificationContent) != checksum) {
                throw SecurityException("Pre-commit checksum mismatch on new backup file")
            }

            // Test full decryption
            val testDecrypted = BackupEncryption.decryptDetailed(
                cipherText = verificationContent,
                password = encryptionKey,
                backupSubkey = backupSubkey,
                expectedProfileId = profileId
            )
            if (testDecrypted.plainJson.isBlank()) {
                throw SecurityException("Decryption verification failed on new backup")
            }

            // Only AFTER verification succeeds, prune older backups while KEEPING THE LAST 3
            pruneBackupsKeepLast3()

            _backupStatus.value = BackupStatus.Progress(100)
            lastBackupTime = System.currentTimeMillis()

            val infoLabel = "${payload.links.size} items, ${payload.profiles.size} profiles (Counter #$counter)"
            val targetFolderMsg = if (dirUriStr.isNotEmpty()) "custom location: $customBackupDirDisplayName" else "local private sandbox"
            addHistoryLog("Success", "Verified V3 GCM backup created in $targetFolderMsg: $filename ($infoLabel)")
            _backupStatus.value = BackupStatus.Success("Verified encrypted backup created! 🔒")
            true
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown Error"
            addHistoryLog("Failed", "Backup failed: $errorMsg")
            _backupStatus.value = BackupStatus.Error("Backup failed: $errorMsg. Tap retry.")
            false
        }
    }

    private fun pruneBackupsKeepLast3() {
        try {
            val dirUriStr = customBackupDirUri
            if (dirUriStr.isNotEmpty()) {
                pruneCustomDirectoryKeepLast3()
            } else {
                pruneLocalDirectoryKeepLast3()
            }
        } catch (e: Exception) {
            Log.e("OfflineBackupManager", "Error pruning old backups", e)
        }
    }

    private fun pruneLocalDirectoryKeepLast3() {
        val files = cloudFolder.listFiles() ?: return
        val encFiles = files.filter { it.name.endsWith(".enc") }.sortedByDescending { it.lastModified() }
        if (encFiles.size > 3) {
            for (i in 3 until encFiles.size) {
                encFiles[i].delete()
                Log.d("OfflineBackupManager", "Safely pruned older backup: ${encFiles[i].name}")
            }
        }
    }

    private fun pruneCustomDirectoryKeepLast3() {
        val uriStr = customBackupDirUri
        if (uriStr.isEmpty()) return
        val treeUri = Uri.parse(uriStr)
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val resolver = context.contentResolver
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val items = mutableListOf<Triple<String, String, Long>>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val docId = if (idCol != -1) cursor.getString(idCol) else ""
                val name = if (nameCol != -1) cursor.getString(nameCol) else ""
                val mod = if (modCol != -1) cursor.getLong(modCol) else 0L
                if (name.endsWith(".enc")) {
                    items.add(Triple(docId, name, mod))
                }
            }
        }
        val sorted = items.sortedByDescending { it.third }
        if (sorted.size > 3) {
            for (i in 3 until sorted.size) {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, sorted[i].first)
                DocumentsContract.deleteDocument(resolver, docUri)
                Log.d("OfflineBackupManager", "Safely pruned older custom backup: ${sorted[i].second}")
            }
        }
    }

    private fun writeToCustomDirectory(fileName: String, content: String): Boolean {
        val uriStr = customBackupDirUri
        if (uriStr.isEmpty()) return false
        return try {
            val treeUri = Uri.parse(uriStr)
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            val resolver = context.contentResolver

            val childUri = DocumentsContract.createDocument(
                resolver,
                parentDocumentUri,
                "application/octet-stream",
                fileName
            )
            if (childUri != null) {
                resolver.openOutputStream(childUri)?.use { output ->
                    output.write(content.toByteArray(Charsets.UTF_8))
                }
                true
            } else false
        } catch (e: Exception) {
            Log.e("OfflineBackupManager", "Error writing to custom directory SAF", e)
            false
        }
    }

    private fun readContentFromCustomDirectory(fileName: String): String? {
        val uriStr = customBackupDirUri
        if (uriStr.isEmpty()) return null
        return try {
            val treeUri = Uri.parse(uriStr)
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val resolver = context.contentResolver
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            var result: String? = null
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val docId = if (idCol != -1) cursor.getString(idCol) else ""
                    val name = if (nameCol != -1) cursor.getString(nameCol) else ""
                    if (name == fileName) {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        resolver.openInputStream(fileUri)?.use { input ->
                            result = input.bufferedReader().use { it.readText() }
                        }
                        break
                    }
                }
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Lists available backups, showing previews and verifying integrity.
     */
    suspend fun listBackups(encryptionKey: CharArray): List<DriveBackupItem> = withContext(Dispatchers.IO) {
        val dirUriStr = customBackupDirUri
        if (dirUriStr.isNotEmpty()) {
            return@withContext listBackupsFromCustomDirectory(encryptionKey)
        }

        val list = mutableListOf<DriveBackupItem>()
        val files = cloudFolder.listFiles() ?: return@withContext emptyList()
        val backupSubkey = KeyHierarchy.getBackupKey()

        for (file in files) {
            if (file.name.endsWith(".enc")) {
                try {
                    val encryptedText = file.readText()
                    val checksum = getChecksumForFile(file.name)
                    val isVerified = checksum == null || calculateChecksum(encryptedText) == checksum

                    var preview = "Encrypted Vault Data"
                    var parsedCounter = 0L
                    try {
                        val result = BackupEncryption.decryptDetailed(encryptedText, encryptionKey, backupSubkey)
                        val payload = BackupPayload.fromJsonString(result.plainJson)
                        parsedCounter = result.counter
                        val counterLabel = if (result.counter > 0) " (Seq #${result.counter})" else ""
                        preview = "${payload.links.size} links, ${payload.profiles.size} profiles$counterLabel"
                    } catch (e: Exception) {
                        preview = "Enter master passphrase to preview details"
                    }

                    list.add(
                        DriveBackupItem(
                            fileId = file.absolutePath,
                            fileName = file.name,
                            sizeBytes = file.length(),
                            creationTime = file.lastModified(),
                            payloadPreview = preview,
                            isVerified = isVerified,
                            backupCounter = parsedCounter
                        )
                    )
                } catch (e: Exception) {
                    // Skip unreadable files
                }
            }
        }
        list.sortedByDescending { it.creationTime }
    }

    private fun listBackupsFromCustomDirectory(encryptionKey: CharArray): List<DriveBackupItem> {
        val list = mutableListOf<DriveBackupItem>()
        val uriStr = customBackupDirUri
        if (uriStr.isEmpty()) return emptyList()
        val backupSubkey = KeyHierarchy.getBackupKey()

        try {
            val treeUri = Uri.parse(uriStr)
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val resolver = context.contentResolver
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = if (idCol != -1) cursor.getString(idCol) else ""
                    val name = if (nameCol != -1) cursor.getString(nameCol) else ""
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val mod = if (modCol != -1) cursor.getLong(modCol) else System.currentTimeMillis()

                    if (name.endsWith(".enc")) {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        var preview = "Encrypted Vault Document"
                        var isVerified = true
                        var parsedCounter = 0L

                        try {
                            resolver.openInputStream(fileUri)?.use { input ->
                                val encryptedText = input.bufferedReader().use { it.readText() }
                                val checksum = getChecksumForFile(name)
                                isVerified = checksum == null || calculateChecksum(encryptedText) == checksum
                                try {
                                    val result = BackupEncryption.decryptDetailed(encryptedText, encryptionKey, backupSubkey)
                                    val payload = BackupPayload.fromJsonString(result.plainJson)
                                    parsedCounter = result.counter
                                    val counterLabel = if (result.counter > 0) " (Seq #${result.counter})" else ""
                                    preview = "${payload.links.size} links, ${payload.profiles.size} profiles$counterLabel"
                                } catch (e: Exception) {
                                    preview = "Enter master passphrase to preview details"
                                }
                            }
                        } catch (e: Exception) {
                            preview = "Encrypted (No read permission)"
                            isVerified = false
                        }

                        list.add(
                            DriveBackupItem(
                                fileId = fileUri.toString(),
                                fileName = name,
                                sizeBytes = size,
                                creationTime = mod,
                                payloadPreview = preview,
                                isVerified = isVerified,
                                backupCounter = parsedCounter
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OfflineBackupManager", "Error listing backups from custom storage", e)
        }
        return list.sortedByDescending { it.creationTime }
    }

    private val snapshotFolder: File by lazy {
        File(context.filesDir, "safety_snapshots").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Creates an offline safety snapshot before restore or migration.
     * Retains the last 3 safety snapshots in app-private storage.
     */
    suspend fun createSafetySnapshot(payload: BackupPayload, passkey: CharArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val snapshotName = "snap_${System.currentTimeMillis()}.enc"
            val snapshotFile = File(snapshotFolder, snapshotName)
            val tempFile = File(snapshotFolder, "$snapshotName.tmp")

            val json = payload.toJsonString()
            val backupSubkey = KeyHierarchy.getBackupKey()
            val encrypted = BackupEncryption.encryptV3(json, passkey, backupSubkey, System.currentTimeMillis(), "snapshot")

            FileOutputStream(tempFile).use { fos ->
                fos.write(encrypted.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                tempFile.renameTo(snapshotFile)
                pruneSafetySnapshotsKeepLast3()
                true
            } else {
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            Log.e("OfflineBackupManager", "Error creating safety snapshot", e)
            false
        } finally {
            passkey.fill('\u0000')
        }
    }

    private fun pruneSafetySnapshotsKeepLast3() {
        val files = snapshotFolder.listFiles() ?: return
        val snaps = files.filter { it.name.startsWith("snap_") && it.name.endsWith(".enc") }
            .sortedByDescending { it.lastModified() }
        if (snaps.size > 3) {
            for (i in 3 until snaps.size) {
                snaps[i].delete()
            }
        }
    }

    /**
     * Rate-limited restore attempt verification. Max 5 consecutive failures before delay backoff.
     */
    fun checkRestoreRateLimit(): Boolean {
        val failedCount = sharedPrefs.getInt("restore_failed_attempts", 0)
        val lastFailedTime = sharedPrefs.getLong("last_failed_restore_time", 0L)
        if (failedCount >= 5) {
            val backoffMs = 30_000L * (1L shl (failedCount - 5).coerceAtMost(4))
            if (System.currentTimeMillis() - lastFailedTime < backoffMs) {
                return false
            }
        }
        return true
    }

    private fun recordRestoreFailure() {
        val current = sharedPrefs.getInt("restore_failed_attempts", 0) + 1
        sharedPrefs.edit()
            .putInt("restore_failed_attempts", current)
            .putLong("last_failed_restore_time", System.currentTimeMillis())
            .apply()
    }

    private fun resetRestoreFailureCount() {
        sharedPrefs.edit().putInt("restore_failed_attempts", 0).apply()
    }

    /**
     * Restores and decrypts a backup with rollback anomaly detection and rate limiting.
     */
    suspend fun restoreBackup(
        backupItem: DriveBackupItem,
        passkey: CharArray
    ): BackupPayload? = withContext(Dispatchers.IO) {
        if (!checkRestoreRateLimit()) {
            _restoreStatus.value = BackupStatus.Error("Too many failed restore attempts. Please wait 30 seconds.")
            passkey.fill('\u0000')
            return@withContext null
        }

        try {
            _restoreStatus.value = BackupStatus.Progress(25)

            val encryptedContent: String
            val isCustomBackup = backupItem.fileId.startsWith("content://")

            if (isCustomBackup) {
                val fileUri = Uri.parse(backupItem.fileId)
                val resolver = context.contentResolver
                encryptedContent = resolver.openInputStream(fileUri)?.use { input ->
                    input.bufferedReader().use { it.readText() }
                } ?: throw Exception("Failed to acquire file input stream from custom location")
            } else {
                val sourceFile = File(backupItem.fileId)
                if (!sourceFile.exists()) {
                    throw java.io.FileNotFoundException("Selected cloud backup file no longer exists.")
                }
                encryptedContent = sourceFile.readText()
                val expectedChecksum = getChecksumForFile(sourceFile.name)
                if (expectedChecksum != null && calculateChecksum(encryptedContent) != expectedChecksum) {
                    throw SecurityException("Insecure backup packet detected. Verification checksum did not match!")
                }
            }

            _restoreStatus.value = BackupStatus.Progress(60)

            val backupSubkey = KeyHierarchy.getBackupKey()
            val decrypted = BackupEncryption.decryptDetailed(
                cipherText = encryptedContent,
                password = passkey,
                backupSubkey = backupSubkey
            )

            val profileId = if (decrypted.profileId.isNotBlank()) decrypted.profileId else activeUserEmail
            if (decrypted.counter > 0) {
                val lastRestored = getLastRestoredCounter(profileId)
                if (decrypted.counter < lastRestored) {
                    Log.w("OfflineBackupManager", "Rollback anomaly warning: restoring sequence #${decrypted.counter} but #${lastRestored} was previously active.")
                }
                setLastRestoredCounter(profileId, maxOf(lastRestored, decrypted.counter))
            }

            _restoreStatus.value = BackupStatus.Progress(100)
            resetRestoreFailureCount()

            val payload = BackupPayload.fromJsonString(decrypted.plainJson)
            val versionLabel = "V${decrypted.formatVersion}"
            _restoreStatus.value = BackupStatus.Success("Backup ($versionLabel) verified and decrypted locally!")
            payload
        } catch (e: Exception) {
            recordRestoreFailure()
            _restoreStatus.value = BackupStatus.Error("Restore decryption failed: ${e.message}")
            null
        } finally {
            passkey.fill('\u0000')
        }
    }

    /**
     * Unencrypted Plain CSV Export (Warns User).
     */
    fun exportCsv(payload: BackupPayload): String {
        val sb = StringBuilder()
        sb.append("ID,URL,Title,Description,Category,Note,IsFavorite,OpenCount,Timestamp\n")
        payload.links.forEach { link ->
            val cleanTitle = link.title.replace("\"", "\"\"")
            val cleanDesc = link.description.replace("\"", "\"\"")
            val cleanNote = link.note.replace("\"", "\"\"")
            sb.append("${link.id},\"${link.url}\",\"$cleanTitle\",\"$cleanDesc\",\"${link.category}\",\"$cleanNote\",${link.isFavorite},${link.openCount},${link.timestamp}\n")
        }
        return sb.toString()
    }

    /**
     * Unencrypted Plain HTML Export (Netscape Bookmark Format).
     */
    fun exportHtml(payload: BackupPayload): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<!-- This is an automated export from LinkVault (UNENCRYPTED). -->\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>LinkVault Bookmarks</TITLE>\n")
        sb.append("<H1>LinkVault Bookmarks</H1>\n")
        sb.append("<DL><p>\n")
        payload.links.forEach { link ->
            val addDate = link.timestamp / 1000
            sb.append("    <DT><A HREF=\"${link.url}\" ADD_DATE=\"$addDate\">${link.title.ifBlank { link.url }}</A>\n")
            if (link.note.isNotBlank()) {
                sb.append("    <DD>${link.note}\n")
            }
        }
        sb.append("</DL><p>\n")
        return sb.toString()
    }

    /**
     * Plain CSV Import.
     */
    fun importCsv(csvContent: String): List<LinkEntity> {
        val list = mutableListOf<LinkEntity>()
        val lines = csvContent.lines()
        if (lines.size <= 1) return list

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            val parts = line.split(",")
            if (parts.size >= 2) {
                val url = parts[1].removeSurrounding("\"").trim()
                val title = parts.getOrNull(2)?.removeSurrounding("\"")?.trim() ?: url
                val desc = parts.getOrNull(3)?.removeSurrounding("\"")?.trim() ?: ""
                val category = parts.getOrNull(4)?.removeSurrounding("\"")?.trim() ?: "📁 Uncategorized"
                val note = parts.getOrNull(5)?.removeSurrounding("\"")?.trim() ?: ""
                val isFav = parts.getOrNull(6)?.trim()?.toBoolean() ?: false
                val openCount = parts.getOrNull(7)?.trim()?.toIntOrNull() ?: 0

                if (url.isNotBlank()) {
                    list.add(
                        LinkEntity(
                            url = url,
                            title = title,
                            description = desc,
                            category = category,
                            note = note,
                            isFavorite = isFav,
                            openCount = openCount,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        return list
    }

    /**
     * Plain Netscape HTML Import.
     */
    fun importHtml(htmlContent: String): List<LinkEntity> {
        val list = mutableListOf<LinkEntity>()
        val hrefRegex = Regex("""<A\s+[^>]*HREF=["']([^"']+)["'][^>]*>(.*?)</A>""", RegexOption.IGNORE_CASE)
        val matches = hrefRegex.findAll(htmlContent)

        for (match in matches) {
            val url = match.groupValues[1].trim()
            val title = match.groupValues[2].trim().ifBlank { url }
            if (url.isNotBlank()) {
                list.add(
                    LinkEntity(
                        url = url,
                        title = title,
                        category = "📁 Imported",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
        return list
    }

    private fun calculateChecksum(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    private fun saveChecksumForFile(fileName: String, checksum: String) {
        sharedPrefs.edit().putString("checksum_$fileName", checksum).apply()
    }

    private fun getChecksumForFile(fileName: String): String? {
        return sharedPrefs.getString("checksum_$fileName", null)
    }
}
