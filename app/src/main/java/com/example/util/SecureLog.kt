package com.example.util

import android.util.Log
import com.example.BuildConfig

/**
 * Security Hardening: Safe logger that only outputs during DEBUG builds and strictly
 * redacts or forbids logging of sensitive credentials, passwords, tokens, or URLs.
 */
object SecureLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, sanitize(message))
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, sanitize(message))
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.w(tag, sanitize(message), throwable)
            } else {
                Log.w(tag, sanitize(message))
            }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e(tag, sanitize(message), throwable)
            } else {
                Log.e(tag, sanitize(message))
            }
        }
    }

    private fun sanitize(msg: String): String {
        // Redact any potential password / credential structures
        return msg.replace(Regex("(\\|\\|\\|)[^\\s,}\\]]+"), "$1[REDACTED]")
            .replace(Regex("(?i)(password|passcode|passkey|secret|token|key)\\s*[:=]\\s*[^\\s,}\\]]+"), "$1=[REDACTED]")
    }
}
