package com.example

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.LinkRepository
import com.example.ui.LinkSaverApp
import com.example.ui.LinkViewModel
import com.example.ui.LinkViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { LinkRepository(database.linkDao()) }
    
    // Instantiate ViewModel using proper custom factory architecture
    private val viewModel: LinkViewModel by viewModels {
        LinkViewModelFactory(repository, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.util.VaultImageLoader.ensureInitialized(applicationContext)

        // Register dynamic screen-off auto-lock receiver
        try {
            registerReceiver(screenOffReceiver, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF))
        } catch (e: Throwable) {
            // Safe fallback
        }

        // Capture share intents on clean cold launch
        handleIncomingShareIntent(intent)

        setContent {
            val isDarkPref by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val isDark = isDarkPref ?: androidx.compose.foundation.isSystemInDarkTheme()
            MyApplicationTheme(darkTheme = isDark) {
                LinkSaverApp(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    isDarkTheme = isDark,
                    onToggleTheme = {
                        viewModel.toggleTheme()
                    }
                )
            }
        }
    }

    private var isBiometricPromptShowing = false
    private var wasStopped = false
    private var backgroundedTimestamp = 0L

    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (android.content.Intent.ACTION_SCREEN_OFF == intent?.action) {
                if (viewModel.isLockEnabled.value) {
                    viewModel.lockVault()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Throwable) {
            // Safe fallback
        }
    }

    // Direct interface called from Compose for secure hardware verification
    fun isBiometricHardwareAvailable(): Boolean {
        return try {
            val biometricManager = BiometricManager.from(this)
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Throwable) {
            false
        }
    }

    // Direct trigger interface using Keystore CryptoObject for hardware-enforced verification
    fun triggerBiometricPrompt(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        runOnUiThread {
            if (isBiometricPromptShowing) {
                return@runOnUiThread
            }

            // Verify lockout status before opening prompt
            val lockout = viewModel.lockoutManager.checkStatus()
            if (lockout is com.example.security.LockoutStatus.Locked) {
                onError("Vault is locked. Wait ${lockout.remainingSeconds}s before retrying.")
                return@runOnUiThread
            } else if (lockout is com.example.security.LockoutStatus.Erased) {
                onError("Vault security threshold exceeded. Vault erased.")
                return@runOnUiThread
            }

            isBiometricPromptShowing = true
            try {
                val executor = ContextCompat.getMainExecutor(this)
                val biometricPrompt = BiometricPrompt(this, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            runOnUiThread {
                                isBiometricPromptShowing = false
                                if (errorCode == BiometricPrompt.ERROR_LOCKOUT || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                                    viewModel.recordFailedUnlockAttempt()
                                }
                                onError(errString.toString())
                            }
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            runOnUiThread {
                                isBiometricPromptShowing = false
                                com.example.security.KeyHierarchy.unlockWithHardwareKeystore(this@MainActivity)
                                viewModel.recordSuccessfulUnlock()
                                onSuccess()
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            runOnUiThread {
                                val status = viewModel.recordFailedUnlockAttempt()
                                if (status is com.example.security.LockoutStatus.Locked) {
                                    onError("Authentication failed. Locked for ${status.remainingSeconds}s.")
                                } else {
                                    onError("Fingerprint unrecognized")
                                }
                            }
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock LinkVault")
                    .setSubtitle("Hardware-authenticated Keystore verification")
                    .setNegativeButtonText("Cancel")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build()

                val cryptoObject = com.example.security.BiometricSecurityHelper.createCryptoObject()
                try {
                    if (cryptoObject != null) {
                        biometricPrompt.authenticate(promptInfo, cryptoObject)
                    } else {
                        biometricPrompt.authenticate(promptInfo)
                    }
                } catch (e: Throwable) {
                    isBiometricPromptShowing = false
                    onError(e.message ?: "Authentication scanner is unavailable")
                }
            } catch (e: Throwable) {
                isBiometricPromptShowing = false
                onError(e.message ?: "Authentication initialization failed")
            }
        }
    }

    private fun isRunningOnEmulator(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT
        val model = android.os.Build.MODEL
        val manufacturer = android.os.Build.MANUFACTURER
        val brand = android.os.Build.BRAND
        val device = android.os.Build.DEVICE
        val product = android.os.Build.PRODUCT
        val hardware = android.os.Build.HARDWARE

        return fingerprint.startsWith("generic") ||
                fingerprint.startsWith("unknown") ||
                model.contains("google_sdk") ||
                model.contains("Emulator") ||
                model.contains("Android SDK built for x86") ||
                manufacturer.contains("Genymotion") ||
                (brand.startsWith("generic") && device.startsWith("generic")) ||
                "google_sdk" == product ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu")
    }

    fun setSecureScreenEnabled(enabled: Boolean) {
        runOnUiThread {
            try {
                val isShieldOn = viewModel.privacyShieldEnabled.value
                val isUnlocked = viewModel.isUnlocked.value
                if (!isRunningOnEmulator() && (enabled || isShieldOn || !isUnlocked)) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            } catch (e: Throwable) {
                // Safe fallback
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check background auto-lock timeout
        val elapsedBg = android.os.SystemClock.elapsedRealtime() - backgroundedTimestamp
        val timeoutMs = viewModel.autoLockTimeoutSeconds.value * 1000L
        if (backgroundedTimestamp > 0 && elapsedBg >= timeoutMs && viewModel.isLockEnabled.value) {
            viewModel.lockVault()
        }
        viewModel.checkLockoutStatus()

        // Enforce FLAG_SECURE whenever privacy shield is active or vault is locked (bypassed on emulator for screen capture)
        val isShieldOn = viewModel.privacyShieldEnabled.value
        val isUnlocked = viewModel.isUnlocked.value
        try {
            if (!isRunningOnEmulator() && (isShieldOn || !isUnlocked)) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        } catch (e: Throwable) {
            // Safe fallback
        }

        if (wasStopped) {
            wasStopped = false
            val active = viewModel.activeUser.value
            val isBiometricAllowed = viewModel.isBiometricAllowed.value
            if (active != null && !isUnlocked && isBiometricHardwareAvailable() && (isBiometricAllowed || isShieldOn)) {
                triggerBiometricPrompt(
                    onSuccess = {
                        viewModel.setUnlocked(true)
                    },
                    onError = {
                        // Passcode fallback is always accessible on screen
                    }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        backgroundedTimestamp = android.os.SystemClock.elapsedRealtime()
        val isShieldOn = viewModel.privacyShieldEnabled.value
        val isLocked = viewModel.isLockEnabled.value
        val isUnlocked = viewModel.isUnlocked.value
        // Obscure app snapshot in Android recent apps switcher when multitasking (bypassed on emulator)
        if (!isRunningOnEmulator() && (isShieldOn || isLocked || !isUnlocked)) {
            try {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } catch (e: Throwable) {
                // Safe fallback
            }
        }
    }

    override fun onStop() {
        super.onStop()
        wasStopped = true
        isBiometricPromptShowing = false
        val active = viewModel.activeUser.value
        val isShieldOn = viewModel.privacyShieldEnabled.value
        // Only lock if lock is actually configured or if hardware biometric is available
        if (active != null && (active.lockEnabled || (isShieldOn && (active.passcode.isNotEmpty() || isBiometricHardwareAvailable())))) {
            viewModel.setUnlocked(false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update current activity intent reference
        handleIncomingShareIntent(intent)
    }

    /**
     * Intercepts and strictly validates intent payloads from external sharing menus or text selections.
     * Enforces scheme, size, and sanitization checks before processing.
     */
    private fun handleIncomingShareIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        
        val action = incomingIntent.action
        val mimeType = incomingIntent.type ?: ""

        val isShareAction = Intent.ACTION_SEND == action && mimeType.startsWith("text/")
        val isProcessTextAction = Intent.ACTION_PROCESS_TEXT == action
        val isImageShareAction = Intent.ACTION_SEND == action && mimeType.startsWith("image/")

        if (isShareAction || isProcessTextAction) {
            val rawText = if (isProcessTextAction) {
                incomingIntent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) 
                    ?: incomingIntent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            } else {
                incomingIntent.getStringExtra(Intent.EXTRA_TEXT)
            }

            if (!rawText.isNullOrBlank()) {
                // Limit maximum incoming text to 64 KB to prevent buffer/DOS attacks
                val safeText = if (rawText.length > 65_536) rawText.substring(0, 65_536) else rawText
                val trimmed = safeText.trim()
                val lower = trimmed.lowercase()
                
                // Disallow dangerous URI schemes masquerading as shared text links
                val isDisallowedScheme = lower.startsWith("javascript:") ||
                        lower.startsWith("data:") ||
                        lower.startsWith("file:") ||
                        lower.startsWith("intent:")
                
                if (!isDisallowedScheme) {
                    viewModel.setIncomingSharedText(trimmed)
                }
            }
            
            incomingIntent.action = null
        } else if (isImageShareAction) {
            val imageUri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri
            }
            
            if (imageUri != null) {
                val scheme = imageUri.scheme?.lowercase()
                // Only accept safe content:// URIs; disallow file:// or remote URLs
                if (scheme == "content") {
                    try {
                        val resolvedMime = contentResolver.getType(imageUri) ?: mimeType
                        if (resolvedMime.startsWith("image/")) {
                            // Verify size <= 15MB to prevent memory exhaustion
                            val sizeBytes = contentResolver.openFileDescriptor(imageUri, "r")?.use { pfd ->
                                pfd.statSize
                            } ?: -1L
                            
                            if (sizeBytes in 0..(15 * 1024 * 1024)) {
                                viewModel.setIncomingSharedImage(imageUri.toString())
                            }
                        }
                    } catch (e: Throwable) {
                        // Safe fallback on unreadable or invalid URI descriptor
                    }
                }
            }
            incomingIntent.action = null
        }
    }
}
