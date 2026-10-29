package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LinkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VaultModuleTest {

    @Test
    fun linkUiMapper_mapsAndCachesImmutableUiModels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LinkUiMapper.clearCache()

        val entity = LinkEntity(
            id = 77,
            url = "https://github.com/jetbrains/kotlin",
            title = "Kotlin Repository",
            category = "💼 Work",
            userEmail = "user@local"
        )

        val first = LinkUiMapper.toUiModel(entity, context, isVaultUnlocked = true)
        val second = LinkUiMapper.toUiModel(entity, context, isVaultUnlocked = true)

        assertEquals("github.com", first.domainHost)
        assertEquals(CategoryIconKey.WORK, first.categoryIconKey)
        assertSame(first, second)
    }

    @Test
    fun linkUiMapper_parsesAndEncodesImagePaths() {
        val paths = listOf("/data/img1.jpg", "/data/img2.png")
        val encoded = LinkUiMapper.encodeImagePaths(paths)
        assertTrue(encoded.startsWith(LinkUiMapper.IMAGE_PATHS_PREFIX))

        val parsed = LinkUiMapper.parseImagePaths(encoded)
        assertEquals(paths, parsed)
    }
}
