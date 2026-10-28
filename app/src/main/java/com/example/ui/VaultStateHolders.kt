package com.example.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.example.data.*
import com.example.security.LockoutManager
import com.example.security.LockoutStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Focused State Holder 1: Authentication, Lockout, Profiles & Folder Locks.
 */
@Stable
class AuthSecurityStateHolder(
    private val context: Context,
    private val sharedPrefs: SharedPreferences,
    private val driveHelper: OfflineBackupManager,
    private val repository: LinkRepository,
    private val scope: CoroutineScope
) {
    val lockoutManager = LockoutManager(context)
    val lockoutStatus = MutableStateFlow<LockoutStatus>(lockoutManager.checkStatus())

    val autoLockTimeoutSeconds = MutableStateFlow(sharedPrefs.getInt("auto_lock_timeout_seconds", 30))
    val privacyShieldEnabled = MutableStateFlow(sharedPrefs.getBoolean("privacy_shield_enabled", true))

    val registeredUsers = MutableStateFlow<List<LinkViewModel.LocalUser>>(emptyList())
    val activeUser = MutableStateFlow<LinkViewModel.LocalUser?>(null)
    val isLockEnabled = MutableStateFlow(false)
    val isBiometricAllowed = MutableStateFlow(false)
    val storedPasscode = MutableStateFlow("")
    val storedPasscodeType = MutableStateFlow("PIN")
    val isUnlocked = MutableStateFlow(true)

    val lockedCustomCategories = MutableStateFlow<Set<String>>(emptySet())
    val unlockedCustomCategories = MutableStateFlow<Set<String>>(emptySet())
    val isPrivateVaultUnlockedFlow = MutableStateFlow(false)
    val isPasswordsUnlockedFlow = MutableStateFlow(false)

    fun setAutoLockTimeout(seconds: Int) {
        autoLockTimeoutSeconds.value = seconds
        sharedPrefs.edit().putInt("auto_lock_timeout_seconds", seconds).apply()
    }

    fun togglePrivacyShield() {
        val next = !privacyShieldEnabled.value
        privacyShieldEnabled.value = next
        sharedPrefs.edit().putBoolean("privacy_shield_enabled", next).apply()
    }

    fun checkLockoutStatus() {
        lockoutStatus.value = lockoutManager.checkStatus()
    }

    fun getRegisteredUsersFromPrefs(): List<LinkViewModel.LocalUser> {
        val serialized = sharedPrefs.getString("registered_local_profiles", "") ?: ""
        if (serialized.isEmpty()) return emptyList()
        return serialized.split(";;").mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 3) {
                LinkViewModel.LocalUser(
                    email = parts[0],
                    name = parts[1],
                    passcode = parts[2],
                    passcodeType = parts.getOrNull(3) ?: "PIN",
                    biometricAllowed = parts.getOrNull(4)?.toBoolean() ?: false,
                    lockEnabled = parts.getOrNull(5)?.toBoolean() ?: false
                )
            } else {
                null
            }
        }
    }

    fun saveUsersToPrefs(users: List<LinkViewModel.LocalUser>) {
        val serialized = users.joinToString(";;") { user ->
            "${user.email}|${user.name}|${user.passcode}|${user.passcodeType}|${user.biometricAllowed}|${user.lockEnabled}"
        }
        sharedPrefs.edit().putString("registered_local_profiles", serialized).apply()
    }

    fun syncActiveUserSettings(user: LinkViewModel.LocalUser?) {
        if (user != null) {
            driveHelper.activeUserEmail = user.email
            isLockEnabled.value = user.lockEnabled
            isBiometricAllowed.value = user.biometricAllowed
            storedPasscode.value = user.passcode
            storedPasscodeType.value = user.passcodeType
            val lockedSaved = sharedPrefs.getStringSet("locked_categories_${user.email}", emptySet()) ?: emptySet()
            lockedCustomCategories.value = lockedSaved
            unlockedCustomCategories.value = emptySet()
            sharedPrefs.edit()
                .putString("active_user_email", user.email)
                .putString("last_active_user_email", user.email)
                .putBoolean("passcode_enabled", user.lockEnabled)
                .putString("passcode_code", user.passcode)
                .putString("passcode_type", user.passcodeType)
                .putBoolean("biometric_allowed", user.biometricAllowed)
                .apply()
        } else {
            driveHelper.activeUserEmail = ""
            isLockEnabled.value = false
            isBiometricAllowed.value = false
            storedPasscode.value = ""
            storedPasscodeType.value = "PIN"
            lockedCustomCategories.value = emptySet()
            unlockedCustomCategories.value = emptySet()
            sharedPrefs.edit()
                .putString("active_user_email", "")
                .putBoolean("passcode_enabled", false)
                .putString("passcode_code", "")
                .putString("passcode_type", "PIN")
                .putBoolean("biometric_allowed", false)
                .apply()
        }
    }
}

/**
 * Focused State Holder 2: Encrypted Cloud & Local Backup / Restore.
 */
@Stable
class BackupStateHolder(
    val driveHelper: OfflineBackupManager,
    private val repository: LinkRepository,
    private val scope: CoroutineScope
) {
    fun performAutoBackupIfEnabled(
        activeUser: LinkViewModel.LocalUser?,
        profiles: List<LinkViewModel.LocalUser>,
        categories: List<String>,
        isDarkTheme: Boolean
    ) {
        if (!driveHelper.autoBackupEnabled) return
        scope.launch(Dispatchers.IO) {
            delay(400)
            val activePass = activeUser?.passcode?.ifBlank { "default_pass" } ?: "default_pass"
            try {
                val payload = BackupPayload(
                    links = repository.allLinks.first(),
                    profiles = profiles,
                    customCategories = categories,
                    isDarkTheme = isDarkTheme,
                    autoBackupEnabled = true
                )
                driveHelper.performBackup(payload, activePass.toCharArray())
            } catch (e: Exception) {
                com.example.util.SecureLog.e("Backup", "Auto backup error", e)
            }
        }
    }
}

/**
 * Focused State Holder 3: Search Debouncing, Segment & Category Filtering.
 */
@Stable
class LinksFilterStateHolder {
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>(null)
    val showOnlyFavorites = MutableStateFlow(false)
    val vaultSegment = MutableStateFlow(LinkViewModel.VaultSegment.ALL)

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val debouncedSearchQuery: Flow<String> = searchQuery
        .debounce { query -> if (query.isEmpty()) 0L else 250L }
        .distinctUntilChanged()
}
