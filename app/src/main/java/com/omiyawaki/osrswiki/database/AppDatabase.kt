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
import com.omiyawaki.osrswiki.database.SavedArticleEntry
import com.omiyawaki.osrswiki.database.OfflineAsset
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.readinglist.database.ReadingList
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import com.omiyawaki.osrswiki.search.db.RecentSearch

// DAOs
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.SavedArticleEntryDao
import com.omiyawaki.osrswiki.database.OfflineAssetDao
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
        SavedArticleEntry::class,
        OfflineAsset::class,
        ReadingList::class,
        ReadingListPage::class,
        OfflineObject::class,
        OfflinePageFts::class,
        HistoryEntry::class,
        RecentSearch::class // Add the new RecentSearch entity.
    ],
    version = 12, // Increment the database version.
    exportSchema = false
)
@TypeConverters(
    com.omiyawaki.osrswiki.database.TypeConverters::class,
    DateConverter::class
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun articleMetaDao(): ArticleMetaDao
    abstract fun savedArticleEntryDao(): SavedArticleEntryDao
    abstract fun offlineAssetDao(): OfflineAssetDao
    abstract fun readingListDao(): ReadingListDao
    abstract fun readingListPageDao(): ReadingListPageDao
    abstract fun offlineObjectDao(): OfflineObjectDao
    abstract fun offlinePageFtsDao(): OfflinePageFtsDao
    abstract fun historyEntryDao(): HistoryEntryDao
    abstract fun recentSearchDao(): RecentSearchDao // Add the accessor for the new DAO.

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

        val instance: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(
                OSRSWikiApp.instance.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
             .addMigrations(
                // Other migrations would be listed here.
                MIGRATION_11_12 // Add the new migration to the builder.
             )
            .build()
        }
    }
}
