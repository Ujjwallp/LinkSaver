package com.example.data

import com.example.core.data.BuildConfig

/**
 * Debug-only seeding helper for generating 500 realistic vault items
 * to benchmark LazyColumn scrolling, date grouping, and UI model mapping.
 *
 * Strictly no-op in release/smooth non-debug builds.
 */
object DebugSeedHelper {

    private val SAMPLE_DOMAINS = listOf(
        "developer.android.com" to "Android Developers Guide",
        "kotlinlang.org" to "Kotlin Coroutines & Flow Reference",
        "m3.material.io" to "Material Design 3 Tokens",
        "github.com" to "Jetpack Compose Samples Repository",
        "news.ycombinator.com" to "Hacker News Frontpage",
        "medium.com" to "Deep Dive into Compose Stability",
        "wikipedia.org" to "Authenticated Encryption (AES-GCM)",
        "youtube.com" to "Android Performance Workshop",
        "stackoverflow.com" to "SQLCipher Room Migration Patterns",
        "arxiv.org" to "Cryptographic Key Hierarchy Paper"
    )

    private val SAMPLE_CATEGORIES = listOf(
        "💻 Coding",
        "📚 Reading",
        "🎬 Video",
        "💡 Inspiration",
        "💼 Work",
        "🛍️ Shopping",
        "📝 Notes",
        "📁 Uncategorized"
    )

    /**
     * Generates [count] deterministic realistic [LinkEntity] records when running in debug or test mode.
     * In non-debug production builds (`!BuildConfig.DEBUG && !allowInTests`), returns an empty list.
     */
    fun generateSeedLinks(
        count: Int = 500,
        userEmail: String = "vault@linkvault.local",
        baseTimestamp: Long = System.currentTimeMillis(),
        allowInTests: Boolean = false
    ): List<LinkEntity> {
        if (!BuildConfig.DEBUG && !allowInTests) {
            return emptyList()
        }

        val hourMs = 3_600_000L
        return List(count) { index ->
            val id = index + 1
            val (domain, baseTitle) = SAMPLE_DOMAINS[index % SAMPLE_DOMAINS.size]
            val category = SAMPLE_CATEGORIES[index % SAMPLE_CATEGORIES.size]
            val isNote = category == "📝 Notes"
            val timestamp = baseTimestamp - (index * 3L * hourMs)
            val description = if (isNote && index % 3 == 0) {
                "IMAGE_PATHS:/data/user/0/com.example/files/attached_img_${id}_a.jpg,/data/user/0/com.example/files/attached_img_${id}_b.jpg"
            } else {
                "Saved reference item #$id for $domain"
            }

            LinkEntity(
                id = id,
                url = "https://$domain/articles/$id?section=${index % 10}",
                title = "$baseTitle #$id",
                description = description,
                timestamp = timestamp,
                category = category,
                isFavorite = index % 5 == 0,
                note = "Summary note for item #$id — performance and architecture notes.",
                userEmail = userEmail,
                openCount = index % 15
            )
        }
    }
}
