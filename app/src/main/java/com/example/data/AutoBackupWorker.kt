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

            val database = AppDatabase.getDatabase(applicationContext)
            val links = database.linkDao().getAllLinksSnapshot()

            val prefs = SecureStorage.getEncryptedPrefs(applicationContext, "linksaver_drive_backup_prefs_encrypted")
            val activeUserEmail = helper.activeUserEmail
            val defaultPasscode = prefs.getString("auto_backup_passcode_$activeUserEmail", "auto_vault_key") ?: "auto_vault_key"

            val payload = BackupPayload(
                links = links,
                profiles = emptyList(),
                customCategories = emptyList(),
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
