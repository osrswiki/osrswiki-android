package com.omiyawaki.osrswiki.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseNames = mutableListOf<String>()

    @After
    fun tearDown() {
        databaseNames.forEach { context.deleteDatabase(it) }
    }

    @Test
    fun version10DatabaseOpensAtCurrentVersionAndPreservesSavedOfflineData() {
        val dbName = "migration-v10-${UUID.randomUUID()}.db"
        databaseNames += dbName
        seedHistoricalDatabase(context.getDatabasePath(dbName), version = 10)

        val database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        database.openHelper.writableDatabase

        assertEquals(16, database.openHelper.readableDatabase.version)
        assertEquals("Abyssal whip", database.readingListPageDao().getAllPages().single().displayTitle)
        assertEquals(
            "offline/abyssal_whip.html",
            database.offlineObjectDao()
                .getOfflineObject("https://oldschool.runescape.wiki/w/Abyssal_whip", "en")
                ?.path
        )

        database.close()
    }

    @Test
    fun versions6Through10OpenAtCurrentVersionWithHistoricalMigrationChain() {
        for (version in 6..10) {
            val dbName = "migration-v$version-${UUID.randomUUID()}.db"
            databaseNames += dbName
            seedHistoricalDatabase(context.getDatabasePath(dbName), version)

            val database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()

            database.openHelper.writableDatabase

            assertEquals(16, database.openHelper.readableDatabase.version)
            if (version >= 8) {
                assertEquals("Abyssal whip", database.readingListPageDao().getAllPages().single().displayTitle)
                assertEquals(
                    "offline/abyssal_whip.html",
                    database.offlineObjectDao()
                        .getOfflineObject("https://oldschool.runescape.wiki/w/Abyssal_whip", "en")
                        ?.path
                )
            } else {
                assertEquals(0, database.readingListPageDao().getAllPages().size)
            }

            database.close()
        }
    }

    private fun seedHistoricalDatabase(dbFile: File, version: Int) {
        require(version in 6..10)
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            createVersion6Schema(db)
            if (version >= 7) createVersion7Schema(db)
            if (version >= 8) {
                createVersion8Schema(db, includeSavedData = true)
            }
            if (version >= 9) addVersion9OfflineSaveType(db)
            if (version >= 10) createVersion10FtsTable(db)
            db.version = version
        }
    }

    private fun createVersion6Schema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `article_meta` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `pageId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `wikiUrl` TEXT NOT NULL,
                `localFilePath` TEXT NOT NULL,
                `lastFetchedTimestamp` INTEGER NOT NULL,
                `revisionId` INTEGER,
                `categories` TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_meta_title` ON `article_meta` (`title`)")
        db.execSQL(
            """
            INSERT INTO `article_meta` (
                `id`, `pageId`, `title`, `wikiUrl`, `localFilePath`,
                `lastFetchedTimestamp`, `revisionId`, `categories`
            )
            VALUES (
                5,
                4151,
                'Abyssal whip',
                'https://oldschool.runescape.wiki/w/Abyssal_whip',
                'offline/abyssal_whip.html',
                1000,
                12345,
                NULL
            )
            """.trimIndent()
        )
    }

    private fun createVersion7Schema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `saved_article_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `article_title` TEXT NOT NULL,
                `normalized_article_title` TEXT NOT NULL,
                `snippet` TEXT,
                `timestamp` INTEGER NOT NULL,
                `status` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_saved_article_entries_normalized_article_title`
            ON `saved_article_entries`(`normalized_article_title`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `offline_assets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `original_url` TEXT NOT NULL UNIQUE,
                `local_file_path` TEXT NOT NULL,
                `used_by_article_ids` TEXT NOT NULL,
                `download_timestamp` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_offline_assets_original_url`
            ON `offline_assets`(`original_url`)
            """.trimIndent()
        )
    }

    private fun createVersion8Schema(db: SQLiteDatabase, includeSavedData: Boolean) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ReadingList` (
                `title` TEXT NOT NULL,
                `description` TEXT,
                `mtime` INTEGER NOT NULL,
                `atime` INTEGER NOT NULL,
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `isDefault` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
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
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ReadingListPage_listId` ON `ReadingListPage` (`listId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ReadingListPage_apiTitle_lang` ON `ReadingListPage` (`apiTitle`, `lang`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `offline_objects` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `url` TEXT COLLATE NOCASE NOT NULL,
                `lang` TEXT NOT NULL,
                `path` TEXT NOT NULL,
                `status` INTEGER NOT NULL,
                `usedByStr` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_offline_objects_url_lang` ON `offline_objects` (`url`, `lang`)")

        if (!includeSavedData) {
            return
        }
        db.execSQL(
            """
            INSERT INTO `ReadingList` (`id`, `title`, `description`, `mtime`, `atime`, `isDefault`)
            VALUES (1, 'Saved pages', NULL, 1000, 1000, 1)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `ReadingListPage` (
                `wiki`, `namespace`, `displayTitle`, `apiTitle`, `description`, `thumbUrl`,
                `listId`, `id`, `mtime`, `atime`, `offline`, `status`, `sizeBytes`, `lang`, `revId`, `remoteId`
            )
            VALUES (
                'OSRS Wiki', 'MAIN', 'Abyssal whip', 'Abyssal whip', 'Weapon', NULL,
                1, 42, 1000, 1000, 1, 1, 2048, 'en', 12345, 67890
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `offline_objects` (`id`, `url`, `lang`, `path`, `status`, `usedByStr`)
            VALUES (
                7,
                'https://oldschool.runescape.wiki/w/Abyssal_whip',
                'en',
                'offline/abyssal_whip.html',
                1,
                '|42|'
            )
            """.trimIndent()
        )
    }

    private fun addVersion9OfflineSaveType(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE `offline_objects` ADD COLUMN `saveType` TEXT NOT NULL DEFAULT 'READING_LIST'")
    }

    private fun createVersion10FtsTable(db: SQLiteDatabase) {
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `offline_page_fts` USING FTS4(`url` TEXT, `title` TEXT, `body` TEXT)")
    }
}
