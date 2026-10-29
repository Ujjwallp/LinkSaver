package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.autofill.CredentialMetadataStore
import com.example.autofill.NeverSaveManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataModuleTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: LinkDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.linkDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun linkDao_insertsAndQueriesLinks() = runTest {
        val link = LinkEntity(
            url = "https://developer.android.com/jetpack/compose",
            title = "Jetpack Compose Docs",
            category = "💻 Coding",
            userEmail = "test@local"
        )
        dao.insertLink(link)

        val all = dao.getAllLinksSnapshot()
        assertEquals(1, all.size)
        assertEquals("Jetpack Compose Docs", all[0].title)
    }

    @Test
    fun duplicateDetector_identifiesNormalizedUrlDuplicates() {
        val items = listOf(
            LinkEntity(id = 1, url = "https://github.com/android/nowinandroid/", title = "Now in Android"),
            LinkEntity(id = 2, url = "https://github.com/android/nowinandroid?utm_source=test", title = "Now in Android Repo")
        )
        val duplicates = DuplicateDetector.scanDuplicates(items, DuplicateDetector.DuplicateType.NORMAL_URL)
        assertTrue(duplicates.isNotEmpty())
        assertEquals(2, duplicates.first().items.size)
    }

    @Test
    fun credentialMetadataAndNeverSaveStore_roundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metadataStore = CredentialMetadataStore(context)
        val saved = metadataStore.saveAppIdentity(
            credentialId = 101,
            appPackage = "com.example.demo",
            appSigners = setOf("AA:BB:CC"),
            appLabel = "Demo App"
        )
        assertEquals("com.example.demo", saved.appPackage)
        assertEquals("Demo App", metadataStore.getMetadata(101).appLabel)

        val neverSave = NeverSaveManager(context)
        neverSave.addNeverSaveDomain("login.example.com")
        assertTrue(neverSave.isNeverSaveDomain("login.example.com"))
        neverSave.removeNeverSaveDomain("login.example.com")
        assertFalse(neverSave.isNeverSaveDomain("login.example.com"))
    }
}
