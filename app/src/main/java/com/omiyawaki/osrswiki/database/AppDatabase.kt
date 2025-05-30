package com.omiyawaki.osrswiki.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters // Import TypeConverters annotation
import com.omiyawaki.osrswiki.OSRSWikiApp

// Entities
import com.omiyawaki.osrswiki.readinglist.database.ReadingList
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.offline.db.OfflineObject

// DAOs
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.readinglist.db.ReadingListDao


@Database(
    entities = [
        ArticleMetaEntity::class,       // Your existing entity
        SavedArticleEntry::class,       // Your existing entity
        OfflineAsset::class,            // Your existing entity
        ReadingList::class,             // New entity
        ReadingListPage::class,         // New entity
        OfflineObject::class            // New entity
    ],
    version = 8, // Keep version as is, or increment if you made other schema changes not yet migrated
    exportSchema = false
)
@TypeConverters(com.omiyawaki.osrswiki.database.TypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun articleMetaDao(): ArticleMetaDao
    abstract fun savedArticleEntryDao(): SavedArticleEntryDao
    abstract fun offlineAssetDao(): OfflineAssetDao

    // DAOs for new offline functionality
    abstract fun readingListDao(): ReadingListDao
    abstract fun readingListPageDao(): ReadingListPageDao
    abstract fun offlineObjectDao(): OfflineObjectDao

    companion object {
        private const val DATABASE_NAME = "osrs_wiki_database.db"

        // Migration comments from previous step...
        // IMPORTANT: Define MIGRATION_7_8 (or equivalent for your version increment)
        // in DatabaseMigrations.kt. It needs to create tables for:
        // ReadingList, ReadingListPage, and OfflineObject.
        // Example for MIGRATION_7_8:
        // val MIGRATION_7_8 = object : Migration(7, 8) {
        //     override fun migrate(database: SupportSQLiteDatabase) {
        //         database.execSQL("CREATE TABLE IF NOT EXISTS `ReadingList` (title TEXT NOT NULL, description TEXT, mtime INTEGER NOT NULL, atime INTEGER NOT NULL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, isDefault INTEGER NOT NULL)")
        //         // For ReadingListPage, since WikiSite and Namespace are converted to String, store them as TEXT
        //         database.execSQL("CREATE TABLE IF NOT EXISTS `ReadingListPage` (wiki TEXT, namespace TEXT, displayTitle TEXT NOT NULL, apiTitle TEXT NOT NULL, description TEXT, thumbUrl TEXT, listId INTEGER NOT NULL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mtime INTEGER NOT NULL, atime INTEGER NOT NULL, offline INTEGER NOT NULL, status INTEGER NOT NULL, sizeBytes INTEGER NOT NULL, lang TEXT NOT NULL, revId INTEGER NOT NULL, remoteId INTEGER NOT NULL)")
        //         database.execSQL("CREATE TABLE IF NOT EXISTS `OfflineObject` (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, url TEXT NOT NULL, lang TEXT NOT NULL, path TEXT NOT NULL, status INTEGER NOT NULL, usedByStr TEXT NOT NULL)")
        //         database.execSQL("CREATE INDEX IF NOT EXISTS index_ReadingListPage_listId ON ReadingListPage(listId)")
        //         database.execSQL("CREATE INDEX IF NOT EXISTS index_ReadingListPage_apiTitle_lang ON ReadingListPage(apiTitle, lang)")
        //         database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_OfflineObject_url_lang ON OfflineObject(url, lang)")
        //     }
        // }


        val instance: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(
                OSRSWikiApp.instance.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
            .addMigrations(
                DatabaseMigrations.MIGRATION_6_7,
                DatabaseMigrations.MIGRATION_7_8
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        }
    }
}
