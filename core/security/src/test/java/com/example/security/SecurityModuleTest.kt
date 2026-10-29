package com.example.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecurityModuleTest {

    @Test
    fun masterPasswordValidator_evaluatesPasswordStrength() {
        val weakResult = MasterPasswordValidator.validate("12345".toCharArray())
        assertFalse(weakResult.isValid)
        assertTrue(weakResult.strength.score < 2)

        val strongResult = MasterPasswordValidator.validate("Tr0ub4dor&3#CorrectHorseBattery".toCharArray())
        assertTrue(strongResult.isValid)
        assertTrue(strongResult.strength.score >= 3)
    }

    @Test
    fun pinSecurity_hashesAndVerifiesPasscode() {
        val pin = "8492".toCharArray()
        val hashed = PinSecurity.hashPasscode(pin)

        assertTrue(PinSecurity.isHashed(hashed))
        assertTrue(PinSecurity.verifyPasscode(pin, hashed))
        assertFalse(PinSecurity.verifyPasscode("0000".toCharArray(), hashed))
    }

    @Test
    fun qrCodeGenerator_encodesValidMatrix() {
        val text = "LINKVAULT-EMERGENCY-KIT-SECRET-12345"
        val matrix = QrCodeGenerator.encode(text)

        assertEquals(29, matrix.size)
        assertEquals(29, matrix[0].size)

        // Verify finder patterns top-left (7x7 with black border)
        assertTrue(matrix[0][0])
        assertTrue(matrix[0][6])
        assertTrue(matrix[6][0])
        assertTrue(matrix[6][6])
    }

    @Test
    fun lockoutManager_tracksAttemptsAndLocks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockoutManager = LockoutManager(context)
        lockoutManager.recordSuccessfulUnlock()

        assertEquals(0, lockoutManager.getFailureCount())
        assertEquals(LockoutStatus.Unlocked, lockoutManager.checkStatus())

        for (i in 1..4) {
            val status = lockoutManager.recordFailedAttempt()
            assertTrue(status is LockoutStatus.Warning)
        }
        assertEquals(4, lockoutManager.getFailureCount())

        // 5th attempt triggers lockout
        val lockedStatus = lockoutManager.recordFailedAttempt()
        assertTrue(lockedStatus is LockoutStatus.Locked)

        lockoutManager.recordSuccessfulUnlock()
        assertEquals(0, lockoutManager.getFailureCount())
        assertEquals(LockoutStatus.Unlocked, lockoutManager.checkStatus())
    }

    @Test
    fun urlSecurityValidator_checksPhishingAndSchemes() {
        assertNull(UrlSecurityValidator.validateAndNormalizeUrl("javascript:alert(1)"))
        assertNull(UrlSecurityValidator.validateAndNormalizeUrl("data:text/html,<script>"))
        assertNull(UrlSecurityValidator.validateAndNormalizeUrl("http://localhost:8080"))

        val safe = UrlSecurityValidator.validateAndNormalizeUrl("https://github.com/torvalds/linux")
        assertNotNull(safe)
        assertTrue(safe!!.startsWith("https://github.com"))
    }
}
