package com.example.security

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enterprise Key Hierarchy:
 * - DEK (Data Encryption Key): 256-bit random key.
 * - Subkeys derived via HKDF-SHA256 (database, credentials, backup, folder).
 * - KEK (Key Encryption Key) = HKDF(Stretched Master Password || Secret Key).
 * - Secret Key: 128-bit random device entropy (formatted A3-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX).
 * - DEK wrapped with KEK (AES-256-GCM) + Android Keystore key.
 */
object KeyHierarchy {

    private const val PREFS_NAME = "linkvault_security_keystore"
    private const val KEY_WRAPPED_DEK = "wrapped_dek_kek"
    private const val KEY_WRAPPED_DEK_HW = "wrapped_dek_hardware"
    private const val KEY_SALT_HEADER = "master_key_header"
    private const val KEY_SECRET_KEY_FORMATTED = "secret_key_saved"
    private const val KEY_HAS_MASTER_KEY = "has_master_key"

    private const val KEYSTORE_ALIAS = "linkvault_master_dek_wrap"
    private const val MIN_ITERATIONS = 600_000
    private const val SALT_LEN = 16
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_LEN_BITS = 128

    private val secureRandom = SecureRandom()

    // Active session in-memory cache (DEK and subkeys), cleared on lock
    @Volatile
    private var activeDek: ByteArray? = null

    @Volatile
    private var activeDatabaseKey: ByteArray? = null

    @Volatile
    private var activeCredentialsKey: ByteArray? = null

    @Volatile
    private var activeBackupKey: ByteArray? = null

    @Volatile
    private var activeFolderKey: ByteArray? = null

    @Volatile
    private var activeSecretKey: String? = null

    /**
     * Labels for HKDF subkey derivation.
     */
    private val LABEL_DB = "linkvault-database-v1".toByteArray(Charsets.UTF_8)
    private val LABEL_CREDENTIALS = "linkvault-credentials-v1".toByteArray(Charsets.UTF_8)
    private val LABEL_BACKUP = "linkvault-backup-v1".toByteArray(Charsets.UTF_8)
    private val LABEL_FOLDER = "linkvault-folder-v1".toByteArray(Charsets.UTF_8)
    private val LABEL_KEK = "linkvault-kek-v1".toByteArray(Charsets.UTF_8)
    private val AAD_WRAP = "linkvault-dek-wrap-v1".toByteArray(Charsets.UTF_8)

    private fun getSecurePrefs(context: Context): android.content.SharedPreferences {
        SecureStorage.migratePlainPrefsToEncrypted(
            context,
            PREFS_NAME,
            "${PREFS_NAME}_encrypted"
        )
        return SecureStorage.getEncryptedPrefs(context, "${PREFS_NAME}_encrypted")
    }

    fun hasMasterKey(context: Context): Boolean {
        val prefs = getSecurePrefs(context)
        return prefs.getBoolean(KEY_HAS_MASTER_KEY, false)
    }

    fun isVaultUnlocked(): Boolean {
        return activeDek != null
    }

    fun getActiveSecretKey(): String? = activeSecretKey

    fun getSavedSecretKey(context: Context): String {
        val active = activeSecretKey
        if (!active.isNullOrBlank()) return active
        val prefs = getSecurePrefs(context)
        return prefs.getString(KEY_SECRET_KEY_FORMATTED, "") ?: ""
    }

    fun lockVault() {
        activeDek?.let { Arrays.fill(it, 0.toByte()) }
        activeDatabaseKey?.let { Arrays.fill(it, 0.toByte()) }
        activeCredentialsKey?.let { Arrays.fill(it, 0.toByte()) }
        activeBackupKey?.let { Arrays.fill(it, 0.toByte()) }
        activeFolderKey?.let { Arrays.fill(it, 0.toByte()) }

        activeDek = null
        activeDatabaseKey = null
        activeCredentialsKey = null
        activeBackupKey = null
        activeFolderKey = null
        activeSecretKey = null
    }

    fun getDatabaseKey(): ByteArray? = activeDatabaseKey
    fun getCredentialsKey(): ByteArray? = activeCredentialsKey
    fun getBackupKey(): ByteArray? = activeBackupKey
    fun getFolderKey(): ByteArray? = activeFolderKey

    /**
     * Generates a 128-bit Secret Key formatted as A3-XXXXX-XXXXX-XXXXX-XXXXX-XXXXX
     */
    fun generateSecretKey(): Pair<ByteArray, String> {
        val charset = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // 32 characters, no confusing 0/O, 1/I
        val sb = StringBuilder("A3-")
        for (i in 0 until 5) {
            for (j in 0 until 5) {
                val index = secureRandom.nextInt(charset.length)
                sb.append(charset[index])
            }
            if (i < 4) sb.append("-")
        }
        val formatted = sb.toString()
        val raw = parseSecretKey(formatted)
        return Pair(raw, formatted)
    }

    /**
     * Normalizes and parses formatted secret key into raw 16 bytes.
     */
    fun parseSecretKey(formatted: String): ByteArray {
        val cleaned = formatted.replace("-", "").trim().uppercase()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val fullHash = md.digest(cleaned.toByteArray(Charsets.UTF_8))
        return fullHash.copyOf(16)
    }

    /**
     * Benchmarks derivation time and scales iterations to target 400-700ms.
     */
    fun benchmarkCost(samplePassword: CharArray, salt: ByteArray): Pair<Int, Long> {
        val start = SystemClock.elapsedRealtime()
        val sample = stretchPassword(samplePassword, salt, 200_000)
        Arrays.fill(sample, 0.toByte())
        val elapsed = SystemClock.elapsedRealtime() - start

        val targetMs = 500L
        val measured = if (elapsed <= 0) 1L else elapsed
        var scaledIterations = ((targetMs.toDouble() / measured) * 200_000).toInt()
        if (scaledIterations < MIN_ITERATIONS) {
            scaledIterations = MIN_ITERATIONS
        }
        return Pair(scaledIterations, measured)
    }

    private fun stretchPassword(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return skf.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Initializes the Master Password, Secret Key, generates 256-bit DEK,
     * derives all subkeys, wraps DEK, and stores to secure persistent storage.
     */
    fun initializeMasterKey(
        context: Context,
        masterPassword: CharArray,
        secretKeyRaw: ByteArray,
        secretKeyFormatted: String
    ) {
        val salt = ByteArray(SALT_LEN)
        secureRandom.nextBytes(salt)

        // Benchmark and stretch
        val (iterations, benchmarkMs) = benchmarkCost(masterPassword, salt)
        val stretched = stretchPassword(masterPassword, salt, iterations)
        val normalizedSecretRaw = if (secretKeyFormatted.isNotBlank()) {
            parseSecretKey(secretKeyFormatted)
        } else {
            secretKeyRaw.copyOf()
        }

        // Combine stretched master password and secret key into KEK
        val ikm = stretched + normalizedSecretRaw
        val kek = Hkdf.deriveKey(ikm, salt, LABEL_KEK, 32)

        // Generate 256-bit random DEK
        val dek = ByteArray(32)
        secureRandom.nextBytes(dek)

        try {
            // Wrap DEK with KEK using AES-256-GCM
            val wrappedDekKek = encryptGcm(kek, dek, AAD_WRAP)

            // Wrap DEK with Android Keystore key
            val hwKey = getOrCreateKeystoreKey()
            val cipherHw = Cipher.getInstance("AES/GCM/NoPadding")
            cipherHw.init(Cipher.ENCRYPT_MODE, hwKey)
            cipherHw.updateAAD(AAD_WRAP)
            val hwIv = cipherHw.iv
            val hwCiphertext = cipherHw.doFinal(dek)
            val wrappedDekHw = hwIv + hwCiphertext

            // Save wrapped state
            val header = "LV-KEY-V1:${Base64.encodeToString(salt, Base64.NO_WRAP)}:$iterations:$benchmarkMs"
            val prefs = getSecurePrefs(context)
            prefs.edit()
                .putString(KEY_SALT_HEADER, header)
                .putString(KEY_WRAPPED_DEK, Base64.encodeToString(wrappedDekKek, Base64.NO_WRAP))
                .putString(KEY_WRAPPED_DEK_HW, Base64.encodeToString(wrappedDekHw, Base64.NO_WRAP))
                .putString(KEY_SECRET_KEY_FORMATTED, secretKeyFormatted)
                .putBoolean(KEY_HAS_MASTER_KEY, true)
                .apply()

            // Cache active keys
            activateKeys(dek, secretKeyFormatted)
        } finally {
            // Wipe sensitive intermediate buffers
            Arrays.fill(stretched, 0.toByte())
            Arrays.fill(normalizedSecretRaw, 0.toByte())
            Arrays.fill(ikm, 0.toByte())
            Arrays.fill(kek, 0.toByte())
            Arrays.fill(dek, 0.toByte())
        }
    }

    /**
     * Unlocks the vault using the Master Password and Secret Key.
     */
    fun unlockWithMasterKey(
        context: Context,
        masterPassword: CharArray,
        secretKeyInput: String
    ): Boolean {
        val prefs = getSecurePrefs(context)
        val header = prefs.getString(KEY_SALT_HEADER, null) ?: return false
        val wrappedKekStr = prefs.getString(KEY_WRAPPED_DEK, null) ?: return false
        val wrappedDekKek = Base64.decode(wrappedKekStr, Base64.NO_WRAP)

        val parts = header.split(":")
        if (parts.size < 4 || parts[0] != "LV-KEY-V1") return false
        val salt = Base64.decode(parts[1], Base64.NO_WRAP)
        val iterations = parts[2].toIntOrNull() ?: MIN_ITERATIONS

        val secretKeyRaw = parseSecretKey(secretKeyInput)
        val stretched = stretchPassword(masterPassword, salt, iterations)
        val ikm = stretched + secretKeyRaw
        val kek = Hkdf.deriveKey(ikm, salt, LABEL_KEK, 32)

        return try {
            val dek = decryptGcm(kek, wrappedDekKek, AAD_WRAP)
            try {
                activateKeys(dek, secretKeyInput.trim().uppercase())
            } finally {
                Arrays.fill(dek, 0.toByte())
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            Arrays.fill(secretKeyRaw, 0.toByte())
            Arrays.fill(stretched, 0.toByte())
            Arrays.fill(ikm, 0.toByte())
            Arrays.fill(kek, 0.toByte())
        }
    }

    /**
     * Fast unlock using hardware Keystore (used during active biometric re-auth).
     */
    fun unlockWithHardwareKeystore(context: Context): Boolean {
        val prefs = getSecurePrefs(context)
        val wrappedHwStr = prefs.getString(KEY_WRAPPED_DEK_HW, null) ?: return false
        val savedSecret = prefs.getString(KEY_SECRET_KEY_FORMATTED, "") ?: ""
        val wrappedDekHw = Base64.decode(wrappedHwStr, Base64.NO_WRAP)

        return try {
            val hwKey = getOrCreateKeystoreKey()
            val iv = wrappedDekHw.copyOfRange(0, GCM_IV_LEN)
            val ciphertext = wrappedDekHw.copyOfRange(GCM_IV_LEN, wrappedDekHw.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, hwKey, GCMParameterSpec(GCM_TAG_LEN_BITS, iv))
            cipher.updateAAD(AAD_WRAP)
            val dek = cipher.doFinal(ciphertext)
            try {
                activateKeys(dek, savedSecret)
            } finally {
                Arrays.fill(dek, 0.toByte())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Changes the Master Password by re-wrapping the DEK.
     * The underlying data keys (DEK and all subkeys) remain unchanged!
     */
    fun changeMasterPassword(
        context: Context,
        oldPassword: CharArray,
        newPassword: CharArray,
        secretKeyInput: String
    ): Boolean {
        // Always verify oldPassword + secretKeyInput before allowing Master Password change
        if (!unlockWithMasterKey(context, oldPassword, secretKeyInput)) {
            return false
        }
        val dek = activeDek?.copyOf() ?: return false

        val secretKeyRaw = parseSecretKey(secretKeyInput)
        val newSalt = ByteArray(SALT_LEN)
        secureRandom.nextBytes(newSalt)

        val (newIterations, benchmarkMs) = benchmarkCost(newPassword, newSalt)
        val newStretched = stretchPassword(newPassword, newSalt, newIterations)
        val ikm = newStretched + secretKeyRaw
        val newKek = Hkdf.deriveKey(ikm, newSalt, LABEL_KEK, 32)

        try {
            val newWrappedDekKek = encryptGcm(newKek, dek, AAD_WRAP)

            val newHeader = "LV-KEY-V1:${Base64.encodeToString(newSalt, Base64.NO_WRAP)}:$newIterations:$benchmarkMs"
            val prefs = getSecurePrefs(context)
            prefs.edit()
                .putString(KEY_SALT_HEADER, newHeader)
                .putString(KEY_WRAPPED_DEK, Base64.encodeToString(newWrappedDekKek, Base64.NO_WRAP))
                .apply()
            return true
        } finally {
            Arrays.fill(dek, 0.toByte())
            Arrays.fill(secretKeyRaw, 0.toByte())
            Arrays.fill(newStretched, 0.toByte())
            Arrays.fill(ikm, 0.toByte())
            Arrays.fill(newKek, 0.toByte())
        }
    }

    private fun activateKeys(dek: ByteArray, secretKey: String) {
        activeDek = dek.copyOf()
        activeDatabaseKey = Hkdf.deriveKey(dek, null, LABEL_DB, 32)
        activeCredentialsKey = Hkdf.deriveKey(dek, null, LABEL_CREDENTIALS, 32)
        activeBackupKey = Hkdf.deriveKey(dek, null, LABEL_BACKUP, 32)
        activeFolderKey = Hkdf.deriveKey(dek, null, LABEL_FOLDER, 32)
        activeSecretKey = secretKey
    }

    private fun encryptGcm(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LEN)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LEN_BITS, iv))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    private fun decryptGcm(key: ByteArray, combined: ByteArray, aad: ByteArray): ByteArray {
        val iv = combined.copyOfRange(0, GCM_IV_LEN)
        val ciphertext = combined.copyOfRange(GCM_IV_LEN, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LEN_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            return keyGen.generateKey()
        }
        return (keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey)
    }
}
