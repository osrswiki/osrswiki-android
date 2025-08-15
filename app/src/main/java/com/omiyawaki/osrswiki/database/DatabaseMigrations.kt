package com.omiyawaki.osrswiki.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omiyawaki.osrswiki.offline.db.OfflineObject // Added import

/**
 * Contains Room database migrations for the application.
 *
 * Migrations should be defined as `val MIGRATION_X_Y = object : Migration(X, Y) { ... }`
 * and then added to the Room.databaseBuilder() in AppDatabase.kt.
 *
 * This file serves as a central place for all migration definitions.
 */
object DatabaseMigrations {

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create SavedArticleEntry table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `saved_article_entries` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `article_title` TEXT NOT NULL,
                    `normalized_article_title` TEXT NOT NULL,
                    `snippet` TEXT,
                    `timestamp` INTEGER NOT NULL,
                    `status` TEXT NOT NULL
                )
            """)
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_saved_article_entries_normalized_article_title`
                ON `saved_article_entries`(`normalized_article_title`)
            """)

            // Create OfflineAsset table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `offline_assets` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `original_url` TEXT NOT NULL UNIQUE,
                    `local_file_path` TEXT NOT NULL,
                    `used_by_article_ids` TEXT NOT NULL,
                    `download_timestamp` INTEGER NOT NULL
                )
            """)
            // The UNIQUE constraint on original_url implies an index.
            // If @Index(value = ["original_url"], unique = true) is also in the entity,
            // an explicit index creation ensures it matches Room's expectation.
            database.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS `index_offline_assets_original_url`
                ON `offline_assets`(`original_url`)
            """)
        }
    }

    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create ReadingList table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `ReadingList` (
                    `title` TEXT NOT NULL, 
                    `description` TEXT, 
                    `mtime` INTEGER NOT NULL, 
                    `atime` INTEGER NOT NULL, 
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `isDefault` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            // Create ReadingListPage table
            // WikiSite and Namespace are stored as TEXT due to TypeConverters
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `ReadingListPage` (
                    `wiki` TEXT NOT NULL, 
                    `namespace` TEXT NOT NULL, 
                    `displayTitle` TEXT NOT NULL, 
                    `apiTitle` TEXT NOT NULL, 
                    `description` TEXT, 
                    `thumbUrl` TEXT, 
                    `listId` INTEGER NOT NULL, 
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `mtime` INTEGER NOT NULL, 
                    `atime` INTEGER NOT NULL, 
                    `offline` INTEGER NOT NULL, 
                    `status` INTEGER NOT NULL, 
                    `sizeBytes` INTEGER NOT NULL, 
                    `lang` TEXT NOT NULL, 
                    `revId` INTEGER NOT NULL, 
                    `remoteId` INTEGER NOT NULL,
                    FOREIGN KEY(`listId`) REFERENCES `ReadingList`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_ReadingListPage_listId` ON `ReadingListPage` (`listId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_ReadingListPage_apiTitle_lang` ON `ReadingListPage` (`apiTitle`, `lang`)")

            // Create OfflineObject table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `OfflineObject` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `url` TEXT NOT NULL, 
                    `lang` TEXT NOT NULL, 
                    `path` TEXT NOT NULL, 
                    `status` INTEGER NOT NULL, 
                    `usedByStr` TEXT NOT NULL
                )
            """.trimIndent())
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_OfflineObject_url_lang` ON `OfflineObject` (`url`, `lang`)")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `offline_objects` ADD COLUMN `saveType` TEXT NOT NULL DEFAULT '${OfflineObject.SAVE_TYPE_READING_LIST}'")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Room will automatically create the FTS table 'offline_page_fts'
            // based on the @Fts4 annotation on the OfflinePageFts entity when the schema is generated.
            // This migration step is primarily to increment the version.
            // If Room did not handle FTS table creation automatically (which it should),
            // you would add the explicit SQL here:
            // database.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `offline_page_fts` USING FTS4(`url` TEXT, `title` TEXT, `body` TEXT)")
            android.util.Log.i("DBMigration", "Migrating database from version 9 to 10. FTS table 'offline_page_fts' will be created by Room.")
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add unique index on page_wikiUrl to prevent duplicate history entries
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_history_entries_page_wikiUrl` ON `history_entries`(`page_wikiUrl`)")
            android.util.Log.i("DBMigration", "Migrating database from version 13 to 14. Added unique index on history_entries.page_wikiUrl")
        }
    }

}
