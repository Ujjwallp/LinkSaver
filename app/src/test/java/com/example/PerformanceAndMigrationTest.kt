package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AttachmentThumbnailManager
import com.example.data.DebugSeedHelper
import com.example.data.FaviconStorageManager
import com.example.data.LinkEntity
import com.example.ui.LinkUiMapper
import com.example.ui.VaultListRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerformanceAndMigrationTest {

    @Test
    fun testRoomSchema3AndOpenCountColumn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.linkDao()
            val id = dao.insertLink(
                LinkEntity(
                    url = "https://developer.android.com/jetpack/compose/performance",
                    title = "Compose Performance",
                    category = "💻 Tech",
                    openCount = 0
                )
            ).toInt()

            dao.incrementOpenCount(id)
            dao.incrementOpenCount(id)

            val snapshot = dao.getAllLinksSnapshot()
            assertEquals(1, snapshot.size)
            assertEquals(2, snapshot.first().openCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun testLegacyOpenCountPreferencesMigration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_legacy_open_counts", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("click_count_id_1", 7)
            .putInt("click_count_id_2", 14)
            .commit()

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.linkDao()
            val id1 = dao.insertLink(LinkEntity(id = 1, url = "https://example.com/1", title = "One")).toInt()
            val id2 = dao.insertLink(LinkEntity(id = 2, url = "https://example.com/2", title = "Two")).toInt()

            AppDatabase.migrateLegacyOpenCountsFromPrefs(prefs, dao)

            val links = dao.getAllLinksSnapshot().associateBy { it.id }
            assertEquals(7, links[id1]?.openCount)
            assertEquals(14, links[id2]?.openCount)
            assertTrue(!prefs.contains("click_count_id_1"))
            assertTrue(!prefs.contains("click_count_id_2"))
        } finally {
            db.close()
        }
    }

    @Test
    fun testLinkUiMapperInstanceCachingAndBenchmark500Items() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LinkUiMapper.clearCache()

        val seeded = DebugSeedHelper.generateSeedLinks(count = 500, userEmail = "bench@example.com")
        assertEquals(500, seeded.size)

        // Cold build timing across 15 iterations and warm cache timing across 50 iterations
        val coldSamplesMs = mutableListOf<Double>()
        for (i in 0 until 15) {
            LinkUiMapper.clearCache()
            val t0 = System.nanoTime()
            val vaultData = LinkUiMapper.buildVaultData(seeded, context, isVaultUnlocked = true)
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
            coldSamplesMs.add(elapsedMs)
            assertEquals(500, vaultData.sortedModels.size)
            assertTrue(vaultData.rows.any { it is VaultListRow.HeaderRow })
        }

        val firstPass = LinkUiMapper.buildVaultData(seeded, context, isVaultUnlocked = true)
        val warmSamplesMs = mutableListOf<Double>()
        for (i in 0 until 50) {
            val t0 = System.nanoTime()
            val secondPass = LinkUiMapper.buildVaultData(seeded, context, isVaultUnlocked = true)
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
            warmSamplesMs.add(elapsedMs)
            // Verify unchanged items reuse the exact same LinkUiModel reference (===)
            assertSame(firstPass.sortedModels[0], secondPass.sortedModels[0])
            assertSame(firstPass.sortedModels[249], secondPass.sortedModels[249])
            assertSame(firstPass.sortedModels[499], secondPass.sortedModels[499])
        }

        coldSamplesMs.sort()
        warmSamplesMs.sort()
        val coldP50 = coldSamplesMs[coldSamplesMs.size / 2]
        val coldP90 = coldSamplesMs[(coldSamplesMs.size * 90) / 100]
        val coldP99 = coldSamplesMs.last()

        val warmP50 = warmSamplesMs[warmSamplesMs.size / 2]
        val warmP90 = warmSamplesMs[(warmSamplesMs.size * 90) / 100]
        val warmP99 = warmSamplesMs.last()

        println(
            "BENCHMARK_500_LINKS: Cold(P50=%.2fms, P90=%.2fms, P99=%.2fms) | WarmCached(P50=%.2fms, P90=%.2fms, P99=%.2fms)"
                .format(coldP50, coldP90, coldP99, warmP50, warmP90, warmP99)
        )
        assertTrue("Warm cached pass should be under 25ms", warmP50 < 25.0)
    }

    @Test
    fun testImagePathsParsingAndOfflineHelpers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val encoded = LinkUiMapper.encodeImagePaths(listOf("/data/img1.jpg", " /data/img2.jpg ", ""))
        assertEquals("IMAGE_PATHS:/data/img1.jpg,/data/img2.jpg", encoded)

        val parsed = LinkUiMapper.parseImagePaths(encoded)
        assertEquals(listOf("/data/img1.jpg", "/data/img2.jpg"), parsed)

        val domain = FaviconStorageManager.normalizeDomain("https://www.github.com/google/ksp")
        assertEquals("github.com", domain)
        assertNotNull(FaviconStorageManager.getDomainFile(context, domain))

        val fallback = AttachmentThumbnailManager.getExistingThumbnailOrOriginal("/nonexistent/original.jpg")
        assertEquals("/nonexistent/original.jpg", fallback)
    }

    @Test
    fun testContentTypeAndPrecomputedLinkUiModelFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LinkUiMapper.clearCache()

        val plainLink = LinkEntity(
            id = 10,
            url = "https://kotlinlang.org",
            title = "Kotlin",
            category = "💻 Coding",
            note = ""
        )
        val noteLink = LinkEntity(
            id = 11,
            url = "https://developer.android.com",
            title = "Android Developers",
            category = "💻 Coding",
            note = "Compose LazyColumn contentType optimization"
        )
        val passItem = LinkEntity(
            id = 12,
            url = "https://github.com",
            title = "GitHub",
            description = "SECURE_PASSWORD",
            category = "🔑 Passwords",
            note = "octocat|||SuperSecret123"
        )

        val vaultData = LinkUiMapper.buildVaultData(listOf(plainLink, noteLink, passItem), context, isVaultUnlocked = true)
        val headerRow = vaultData.rows.filterIsInstance<VaultListRow.HeaderRow>().first()
        val itemRows = vaultData.rows.filterIsInstance<VaultListRow.ItemRow>()

        assertEquals("header", headerRow.contentType)
        assertEquals("header", headerRow.contentTypeForDensity(isDenseMode = false))
        assertEquals("header", headerRow.contentTypeForDensity(isDenseMode = true))
        assertEquals("3", headerRow.countText)

        val plainRow = itemRows.first { it.uiModel.id == 10 }
        val noteRow = itemRows.first { it.uiModel.id == 11 }
        val passRow = itemRows.first { it.uiModel.id == 12 }

        assertEquals("link_card_plain_item", plainRow.contentTypeForDensity(isDenseMode = false))
        assertEquals("dense_link_item", plainRow.contentTypeForDensity(isDenseMode = true))
        assertEquals("kotlinlang.org", plainRow.uiModel.domainBadgeText)

        assertEquals("link_card_with_note_item", noteRow.contentTypeForDensity(isDenseMode = false))
        assertEquals("dense_link_item", noteRow.contentTypeForDensity(isDenseMode = true))
        assertTrue(noteRow.uiModel.hasNotePreview)

        assertEquals("password_card_item", passRow.contentTypeForDensity(isDenseMode = false))
        assertEquals("dense_password_item", passRow.contentTypeForDensity(isDenseMode = true))
        assertEquals("octocat", passRow.uiModel.displayUsername)
        assertTrue(passRow.uiModel.hasAssociatedDomain)
        assertEquals("🌐 github.com ↗", passRow.uiModel.associatedDomainLabel)
    }
}
