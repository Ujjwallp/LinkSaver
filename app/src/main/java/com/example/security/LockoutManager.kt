package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock

sealed class LockoutStatus {
    object Unlocked : LockoutStatus()
    data class Warning(val remainingAttemptsBeforeLockout: Int) : LockoutStatus()
    data class Locked(val remainingSeconds: Long, val failureCount: Int) : LockoutStatus()
    object Erased : LockoutStatus()
}

class LockoutManager(context: Context) {

    private val prefs: SharedPreferences = run {
        SecureStorage.migratePlainPrefsToEncrypted(
            context,
            "linkvault_lockout_security",
            "linkvault_lockout_security_encrypted"
        )
        SecureStorage.getEncryptedPrefs(context, "linkvault_lockout_security_encrypted")
    }

    companion object {
        private const val KEY_FAILURE_COUNT = "consecutive_failure_count"
        private const val KEY_LOCKOUT_DURATION_SEC = "lockout_duration_sec"
        private const val KEY_LOCKOUT_START_ELAPSED = "lockout_start_elapsed"
        private const val KEY_LOCKOUT_START_WALL = "lockout_start_wall"
        private const val KEY_AUTO_ERASE_ENABLED = "auto_erase_vault_enabled"
        private const val KEY_AUTO_ERASE_THRESHOLD = "auto_erase_vault_threshold"

        const val DEFAULT_AUTO_ERASE_THRESHOLD = 10
    }

    var isAutoEraseEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ERASE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ERASE_ENABLED, value).apply()

    var autoEraseThreshold: Int
        get() = prefs.getInt(KEY_AUTO_ERASE_THRESHOLD, DEFAULT_AUTO_ERASE_THRESHOLD)
        set(value) = prefs.edit().putInt(KEY_AUTO_ERASE_THRESHOLD, value).apply()

    fun getFailureCount(): Int = prefs.getInt(KEY_FAILURE_COUNT, 0)

    /**
     * Inspects the current lockout state based on monotonic clock and stored state.
     */
    fun checkStatus(): LockoutStatus {
        val failures = prefs.getInt(KEY_FAILURE_COUNT, 0)

        // Check if auto-erase threshold reached
        if (isAutoEraseEnabled && failures >= autoEraseThreshold) {
            return LockoutStatus.Erased
        }

        val durationSec = prefs.getLong(KEY_LOCKOUT_DURATION_SEC, 0L)
        if (durationSec <= 0L) {
            return if (failures in 1..4) {
                LockoutStatus.Warning(5 - failures)
            } else {
                LockoutStatus.Unlocked
            }
        }

        val startElapsed = prefs.getLong(KEY_LOCKOUT_START_ELAPSED, 0L)
        val startWall = prefs.getLong(KEY_LOCKOUT_START_WALL, 0L)
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        // Calculate elapsed time resisting reboot or clock tampering
        val elapsedSec = if (nowElapsed >= startElapsed) {
            (nowElapsed - startElapsed) / 1000L
        } else {
            // System was rebooted since startElapsed was recorded; fallback to wall clock with rollback guard
            val wallDiff = (nowWall - startWall) / 1000L
            if (wallDiff in 0..durationSec) wallDiff else 0L
        }

        val remainingSec = durationSec - elapsedSec
        return if (remainingSec > 0) {
            LockoutStatus.Locked(remainingSec, failures)
        } else {
            // Lockout period has elapsed; clear duration, but keep failure level until successful unlock
            prefs.edit().putLong(KEY_LOCKOUT_DURATION_SEC, 0L).apply()
            LockoutStatus.Unlocked
        }
    }

    /**
     * Records a failed attempt and computes the new lockout penalty.
     * Lockout thresholds:
     * - 5 attempts: 30 seconds
     * - 6 attempts: 5 minutes (300 s)
     * - 7 attempts: 1 hour (3600 s)
     * - 8+ attempts: 24 hours (86400 s)
     */
    fun recordFailedAttempt(): LockoutStatus {
        val newFailures = prefs.getInt(KEY_FAILURE_COUNT, 0) + 1
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        if (isAutoEraseEnabled && newFailures >= autoEraseThreshold) {
            prefs.edit().putInt(KEY_FAILURE_COUNT, newFailures).apply()
            return LockoutStatus.Erased
        }

        val durationSec: Long = when {
            newFailures >= 8 -> 86400L // 24 hours
            newFailures == 7 -> 3600L  // 1 hour
            newFailures == 6 -> 300L   // 5 minutes
            newFailures == 5 -> 30L    // 30 seconds
            else -> 0L
        }

        prefs.edit()
            .putInt(KEY_FAILURE_COUNT, newFailures)
            .putLong(KEY_LOCKOUT_DURATION_SEC, durationSec)
            .putLong(KEY_LOCKOUT_START_ELAPSED, nowElapsed)
            .putLong(KEY_LOCKOUT_START_WALL, nowWall)
            .apply()

        return if (durationSec > 0L) {
            LockoutStatus.Locked(durationSec, newFailures)
        } else {
            LockoutStatus.Warning(5 - newFailures)
        }
    }

    /**
     * Clears all failed attempts and lockout timers upon successful authentication.
     */
    fun recordSuccessfulUnlock() {
        prefs.edit()
            .putInt(KEY_FAILURE_COUNT, 0)
            .putLong(KEY_LOCKOUT_DURATION_SEC, 0L)
            .putLong(KEY_LOCKOUT_START_ELAPSED, 0L)
            .putLong(KEY_LOCKOUT_START_WALL, 0L)
            .apply()
    }
}
