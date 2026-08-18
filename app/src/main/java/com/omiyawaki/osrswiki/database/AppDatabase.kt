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
    version = 18,
    exportSchema = true
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
                val mediaWikiPageIdSelect = if (columnExists(db, "ReadingListPage", "mediaWikiPageId")) {
                    "`mediaWikiPageId`"
                } else {
                    "NULL"
                }
                val downloadProgressSelect = if (columnExists(db, "ReadingListPage", "downloadProgress")) {
                    "`downloadProgress`"
                } else {
                    "0"
                }
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ReadingListPage_new` (
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
                        `mediaWikiPageId` INTEGER,
                        `downloadProgress` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `ReadingListPage_new` (
                        `wiki`, `namespace`, `displayTitle`, `apiTitle`, `description`, `thumbUrl`,
                        `listId`, `id`, `mtime`, `atime`, `offline`, `status`, `sizeBytes`, `lang`,
                        `revId`, `remoteId`, `mediaWikiPageId`, `downloadProgress`
                    )
                    SELECT
                        `wiki`, `namespace`, `displayTitle`, `apiTitle`, `description`, `thumbUrl`,
                        `listId`, `id`, `mtime`, `atime`, `offline`, `status`, `sizeBytes`, `lang`,
                        `revId`, `remoteId`, $mediaWikiPageIdSelect, $downloadProgressSelect
                    FROM `ReadingListPage`
                """.trimIndent())
                db.execSQL("DROP TABLE `ReadingListPage`")
                db.execSQL("ALTER TABLE `ReadingListPage_new` RENAME TO `ReadingListPage`")
            }
        }

        /** Keeps reading-list and full-archive copies of the same URL as independent objects. */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `offline_objects_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `url` TEXT NOT NULL,
                        `lang` TEXT NOT NULL,
                        `path` TEXT NOT NULL,
                        `status` INTEGER NOT NULL,
                        `usedByStr` TEXT NOT NULL,
                        `saveType` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `offline_objects_new`
                        (`id`, `url`, `lang`, `path`, `status`, `usedByStr`, `saveType`)
                    SELECT `id`, `url`, `lang`, `path`, `status`, `usedByStr`, `saveType`
                    FROM `offline_objects`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `offline_objects`")
                db.execSQL("ALTER TABLE `offline_objects_new` RENAME TO `offline_objects`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_offline_objects_url_lang_saveType` " +
                        "ON `offline_objects` (`url`, `lang`, `saveType`)"
                )
            }
        }

        /**
         * Marks the durable, exhaustive reading-list settlement generation per page. Version 17
         * could call an offline row SAVED after persisting only a subset of rendered artwork. Keep
         * every existing object and metadata row in place, but expose each formerly saved offline
         * row as a retryable update. The app never silently starts an unbounded dataSync job after
         * upgrade; an explicit Retry/Save action performs the current exhaustive settlement.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `ReadingListPage` " +
                        "ADD COLUMN `durableSettlementVersion` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    UPDATE `ReadingListPage`
                    SET `status` = ${ReadingListPage.STATUS_ERROR},
                        `downloadProgress` = 0
                    WHERE `offline` = 1
                      AND `status` = ${ReadingListPage.STATUS_SAVED}
                      AND `durableSettlementVersion` < ${ReadingListPage.CURRENT_DURABLE_SETTLEMENT_VERSION}
                    """.trimIndent()
                )
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            DatabaseMigrations.MIGRATION_6_7,
            DatabaseMigrations.MIGRATION_7_8,
            DatabaseMigrations.MIGRATION_8_9,
            DatabaseMigrations.MIGRATION_9_10,
            DatabaseMigrations.MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18
        )

        val instance: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(
                OSRSWikiApp.instance.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
             .addMigrations(*ALL_MIGRATIONS)
            .build()
        }

        private fun columnExists(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String
        ): Boolean {
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == columnName) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
