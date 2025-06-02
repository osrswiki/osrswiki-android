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
import com.omiyawaki.osrswiki.database.OfflinePageFts // <<< ADDED FTS ENTITY IMPORT

import com.omiyawaki.osrswiki.readinglist.database.ReadingList
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.offline.db.OfflineObject // Ensure this path is correct based on your project

// DAOs
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.SavedArticleEntryDao
import com.omiyawaki.osrswiki.database.OfflineAssetDao
import com.omiyawaki.osrswiki.database.OfflinePageFtsDao // <<< ADDED FTS DAO IMPORT

import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao // Ensure this path is correct
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao   // Ensure this path is correct
import com.omiyawaki.osrswiki.readinglist.db.ReadingListDao     // Ensure this path is correct


@Database(
    entities = [
        ArticleMetaEntity::class,
        SavedArticleEntry::class,
        OfflineAsset::class,
        ReadingList::class,
        ReadingListPage::class,
        OfflineObject::class,
        OfflinePageFts::class // <<< ADDED FTS ENTITY
    ],
    version = 10, // <<< INCREMENTED VERSION
    exportSchema = false // Consider setting to true and managing schemas if preferred
)
@TypeConverters(com.omiyawaki.osrswiki.database.TypeConverters::class) // Ensure this TypeConverter class exists
abstract class AppDatabase : RoomDatabase() {

    abstract fun articleMetaDao(): ArticleMetaDao
    abstract fun savedArticleEntryDao(): SavedArticleEntryDao
    abstract fun offlineAssetDao(): OfflineAssetDao
    abstract fun readingListDao(): ReadingListDao
    abstract fun readingListPageDao(): ReadingListPageDao
    abstract fun offlineObjectDao(): OfflineObjectDao
    abstract fun offlinePageFtsDao(): OfflinePageFtsDao // <<< ADDED FTS DAO ACCESSOR

    companion object {
        private const val DATABASE_NAME = "osrs_wiki_database.db"

        // Reverted to the original 'instance' pattern using lazy delegate
        val instance: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(
                OSRSWikiApp.instance.applicationContext, // This relies on OSRSWikiApp.instance being correctly set up
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(
                    DatabaseMigrations.MIGRATION_6_7,
                    DatabaseMigrations.MIGRATION_7_8,
                    DatabaseMigrations.MIGRATION_8_9,
                    DatabaseMigrations.MIGRATION_9_10 // <<< Ensure MIGRATION_9_10 is correctly referenced
                )
                // .fallbackToDestructiveMigration() // Consider your strategy
                .build()
        }
    }
}