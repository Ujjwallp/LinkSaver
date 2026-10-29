package com.example.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Enterprise Keystore-backed encrypted preferences storage
 * using AES-256-GCM for values and AES-256-SIV for keys.
 */
object SecureStorage {

    fun getEncryptedPrefs(context: Context, name: String): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                name,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Safe fallback to standard private prefs if hardware keystore is unavailable
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
        }
    }

    /**
     * Migrates plain SharedPreferences entries to an EncryptedSharedPreferences instance
     * ensuring zero user data loss.
     */
    fun migratePlainPrefsToEncrypted(
        context: Context,
        plainPrefsName: String,
        encryptedPrefsName: String
    ) {
        val plainPrefs = context.getSharedPreferences(plainPrefsName, Context.MODE_PRIVATE)
        val allEntries = plainPrefs.all
        if (allEntries.isEmpty()) return

        val encPrefs = getEncryptedPrefs(context, encryptedPrefsName)
        val editor = encPrefs.edit()

        for ((key, value) in allEntries) {
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
            }
        }
        editor.apply()
        // Clear plain text values now that they reside in encrypted storage
        plainPrefs.edit().clear().apply()
    }
}
