package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.AttachmentThumbnailManager
import com.example.data.FaviconStorageManager
import com.example.data.LinkEntity
import com.example.security.RecordEncryption
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Immutable
enum class CategoryIconKey {
    LOCK,
    KEY,
    NOTES,
    READ,
    VIDEO,
    INSPIRATION,
    WORK,
    SHOPPING,
    WEB;

    fun toImageVector(): ImageVector = when (this) {
        LOCK -> Icons.Filled.Lock
        KEY -> Icons.Filled.Key
        NOTES -> Icons.Filled.Description
        READ -> Icons.Filled.MenuBook
        VIDEO -> Icons.Filled.PlayCircle
        INSPIRATION -> Icons.Filled.AutoAwesome
        WORK -> Icons.Filled.Work
        SHOPPING -> Icons.Filled.ShoppingBag
        WEB -> Icons.Filled.Language
    }
}

/**
 * Precomputed, @Immutable UI representation of a [LinkEntity].
 * - Built off the main thread on Dispatchers.Default.
 * - Holds zero decrypted passwords (password secrets are decrypted only on-demand upon reveal/copy).
 * - Preserves instance identity across list updates when an item is unchanged so Compose skips recomposition.
 */
@Immutable
data class LinkUiModel(
    val id: Int,
    val title: String,
    val displayTitle: String,
    val displayUrl: String,
    val domainHost: String,
    val domainPath: String,
    val isWebLink: Boolean,
    val categoryLabel: String,
    val categoryIconKey: CategoryIconKey,
    val tagLabel: String,
    val isFavorite: Boolean,
    val isPassword: Boolean,
    val imagePaths: List<String>,
    val thumbnailPaths: List<String>,
    val dateText: String,
    val openCount: Int,
    val faviconPath: String?,
    val usernamePreview: String,
    val notePreview: String,
    val entity: LinkEntity,
    val domainInitial: String = "L",
    val domainColorArgb: Long = 0xFF6200EE,
    val domainBadgeText: String = domainHost.ifBlank { "web" },
    val denseSubtitleText: String = if (isPassword) {
        "${usernamePreview.ifBlank { "Account" }} ••••••••"
    } else {
        domainHost.ifBlank { displayUrl }
    },
    val hasNotePreview: Boolean = notePreview.isNotBlank(),
    val hasAssociatedDomain: Boolean = domainHost.isNotBlank() && domainHost != "localhost",
    val associatedDomainLabel: String = if (domainHost.isNotBlank() && domainHost != "localhost") {
        "🌐 $domainHost ↗"
    } else {
        ""
    },
    val displayUsername: String = usernamePreview.ifBlank { "No username" },
    val shortDateText: String = dateText.substringBefore(" •")
)

/**
 * Flat row model for LazyColumn so date headers and items are emitted in a single pass
 * on Dispatchers.Default with stable keys and contentTypes.
 */
@Immutable
sealed interface VaultListRow {
    val rowKey: Any
    val contentType: String

    fun contentTypeForDensity(isDenseMode: Boolean): String = contentType

    @Immutable
    data class HeaderRow(
        val title: String,
        val count: Int,
        val countText: String = count.toString(),
        override val rowKey: String = "header_$title"
    ) : VaultListRow {
        val dateHeader: String get() = title
        override val contentType: String get() = "header"
    }

    @Immutable
    data class ItemRow(
        val uiModel: LinkUiModel,
        val itemIndex: Int,
        val isEven: Boolean = itemIndex % 2 == 0,
        override val rowKey: Int = uiModel.id
    ) : VaultListRow {
        val model: LinkUiModel get() = uiModel
        override val contentType: String
            get() = when {
                uiModel.isPassword -> "password_card_item"
                uiModel.hasNotePreview -> "link_card_with_note_item"
                else -> "link_card_plain_item"
            }

        override fun contentTypeForDensity(isDenseMode: Boolean): String {
            return if (isDenseMode) {
                if (uiModel.isPassword) "dense_password_item" else "dense_link_item"
            } else {
                contentType
            }
        }
    }
}

/**
 * Immutable wrapper for lists exposed to Compose to guarantee structural stability.
 */
@Immutable
data class ImmutableVaultData(
    val rows: List<VaultListRow> = emptyList(),
    val uiModels: List<LinkUiModel> = emptyList()
) {
    val sortedModels: List<LinkUiModel> get() = uiModels
}

object LinkUiMapper {

    const val IMAGE_PATHS_PREFIX = "IMAGE_PATHS:"

    private val threadSafeDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    }

    private val threadSafeShortDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MMM d", Locale.getDefault())
    }

    private data class CachedUiEntry(
        val entity: LinkEntity,
        val faviconPath: String?,
        val isUnlocked: Boolean,
        val uiModel: LinkUiModel
    )

    private val instanceCache = ConcurrentHashMap<Int, CachedUiEntry>()

    fun clearCache() {
        instanceCache.clear()
    }

    fun invalidateCache(id: Int) {
        instanceCache.remove(id)
    }

    fun getDateGroupLabel(timestamp: Long): String {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val startOfYesterday = startOfToday - 24 * 60 * 60 * 1000L
        val startOfThisWeek = startOfToday - 7 * 24 * 60 * 60 * 1000L
        val startOfLastWeek = startOfToday - 14 * 24 * 60 * 60 * 1000L
        val startOfThisMonth = startOfToday - 30 * 24 * 60 * 60 * 1000L
        return when {
            timestamp >= startOfToday -> "Today"
            timestamp >= startOfYesterday -> "Yesterday"
            timestamp >= startOfThisWeek -> "This Week"
            timestamp >= startOfLastWeek -> "Last Week"
            timestamp >= startOfThisMonth -> "This Month"
            else -> "Older"
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        val formatter = threadSafeDateFormat.get() ?: SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    fun formatShortTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val oneDayMs = 24 * 60 * 60 * 1000L
        return when {
            diff < 60_000L -> "Just now"
            diff < 3_600_000L -> "${(diff / 60_000L).coerceAtLeast(1)}m ago"
            diff < oneDayMs -> "${(diff / 3_600_000L).coerceAtLeast(1)}h ago"
            diff < 2 * oneDayMs -> "Yesterday"
            diff < 7 * oneDayMs -> "${(diff / oneDayMs).coerceAtLeast(2)}d ago"
            else -> {
                val shortFmt = threadSafeShortDateFormat.get() ?: SimpleDateFormat("MMM d", Locale.getDefault())
                shortFmt.format(Date(timestamp))
            }
        }
    }

    /**
     * Centralized parser for "IMAGE_PATHS:" description field so UI composables never
     * repeatedly call removePrefix().split(",") during composition.
     */
    fun parseImagePaths(description: String): List<String> {
        if (!description.startsWith(IMAGE_PATHS_PREFIX)) return emptyList()
        val raw = description.removePrefix(IMAGE_PATHS_PREFIX)
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun encodeImagePaths(paths: List<String>): String {
        val clean = paths.map { it.trim() }.filter { it.isNotEmpty() }
        return if (clean.isEmpty()) "" else IMAGE_PATHS_PREFIX + clean.joinToString(",")
    }

    fun resolveCategoryIconKey(category: String, url: String): CategoryIconKey {
        val lowerCat = category.lowercase(Locale.US)
        val lowerUrl = url.lowercase(Locale.US)
        return when {
            category == "🔐 Private Vault" -> CategoryIconKey.LOCK
            category == "🔑 Passwords" -> CategoryIconKey.KEY
            category == "📝 Notes" -> CategoryIconKey.NOTES
            lowerCat.contains("read") || category.contains("📚") || lowerUrl.contains("medium.com") || lowerUrl.contains("wikipedia.org") -> CategoryIconKey.READ
            lowerCat.contains("video") || category.contains("🎬") || lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") || lowerUrl.contains("vimeo") || lowerUrl.contains("tiktok.com") -> CategoryIconKey.VIDEO
            lowerCat.contains("inspiration") || category.contains("💡") || lowerUrl.contains("pinterest") || lowerUrl.contains("instagram.com") || lowerUrl.contains("dribbble") -> CategoryIconKey.INSPIRATION
            lowerCat.contains("work") || category.contains("💼") || lowerUrl.contains("github") || lowerUrl.contains("notion") || lowerUrl.contains("google") -> CategoryIconKey.WORK
            lowerCat.contains("shopping") || category.contains("🛍️") || lowerUrl.contains("amazon") || lowerUrl.contains("ebay") -> CategoryIconKey.SHOPPING
            else -> CategoryIconKey.WEB
        }
    }

    fun extractDomainAndPath(rawUrl: String): Pair<String, String> {
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
            val host = (uri.host ?: "").removePrefix("www.")
            val path = uri.path ?: ""
            val cleanPath = if (path.length > 24) path.substring(0, 22) + "…" else path
            val resolvedHost = host.ifBlank {
                clean.removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .substringBefore('/')
                    .substringBefore(':')
            }
            Pair(resolvedHost, cleanPath)
        } catch (e: Exception) {
            Pair(clean, "")
        }
    }

    /**
     * Maps a [LinkEntity] to an @Immutable [LinkUiModel] with instance-level caching.
     * If the entity and its local favicon path have not changed, returns the exact same
     * [LinkUiModel] reference (`===`) so Compose skips recomposition of unchanged cards.
     */
    fun toUiModel(
        entity: LinkEntity,
        context: Context?,
        isVaultUnlocked: Boolean = true
    ): LinkUiModel {
        val isPassword = entity.category == "🔑 Passwords" || entity.description == "SECURE_PASSWORD"
        val isWebLink = entity.category != "📝 Notes" &&
            !isPassword &&
            entity.category != "🔐 Private Vault" &&
            (entity.url.startsWith("http://", ignoreCase = true) ||
                entity.url.startsWith("https://", ignoreCase = true) ||
                (entity.url.isNotBlank() && entity.url.contains(".")))

        val (domainHost, domainPath) = extractDomainAndPath(entity.url)
        val faviconPath = if (context != null && domainHost.isNotBlank() && domainHost.contains(".")) {
            FaviconStorageManager.getLocalFaviconPathIfPresent(context, domainHost)
        } else {
            null
        }

        val cached = instanceCache[entity.id]
        if (cached != null &&
            cached.entity == entity &&
            cached.faviconPath == faviconPath &&
            cached.isUnlocked == isVaultUnlocked
        ) {
            return cached.uiModel
        }

        val displayTitle = entity.title.ifBlank { domainHost.ifBlank { entity.url } }
        val displayUrl = if (domainHost.isNotBlank()) {
            "$domainHost${if (domainPath.isNotBlank() && domainPath != "/") domainPath else ""}"
        } else {
            entity.url
        }

        val categoryIconKey = resolveCategoryIconKey(entity.category, entity.url)
        val tagLabel = if (entity.isFavorite) "⭐ STARRED" else entity.category.uppercase(Locale.getDefault())
        val imagePaths = parseImagePaths(entity.description)
        val thumbnailPaths = if (imagePaths.isEmpty()) {
            emptyList()
        } else {
            imagePaths.map { AttachmentThumbnailManager.getExistingThumbnailOrOriginal(it) }
        }
        val dateText = formatTimestamp(entity.timestamp)

        // Security rule: NEVER store decrypted passwords in long-lived UI models.
        // For password items, only extract the non-secret username preview.
        val usernamePreview: String
        val notePreview: String

        if (isPassword) {
            usernamePreview = extractUsernamePreviewOnly(context, entity, isVaultUnlocked)
            notePreview = ""
        } else {
            usernamePreview = ""
            notePreview = if (entity.note.isBlank()) {
                ""
            } else if (context != null && RecordEncryption.isEncrypted(entity.note)) {
                RecordEncryption.decrypt(
                    context = context,
                    storedValue = entity.note,
                    recordId = entity.id,
                    userEmail = entity.userEmail,
                    isUnlocked = isVaultUnlocked
                )
            } else {
                entity.note
            }
        }

        val cleanHost = domainHost.removePrefix("www.").trim()
        val initialSource = cleanHost.ifBlank { displayTitle.trim() }
        val domainInitial = initialSource.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "L"
        val domainPalette = longArrayOf(
            0xFF6200EE, 0xFF00897B, 0xFFD81B60, 0xFF1E88E5,
            0xFFF4511E, 0xFF3949AB, 0xFF43A047, 0xFF8E24AA
        )
        val colorIdx = (cleanHost.ifBlank { entity.url }.hashCode() and 0x7FFFFFFF) % domainPalette.size
        val domainColorArgb = domainPalette[colorIdx]

        val model = LinkUiModel(
            id = entity.id,
            title = entity.title,
            displayTitle = displayTitle,
            displayUrl = displayUrl,
            domainHost = domainHost,
            domainPath = domainPath,
            isWebLink = isWebLink,
            categoryLabel = entity.category,
            categoryIconKey = categoryIconKey,
            tagLabel = tagLabel,
            isFavorite = entity.isFavorite,
            isPassword = isPassword,
            imagePaths = imagePaths,
            thumbnailPaths = thumbnailPaths,
            dateText = dateText,
            openCount = entity.openCount,
            faviconPath = faviconPath,
            usernamePreview = usernamePreview,
            notePreview = notePreview,
            entity = entity,
            domainInitial = domainInitial,
            domainColorArgb = domainColorArgb,
            shortDateText = formatShortTimestamp(entity.timestamp)
        )

        if (entity.id != 0) {
            instanceCache[entity.id] = CachedUiEntry(entity, faviconPath, isVaultUnlocked, model)
        }
        return model
    }

    private fun extractUsernamePreviewOnly(
        context: Context?,
        entity: LinkEntity,
        isVaultUnlocked: Boolean
    ): String {
        if (entity.note.isBlank()) return ""
        return try {
            val raw = if (context != null && RecordEncryption.isEncrypted(entity.note)) {
                if (!isVaultUnlocked) return ""
                RecordEncryption.decrypt(
                    context = context,
                    storedValue = entity.note,
                    recordId = entity.id,
                    userEmail = entity.userEmail,
                    isUnlocked = true
                )
            } else if (!RecordEncryption.isEncrypted(entity.note)) {
                entity.note
            } else {
                ""
            }
            raw.substringBefore("|||").trim()
        } catch (e: Throwable) {
            ""
        }
    }

    /**
     * Sorts [links] by timestamp descending, groups them into date buckets, and emits both
     * the flat [VaultListRow] list (headers + items) and the [LinkUiModel] list on a background thread.
     */
    fun buildVaultData(
        links: List<LinkEntity>,
        context: Context?,
        isVaultUnlocked: Boolean = true
    ): ImmutableVaultData {
        if (links.isEmpty()) return ImmutableVaultData()

        val sorted = links.sortedByDescending { it.timestamp }
        val uiModels = ArrayList<LinkUiModel>(sorted.size)

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfYesterday = startOfToday - 24 * 60 * 60 * 1000L
        val startOfThisWeek = startOfToday - 7 * 24 * 60 * 60 * 1000L
        val startOfLastWeek = startOfToday - 14 * 24 * 60 * 60 * 1000L
        val startOfThisMonth = startOfToday - 30 * 24 * 60 * 60 * 1000L

        val bucketOrder = listOf("Today", "Yesterday", "This Week", "Last Week", "This Month", "Older")
        val buckets = LinkedHashMap<String, MutableList<VaultListRow.ItemRow>>(bucketOrder.size)
        for (label in bucketOrder) {
            buckets[label] = mutableListOf()
        }

        sorted.forEachIndexed { index, entity ->
            val uiModel = toUiModel(entity, context, isVaultUnlocked)
            uiModels.add(uiModel)
            val itemRow = VaultListRow.ItemRow(
                uiModel = uiModel,
                itemIndex = index,
                isEven = index % 2 == 0
            )
            val t = entity.timestamp
            val bucketName = when {
                t >= startOfToday -> "Today"
                t >= startOfYesterday -> "Yesterday"
                t >= startOfThisWeek -> "This Week"
                t >= startOfLastWeek -> "Last Week"
                t >= startOfThisMonth -> "This Month"
                else -> "Older"
            }
            buckets[bucketName]?.add(itemRow)
        }

        val flatRows = ArrayList<VaultListRow>(sorted.size + bucketOrder.size)
        for ((title, itemsInBucket) in buckets) {
            if (itemsInBucket.isNotEmpty()) {
                flatRows.add(VaultListRow.HeaderRow(title = title, count = itemsInBucket.size))
                flatRows.addAll(itemsInBucket)
            }
        }

        return ImmutableVaultData(
            rows = flatRows,
            uiModels = uiModels
        )
    }
}
