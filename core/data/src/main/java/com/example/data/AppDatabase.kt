package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LinkEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DB_NAME = "link_saver_database"

        private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            return try {
                db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0 && cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                            return true
                        }
                    }
                    false
                }
            } catch (_: Throwable) {
                false
            }
        }

        /**
         * Migration from version 1 to version 2:
         * Preserves existing links while safely adding the userEmail isolation column.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "links", "userEmail")) {
                    db.execSQL("ALTER TABLE links ADD COLUMN userEmail TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        /**
         * Migration from version 2 to version 3:
         * - Adds `openCount` INTEGER column (default 0) to replace per-card SharedPreferences reads.
         * - Adds B-tree indexes on `url`, `category`, `timestamp`, and `userEmail` for fast queries.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "links", "openCount")) {
                    db.execSQL("ALTER TABLE links ADD COLUMN openCount INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_links_url` ON `links` (`url`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_links_category` ON `links` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_links_timestamp` ON `links` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_links_userEmail` ON `links` (`userEmail`)")
            }
        }

        /**
         * Copies any legacy `click_count_id_<id>` values from [prefs] into the `links.openCount`
         * column and removes the obsolete SharedPreferences keys.
         */
        suspend fun migrateLegacyOpenCountsFromPrefs(prefs: SharedPreferences, dao: LinkDao) {
            val allEntries = prefs.all
            val clickKeys = allEntries.keys.filter { it.startsWith("click_count_id_") }
            if (clickKeys.isEmpty()) return

            val editor = prefs.edit()
            for (key in clickKeys) {
                val id = key.removePrefix("click_count_id_").toIntOrNull()
                val count = (allEntries[key] as? Number)?.toInt() ?: 0
                if (id != null && count > 0) {
                    val existing = dao.getLinkById(id)
                    if (existing != null && existing.openCount < count) {
                        dao.setOpenCount(id, count)
                    }
                }
                editor.remove(key)
            }
            editor.apply()
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val passphrase = DatabaseSecurityManager.getDatabasePassphrase(appContext, DB_NAME)

                    // Safely migrate unencrypted database to encrypted SQLCipher if needed
                    DatabaseSecurityManager.ensureEncrypted(appContext, DB_NAME, passphrase)

                    val supportFactory = DatabaseSecurityManager.createSupportFactory(appContext, DB_NAME, passphrase)

                    val builder = Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        DB_NAME
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)

                    if (supportFactory != null) {
                        builder.openHelperFactory(supportFactory)
                    }

                    val instance = builder.build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }
}
