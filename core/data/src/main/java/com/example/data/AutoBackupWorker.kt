package com.example.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.security.SecureStorage
import com.example.util.SecureLog as Log
import java.util.concurrent.TimeUnit

/**
 * Scheduled WorkManager Worker for offline local auto-backups.
 * Runs periodically when device is idle and/or charging.
 * Auto-prunes older automatic backups, maintaining the last 5 in local storage.
 */
class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val helper = OfflineBackupManager(applicationContext)
            if (!helper.autoBackupEnabled) {
                return Result.success()
            }

            val prefs = SecureStorage.getEncryptedPrefs(applicationContext, "linksaver_drive_backup_prefs_encrypted")
            val activeUserEmail = helper.activeUserEmail
            val defaultPasscode = prefs.getString("auto_backup_passcode_$activeUserEmail", null)
            if (defaultPasscode.isNullOrBlank()) {
                Log.d("AutoBackupWorker", "No auto backup passphrase configured; skipping scheduled backup.")
                return Result.success()
            }

            val database = AppDatabase.getDatabase(applicationContext)
            val links = database.linkDao().getAllLinksSnapshot()

            val secPrefs = SecureStorage.getEncryptedPrefs(applicationContext, "linksaver_vault_security_encrypted")
            val profilesSerialized = secPrefs.getString("registered_local_profiles", "") ?: ""
            val profiles = if (profilesSerialized.isNotEmpty()) {
                profilesSerialized.split(";;").mapNotNull { line ->
                    val parts = line.split("|")
                    if (parts.size >= 3) {
                        LocalUser(
                            email = parts[0],
                            name = parts[1],
                            passcode = parts[2],
                            passcodeType = parts.getOrNull(3) ?: "PIN",
                            biometricAllowed = parts.getOrNull(4)?.toBoolean() ?: false,
                            lockEnabled = parts.getOrNull(5)?.toBoolean() ?: false
                        )
                    } else null
                }
            } else emptyList()

            val derivedCategories = links.map { it.category }.filter { it.isNotBlank() }.distinct()

            val payload = BackupPayload(
                links = links,
                profiles = profiles,
                customCategories = derivedCategories,
                isDarkTheme = true,
                autoBackupEnabled = true
            )

            val success = helper.performBackup(payload, defaultPasscode.toCharArray())
            if (success) {
                Log.d("AutoBackupWorker", "Scheduled offline auto-backup succeeded.")
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("AutoBackupWorker", "Error executing auto-backup work", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "linkvault_offline_auto_backup"

        fun schedulePeriodic(context: Context, intervalHours: Long = 24) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
