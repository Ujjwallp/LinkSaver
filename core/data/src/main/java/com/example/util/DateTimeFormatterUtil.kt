package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Consolidated, thread-safe date and time formatting utility for LinkVault.
 * Replaces scattered SimpleDateFormat instantiations across UI and data layers.
 */
object DateTimeFormatterUtil {

    private val fullDateTimeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    }

    private val shortDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MMM d", Locale.getDefault())
    }

    private val timeAndDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("hh:mm a • MMM d", Locale.getDefault())
    }

    private val backupItemFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    }

    private val logTimeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    fun formatFullDateTime(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return ""
        return fullDateTimeFormat.get()?.format(Date(timestampMillis)).orEmpty()
    }

    fun formatShortDate(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return ""
        return shortDateFormat.get()?.format(Date(timestampMillis)).orEmpty()
    }

    fun formatTimeAndDate(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return ""
        return timeAndDateFormat.get()?.format(Date(timestampMillis)).orEmpty()
    }

    fun formatBackupItemTime(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return ""
        return backupItemFormat.get()?.format(Date(timestampMillis)).orEmpty()
    }

    fun formatLogTime(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return ""
        return logTimeFormat.get()?.format(Date(timestampMillis)).orEmpty()
    }

    /**
     * Relative formatted label ("Just now", "5m ago", "2h ago", "Yesterday", or full date).
     */
    fun formatRelativeTime(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (timestampMillis <= 0L) return ""
        val diff = nowMillis - timestampMillis
        if (diff < 0L) return formatFullDateTime(timestampMillis)

        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days == 1L -> "Yesterday"
            days < 7 -> "${days}d ago"
            else -> formatShortDate(timestampMillis)
        }
    }
}
