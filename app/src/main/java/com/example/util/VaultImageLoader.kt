package com.example.util

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

/**
 * Configures a single application-wide Coil [ImageLoader] with:
 * - Bounded memory cache (20% of available heap)
 * - Bounded disk cache (32 MB in app cacheDir)
 * - crossfade(false) to avoid per-item alpha animations during scrolling
 * - 100% offline local image and file URI support
 */
object VaultImageLoader {

    private const val DISK_CACHE_SIZE_BYTES = 32L * 1024L * 1024L // 32 MB
    private const val MEMORY_CACHE_PERCENT = 0.20

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context): ImageLoader {
        val appContext = context.applicationContext
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    val loader = ImageLoader.Builder(appContext)
                        .memoryCache {
                            MemoryCache.Builder(appContext)
                                .maxSizePercent(MEMORY_CACHE_PERCENT)
                                .build()
                        }
                        .diskCache {
                            DiskCache.Builder()
                                .directory(File(appContext.cacheDir, "vault_coil_disk_cache"))
                                .maxSizeBytes(DISK_CACHE_SIZE_BYTES)
                                .build()
                        }
                        .crossfade(false)
                        .allowHardware(true)
                        .build()
                    Coil.setImageLoader(loader)
                    initialized = true
                    return loader
                }
            }
        }
        return Coil.imageLoader(appContext)
    }
}
