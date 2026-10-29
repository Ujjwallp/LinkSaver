package com.example.data

import android.net.Uri
import java.util.Locale

object DuplicateDetector {

    private val TRACKING_PARAMS = setOf(
        "fbclid",
        "gclid",
        "dclid",
        "msclkid",
        "twclid",
        "igshid",
        "yclid",
        "mc_cid",
        "mc_eid",
        "_ga",
        "_gl",
        "rb_clickid",
        "s_kwcid"
    )

    private fun isTrackingParam(paramName: String): Boolean {
        val lower = paramName.lowercase(Locale.US)
        return lower.startsWith("utm_") || TRACKING_PARAMS.contains(lower)
    }

    /**
     * Normalizes a URL by:
     * - Removing protocols (http://, https://) and leading "www."
     * - Stripping URL fragments (#...)
     * - Removing ONLY known tracking query parameters (utm_*, fbclid, gclid, etc.)
     * - Preserving legitimate query parameters (e.g. youtube.com/watch?v=..., ?id=..., ?q=...)
     * - Removing trailing slashes on the path
     */
    fun normalizeUrl(url: String): String {
        var clean = url.trim()
        if (clean.isEmpty()) return ""

        // Strip fragment (#...)
        val hashIndex = clean.indexOf('#')
        if (hashIndex != -1) {
            clean = clean.substring(0, hashIndex)
        }

        // Remove protocols (case-insensitive)
        when {
            clean.startsWith("https://", ignoreCase = true) -> clean = clean.substring(8)
            clean.startsWith("http://", ignoreCase = true) -> clean = clean.substring(7)
        }

        // Remove leading www.
        if (clean.startsWith("www.", ignoreCase = true)) {
            clean = clean.substring(4)
        }

        // Split base path and query string
        val queryIndex = clean.indexOf('?')
        val basePart: String
        val queryPart: String?

        if (queryIndex != -1) {
            basePart = clean.substring(0, queryIndex)
            queryPart = clean.substring(queryIndex + 1)
        } else {
            basePart = clean
            queryPart = null
        }

        // Normalize base (lowercase host/path and strip trailing slashes)
        var normalizedBase = basePart.lowercase(Locale.getDefault()).trimEnd('/')

        // Filter query parameters: strip only tracking params, preserve legitimate params
        if (!queryPart.isNullOrBlank()) {
            val keptParams = queryPart.split('&')
                .mapNotNull { rawParam ->
                    if (rawParam.isBlank()) return@mapNotNull null
                    val eqIdx = rawParam.indexOf('=')
                    val rawKey = if (eqIdx != -1) rawParam.substring(0, eqIdx) else rawParam
                    val rawVal = if (eqIdx != -1) rawParam.substring(eqIdx + 1) else null
                    val keyTrimmed = rawKey.trim()
                    if (keyTrimmed.isEmpty() || isTrackingParam(keyTrimmed)) {
                        null
                    } else {
                        val normalizedKey = keyTrimmed.lowercase(Locale.US)
                        if (rawVal != null) "$normalizedKey=${rawVal.trim()}" else normalizedKey
                    }
                }
                .sorted()

            if (keptParams.isNotEmpty()) {
                normalizedBase = "$normalizedBase?${keptParams.joinToString("&")}"
            }
        }

        return normalizedBase
    }

    /**
     * Extracts the domain name from a URL via consolidated UrlParserUtil.
     */
    fun extractDomain(url: String): String = com.example.util.UrlParserUtil.extractDomain(url)

    /**
     * Represents a duplicate group centered around a common criterion.
     */
    data class DuplicateGroup(
        val key: String, // The duplicate key (normalized URL, title, or domain)
        val type: DuplicateType,
        val items: List<LinkEntity>
    )

    enum class DuplicateType {
        EXACT_URL, NORMAL_URL, TITLE, DOMAIN
    }

    /**
     * Scans and groups all links into duplicate groups based on criteria.
     */
    fun scanDuplicates(links: List<LinkEntity>, type: DuplicateType): List<DuplicateGroup> {
        if (links.size < 2) return emptyList()

        val groups = when (type) {
            DuplicateType.EXACT_URL -> {
                links.groupBy { it.url.trim() }
            }
            DuplicateType.NORMAL_URL -> {
                links.groupBy { normalizeUrl(it.url) }
            }
            DuplicateType.TITLE -> {
                links.groupBy { it.title.trim().lowercase(Locale.getDefault()) }
            }
            DuplicateType.DOMAIN -> {
                links.groupBy { extractDomain(it.url) }
            }
        }

        return groups.filter { it.value.size >= 2 && it.key.isNotBlank() }
            .map { (key, list) ->
                DuplicateGroup(
                    key = key,
                    type = type,
                    items = list.sortedBy { it.timestamp } // Sort oldest to newest initially
                )
            }
    }
}
