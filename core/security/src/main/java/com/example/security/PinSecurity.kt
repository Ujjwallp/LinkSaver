package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Enterprise-grade PIN and Passcode Security.
 * Enforces PBKDF2WithHmacSHA256 with 600,000 iterations and a 16-byte random salt.
 * All comparisons are constant-time to prevent timing attacks.
 */
object PinSecurity {

    private const val ITERATIONS = 600_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val PREFIX = "\$pbkdf2-sha256\$"

    private val secureRandom = SecureRandom()

    /**
     * Generates a 16-byte cryptographically secure random salt.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Hashes a PIN or Passcode with a 16-byte salt using PBKDF2-HMAC-SHA256 (600,000 iterations).
     * Returns encoded string in format: $pbkdf2-sha256$i=600000$saltHex$hashHex
     */
    fun hashPasscode(passcode: CharArray, salt: ByteArray = generateSalt()): String {
        return try {
            val spec = PBEKeySpec(passcode, salt, ITERATIONS, KEY_LENGTH_BITS)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = skf.generateSecret(spec).encoded
            val saltHex = salt.joinToString("") { "%02x".format(it) }
            val hashHex = hash.joinToString("") { "%02x".format(it) }
            "$PREFIX$ITERATIONS$$saltHex$$hashHex"
        } finally {
            // Note: Caller is responsible for zeroing their own passcode array if needed
        }
    }

    /**
     * Checks if a stored passcode string is already hashed with PBKDF2.
     */
    fun isHashed(storedString: String): Boolean {
        return storedString.startsWith(PREFIX)
    }

    /**
     * Verifies an entered passcode against the stored value.
     * Supports both modern hashed passcodes and legacy plain-text passcodes (for transparent migration).
     * Performs constant-time comparison to prevent timing side-channels.
     */
    fun verifyPasscode(entered: CharArray, stored: String): Boolean {
        if (stored.isBlank()) return entered.isEmpty()

        if (!isHashed(stored)) {
            // Legacy plain-text constant-time comparison
            val enteredBytes = String(entered).toByteArray(Charsets.UTF_8)
            val storedBytes = stored.toByteArray(Charsets.UTF_8)
            return MessageDigest.isEqual(enteredBytes, storedBytes)
        }

        return try {
            // Format: $pbkdf2-sha256$i=600000$saltHex$hashHex
            val parts = stored.removePrefix(PREFIX).split("$")
            if (parts.size != 3) return false

            val iterations = parts[0].toIntOrNull() ?: ITERATIONS
            val salt = hexToByteArray(parts[1])
            val expectedHash = hexToByteArray(parts[2])

            val spec = PBEKeySpec(entered, salt, iterations, expectedHash.size * 8)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val computedHash = skf.generateSecret(spec).encoded

            MessageDigest.isEqual(computedHash, expectedHash)
        } catch (e: Throwable) {
            false
        }
    }

    private fun hexToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
