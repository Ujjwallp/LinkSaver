package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.example.util.SecureLog as Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LinkViewModel(
    private val repository: LinkRepository,
    private val context: Context
) : ViewModel() {

    private val sharedPrefs = run {
        com.example.security.SecureStorage.migratePlainPrefsToEncrypted(
            context,
            "linksaver_vault_security",
            "linksaver_vault_security_encrypted"
        )
        com.example.security.SecureStorage.getEncryptedPrefs(context, "linksaver_vault_security_encrypted")
    }

    // Offline Local Backup Manager instance
    val driveHelper = OfflineBackupManager(context)
    val backupManager: OfflineBackupManager get() = driveHelper

    // Focused State Holders
    val authState = AuthSecurityStateHolder(context, sharedPrefs, driveHelper, repository, viewModelScope)
    val backupState = BackupStateHolder(driveHelper, repository, viewModelScope)
    val filterState = LinksFilterStateHolder()

    // Security Lockout and Exponential Backoff Manager
    val lockoutManager: com.example.security.LockoutManager get() = authState.lockoutManager
    private val _lockoutStatus: MutableStateFlow<com.example.security.LockoutStatus> get() = authState.lockoutStatus
    val lockoutStatus: StateFlow<com.example.security.LockoutStatus> = _lockoutStatus.asStateFlow()

    // Configurable Auto-Lock Timeout (seconds; default 30s)
    val autoLockTimeoutSeconds: MutableStateFlow<Int> get() = authState.autoLockTimeoutSeconds

    fun setAutoLockTimeout(seconds: Int) = authState.setAutoLockTimeout(seconds)

    fun checkLockoutStatus() = authState.checkLockoutStatus()

    fun recordFailedUnlockAttempt(): com.example.security.LockoutStatus {
        val newStatus = lockoutManager.recordFailedAttempt()
        _lockoutStatus.value = newStatus
        if (newStatus is com.example.security.LockoutStatus.Erased) {
            eraseLocalVault()
        }
        return newStatus
    }

    fun recordSuccessfulUnlock() {
        lockoutManager.recordSuccessfulUnlock()
        _lockoutStatus.value = com.example.security.LockoutStatus.Unlocked
        _isUnlocked.value = true
        migratePlaintextCredentials()
    }

    fun lockVault() {
        _isUnlocked.value = false
        com.example.security.KeyHierarchy.lockVault()
        checkLockoutStatus()
    }

    fun eraseLocalVault() {
        viewModelScope.launch {
            try {
                repository.deleteAllLinks()
            } catch (e: Throwable) {
                Log.e("LinkViewModel", "Error erasing links", e)
            }
            logout()
            _lockoutStatus.value = com.example.security.LockoutStatus.Erased
        }
    }

    // Theme state persisted and backed up/restored
    private val _isDarkTheme = MutableStateFlow<Boolean?>(null)
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        val current = _isDarkTheme.value ?: false
        val newValue = !current
        _isDarkTheme.value = newValue
        sharedPrefs.edit().putBoolean("is_dark_theme", newValue).apply()
    }

    enum class VaultSegment {
        ALL,
        LINKS,
        PASSWORDS,
        PINNED
    }

    enum class ViewDensity {
        COMFORTABLE,
        DENSE
    }

    @Immutable
    data class VaultCounts(
        val all: Int = 0,
        val links: Int = 0,
        val passwords: Int = 0,
        val pinned: Int = 0
    )

    // Segment Filter Switcher state
    val vaultSegment: MutableStateFlow<VaultSegment> get() = filterState.vaultSegment

    // View Density Mode (Comfortable vs Dense List)
    val viewDensity = MutableStateFlow(
        if (sharedPrefs.getString("view_density", "COMFORTABLE") == "DENSE") ViewDensity.DENSE else ViewDensity.COMFORTABLE
    )

    fun toggleViewDensity() {
        val next = if (viewDensity.value == ViewDensity.COMFORTABLE) ViewDensity.DENSE else ViewDensity.COMFORTABLE
        viewDensity.value = next
        sharedPrefs.edit().putString("view_density", next.name).apply()
    }

    // Privacy Shield (FLAG_SECURE & quick biometric auto-lock)
    val privacyShieldEnabled: MutableStateFlow<Boolean> get() = authState.privacyShieldEnabled

    fun togglePrivacyShield() = authState.togglePrivacyShield()

    fun setVaultSegment(segment: VaultSegment) {
        vaultSegment.value = segment
        if (segment == VaultSegment.PINNED) {
            showOnlyFavorites.value = true
            if (selectedCategory.value == "🔑 Passwords") {
                selectedCategory.value = null
            }
        } else {
            showOnlyFavorites.value = false
            if (segment == VaultSegment.PASSWORDS) {
                selectedCategory.value = "🔑 Passwords"
            } else if (selectedCategory.value == "🔑 Passwords") {
                selectedCategory.value = null
            }
        }
    }

    // Definition of local user profile
    @Immutable
    data class LocalUser(
        val email: String,
        val name: String,
        val passcode: String,
        val passcodeType: String = "PIN",
        val biometricAllowed: Boolean = false,
        val lockEnabled: Boolean = false
    )

    private val _registeredUsers: MutableStateFlow<List<LocalUser>> get() = authState.registeredUsers
    val registeredUsers: StateFlow<List<LocalUser>> = _registeredUsers.asStateFlow()

    private val _activeUser: MutableStateFlow<LocalUser?> get() = authState.activeUser
    val activeUser: StateFlow<LocalUser?> = _activeUser.asStateFlow()

    private val _isLockEnabled: MutableStateFlow<Boolean> get() = authState.isLockEnabled
    val isLockEnabled: StateFlow<Boolean> = _isLockEnabled.asStateFlow()

    private val _isBiometricAllowed: MutableStateFlow<Boolean> get() = authState.isBiometricAllowed
    val isBiometricAllowed: StateFlow<Boolean> = _isBiometricAllowed.asStateFlow()

    private val _storedPasscode: MutableStateFlow<String> get() = authState.storedPasscode
    val storedPasscode: StateFlow<String> = _storedPasscode.asStateFlow()

    private val _storedPasscodeType: MutableStateFlow<String> get() = authState.storedPasscodeType
    val storedPasscodeType: StateFlow<String> = _storedPasscodeType.asStateFlow()

    private val _isUnlocked: MutableStateFlow<Boolean> get() = authState.isUnlocked
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    val lockedCustomCategories: MutableStateFlow<Set<String>> get() = authState.lockedCustomCategories
    val unlockedCustomCategories: MutableStateFlow<Set<String>> get() = authState.unlockedCustomCategories

    fun toggleFolderLock(category: String) {
        val user = _activeUser.value ?: return
        val current = lockedCustomCategories.value.toMutableSet()
        if (current.contains(category)) {
            current.remove(category)
            unlockedCustomCategories.value = unlockedCustomCategories.value - category
        } else {
            current.add(category)
        }
        lockedCustomCategories.value = current
        sharedPrefs.edit().putStringSet("locked_categories_${user.email}", current).apply()
    }

    fun setFolderUnlockedInSession(category: String, unlocked: Boolean) {
        val current = unlockedCustomCategories.value.toMutableSet()
        if (unlocked) {
            current.add(category)
        } else {
            current.remove(category)
        }
        unlockedCustomCategories.value = current
    }

    fun lockAllCustomFolders() {
        if (unlockedCustomCategories.value.isNotEmpty()) {
            unlockedCustomCategories.value = emptySet()
        }
    }

    fun lockFolderIfDifferent(selectedCat: String?) {
        val current = unlockedCustomCategories.value
        if (current.isNotEmpty()) {
            val updated = current.filter { it == selectedCat }.toSet()
            if (updated != current) {
                unlockedCustomCategories.value = updated
            }
        }
    }

    /**
     * Debug-only helper to seed 500 realistic links for scrolling frame-timing benchmarks.
     * No-op in release builds.
     */
    fun seedBenchmarkItemsIfDebug(count: Int = 500) {
        if (!com.example.BuildConfig.DEBUG) return
        viewModelScope.launch(Dispatchers.IO) {
            val userEmail = _activeUser.value?.email ?: "vault@linkvault.local"
            val items = DebugSeedHelper.generateSeedLinks(count = count, userEmail = userEmail)
            if (items.isNotEmpty()) {
                repository.insertLinks(items)
            }
        }
    }

    private suspend fun seedDefaultVaultData() {
        // No mock data seeded; database remains 100% clean for user data
    }

    private fun getRegisteredUsersFromPrefs(): List<LocalUser> = authState.getRegisteredUsersFromPrefs()

    private fun saveUsersToPrefs(users: List<LocalUser>) = authState.saveUsersToPrefs(users)

    private fun syncActiveUserSettings(user: LocalUser?) = authState.syncActiveUserSettings(user)

    fun updateUserProfile(updated: LocalUser) {
        val currentList = _registeredUsers.value.toMutableList()
        val index = currentList.indexOfFirst { it.email.equals(updated.email, ignoreCase = true) }
        if (index != -1) {
            currentList[index] = updated
        } else {
            currentList.add(updated)
        }
        _registeredUsers.value = currentList
        saveUsersToPrefs(currentList)

        if (_activeUser.value?.email?.equals(updated.email, ignoreCase = true) == true) {
            _activeUser.value = updated
            syncActiveUserSettings(updated)
        }
    }

    fun registerNewLocalUser(name: String, email: String, pincode: String, allowBiometrics: Boolean): Boolean {
        val currentList = _registeredUsers.value.toMutableList()
        if (currentList.any { it.email.equals(email, ignoreCase = true) }) {
            return false
        }
        val hashedPasscode = if (pincode.isNotBlank()) com.example.security.PinSecurity.hashPasscode(pincode.toCharArray()) else ""
        val newUser = LocalUser(
            email = email.trim(),
            name = name.trim(),
            passcode = hashedPasscode,
            passcodeType = if (pincode.isNotBlank()) "PIN" else "FINGERPRINT",
            biometricAllowed = allowBiometrics,
            lockEnabled = allowBiometrics || pincode.isNotBlank()
        )
        currentList.add(newUser)
        _registeredUsers.value = currentList
        saveUsersToPrefs(currentList)
        loginUser(newUser)
        return true
    }

    fun loginUser(user: LocalUser) {
        sharedPrefs.edit().putString("last_active_user_email", user.email).apply()
        _activeUser.value = user
        syncActiveUserSettings(user)
        _isUnlocked.value = !user.lockEnabled
    }

    fun logout() {
        _activeUser.value = null
        syncActiveUserSettings(null)
        _isUnlocked.value = false
    }

    fun deleteUserProfile(email: String) {
        val currentList = _registeredUsers.value.filterNot { it.email.equals(email, ignoreCase = true) }
        _registeredUsers.value = currentList
        saveUsersToPrefs(currentList)
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteLinksByUserEmail(email)
        }
        if (_activeUser.value?.email?.equals(email, ignoreCase = true) == true) {
            logout()
        }
    }

    fun setUnlocked(unlocked: Boolean) {
        _isUnlocked.value = unlocked
        if (unlocked) {
            recordSuccessfulUnlock()
        }
    }

    fun enableLock(enabled: Boolean, passcode: String, passcodeType: String, allowBiometric: Boolean) {
        val current = _activeUser.value ?: return
        val finalPasscode = if (passcode.isNotBlank() && !com.example.security.PinSecurity.isHashed(passcode)) {
            com.example.security.PinSecurity.hashPasscode(passcode.toCharArray())
        } else {
            passcode
        }
        val updated = current.copy(
            lockEnabled = enabled,
            passcode = finalPasscode,
            passcodeType = passcodeType,
            biometricAllowed = allowBiometric
        )
        updateUserProfile(updated)
    }

    fun disableLock() {
        val current = _activeUser.value ?: return
        val updated = current.copy(
            lockEnabled = false,
            passcode = ""
        )
        updateUserProfile(updated)
    }

    fun verifyPasscode(entered: String): Boolean {
        val currentLockout = lockoutManager.checkStatus()
        if (currentLockout is com.example.security.LockoutStatus.Locked || currentLockout is com.example.security.LockoutStatus.Erased) {
            _lockoutStatus.value = currentLockout
            return false
        }

        val stored = _storedPasscode.value
        val match = com.example.security.PinSecurity.verifyPasscode(entered.toCharArray(), stored)
        if (match) {
            // Transparent migration: if stored PIN was not hashed, hash it with PBKDF2 immediately and delete plain text
            val current = _activeUser.value
            if (current != null && stored.isNotBlank() && !com.example.security.PinSecurity.isHashed(stored)) {
                val hashed = com.example.security.PinSecurity.hashPasscode(entered.toCharArray())
                val updated = current.copy(passcode = hashed)
                updateUserProfile(updated)
            }
            recordSuccessfulUnlock()
        } else {
            recordFailedUnlockAttempt()
        }
        return match
    }

    fun toggleBiometricSetting(allowed: Boolean) {
        val current = _activeUser.value ?: return
        val updated = current.copy(biometricAllowed = allowed)
        updateUserProfile(updated)
    }

    val searchQuery: MutableStateFlow<String> get() = filterState.searchQuery
    val isPrivateVaultUnlockedFlow: MutableStateFlow<Boolean> get() = authState.isPrivateVaultUnlockedFlow
    val isPasswordsUnlockedFlow: MutableStateFlow<Boolean> get() = authState.isPasswordsUnlockedFlow

    fun setPrivateVaultUnlocked(unlocked: Boolean) {
        isPrivateVaultUnlockedFlow.value = unlocked
    }

    fun setPasswordsUnlocked(unlocked: Boolean) {
        isPasswordsUnlockedFlow.value = unlocked
    }

    @Immutable
    data class SecurityContext(
        val query: String,
        val privateUnlocked: Boolean,
        val passwordsUnlocked: Boolean,
        val lockedCustom: Set<String>,
        val unlockedCustom: Set<String>
    )

    // Uses debouncedSearchQuery (250ms debounce) so typing does not trigger full list filter on every keystroke
    val securityFilterState: Flow<SecurityContext> = combine(
        filterState.debouncedSearchQuery,
        isPrivateVaultUnlockedFlow,
        isPasswordsUnlockedFlow,
        lockedCustomCategories,
        unlockedCustomCategories
    ) { query, privateUnlocked, passwordsUnlocked, lockedCustom, unlockedCustom ->
        SecurityContext(query, privateUnlocked, passwordsUnlocked, lockedCustom, unlockedCustom)
    }.distinctUntilChanged()

    val selectedCategory: MutableStateFlow<String?> get() = filterState.selectedCategory
    val showOnlyFavorites: MutableStateFlow<Boolean> get() = filterState.showOnlyFavorites

    // Standard list of suggested categories with emojis for beautiful visual presentation
    val defaultCategories = listOf(
        "🔑 Passwords",
        "🔐 Private Vault",
        "📝 Notes"
    )

    private fun getDateGroupLabel(timestamp: Long): String {
        return LinkUiMapper.getDateGroupLabel(timestamp)
    }

    private fun matchesDateQuery(timestamp: Long, query: String): Boolean {
        val clean = query.trim().lowercase()
        if (clean.isBlank()) return false
        val label = getDateGroupLabel(timestamp).lowercase()
        if (label.contains(clean)) return true

        val sdfFull = java.text.SimpleDateFormat("EEEE MMMM d yyyy dd/MM/yyyy dd-MM-yyyy MMM", java.util.Locale.getDefault())
        val formatted = sdfFull.format(java.util.Date(timestamp)).lowercase()
        return formatted.contains(clean)
    }

    val manuallyCreatedCategories = MutableStateFlow<List<String>>(emptyList())

    fun addManualCategory(category: String) {
        val trimmed = category.trim()
        if (trimmed.isNotBlank() && !manuallyCreatedCategories.value.contains(trimmed)) {
            manuallyCreatedCategories.value = manuallyCreatedCategories.value + trimmed
        }
    }

    fun deleteCategory(category: String, deleteLinks: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            manuallyCreatedCategories.value = manuallyCreatedCategories.value.filter { it != category }
            val allLinksList = repository.allLinks.first()
            val userEmail = _activeUser.value?.email ?: ""
            val matchedLinks = allLinksList.filter { link ->
                link.category == category &&
                (link.userEmail.equals(userEmail, ignoreCase = true) || link.userEmail.isEmpty())
            }
            for (link in matchedLinks) {
                if (deleteLinks) {
                    repository.delete(link)
                } else {
                    repository.update(link.copy(category = "📁 Uncategorized"))
                }
            }
            if (selectedCategory.value == category) {
                selectedCategory.value = null
            }
            autoBackupIfNeeded()
        }
    }

    val allUserUnfilteredLinks: StateFlow<List<LinkEntity>> = combine(
        repository.allLinks,
        _activeUser
    ) { all, active ->
        val userEmail = active?.email ?: ""
        all.filter { link ->
            link.userEmail.equals(userEmail, ignoreCase = true) || link.userEmail.isEmpty()
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Dynamic categories list combining defaults with any newly created ones
    val categories: StateFlow<List<String>> = combine(
        repository.allLinks,
        manuallyCreatedCategories,
        _activeUser
    ) { all, manual, active ->
        val userEmail = active?.email ?: ""
        val dbCategories = all.filter { link ->
            link.userEmail.equals(userEmail, ignoreCase = true) || link.userEmail.isEmpty()
        }.map { it.category }.distinct()
        (defaultCategories + manual + dbCategories).distinct()
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = defaultCategories
        )

    val vaultCounts: StateFlow<VaultCounts> = combine(
        allUserUnfilteredLinks,
        isPasswordsUnlockedFlow
    ) { all, passwordsUnlocked ->
        val linksCount = all.count { it.category != "🔑 Passwords" && it.description != "SECURE_PASSWORD" }
        val passwordsCount = all.count { it.category == "🔑 Passwords" || it.description == "SECURE_PASSWORD" }
        val pinnedCount = all.count { it.isFavorite }
        val allCount = if (passwordsUnlocked) all.size else all.count { it.category != "🔑 Passwords" && it.category != "🔐 Private Vault" }
        VaultCounts(
            all = allCount,
            links = linksCount,
            passwords = passwordsCount,
            pinned = pinnedCount
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VaultCounts(0, 0, 0, 0)
        )

    // Off-main-thread filtered links list with distinctUntilChanged
    val links: StateFlow<List<LinkEntity>> = combine(
        allUserUnfilteredLinks,
        securityFilterState,
        selectedCategory,
        vaultSegment
    ) { userLinks, secContext, category, segment ->
        userLinks.filter { link ->
            val matchesQuery = secContext.query.isEmpty() ||
                    link.title.contains(secContext.query, ignoreCase = true) ||
                    link.url.contains(secContext.query, ignoreCase = true) ||
                    (!com.example.security.RecordEncryption.isEncrypted(link.note) && link.note.contains(secContext.query, ignoreCase = true)) ||
                    link.category.contains(secContext.query, ignoreCase = true) ||
                    matchesDateQuery(link.timestamp, secContext.query)

            val isPassword = link.category == "🔑 Passwords" || link.description == "SECURE_PASSWORD"
            val matchesSegment = when (segment) {
                VaultSegment.ALL -> true
                VaultSegment.LINKS -> !isPassword
                VaultSegment.PASSWORDS -> isPassword
                VaultSegment.PINNED -> link.isFavorite
            }

            val matchesCategory = if (category == null) {
                if (segment == VaultSegment.PASSWORDS) {
                    secContext.passwordsUnlocked
                } else if (secContext.query.isNotEmpty()) {
                    val isSecret = link.category == "🔐 Private Vault" || link.category == "🔑 Passwords" || secContext.lockedCustom.contains(link.category)
                    if (isSecret) {
                        (link.category == "🔐 Private Vault" && secContext.privateUnlocked) ||
                        (link.category == "🔑 Passwords" && secContext.passwordsUnlocked) ||
                        (secContext.lockedCustom.contains(link.category) && secContext.unlockedCustom.contains(link.category))
                    } else {
                        true
                    }
                } else {
                    if (segment == VaultSegment.LINKS) {
                        link.category != "🔐 Private Vault" && !secContext.lockedCustom.contains(link.category)
                    } else {
                        link.category != "🔐 Private Vault" && link.category != "🔑 Passwords" && !secContext.lockedCustom.contains(link.category)
                    }
                }
            } else {
                link.category == category
            }
            val matchesFav = segment != VaultSegment.PINNED || link.isFavorite

            matchesQuery && matchesSegment && matchesCategory && matchesFav
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Precomputed @Immutable Vault UI Data (sorted items + date-grouped rows)
     * built entirely on Dispatchers.Default with instance-level LinkUiModel caching.
     */
    val vaultData: StateFlow<ImmutableVaultData> = combine(
        links,
        _isUnlocked
    ) { filteredLinks, unlocked ->
        LinkUiMapper.buildVaultData(
            links = filteredLinks,
            context = context,
            isVaultUnlocked = unlocked
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ImmutableVaultData()
        )

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onCategorySelected(category: String?) {
        selectedCategory.value = category
        searchQuery.value = ""
    }

    fun onToggleFavoritesOnly() {
        showOnlyFavorites.value = !showOnlyFavorites.value
    }

    // Capture and save standard user actions
    fun addLink(
        url: String,
        title: String = "",
        category: String = "📁 Uncategorized",
        note: String = "",
        isFavorite: Boolean = false,
        description: String = "",
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val cleanedUrl = cleanUrlProtocol(url)
            val finalTitle = title.ifBlank { cleanedUrl }
            val userEmail = _activeUser.value?.email ?: ""
            val isCredential = category.contains("Password") || category.contains("🔑") || note.contains("|||")

            val entity = LinkEntity(
                url = cleanedUrl,
                title = finalTitle,
                description = description,
                category = if (category.isBlank()) "📁 Uncategorized" else category,
                note = note,
                isFavorite = isFavorite,
                userEmail = userEmail
            )

            val id = repository.insert(entity).toInt()

            if (isCredential && note.isNotBlank()) {
                val encNote = com.example.security.RecordEncryption.encrypt(context, note, id, userEmail)
                repository.update(entity.copy(id = id, note = encNote))
            }

            onSuccess?.invoke()

            // Background fetch of webpage title & local 64x64 favicon (zero network during list scroll)
            launch(Dispatchers.IO) {
                FaviconStorageManager.fetchAndCacheFaviconForUrl(context, cleanedUrl)
            }

            if (title.isBlank() || title == url) {
                fetchWebTitleInBackground(id, cleanedUrl)
            }

            autoBackupIfNeeded()
        }
    }

    fun updateLink(link: LinkEntity) {
        viewModelScope.launch {
            val isCredential = link.category.contains("Password") || link.category.contains("🔑") || link.note.contains("|||")
            val finalLink = if (isCredential && !com.example.security.RecordEncryption.isEncrypted(link.note) && link.note.isNotBlank()) {
                val encNote = com.example.security.RecordEncryption.encrypt(context, link.note, link.id, link.userEmail)
                link.copy(note = encNote)
            } else {
                link
            }
            repository.update(finalLink)
            launch(Dispatchers.IO) {
                FaviconStorageManager.fetchAndCacheFaviconForUrl(context, finalLink.url)
            }
            autoBackupIfNeeded()
        }
    }

    fun getDecryptedNote(link: LinkEntity): String {
        return com.example.security.RecordEncryption.decrypt(
            context = context,
            storedValue = link.note,
            recordId = link.id,
            userEmail = link.userEmail,
            isUnlocked = _isUnlocked.value
        )
    }

    fun migratePlaintextCredentials() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentLinks = repository.allLinks.first()
                currentLinks.forEach { link ->
                    val isCredential = link.category.contains("Password") || link.category.contains("🔑") || link.note.contains("|||")
                    if (isCredential && !com.example.security.RecordEncryption.isEncrypted(link.note) && link.note.isNotBlank()) {
                        val encNote = com.example.security.RecordEncryption.encrypt(context, link.note, link.id, link.userEmail)
                        repository.update(link.copy(note = encNote))
                    }
                }
            } catch (e: Exception) {
                // Safe ignore
            }
        }
    }

    fun toggleFavorite(link: LinkEntity) {
        viewModelScope.launch {
            repository.update(link.copy(isFavorite = !link.isFavorite))
            autoBackupIfNeeded()
        }
    }

    fun deleteLink(link: LinkEntity) {
        viewModelScope.launch {
            repository.delete(link)
            LinkUiMapper.invalidateCache(link.id)
            autoBackupIfNeeded()
        }
    }

    fun fetchWebTitleInBackground(linkId: Int, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val fetchedTitle = repository.fetchTitleFromUrl(url)
            if (!fetchedTitle.isNullOrBlank()) {
                val current = repository.getLinkById(linkId)
                if (current != null) {
                    repository.update(current.copy(title = fetchedTitle))
                }
            }
        }
    }

    // Extracts URL and remaining title context from arbitrary texts
    fun extractUrlAndTitle(sharedText: String): Pair<String, String> {
        val urlRegex = """(https?://[^\s]+)""".toRegex()
        val match = urlRegex.find(sharedText)
        return if (match != null) {
            val url = match.value
            val title = sharedText.replace(url, "")
                .replace(Regex("""[\r\n\t]+"""), " ")
                .trim()
                .trim { it in listOf('-', ':', '|', ' ', ',', ';') }
            Pair(url, title)
        } else {
            val trimmed = sharedText.trim()
            if (!trimmed.contains(" ") && !trimmed.contains("\n") && !trimmed.contains("\r")) {
                val simpleDomainRegex = """^[a-zA-Z0-9-]+\.[a-zA-Z]{2,12}(?:/[^\s]*)?$""".toRegex()
                if (simpleDomainRegex.matches(trimmed)) {
                    return Pair("https://$trimmed", "")
                }
            }
            Pair("", sharedText)
        }
    }

    private fun cleanUrlProtocol(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }.trim()
    }

    // ==========================================
    // BACKUP & RESTORE BUSINESS LOGIC
    // ==========================================

    fun autoBackupIfNeeded() {
        backupState.performAutoBackupIfEnabled(
            activeUser = _activeUser.value,
            profiles = _registeredUsers.value,
            categories = categories.value,
            isDarkTheme = _isDarkTheme.value ?: false
        )
    }

    fun backupToDrive(
        encryptionPassword: CharArray,
        simulateRetry: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val metadataStore = com.example.autofill.CredentialMetadataStore(context)
                val neverSaveManager = com.example.autofill.NeverSaveManager(context)
                val payload = BackupPayload(
                    links = repository.allLinks.first(),
                    profiles = _registeredUsers.value,
                    customCategories = categories.value,
                    neverSaveList = neverSaveManager.neverSaveDomains.value.toList(),
                    credentialAppMetadata = metadataStore.getAllMetadataMap().values.toList(),
                    isDarkTheme = _isDarkTheme.value ?: false,
                    autoBackupEnabled = driveHelper.autoBackupEnabled
                )
                val success = driveHelper.performBackup(payload, encryptionPassword, simulateRetry)
                if (success) {
                    onSuccess()
                } else {
                    val status = driveHelper.backupStatus.value
                    val msg = if (status is BackupStatus.Error) status.message else "Backup error"
                    onError(msg)
                }
            } catch (e: Exception) {
                onError(e.message ?: "Backup failed")
            }
        }
    }

    fun restoreFromDrive(
        backupItem: DriveBackupItem,
        decryptionPassword: CharArray,
        mode: String, // "MERGE" or "REPLACE"
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val payload = driveHelper.restoreBackup(backupItem, decryptionPassword)
                if (payload != null) {
                    val metadataStore = com.example.autofill.CredentialMetadataStore(context)
                    val neverSaveManager = com.example.autofill.NeverSaveManager(context)
                    if (mode == "REPLACE") {
                        repository.deleteAllLinks()
                        LinkUiMapper.clearCache()
                    }
                    repository.insertLinks(payload.links)
                    metadataStore.restoreAllMetadata(
                        items = payload.credentialAppMetadata,
                        replaceExisting = (mode == "REPLACE")
                    )
                    if (mode == "REPLACE") {
                        neverSaveManager.clearAllNeverSaveDomains()
                    }
                    payload.neverSaveList.forEach { neverSaveManager.addNeverSaveDomain(it) }

                    // Write profile updates
                    _registeredUsers.value = payload.profiles
                    saveUsersToPrefs(payload.profiles)

                    // Restore settings & theme
                    _isDarkTheme.value = payload.isDarkTheme
                    sharedPrefs.edit().putBoolean("is_dark_theme", payload.isDarkTheme).apply()
                    driveHelper.autoBackupEnabled = payload.autoBackupEnabled

                    // Sync active user context
                    val curActive = _activeUser.value
                    val restoredActive = payload.profiles.find { it.email.equals(curActive?.email, ignoreCase = true) }
                        ?: payload.profiles.firstOrNull()

                    _activeUser.value = restoredActive
                    syncActiveUserSettings(restoredActive)

                    onSuccess()
                } else {
                    val status = driveHelper.restoreStatus.value
                    val msg = if (status is BackupStatus.Error) status.message else "Passphrase/decryption failed"
                    onError(msg)
                }
            } catch (e: Exception) {
                onError("Restore failed: ${e.message}")
            }
        }
    }

    // ==========================================
    // DUPLICATE DETECTION & OPEN COUNT LOGIC
    // ==========================================

    // Click count tracking stored directly in Room openCount column (no SharedPreferences reads per card)
    fun incrementOpenCount(linkId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.incrementOpenCount(linkId)
            } catch (e: Throwable) {
                Log.e("LinkViewModel", "Error incrementing openCount", e)
            }
        }
    }

    fun getOpenCount(linkId: Int): Int {
        return allUserUnfilteredLinks.value.find { it.id == linkId }?.openCount ?: 0
    }

    suspend fun findExistingDuplicateUrl(url: String): LinkEntity? {
        val userEmail = _activeUser.value?.email ?: ""
        val normalized = DuplicateDetector.normalizeUrl(url)
        return repository.allLinks.first().find {
            it.userEmail.equals(userEmail, ignoreCase = true) && DuplicateDetector.normalizeUrl(it.url) == normalized
        }
    }

    fun getDuplicateGroups(type: DuplicateDetector.DuplicateType): List<DuplicateDetector.DuplicateGroup> {
        val userEmail = _activeUser.value?.email ?: ""
        // Exclude passwords from duplicate checks and bulk cleanup to prevent accidental security credential loss
        val userLinks = links.value.filter {
            it.userEmail.equals(userEmail, ignoreCase = true) &&
            it.category != "🔑 Passwords" &&
            it.description != "SECURE_PASSWORD"
        }
        return DuplicateDetector.scanDuplicates(userLinks, type)
    }

    fun resolveDuplicatesInGroup(group: DuplicateDetector.DuplicateGroup, action: String) {
        viewModelScope.launch {
            if (group.items.size < 2) return@launch
            when (action) {
                "KEEP_NEWEST" -> {
                    val sorted = group.items.sortedByDescending { it.timestamp }
                    val toDelete = sorted.drop(1)
                    toDelete.forEach { repository.delete(it) }
                }
                "KEEP_OLDEST" -> {
                    val sorted = group.items.sortedBy { it.timestamp }
                    val toDelete = sorted.drop(1)
                    toDelete.forEach { repository.delete(it) }
                }
                "KEEP_FAVORITE" -> {
                    val sortedByFavThenNewest = group.items.sortedWith(
                        compareByDescending<LinkEntity> { it.isFavorite }
                            .thenByDescending { it.timestamp }
                    )
                    val toDelete = sortedByFavThenNewest.drop(1)
                    toDelete.forEach { repository.delete(it) }
                }
                "KEEP_MOST_OPENED" -> {
                    val sortedByClicksThenNewest = group.items.sortedWith(
                        compareByDescending<LinkEntity> { it.openCount }
                            .thenByDescending { it.timestamp }
                    )
                    val toDelete = sortedByClicksThenNewest.drop(1)
                    toDelete.forEach { repository.delete(it) }
                }
                "DELETE_DUPLICATES" -> {
                    group.items.forEach { repository.delete(it) }
                }
                "MERGE" -> {
                    val sorted = group.items.sortedBy { it.timestamp } // Oldest remains primary host
                    var keeper = sorted.first()
                    val toDelete = sorted.drop(1)

                    var mergedNotes = keeper.note
                    var isAnyFavorite = keeper.isFavorite
                    var totalOpens = keeper.openCount

                    toDelete.forEach { item ->
                        if (item.isFavorite) isAnyFavorite = true
                        totalOpens += item.openCount
                        if (item.note.isNotBlank() && !mergedNotes.contains(item.note)) {
                            mergedNotes = if (mergedNotes.isBlank()) item.note else "$mergedNotes | ${item.note}"
                        }
                    }

                    keeper = keeper.copy(isFavorite = isAnyFavorite, note = mergedNotes, openCount = totalOpens)
                    repository.update(keeper)
                    toDelete.forEach { repository.delete(it) }
                }
            }
        }
    }

    // Flows and state for Universal Save panel
    private val _incomingSharedText = MutableStateFlow<String?>(null)
    val incomingSharedText: StateFlow<String?> = _incomingSharedText.asStateFlow()

    fun setIncomingSharedText(text: String?) {
        _incomingSharedText.value = text
    }

    private val _incomingSharedImage = MutableStateFlow<String?>(null)
    val incomingSharedImage: StateFlow<String?> = _incomingSharedImage.asStateFlow()

    fun setIncomingSharedImage(uriString: String?) {
        _incomingSharedImage.value = uriString
    }

    fun copyUriToInternalStorage(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val extPrefix = if (uriString.contains(".png")) "png" else "jpg"
            val fileName = "attached_img_${System.currentTimeMillis()}.$extPrefix"
            val file = java.io.File(context.filesDir, fileName)
            file.outputStream().use { outputStream ->
                inputStream.use { it.copyTo(outputStream) }
            }
            // Generate ~400px JPEG thumbnail immediately so list/edit previews never decode full-res photos
            AttachmentThumbnailManager.generateThumbnailSync(file.absolutePath)
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val _clipboardNeverAsk = MutableStateFlow(sharedPrefs.getBoolean("clipboard_never_ask", false))
    val clipboardNeverAsk: StateFlow<Boolean> = _clipboardNeverAsk.asStateFlow()

    fun setClipboardNeverAsk(neverAsk: Boolean) {
        _clipboardNeverAsk.value = neverAsk
        sharedPrefs.edit().putBoolean("clipboard_never_ask", neverAsk).apply()
    }

    init {
        val users = getRegisteredUsersFromPrefs().toMutableList()
        val defaultProfile = LocalUser(
            email = "vault@linkvault.local",
            name = "My Vault",
            passcode = "",
            passcodeType = "FINGERPRINT",
            biometricAllowed = true,
            lockEnabled = true
        )
        if (users.isEmpty()) {
            users.add(defaultProfile)
            saveUsersToPrefs(users)
        }

        // Apply default-on for both Security (Vault Lock) and Privacy Shield
        if (!sharedPrefs.getBoolean("defaults_v2_security_shield_applied", false)) {
            val updatedUsers = users.map { user ->
                user.copy(lockEnabled = true, biometricAllowed = true)
            }
            users.clear()
            users.addAll(updatedUsers)
            saveUsersToPrefs(users)
            sharedPrefs.edit()
                .putBoolean("privacy_shield_enabled", true)
                .putBoolean("defaults_v2_security_shield_applied", true)
                .apply()
            privacyShieldEnabled.value = true
        }

        _registeredUsers.value = users
        val lastEmail = sharedPrefs.getString("last_active_user_email", defaultProfile.email) ?: defaultProfile.email
        val finalUser = users.find { it.email.equals(lastEmail, ignoreCase = true) } ?: users.first()

        // Retrieve and restore saved dark theme preference
        if (sharedPrefs.contains("is_dark_theme")) {
            _isDarkTheme.value = sharedPrefs.getBoolean("is_dark_theme", false)
        }

        _activeUser.value = finalUser
        syncActiveUserSettings(finalUser)
        _isUnlocked.value = !finalUser.lockEnabled

        // Seed initial items if database is empty, migrate legacy openCounts, and backfill local favicons & thumbnails
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // One-time migration of legacy SharedPreferences click_count_id_<id> into Room openCount column
                try {
                    val dao = AppDatabase.getDatabase(context).linkDao()
                    AppDatabase.migrateLegacyOpenCountsFromPrefs(sharedPrefs, dao)
                } catch (e: Throwable) {
                    Log.e("LinkViewModel", "Error migrating legacy openCount", e)
                }

                val currentLinks = repository.allLinks.first()

                // Backfill thumbnails for existing note attachments on IO thread
                AttachmentThumbnailManager.backfillThumbnails(currentLinks)
            } catch (e: Throwable) {
                com.example.util.SecureLog.e("LinkViewModel", "Error checking/seeding vault data", e)
            }
        }

        // Feature 1: Optional automatic background backup
        if (driveHelper.autoBackupEnabled) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    kotlinx.coroutines.delay(2000) // Delay to ensure app fully launches
                    val activePass = finalUser.passcode.ifBlank { "default_pass" }
                    val payload = BackupPayload(
                        links = repository.allLinks.first(),
                        profiles = _registeredUsers.value,
                        customCategories = categories.value,
                        isDarkTheme = _isDarkTheme.value ?: false,
                        autoBackupEnabled = true
                    )
                    // Silent background backup
                    driveHelper.performBackup(payload, activePass.toCharArray())
                } catch (e: Throwable) {
                    com.example.util.SecureLog.e("LinkViewModel", "Auto backup error", e)
                }
            }
        }
    }
}

// Simple Factory for constructing our ViewModel with manual dependency injection
class LinkViewModelFactory(
    private val repository: LinkRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LinkViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LinkViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
