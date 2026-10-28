package com.example.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * Detects whether the device hardware provides StrongBox Keymaster,
 * a Trusted Execution Environment (TEE), or Software-only emulation.
 */
object HardwareSecurityLevel {

    enum class SecurityLevel(val title: String, val badge: String, val description: String) {
        STRONGBOX(
            "StrongBox Keymaster",
            "Hardware Level 3 (Dedicated Secure Element)",
            "Keys are generated and bound inside a dedicated, tamper-resistant hardware security module (HSM)."
        ),
        TEE(
            "Trusted Execution Environment (TEE)",
            "Hardware Level 2 (Secure World)",
            "Keys are isolated inside the ARM TrustZone / TEE and protected from Android OS compromise."
        ),
        SOFTWARE(
            "Software Fallback",
            "Hardware Level 1 (Cryptographic Keystore)",
            "Keys are managed securely by the Android Keystore with software encryption wrappers."
        )
    }

    private const val PROBE_KEY_ALIAS = "linkvault_sec_probe_key"

    fun detectLevel(context: Context): SecurityLevel {
        // First check PackageManager for StrongBox feature (API 28+)
        val hasStrongBox = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } else {
            false
        }

        if (hasStrongBox) {
            return SecurityLevel.STRONGBOX
        }

        // Test probe key inside AndroidKeyStore to check KeyInfo
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(PROBE_KEY_ALIAS)) {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(
                    PROBE_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            }

            val key = keyStore.getKey(PROBE_KEY_ALIAS, null) as? SecretKey
            if (key != null) {
                val factory = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as? KeyInfo

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    when (keyInfo?.securityLevel) {
                        KeyProperties.SECURITY_LEVEL_STRONGBOX -> SecurityLevel.STRONGBOX
                        KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> SecurityLevel.TEE
                        else -> if (keyInfo?.isInsideSecureHardware == true) SecurityLevel.TEE else SecurityLevel.SOFTWARE
                    }
                } else {
                    if (keyInfo?.isInsideSecureHardware == true) SecurityLevel.TEE else SecurityLevel.SOFTWARE
                }
            } else {
                SecurityLevel.SOFTWARE
            }
        } catch (e: Exception) {
            SecurityLevel.SOFTWARE
        }
    }
}
