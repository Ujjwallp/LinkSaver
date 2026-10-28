package com.example.data

import com.example.security.UrlSecurityValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class LinkRepository(private val linkDao: LinkDao) {
    val allLinks: Flow<List<LinkEntity>> = linkDao.getAllLinks().catch { emit(emptyList()) }
    val favorites: Flow<List<LinkEntity>> = linkDao.getFavoriteLinks().catch { emit(emptyList()) }

    companion object {
        private const val MAX_TITLE_LENGTH = 300

        private val TITLE_REGEX = Regex(
            pattern = """<title\b[^>]*>(.*?)</title>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        private val NUMERIC_ENTITY_REGEX = Regex("""&#(\d+);""")
        private val HEX_ENTITY_REGEX = Regex("""&#x([0-9a-fA-F]+);""")

        fun extractHtmlTitle(html: String): String? {
            val rawTitle = TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim() ?: return null
            if (rawTitle.isEmpty()) return null
            val decoded = decodeHtmlEntities(rawTitle)
            return if (decoded.length > MAX_TITLE_LENGTH) {
                decoded.substring(0, MAX_TITLE_LENGTH).trim()
            } else {
                decoded.ifBlank { null }
            }
        }

        fun decodeHtmlEntities(input: String): String {
            var result = input
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&#x27;", "'", ignoreCase = true)
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&ndash;", "–")
                .replace("&mdash;", "—")

            result = NUMERIC_ENTITY_REGEX.replace(result) { match ->
                val code = match.groupValues[1].toIntOrNull()
                if (code != null && code in 32..0x10FFFF) String(Character.toChars(code)) else match.value
            }

            result = HEX_ENTITY_REGEX.replace(result) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                if (code != null && code in 32..0x10FFFF) String(Character.toChars(code)) else match.value
            }

            return result
                .replace(Regex("""\s+"""), " ")
                .trim()
        }
    }

    suspend fun getLinkByUrl(url: String): LinkEntity? = linkDao.getLinkByUrl(url)
    suspend fun getLinkById(id: Int): LinkEntity? = linkDao.getLinkById(id)

    suspend fun insert(link: LinkEntity): Long = linkDao.insertLink(link)
    suspend fun update(link: LinkEntity) = linkDao.updateLink(link)
    suspend fun delete(link: LinkEntity) = linkDao.deleteLink(link)
    suspend fun deleteById(id: Int) = linkDao.deleteLinkById(id)
    suspend fun deleteLinksByUserEmail(email: String) = linkDao.deleteLinksByUserEmail(email)

    fun search(query: String): Flow<List<LinkEntity>> {
        return linkDao.searchLinks("%$query%")
    }

    /**
     * Offline title extraction: returns null as network calls are disabled.
     * Shared intents provide titles directly or fall back to normalized domain names.
     */
    suspend fun fetchTitleFromUrl(url: String): String? {
        return null
    }

    suspend fun deleteAllLinks() = linkDao.deleteAllLinks()

    suspend fun incrementOpenCount(id: Int) = linkDao.incrementOpenCount(id)

    suspend fun setOpenCount(id: Int, count: Int) = linkDao.setOpenCount(id, count)

    suspend fun insertLinks(links: List<LinkEntity>) {
        linkDao.insertLinks(links)
    }
}
