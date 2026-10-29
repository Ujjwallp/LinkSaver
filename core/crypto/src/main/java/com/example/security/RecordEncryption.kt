package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Per-record AES-256-GCM encryption with unique 12-byte random nonces
 * and Additional Authenticated Data (AAD) bound to record id + profile id + schema version.
 */
object RecordEncryption {

    private const val PREFIX = "LV-ENC-V1:"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_LEN_BITS = 128
    private const val KEYSTORE_FALLBACK_ALIAS = "linkvault_record_keystore_key"

    private val secureRandom = SecureRandom()

    fun isEncrypted(data: String): Boolean {
        return data.startsWith(PREFIX)
    }

    /**
     * Constructs the cryptographically bound Additional Authenticated Data (AAD).
     */
    private fun buildAad(recordId: Int, userEmail: String): ByteArray {
        val normalizedEmail = userEmail.trim().lowercase()
        return "$recordId:$normalizedEmail:v1".toByteArray(Charsets.UTF_8)
    }

    /**
     * Resolves the encryption key: prefers HKDF-derived credentialsKey from the active DEK,
     * or falls back to an Android KeyStore AES-256 hardware key.
     */
    private fun resolveKey(context: Context): SecretKey {
        val activeKey = KeyHierarchy.getCredentialsKey()
        if (activeKey != null) {
            return SecretKeySpec(activeKey, "AES")
        }
        return getOrCreateKeystoreFallbackKey()
    }

    private fun getOrCreateKeystoreFallbackKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(KEYSTORE_FALLBACK_ALIAS)) {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_FALLBACK_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            } else {
                keyStore.getKey(KEYSTORE_FALLBACK_ALIAS, null) as SecretKey
            }
        } catch (_: Throwable) {
            val derived = Hkdf.deriveKey(
                KEYSTORE_FALLBACK_ALIAS.toByteArray(Charsets.UTF_8),
                "linkvault-record-fallback-salt".toByteArray(Charsets.UTF_8),
                "linkvault-record-key-v1".toByteArray(Charsets.UTF_8),
                32
            )
            SecretKeySpec(derived, "AES")
        }
    }

    /**
     * Encrypts a sensitive credential or note value with AES-256-GCM.
     */
    fun encrypt(
        context: Context,
        plaintext: String,
        recordId: Int,
        userEmail: String
    ): String {
        if (plaintext.isBlank()) return plaintext
        if (isEncrypted(plaintext)) return plaintext // Already encrypted

        val key = resolveKey(context)
        val iv = ByteArray(GCM_IV_LEN)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LEN_BITS, iv))
        cipher.updateAAD(buildAad(recordId, userEmail))

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

        return "$PREFIX$ivBase64:$cipherBase64"
    }

    /**
     * Decrypts a sensitive credential or note value.
     * If the vault is locked and cannot be decrypted, returns a masked placeholder.
     */
    fun decrypt(
        context: Context,
        storedValue: String,
        recordId: Int,
        userEmail: String,
        isUnlocked: Boolean = true
    ): String {
        if (!isEncrypted(storedValue)) {
            return storedValue // Legacy plain text
        }

        if (!isUnlocked) {
            return "[Protected Credential · Unlock Vault to Decrypt]"
        }

        return try {
            val payload = storedValue.substring(PREFIX.length)
            val parts = payload.split(":")
            if (parts.size != 2) return storedValue

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)

            val key = resolveKey(context)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LEN_BITS, iv))
            cipher.updateAAD(buildAad(recordId, userEmail))

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // Decryption failure (tampered ciphertext or mismatched AAD)
            "[Protected Credential · Decryption Failed]"
        }
    }
}
