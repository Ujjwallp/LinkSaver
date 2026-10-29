package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LinkVault", appName)
  }

  @Test
  fun `test real Room v2 to v3 migration validation`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbFile = context.getDatabasePath("test_mig_v2_v3.db")
    if (dbFile.exists()) dbFile.delete()
    dbFile.parentFile?.mkdirs()

    // Create a version 2 database matching the exact v2 schema
    SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqLiteDb ->
      sqLiteDb.execSQL(
        "CREATE TABLE IF NOT EXISTS `links` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `category` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `note` TEXT NOT NULL, `userEmail` TEXT NOT NULL)"
      )
      sqLiteDb.execSQL(
        "INSERT INTO `links` (`url`, `title`, `description`, `timestamp`, `category`, `isFavorite`, `note`, `userEmail`) VALUES ('https://example.com', 'Example', '', 1000, 'Uncategorized', 0, '', 'vault@linkvault.local')"
      )
      sqLiteDb.version = 2
    }

    val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_mig_v2_v3.db")
      .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
      .allowMainThreadQueries()
      .build()

    try {
      val snapshot = roomDb.linkDao().getAllLinksSnapshot()
      assertEquals(1, snapshot.size)
      assertEquals("https://example.com", snapshot.first().url)
      assertEquals(0, snapshot.first().openCount)
    } finally {
      roomDb.close()
      dbFile.delete()
    }
  }

  @Test
  fun `test MainActivity cold start launch`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertNotNull(activity)
      }
    }
  }
}

