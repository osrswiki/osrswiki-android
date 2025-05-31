package com.omiyawaki.osrswiki.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.omiyawaki.osrswiki.OSRSWikiApp

// Entities - Using the correct package based on 'tree' output
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.database.SavedArticleEntry
import com.omiyawaki.osrswiki.database.OfflineAsset

import com.omiyawaki.osrswiki.readinglist.database.ReadingList
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.offline.db.OfflineObject

// DAOs - Using the correct package based on previous 'find' and 'tree' output
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.SavedArticleEntryDao
import com.omiyawaki.osrswiki.database.OfflineAssetDao

import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.readinglist.db.ReadingListDao


@Database(
    entities = [
        ArticleMetaEntity::class,
        SavedArticleEntry::class,
        OfflineAsset::class,
        ReadingList::class,
        ReadingListPage::class,
        OfflineObject::class
    ],
    version = 9, 
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

        val instance: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(
                OSRSWikiApp.instance.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
            .addMigrations(
                DatabaseMigrations.MIGRATION_6_7, 
                DatabaseMigrations.MIGRATION_7_8, 
                DatabaseMigrations.MIGRATION_8_9  
            )
            // .fallbackToDestructiveMigration() // Consider your strategy
            .build()
        }
    }
}
