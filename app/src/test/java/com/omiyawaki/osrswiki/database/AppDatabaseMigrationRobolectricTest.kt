package com.omiyawaki.osrswiki.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

        assertEquals(18, database.openHelper.readableDatabase.version)
        val legacyPage = database.readingListPageDao().getAllPages().single()
        assertEquals("Abyssal whip", legacyPage.displayTitle)
        assertEquals(ReadingListPage.STATUS_ERROR, legacyPage.status)
        assertEquals(ReadingListPage.DURABLE_SETTLEMENT_VERSION_NONE, legacyPage.durableSettlementVersion)
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

            assertEquals(18, database.openHelper.readableDatabase.version)
            if (version >= 8) {
                val legacyPage = database.readingListPageDao().getAllPages().single()
                assertEquals("Abyssal whip", legacyPage.displayTitle)
                assertEquals(ReadingListPage.STATUS_ERROR, legacyPage.status)
                assertEquals(
                    ReadingListPage.DURABLE_SETTLEMENT_VERSION_NONE,
                    legacyPage.durableSettlementVersion
                )
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

    @Test
    fun durableSettlementMigrationMarksOnlyOfflineSavedRowsRetryableAndIsIdempotent() {
        val dbName = "migration-settlement-${UUID.randomUUID()}.db"
        databaseNames += dbName
        val dbFile = context.getDatabasePath(dbName)
        seedHistoricalDatabase(dbFile, version = 10)
        addLegacySettlementStateVariants(dbFile)

        var database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        database.openHelper.writableDatabase

        val migrated = database.readingListPageDao().getAllPages().associateBy { it.id }
        val requeued = requireNotNull(migrated[42L])
        assertEquals(ReadingListPage.STATUS_ERROR, requeued.status)
        assertEquals(0, requeued.downloadProgress)
        assertEquals(2_048L, requeued.sizeBytes)
        assertTrue(requeued.offline)
        assertEquals(ReadingListPage.DURABLE_SETTLEMENT_VERSION_NONE, requeued.durableSettlementVersion)

        assertEquals(ReadingListPage.STATUS_ERROR, migrated.getValue(43L).status)
        assertEquals(ReadingListPage.STATUS_QUEUE_FOR_DELETE, migrated.getValue(44L).status)
        assertEquals(ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE, migrated.getValue(45L).status)
        assertEquals(ReadingListPage.STATUS_QUEUE_FOR_SAVE, migrated.getValue(46L).status)
        assertEquals(ReadingListPage.STATUS_SAVED, migrated.getValue(47L).status)
        assertFalse(migrated.getValue(47L).offline)
        migrated.values.forEach {
            assertEquals(
                ReadingListPage.DURABLE_SETTLEMENT_VERSION_NONE,
                it.durableSettlementVersion
            )
        }

        val visibleDuringRefresh = runBlocking {
            database.readingListPageDao().getFullySavedPagesObservable().first()
        }
        assertTrue(visibleDuringRefresh.any { it.id == 42L && it.hasReadableOfflineSnapshot })
        assertTrue(visibleDuringRefresh.any { it.id == 43L && it.hasReadableOfflineSnapshot })
        assertFalse(visibleDuringRefresh.any { it.id == 46L })
        assertEquals(
            "offline/abyssal_whip.html",
            database.offlineObjectDao()
                .getOfflineObject("https://oldschool.runescape.wiki/w/Abyssal_whip", "en")
                ?.path
        )

        database.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        database.openHelper.writableDatabase
        val retryableAfterRelaunch = requireNotNull(database.readingListPageDao().getPageById(42L))
        assertEquals(ReadingListPage.STATUS_ERROR, retryableAfterRelaunch.status)
        assertEquals(
            ReadingListPage.DURABLE_SETTLEMENT_VERSION_NONE,
            retryableAfterRelaunch.durableSettlementVersion
        )
        assertTrue(retryableAfterRelaunch.hasReadableOfflineSnapshot)

        runBlocking {
            database.readingListPageDao().updatePageStatusToSavedAndMtime(
                pageId = 42L,
                newStatus = retryableAfterRelaunch.retryQueueStatus,
                currentTimeMs = 1_500L
            )
        }
        assertEquals(
            ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE,
            retryableAfterRelaunch.retryQueueStatus
        )
        assertEquals(
            1,
            runBlocking {
                database.readingListPageDao().transitionQueuedSaveToSaved(
                    pageId = 42L,
                    newSizeBytes = 4_096L,
                    currentTimeMs = 2_000L
                )
            }
        )
        val settled = requireNotNull(database.readingListPageDao().getPageById(42L))
        assertEquals(ReadingListPage.STATUS_SAVED, settled.status)
        assertEquals(
            ReadingListPage.CURRENT_DURABLE_SETTLEMENT_VERSION,
            settled.durableSettlementVersion
        )
        database.close()

        database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        database.openHelper.writableDatabase
        val afterRelaunch = requireNotNull(database.readingListPageDao().getPageById(42L))
        assertEquals(ReadingListPage.STATUS_SAVED, afterRelaunch.status)
        assertEquals(
            ReadingListPage.CURRENT_DURABLE_SETTLEMENT_VERSION,
            afterRelaunch.durableSettlementVersion
        )
        assertEquals(4_096L, afterRelaunch.sizeBytes)
        database.close()
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

    private fun addLegacySettlementStateVariants(dbFile: File) {
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            fun insert(id: Long, offline: Int, status: Long, sizeBytes: Long) {
                db.execSQL(
                    """
                    INSERT INTO `ReadingListPage` (
                        `wiki`, `namespace`, `displayTitle`, `apiTitle`, `description`, `thumbUrl`,
                        `listId`, `id`, `mtime`, `atime`, `offline`, `status`, `sizeBytes`, `lang`,
                        `revId`, `remoteId`
                    )
                    VALUES (
                        'OSRS Wiki', 'MAIN', 'Legacy $id', 'Legacy_$id', NULL, NULL,
                        1, $id, 1000, 1000, $offline, $status, $sizeBytes, 'en', 1, 0
                    )
                    """.trimIndent()
                )
            }

            insert(43L, offline = 1, status = ReadingListPage.STATUS_ERROR, sizeBytes = 1_024L)
            insert(44L, offline = 1, status = ReadingListPage.STATUS_QUEUE_FOR_DELETE, sizeBytes = 2_048L)
            insert(45L, offline = 1, status = ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE, sizeBytes = 2_048L)
            insert(46L, offline = 1, status = ReadingListPage.STATUS_QUEUE_FOR_SAVE, sizeBytes = 0L)
            insert(47L, offline = 0, status = ReadingListPage.STATUS_SAVED, sizeBytes = 0L)
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
