package com.omiyawaki.osrswiki.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omiyawaki.osrswiki.OSRSWikiApp

// Entities
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.readinglist.database.ReadingList
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import com.omiyawaki.osrswiki.search.db.RecentSearch

// DAOs
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.OfflinePageFtsDao
import com.omiyawaki.osrswiki.history.db.HistoryEntryDao
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.readinglist.db.ReadingListDao
import com.omiyawaki.osrswiki.search.db.RecentSearchDao

// Converters
import com.omiyawaki.osrswiki.database.converters.DateConverter


@Database(
    entities = [
        ArticleMetaEntity::class,
        ReadingList::class,
        ReadingListPage::class,
        OfflineObject::class,
        OfflinePageFts::class,
        HistoryEntry::class,
        RecentSearch::class
    ],
    version = 16, // Increment the database version to remove legacy entities.
    exportSchema = false
)
@TypeConverters(
    com.omiyawaki.osrswiki.database.TypeConverters::class,
    DateConverter::class
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun articleMetaDao(): ArticleMetaDao
    abstract fun readingListDao(): ReadingListDao
    abstract fun readingListPageDao(): ReadingListPageDao
    abstract fun offlineObjectDao(): OfflineObjectDao
    abstract fun offlinePageFtsDao(): OfflinePageFtsDao
    abstract fun historyEntryDao(): HistoryEntryDao
    abstract fun recentSearchDao(): RecentSearchDao

    companion object {
        private const val DATABASE_NAME = "osrs_wiki_database.db"

        /**
         * Migration from version 11 to 12. Adds the `recent_searches` table.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recent_searches` " +
                            "(`query` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`query`))"
                )
            }
        }

        /**
         * Migration from version 12 to 13. Adds snippet and thumbnail_url columns to history_entries table.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history_entries ADD COLUMN snippet TEXT")
                db.execSQL("ALTER TABLE history_entries ADD COLUMN thumbnail_url TEXT")
            }
        }

        /**
         * Migration from version 13 to 14. Adds unique index on page_wikiUrl to prevent duplicate history entries.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_history_entries_page_wikiUrl` ON `history_entries`(`page_wikiUrl`)")
            }
        }

        /**
         * Migration from version 14 to 15. Changes primary key from auto-generated id to page_wikiUrl.
         * Handles deduplication of existing data by keeping only the most recent entry per URL.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Step 1: Create the new table with page_wikiUrl as primary key
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `history_entries_new` (
                        `page_wikiUrl` TEXT NOT NULL,
                        `page_displayText` TEXT NOT NULL,
                        `page_pageId` INTEGER,
                        `page_apiPath` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `source` INTEGER NOT NULL,
                        `is_archived` INTEGER NOT NULL DEFAULT 0,
                        `snippet` TEXT,
                        `thumbnail_url` TEXT,
                        PRIMARY KEY(`page_wikiUrl`)
                    )
                """.trimIndent())
                
                // Step 2: Copy data from old table, keeping only the most recent entry per URL
                // This handles the deduplication by using MAX(timestamp) with GROUP BY
                db.execSQL("""
                    INSERT INTO `history_entries_new` (
                        `page_wikiUrl`, `page_displayText`, `page_pageId`, `page_apiPath`,
                        `timestamp`, `source`, `is_archived`, `snippet`, `thumbnail_url`
                    )
                    SELECT 
                        `page_wikiUrl`, 
                        `page_displayText`, 
                        `page_pageId`, 
                        `page_apiPath`,
                        `timestamp`, 
                        `source`, 
                        `is_archived`, 
                        `snippet`, 
                        `thumbnail_url`
                    FROM `history_entries` h1
                    WHERE `timestamp` = (
                        SELECT MAX(`timestamp`) 
                        FROM `history_entries` h2 
                        WHERE h1.`page_wikiUrl` = h2.`page_wikiUrl`
                    )
                """.trimIndent())
                
                // Step 3: Drop the old table
                db.execSQL("DROP TABLE `history_entries`")
                
                // Step 4: Rename the new table to the original name
                db.execSQL("ALTER TABLE `history_entries_new` RENAME TO `history_entries`")
            }
        }

        /**
         * Migration from version 15 to 16. Removes legacy offline tables in favor of unified ReadingListPage system.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop legacy tables - these were part of the old offline system
                db.execSQL("DROP TABLE IF EXISTS `saved_article_entries`")
                db.execSQL("DROP TABLE IF EXISTS `offline_assets`")
            }
        }

        val instance: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(
                OSRSWikiApp.instance.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
             .addMigrations(
                MIGRATION_11_12, // Add the new migration to the builder.
                MIGRATION_12_13, // Add the new migration for snippet and thumbnail_url columns.
                MIGRATION_13_14, // Add the new migration for unique index on page_wikiUrl.
                MIGRATION_14_15, // Add the new migration for primary key change and deduplication.
                MIGRATION_15_16  // Remove legacy offline system tables.
             )
            .build()
        }
    }
}
