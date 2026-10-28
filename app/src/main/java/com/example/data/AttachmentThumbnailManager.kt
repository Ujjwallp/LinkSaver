package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Generates and caches ~400px long-side thumbnails (JPEG quality 80) beside full-size
 * attached note images so list/preview rows never decode multi-megabyte originals.
 */
object AttachmentThumbnailManager {

    const val THUMB_MAX_DIMENSION_PX = 400
    private const val THUMB_JPEG_QUALITY = 80
    private val verifiedThumbCache = ConcurrentHashMap<String, String>()

    /**
     * Returns the expected thumbnail file path for a given [originalPath].
     */
    fun computeThumbnailFile(originalPath: String): File {
        val original = File(originalPath)
        val baseName = original.nameWithoutExtension
        val parent = original.parentFile ?: File(".")
        return File(parent, "${baseName}_thumb.jpg")
    }

    /**
     * Non-blocking lookup: returns the local `_thumb.jpg` path if already generated,
     * otherwise falls back to [originalPath] while background backfill creates it.
     */
    fun getExistingThumbnailOrOriginal(originalPath: String): String {
        if (originalPath.isBlank()) return originalPath
        val cached = verifiedThumbCache[originalPath]
        if (cached != null) return cached

        val thumbFile = computeThumbnailFile(originalPath)
        return if (thumbFile.exists() && thumbFile.length() > 0L) {
            val path = thumbFile.absolutePath
            verifiedThumbCache[originalPath] = path
            path
        } else {
            originalPath
        }
    }

    /**
     * Generates a ~400px long-side thumbnail beside [originalPath] synchronously or on IO dispatcher.
     * Returns the thumbnail path on success, or [originalPath] if decoding fails.
     */
    fun generateThumbnailSync(originalPath: String): String {
        if (originalPath.isBlank()) return originalPath
        try {
            val origFile = File(originalPath)
            if (!origFile.exists() || origFile.length() <= 0L) return originalPath

            val thumbFile = computeThumbnailFile(originalPath)
            if (thumbFile.exists() && thumbFile.length() > 0L) {
                verifiedThumbCache[originalPath] = thumbFile.absolutePath
                return thumbFile.absolutePath
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(originalPath, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return originalPath

            val longSide = maxOf(width, height)
            var inSampleSize = 1
            while (longSide / (inSampleSize * 2) >= THUMB_MAX_DIMENSION_PX) {
                inSampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeFile(originalPath, decodeOpts) ?: return originalPath

            val curLong = maxOf(decoded.width, decoded.height)
            val finalBitmap = if (curLong > THUMB_MAX_DIMENSION_PX) {
                val ratio = THUMB_MAX_DIMENSION_PX.toFloat() / curLong.toFloat()
                val targetW = maxOf(1, (decoded.width * ratio).toInt())
                val targetH = maxOf(1, (decoded.height * ratio).toInt())
                val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                if (scaled !== decoded) decoded.recycle()
                scaled
            } else {
                decoded
            }

            FileOutputStream(thumbFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_JPEG_QUALITY, out)
                out.flush()
            }
            finalBitmap.recycle()

            if (thumbFile.exists() && thumbFile.length() > 0L) {
                verifiedThumbCache[originalPath] = thumbFile.absolutePath
                return thumbFile.absolutePath
            }
        } catch (e: Throwable) {
            // Fall back safely to original path
        }
        return originalPath
    }

    /**
     * Backfills thumbnails in the background for all existing note attachments.
     */
    suspend fun backfillThumbnails(links: List<LinkEntity>) = withContext(Dispatchers.IO) {
        for (link in links) {
            if (!link.description.startsWith("IMAGE_PATHS:")) continue
            val paths = link.description
                .removePrefix("IMAGE_PATHS:")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            for (path in paths) {
                generateThumbnailSync(path)
            }
        }
    }
}
