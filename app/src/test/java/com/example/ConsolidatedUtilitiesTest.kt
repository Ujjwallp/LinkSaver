package com.example

import com.example.ui.LinkUiMapper
import com.example.util.DateTimeFormatterUtil
import com.example.util.UrlParserUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class ConsolidatedUtilitiesTest {

    // ==========================================
    // UrlParserUtil Tests
    // ==========================================

    @Test
    fun urlParser_extractDomain_standardUrls() {
        assertEquals("github.com", UrlParserUtil.extractDomain("https://github.com/android/architecture"))
        assertEquals("google.com", UrlParserUtil.extractDomain("https://www.google.com/search?q=jetpack+compose"))
        assertEquals("news.ycombinator.com", UrlParserUtil.extractDomain("http://news.ycombinator.com/item?id=12345"))
        assertEquals("example.org", UrlParserUtil.extractDomain("example.org/path/subpath"))
        assertEquals("localhost", UrlParserUtil.extractDomain("http://localhost:8080/api/v1"))
        assertEquals("", UrlParserUtil.extractDomain(""))
        assertEquals("", UrlParserUtil.extractDomain("   "))
    }

    @Test
    fun urlParser_extractDomainAndPath() {
        val (domain, path) = UrlParserUtil.extractDomainAndPath("https://github.com/torvalds/linux")
        assertEquals("github.com", domain)
        assertEquals("/torvalds/linux", path)

        val (emptyDomain, emptyPath) = UrlParserUtil.extractDomainAndPath("")
        assertEquals("", emptyDomain)
        assertEquals("", emptyPath)

        val (_, truncatedPath) = UrlParserUtil.extractDomainAndPath(
            "https://example.com/very/long/nested/path/that/exceeds/the/maximum/allowed/length",
            maxPathLength = 20
        )
        assertTrue(truncatedPath.endsWith("…"))
    }

    @Test
    fun urlParser_normalizeForComparison() {
        val normalized1 = UrlParserUtil.normalizeForComparison("https://example.com/page?utm_source=twitter&utm_medium=social")
        val normalized2 = UrlParserUtil.normalizeForComparison("https://example.com/page")
        assertEquals(normalized2, normalized1)
    }

    // ==========================================
    // DateTimeFormatterUtil Tests
    // ==========================================

    @Test
    fun dateTimeFormatter_validAndEmptyTimestamps() {
        assertEquals("", DateTimeFormatterUtil.formatFullDateTime(0L))
        assertEquals("", DateTimeFormatterUtil.formatFullDateTime(-100L))
        assertEquals("", DateTimeFormatterUtil.formatShortDate(0L))
        assertEquals("", DateTimeFormatterUtil.formatTimeAndDate(0L))
        assertEquals("", DateTimeFormatterUtil.formatBackupItemTime(0L))
        assertEquals("", DateTimeFormatterUtil.formatLogTime(0L))

        val cal = Calendar.getInstance().apply {
            set(2025, Calendar.MAY, 15, 14, 30, 0)
        }
        val timestamp = cal.timeInMillis

        val full = DateTimeFormatterUtil.formatFullDateTime(timestamp)
        assertTrue(full.contains("May") || full.contains("15") || full.contains("2025"))

        val timeAndDate = DateTimeFormatterUtil.formatTimeAndDate(timestamp)
        assertTrue(timeAndDate.contains("May") || timeAndDate.contains("15"))

        val backupTime = DateTimeFormatterUtil.formatBackupItemTime(timestamp)
        assertTrue(backupTime.isNotEmpty())

        val logTime = DateTimeFormatterUtil.formatLogTime(timestamp)
        assertTrue(logTime.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun dateTimeFormatter_relativeTimeBuckets() {
        val now = 1700000000000L

        assertEquals("Just now", DateTimeFormatterUtil.formatRelativeTime(now - 15_000L, now))
        assertEquals("5m ago", DateTimeFormatterUtil.formatRelativeTime(now - 5 * 60_000L, now))
        assertEquals("3h ago", DateTimeFormatterUtil.formatRelativeTime(now - 3 * 3600_000L, now))
        assertEquals("Yesterday", DateTimeFormatterUtil.formatRelativeTime(now - 24 * 3600_000L, now))
        assertEquals("4d ago", DateTimeFormatterUtil.formatRelativeTime(now - 4 * 24 * 3600_000L, now))
    }

    // ==========================================
    // LinkUiMapper Consolidated Utility Tests
    // ==========================================

    @Test
    fun linkUiMapper_parseImagePaths_roundTrip() {
        assertEquals(emptyList<String>(), LinkUiMapper.parseImagePaths(""))
        assertEquals(emptyList<String>(), LinkUiMapper.parseImagePaths("Normal note text without images"))

        val paths = listOf("/data/user/0/app/files/img1.jpg", "/data/user/0/app/files/img2.png")
        val serialized = LinkUiMapper.encodeImagePaths(paths)
        assertEquals("IMAGE_PATHS:/data/user/0/app/files/img1.jpg,/data/user/0/app/files/img2.png", serialized)

        val parsed = LinkUiMapper.parseImagePaths(serialized)
        assertEquals(paths, parsed)
    }

    @Test
    fun linkUiMapper_delegatesDomainExtraction() {
        val (domain, path) = LinkUiMapper.extractDomainAndPath("https://kotlinlang.org/docs/home.html")
        assertEquals("kotlinlang.org", domain)
        assertEquals("/docs/home.html", path)
    }
}
