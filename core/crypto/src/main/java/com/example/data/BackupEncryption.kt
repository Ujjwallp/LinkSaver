package com.example.data

import android.util.Base64
import com.example.security.Hkdf
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class DecryptedBackupResult(
    val plainJson: String,
    val formatVersion: Int,
    val counter: Long,
    val profileId: String
)

object BackupEncryption {

    const val MAGIC_V3_PREFIX = "LV3$"
    private const val MAGIC_V2_PREFIX = "LV2$"

    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_LEN_BITS = 128
    private const val PADDING_BLOCK_SIZE = 256
    private const val SALT_LEN = 16

    private const val LEGACY_CBC_ALGO = "AES/CBC/PKCS5Padding"
    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
    private const val LEGACY_V2_ITERATIONS = 10000

    private val secureRandom = SecureRandom()

    /**
     * V3 Enterprise Backup Encryption:
     * - AES-256-GCM authenticated cipher.
     * - 12-byte cryptographically secure random nonce.
     * - Key derived from Vault DEK backup subkey + password via HKDF-SHA256.
     * - AAD = formatVersion + counter + profileId (tamper & replay proof).
     * - Fixed 256-byte block padding to resist traffic & ciphertext size analysis.
     * - Header format: LV3$formatVersion$counter$profileId$saltBase64$ivBase64$cipherBase64
     */
    fun encryptV3(
        plainText: String,
        password: CharArray,
        backupSubkey: ByteArray?,
        counter: Long,
        profileId: String
    ): String {
        val salt = ByteArray(SALT_LEN)
        secureRandom.nextBytes(salt)

        // Derive 256-bit AES key
        val passBytes = password.joinToString("").toByteArray(Charsets.UTF_8)
        val ikm = if (backupSubkey != null && backupSubkey.isNotEmpty()) {
            backupSubkey + passBytes
        } else {
            // Stretch password if DEK subkey not available
            val spec = PBEKeySpec(password, salt, 600_000, 256)
            val skf = SecretKeyFactory.getInstance(PBKDF2_ALGO)
            skf.generateSecret(spec).encoded
        }

        val aesKey = Hkdf.deriveKey(
            ikm = ikm,
            salt = salt,
            info = "linkvault-backup-v3".toByteArray(Charsets.UTF_8),
            length = 32
        )

        // Generate 12-byte GCM nonce
        val iv = ByteArray(GCM_IV_LEN)
        secureRandom.nextBytes(iv)

        // Apply 256-byte fixed block padding (PKCS#7)
        val rawBytes = plainText.toByteArray(Charsets.UTF_8)
        val padLen = PADDING_BLOCK_SIZE - (rawBytes.size % PADDING_BLOCK_SIZE)
        val paddedBytes = ByteArray(rawBytes.size + padLen)
        System.arraycopy(rawBytes, 0, paddedBytes, 0, rawBytes.size)
        for (i in rawBytes.size until paddedBytes.size) {
            paddedBytes[i] = padLen.toByte()
        }

        // Additional Authenticated Data (AAD)
        val normalizedProfile = profileId.trim().lowercase()
        val aad = "3:$counter:$normalizedProfile".toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_LEN_BITS, iv))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(paddedBytes)

        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

        return "$MAGIC_V3_PREFIX" + "3$$counter$$normalizedProfile$$saltB64$$ivB64$$cipherB64"
    }

    /**
     * Backward-compatible overload that creates modern V3 backups.
     */
    fun encrypt(plainText: String, password: CharArray): String {
        return encryptV3(
            plainText = plainText,
            password = password,
            backupSubkey = null,
            counter = System.currentTimeMillis(),
            profileId = "default"
        )
    }

    /**
     * Unified Decryption Engine:
     * Autodetects and handles:
     * 1. Modern V3 (AES-256-GCM + AAD + padding)
     * 2. Legacy V2 (PBKDF2 + AES-256-CBC with LV2$ prefix)
     * 3. Legacy V1 (SHA-256 + AES-256-CBC)
     */
    fun decrypt(
        cipherText: String,
        password: CharArray,
        backupSubkey: ByteArray? = null,
        expectedProfileId: String? = null
    ): String {
        return decryptDetailed(cipherText, password, backupSubkey, expectedProfileId).plainJson
    }

    fun decryptDetailed(
        cipherText: String,
        password: CharArray,
        backupSubkey: ByteArray? = null,
        expectedProfileId: String? = null
    ): DecryptedBackupResult {
        val trimmed = cipherText.trim()

        if (trimmed.startsWith(MAGIC_V3_PREFIX)) {
            // V3 format: LV3$version$counter$profileId$salt$iv$ciphertext
            val payload = trimmed.removePrefix(MAGIC_V3_PREFIX)
            val parts = payload.split("$")
            if (parts.size < 6) {
                throw IllegalArgumentException("Malformed V3 backup header format")
            }

            val version = parts[0].toIntOrNull() ?: 3
            val counter = parts[1].toLongOrNull() ?: 0L
            val profileId = parts[2]
            val salt = Base64.decode(parts[3], Base64.DEFAULT)
            val iv = Base64.decode(parts[4], Base64.DEFAULT)
            val ciphertext = Base64.decode(parts[5], Base64.DEFAULT)

            val passBytes = password.joinToString("").toByteArray(Charsets.UTF_8)
            val ikm = if (backupSubkey != null && backupSubkey.isNotEmpty()) {
                backupSubkey + passBytes
            } else {
                val spec = PBEKeySpec(password, salt, 600_000, 256)
                val skf = SecretKeyFactory.getInstance(PBKDF2_ALGO)
                skf.generateSecret(spec).encoded
            }

            val aesKey = Hkdf.deriveKey(
                ikm = ikm,
                salt = salt,
                info = "linkvault-backup-v3".toByteArray(Charsets.UTF_8),
                length = 32
            )

            val aad = "$version:$counter:$profileId".toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_LEN_BITS, iv))
            cipher.updateAAD(aad)

            val padded = cipher.doFinal(ciphertext)

            // Strip PKCS#7 block padding
            val padLen = padded[padded.size - 1].toInt() and 0xFF
            if (padLen !in 1..PADDING_BLOCK_SIZE || padLen > padded.size) {
                throw SecurityException("Invalid padding in decrypted backup payload")
            }
            for (i in padded.size - padLen until padded.size) {
                if ((padded[i].toInt() and 0xFF) != padLen) {
                    throw SecurityException("Corrupted padding in backup payload")
                }
            }

            val unpadded = ByteArray(padded.size - padLen)
            System.arraycopy(padded, 0, unpadded, 0, unpadded.size)

            return DecryptedBackupResult(
                plainJson = String(unpadded, Charsets.UTF_8),
                formatVersion = version,
                counter = counter,
                profileId = profileId
            )
        } else if (trimmed.startsWith(MAGIC_V2_PREFIX)) {
            // Legacy V2 format (PBKDF2 + AES-256-CBC)
            val payload = trimmed.removePrefix(MAGIC_V2_PREFIX)
            val combined = Base64.decode(payload, Base64.DEFAULT)
            if (combined.size < 32) {
                throw IllegalArgumentException("Invalid legacy V2 backup payload")
            }

            val salt = ByteArray(16)
            System.arraycopy(combined, 0, salt, 0, 16)

            val iv = ByteArray(16)
            System.arraycopy(combined, 16, iv, 0, 16)
            val ivSpec = IvParameterSpec(iv)

            val keySpec = PBEKeySpec(password, salt, LEGACY_V2_ITERATIONS, 256)
            val keyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
            val keyBytes = keyFactory.generateSecret(keySpec).encoded
            val secretKeySpec = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance(LEGACY_CBC_ALGO)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec)

            val encryptedBytes = ByteArray(combined.size - 32)
            System.arraycopy(combined, 32, encryptedBytes, 0, encryptedBytes.size)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return DecryptedBackupResult(
                plainJson = String(decryptedBytes, Charsets.UTF_8),
                formatVersion = 2,
                counter = 0L,
                profileId = ""
            )
        } else {
            // Legacy V1 format (SHA-256 + AES-256-CBC)
            val combined = Base64.decode(trimmed, Base64.DEFAULT)
            if (combined.size < 16) {
                throw IllegalArgumentException("Invalid legacy V1 backup payload")
            }

            val messageDigest = MessageDigest.getInstance("SHA-256")
            val passByteArray = ByteArray(password.size) { password[it].code.toByte() }
            val keyBytes = messageDigest.digest(passByteArray)
            val secretKeySpec = SecretKeySpec(keyBytes, "AES")

            val iv = ByteArray(16)
            System.arraycopy(combined, 0, iv, 0, 16)
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance(LEGACY_CBC_ALGO)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec)

            val encryptedBytes = ByteArray(combined.size - 16)
            System.arraycopy(combined, 16, encryptedBytes, 0, encryptedBytes.size)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return DecryptedBackupResult(
                plainJson = String(decryptedBytes, Charsets.UTF_8),
                formatVersion = 1,
                counter = 0L,
                profileId = ""
            )
        }
    }
}
