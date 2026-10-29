package com.example.security

import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

/**
 * RFC 5869 compliant HMAC-based Extract-and-Expand Key Derivation Function (HKDF)
 * using HMAC-SHA256.
 */
object Hkdf {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val HASH_LEN = 32 // SHA-256 output length in bytes

    /**
     * HKDF-Extract(salt, IKM) -> PRK
     */
    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val saltKey = if (salt == null || salt.isEmpty()) {
            SecretKeySpec(ByteArray(HASH_LEN), HMAC_ALGORITHM)
        } else {
            SecretKeySpec(salt, HMAC_ALGORITHM)
        }
        mac.init(saltKey)
        return mac.doFinal(ikm)
    }

    /**
     * HKDF-Expand(PRK, info, length) -> OKM
     */
    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length <= 255 * HASH_LEN) { "Cannot expand to more than 255 * $HASH_LEN bytes" }

        val n = ceil(length.toDouble() / HASH_LEN).toInt()
        val okm = ByteArray(length)
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))

        var t = ByteArray(0)
        var offset = 0

        for (i in 1..n) {
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()

            val bytesToCopy = if (i == n) length - offset else HASH_LEN
            System.arraycopy(t, 0, okm, offset, bytesToCopy)
            offset += bytesToCopy
        }

        return okm
    }

    /**
     * Full HKDF derivation: extract then expand.
     */
    fun deriveKey(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray {
        val prk = extract(salt, ikm)
        return expand(prk, info, length)
    }
}
