package com.example.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BackupEncryption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CryptoModuleTest {

    @Test
    fun hkdf_derivesDistinctDomainSeparatedSubkeys() {
        val ikm = "master-key-material-32-bytes-len".toByteArray(Charsets.UTF_8)
        val salt = "16-byte-salt-val".toByteArray(Charsets.UTF_8)

        val dbKey = Hkdf.deriveKey(ikm, salt, "db-key".toByteArray(Charsets.UTF_8), 32)
        val credKey = Hkdf.deriveKey(ikm, salt, "cred-key".toByteArray(Charsets.UTF_8), 32)
        val dbKeyRepeat = Hkdf.deriveKey(ikm, salt, "db-key".toByteArray(Charsets.UTF_8), 32)

        assertEquals(32, dbKey.size)
        assertEquals(32, credKey.size)
        assertArrayEquals(dbKey, dbKeyRepeat)
        assertFalse(dbKey.contentEquals(credKey))
    }

    @Test
    fun recordEncryption_encryptAndDecryptRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val secretNote = "Top secret bank recovery codes: 9912-4481-0023"
        val recordId = 42
        val email = "local_user"

        val encrypted = RecordEncryption.encrypt(context, secretNote, recordId, email)
        assertTrue(RecordEncryption.isEncrypted(encrypted))
        assertNotEquals(secretNote, encrypted)

        val decrypted = RecordEncryption.decrypt(context, encrypted, recordId, email, isUnlocked = true)
        assertEquals(secretNote, decrypted)
    }

    @Test
    fun backupEncryption_v3AuthenticatedRoundTrip() {
        val jsonPayload = """{"links":[{"title":"GitHub","url":"https://github.com"}]}"""
        val password = "StrongBackupPassword!2026".toCharArray()
        val subkey = ByteArray(32) { it.toByte() }

        val encrypted = BackupEncryption.encryptV3(
            plainText = jsonPayload,
            password = password,
            backupSubkey = subkey,
            counter = 7L,
            profileId = "local_vault"
        )

        assertTrue(encrypted.startsWith(BackupEncryption.MAGIC_V3_PREFIX))

        val detailed = BackupEncryption.decryptDetailed(
            cipherText = encrypted,
            password = password,
            backupSubkey = subkey,
            expectedProfileId = "local_vault"
        )

        assertEquals(jsonPayload, detailed.plainJson)
        assertEquals(3, detailed.formatVersion)
        assertEquals(7L, detailed.counter)
        assertEquals("local_vault", detailed.profileId)
    }
}
