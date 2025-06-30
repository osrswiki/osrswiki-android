package com.omiyawaki.osrswiki.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.omiyawaki.osrswiki.OSRSWikiApp // Ensure this import points to your Application class

// Entities
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.database.SavedArticleEntry
import com.omiyawaki.osrswiki.database.OfflineAsset
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.history.db.HistoryEntry // <<< ADDED HISTORY ENTITY IMPORT
import com.omiyawaki.osrswiki.readinglist.database.ReadingList
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.offline.db.OfflineObject // Ensure this path is correct based on your project

// DAOs
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.SavedArticleEntryDao
import com.omiyawaki.osrswiki.database.OfflineAssetDao
import com.omiyawaki.osrswiki.database.OfflinePageFtsDao
import com.omiyawaki.osrswiki.history.db.HistoryEntryDao // <<< ADDED HISTORY DAO IMPORT
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao // Ensure this path is correct
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao   // Ensure this path is correct
import com.omiyawaki.osrswiki.readinglist.db.ReadingListDao     // Ensure this path is correct

// Converters
import com.omiyawaki.osrswiki.database.converters.DateConverter // Import the new DateConverter


@Database(
    entities = [
        ArticleMetaEntity::class,
        SavedArticleEntry::class,
        OfflineAsset::class,
        ReadingList::class,
        ReadingListPage::class,
        OfflineObject::class,
        OfflinePageFts::class,
        HistoryEntry::class // <<< ADDED HISTORY ENTITY
    ],
    version = 11, // <<< Current version
    exportSchema = false
)
@TypeConverters(
    com.omiyawaki.osrswiki.database.TypeConverters::class, // Your existing converters
    DateConverter::class                                   // Our new DateConverter
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun articleMetaDao(): ArticleMetaDao
    abstract fun savedArticleEntryDao(): SavedArticleEntryDao
    abstract fun offlineAssetDao(): OfflineAssetDao
    abstract fun readingListDao(): ReadingListDao
    abstract fun readingListPageDao(): ReadingListPageDao
    abstract fun offlineObjectDao(): OfflineObjectDao
    abstract fun offlinePageFtsDao(): OfflinePageFtsDao
    abstract fun historyEntryDao(): HistoryEntryDao // <<< ADDED HISTORY DAO ACCESSOR

    companion object {
        private const val DATABASE_NAME = "osrs_wiki_database.db"

        val instance: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(
                OSRSWikiApp.instance.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
            // .addMigrations( // <<< TEMPORARILY COMMENTED OUT FOR DEVELOPMENT
            //     DatabaseMigrations.MIGRATION_6_7,
            //     DatabaseMigrations.MIGRATION_7_8,
            //     DatabaseMigrations.MIGRATION_8_9,
            //     DatabaseMigrations.MIGRATION_9_10
            // )
                .fallbackToDestructiveMigration(true)
            .build()
        }
    }
}
