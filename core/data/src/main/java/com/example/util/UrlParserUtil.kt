package com.example.util

import android.net.Uri
import com.example.data.DuplicateDetector
import java.util.Locale

/**
 * Consolidated URL & Domain Parser for LinkVault.
 * Provides a single, authoritative implementation for domain extraction,
 * URL normalization, and path breakdown across UI and data layers.
 */
object UrlParserUtil {

    /**
     * Extracts a clean, lowercase domain from any URL or web address.
     * Strips protocols (http, https), "www.", trailing slashes, ports, and query parameters.
     */
    fun extractDomain(rawUrl: String): String {
        val clean = rawUrl.trim()
        if (clean.isEmpty()) return ""

        return try {
            val candidate = if (!clean.startsWith("http://", ignoreCase = true) &&
                !clean.startsWith("https://", ignoreCase = true)
            ) {
                "https://$clean"
            } else {
                clean
            }
            val uri = Uri.parse(candidate)
            var host = uri.host?.lowercase(Locale.US) ?: ""
            if (host.startsWith("www.")) {
                host = host.substring(4)
            }
            if (host.isBlank()) {
                clean.removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .substringBefore('/')
                    .substringBefore(':')
                    .lowercase(Locale.US)
            } else {
                host
            }
        } catch (e: Exception) {
            clean.removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .substringBefore('/')
                .substringBefore(':')
                .lowercase(Locale.US)
        }
    }

    /**
     * Extracts both domain host and a clean display path (truncated with ellipsis if over 24 chars).
     */
    fun extractDomainAndPath(rawUrl: String, maxPathLength: Int = 24): Pair<String, String> {
        val clean = rawUrl.trim()
        if (clean.isEmpty()) return Pair("", "")

        return try {
            val withProtocol = if (!clean.startsWith("http://", ignoreCase = true) &&
                !clean.startsWith("https://", ignoreCase = true)
            ) {
                "https://$clean"
            } else {
                clean
            }
            val uri = Uri.parse(withProtocol)
            val host = (uri.host ?: "").removePrefix("www.").lowercase(Locale.US)
            var path = uri.path ?: ""
            if (path.isEmpty()) {
                val afterHost = withProtocol.substringAfter("://").substringAfter('/', "")
                if (afterHost.isNotEmpty()) {
                    path = "/" + afterHost.substringBefore('?').substringBefore('#')
                }
            }
            val cleanPath = if (path.length > maxPathLength) {
                path.substring(0, maxPathLength - 2) + "…"
            } else {
                path
            }
            val resolvedHost = host.ifBlank {
                extractDomain(clean)
            }
            Pair(resolvedHost, cleanPath)
        } catch (e: Exception) {
            Pair(extractDomain(clean), "")
        }
    }

    /**
     * Normalizes a URL by stripping tracking parameters, normalizing schemes, and removing trailing slashes.
     */
    fun normalizeForComparison(url: String): String {
        return DuplicateDetector.normalizeUrl(url)
    }
}
