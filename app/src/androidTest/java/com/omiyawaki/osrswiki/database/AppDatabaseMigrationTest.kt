package com.omiyawaki.osrswiki.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

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
        seedVersion10Database(context.getDatabasePath(dbName))

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

    private fun seedVersion10Database(dbFile: File) {
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
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
                    `usedByStr` TEXT NOT NULL,
                    `saveType` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_offline_objects_url_lang` ON `offline_objects` (`url`, `lang`)")
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `offline_page_fts` USING FTS4(`url` TEXT, `title` TEXT, `body` TEXT)")

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
                INSERT INTO `offline_objects` (`id`, `url`, `lang`, `path`, `status`, `usedByStr`, `saveType`)
                VALUES (
                    7,
                    'https://oldschool.runescape.wiki/w/Abyssal_whip',
                    'en',
                    'offline/abyssal_whip.html',
                    1,
                    '|42|',
                    'READING_LIST'
                )
                """.trimIndent()
            )
            db.version = 10
        }
    }
}
