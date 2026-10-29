package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages 100% offline local storage of 64x64 icons per domain.
 * - Zero network requests.
 * - Generates high-contrast deterministic circle avatar icons with domain initials locally.
 * - Zero repeated File.exists() disk checks via in-memory domain cache.
 */
object FaviconStorageManager {

    private const val FAVICON_DIR_NAME = "favicons"
    private const val TARGET_SIZE_PX = 64

    private val localPathCache = ConcurrentHashMap<String, String>()

    private val _faviconUpdateTick = MutableStateFlow(0L)
    val faviconUpdateTick: StateFlow<Long> = _faviconUpdateTick.asStateFlow()

    val isUserScrolling = AtomicBoolean(false)

    fun setScrolling(scrolling: Boolean) {
        isUserScrolling.set(scrolling)
    }

    fun normalizeDomain(rawDomainOrUrl: String): String {
        val clean = rawDomainOrUrl.trim().lowercase(Locale.US)
        if (clean.isBlank() || clean == "localhost") return ""
        val withoutScheme = clean
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
        val host = withoutScheme.substringBefore('/').substringBefore(':').substringBefore('?')
        return if (host.contains('.') && host.length in 3..253) host else ""
    }

    fun getDomainFile(context: Context, domainHost: String): File? {
        val normalized = normalizeDomain(domainHost)
        if (normalized.isEmpty()) return null
        val dir = File(context.filesDir, FAVICON_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val hash = sha256Hex(normalized).take(32)
        return File(dir, "fav_$hash.png")
    }

    fun getLocalFaviconPathIfPresent(context: Context, domainHost: String): String? {
        val normalized = normalizeDomain(domainHost)
        if (normalized.isEmpty()) return null

        val cached = localPathCache[normalized]
        if (cached != null) {
            return cached.ifEmpty { null }
        }

        val file = getDomainFile(context, normalized) ?: return null
        return if (file.exists() && file.length() > 0L) {
            val path = file.absolutePath
            localPathCache[normalized] = path
            path
        } else {
            // Generate local icon on first missing lookup
            val generatedPath = generateLocalDomainIcon(context, normalized)
            if (generatedPath != null) {
                localPathCache[normalized] = generatedPath
                generatedPath
            } else {
                localPathCache[normalized] = ""
                null
            }
        }
    }

    /**
     * Generates a local 64x64 icon (colored circle with domain's initial) offline.
     */
    suspend fun fetchAndCacheFaviconForUrl(context: Context, urlOrDomain: String): String? = withContext(Dispatchers.IO) {
        val domain = normalizeDomain(urlOrDomain)
        if (domain.isEmpty()) return@withContext null

        val existing = getLocalFaviconPathIfPresent(context, domain)
        if (existing != null) return@withContext existing

        generateLocalDomainIcon(context, domain)
    }

    suspend fun backfillMissingFavicons(context: Context, links: List<LinkEntity>) = withContext(Dispatchers.IO) {
        val distinctDomains = links
            .asSequence()
            .filter { it.category != "📝 Notes" && it.category != "🔑 Passwords" && it.description != "SECURE_PASSWORD" }
            .map { normalizeDomain(it.url) }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

        for (domain in distinctDomains) {
            if (getLocalFaviconPathIfPresent(context, domain) != null) continue
            generateLocalDomainIcon(context, domain)
        }
    }

    private fun generateLocalDomainIcon(context: Context, domainHost: String): String? {
        val destFile = getDomainFile(context, domainHost) ?: return null
        val initialChar = domainHost.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: 'L'

        val hash = sha256Hex(domainHost)
        val hue = (hash.take(6).toIntOrNull(16) ?: 0) % 360
        val bgColor = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.65f, 0.75f))

        val bitmap = Bitmap.createBitmap(TARGET_SIZE_PX, TARGET_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(TARGET_SIZE_PX / 2f, TARGET_SIZE_PX / 2f, TARGET_SIZE_PX / 2f, circlePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val yPos = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(initialChar.toString(), canvas.width / 2f, yPos, textPaint)

        return try {
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            bitmap.recycle()
            val path = destFile.absolutePath
            localPathCache[domainHost] = path
            _faviconUpdateTick.value = System.currentTimeMillis()
            path
        } catch (e: Exception) {
            bitmap.recycle()
            null
        }
    }

    fun saveBitmapForDomain(context: Context, domainHost: String, bitmap: Bitmap): String? {
        val normalized = normalizeDomain(domainHost)
        if (normalized.isEmpty()) return null
        val destFile = getDomainFile(context, normalized) ?: return null
        val scaled = if (bitmap.width != TARGET_SIZE_PX || bitmap.height != TARGET_SIZE_PX) {
            Bitmap.createScaledBitmap(bitmap, TARGET_SIZE_PX, TARGET_SIZE_PX, true)
        } else {
            bitmap
        }
        FileOutputStream(destFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        val path = destFile.absolutePath
        localPathCache[normalized] = path
        _faviconUpdateTick.value = System.currentTimeMillis()
        return path
    }

    fun clearMemoryCacheForTesting() {
        localPathCache.clear()
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
