package com.example.autofill

import android.content.Context
import com.example.security.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Manages the local Never-Save list for both website domains and native Android app packages.
 */
class NeverSaveManager(context: Context) {

    private val prefs = SecureStorage.getEncryptedPrefs(
        context.applicationContext,
        "linksaver_never_save_prefs_encrypted"
    )

    private val _neverSaveDomains = MutableStateFlow<Set<String>>(emptySet())
    val neverSaveDomains: StateFlow<Set<String>> = _neverSaveDomains.asStateFlow()

    init {
        loadDomains()
    }

    private fun loadDomains() {
        val saved = prefs.getStringSet("never_save_domains_set", emptySet()) ?: emptySet()
        _neverSaveDomains.value = saved
    }

    fun normalizeTarget(raw: String): String {
        val trimmed = raw.trim().lowercase(Locale.US)
        if (trimmed.startsWith("app://") || trimmed.startsWith("android://")) {
            return AppIdentityVerifier.normalizePackageName(trimmed)
        }
        return trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore(':')
            .trim()
    }

    fun isNeverSaveDomain(domainOrPackage: String): Boolean {
        val clean = normalizeTarget(domainOrPackage)
        if (clean.isBlank()) return false
        val current = _neverSaveDomains.value
        if (current.contains(clean)) return true
        val registrable = AppIdentityVerifier.normalizeRegistrableDomain(clean)
        return registrable.isNotBlank() && current.contains(registrable)
    }

    fun isNeverSavePackage(packageName: String): Boolean {
        val normPkg = AppIdentityVerifier.normalizePackageName(packageName)
        if (normPkg.isBlank()) return false
        return _neverSaveDomains.value.contains(normPkg)
    }

    fun isNeverSaveTarget(domainHost: String, packageName: String): Boolean {
        if (packageName.isNotBlank() && isNeverSavePackage(packageName)) return true
        if (domainHost.isNotBlank() && isNeverSaveDomain(domainHost)) return true
        return false
    }

    fun addNeverSaveDomain(domainOrPackage: String) {
        val clean = normalizeTarget(domainOrPackage)
        if (clean.isBlank()) return
        val current = _neverSaveDomains.value.toMutableSet()
        current.add(clean)
        prefs.edit().putStringSet("never_save_domains_set", current).apply()
        _neverSaveDomains.value = current
    }

    fun addNeverSavePackage(packageName: String) {
        val normPkg = AppIdentityVerifier.normalizePackageName(packageName)
        if (normPkg.isBlank()) return
        addNeverSaveDomain(normPkg)
    }

    fun removeNeverSaveDomain(domainOrPackage: String) {
        val clean = normalizeTarget(domainOrPackage)
        val current = _neverSaveDomains.value.toMutableSet()
        current.remove(clean)
        prefs.edit().putStringSet("never_save_domains_set", current).apply()
        _neverSaveDomains.value = current
    }

    fun clearAllNeverSaveDomains() {
        prefs.edit().putStringSet("never_save_domains_set", emptySet()).apply()
        _neverSaveDomains.value = emptySet()
    }
}
