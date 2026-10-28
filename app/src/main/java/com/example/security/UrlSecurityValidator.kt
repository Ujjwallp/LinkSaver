package com.example.security

import java.net.InetAddress
import java.net.URI
import java.util.Locale

/**
 * Offline URL & Address Safety Validator.
 * Standardizes URL string normalization and validates hosts offline.
 */
object UrlSecurityValidator {

    fun isPrivateOrReservedIpBytes(bytes: ByteArray): Boolean {
        if (bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF

            if (b0 == 0) return true
            if (b0 == 10) return true
            if (b0 == 100 && (b1 in 64..127)) return true
            if (b0 == 127) return true
            if (b0 == 169 && b1 == 254) return true
            if (b0 == 172 && (b1 in 16..31)) return true
            if (b0 == 192 && b1 == 168) return true
            if (b0 >= 224) return true

            return false
        } else if (bytes.size == 16) {
            var allZeroPrefix = true
            for (i in 0 until 15) {
                if (bytes[i].toInt() != 0) {
                    allZeroPrefix = false
                    break
                }
            }
            if (allZeroPrefix && (bytes[15].toInt() == 0 || bytes[15].toInt() == 1)) {
                return true
            }

            var isIpv4Mapped = true
            for (i in 0 until 10) {
                if (bytes[i].toInt() != 0) {
                    isIpv4Mapped = false
                    break
                }
            }
            if (isIpv4Mapped && (bytes[10].toInt() and 0xFF) == 0xFF && (bytes[11].toInt() and 0xFF) == 0xFF) {
                val ipv4Bytes = bytes.copyOfRange(12, 16)
                return isPrivateOrReservedIpBytes(ipv4Bytes)
            }

            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF

            if ((b0 and 0xFE) == 0xFC) return true
            if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return true
            if (b0 == 0xFE && (b1 and 0xC0) == 0xC0) return true
            if (b0 == 0xFF) return true

            return false
        }
        return true
    }

    fun isPrivateOrReservedAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress ||
            address.isAnyLocalAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        return isPrivateOrReservedIpBytes(address.address)
    }

    fun isBlockedHostName(host: String): Boolean {
        val cleanHost = host.trim().lowercase(Locale.US).removeSurrounding("[", "]")
        if (cleanHost.isEmpty()) return true
        if (cleanHost == "localhost" ||
            cleanHost.endsWith(".localhost") ||
            cleanHost.endsWith(".local") ||
            cleanHost.endsWith(".internal") ||
            cleanHost == "::1" ||
            cleanHost == "0.0.0.0"
        ) {
            return true
        }
        return false
    }

    fun validateAndNormalizeUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty() || trimmed.length > 2048) return null

        val candidate = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            if (trimmed.contains("://")) {
                return null
            }
            "https://$trimmed"
        } else {
            trimmed
        }

        return try {
            val uri = URI(candidate)
            val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host ?: return null
            if (isBlockedHostName(host)) return null
            uri.toASCIIString()
        } catch (e: Exception) {
            null
        }
    }
}
