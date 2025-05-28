package com.omiyawaki.osrswiki.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.omiyawaki.osrswiki.offline.db.OfflineAsset
import com.omiyawaki.osrswiki.offline.db.OfflineAssetDao
import com.omiyawaki.osrswiki.offline.db.SavedArticleEntry
import com.omiyawaki.osrswiki.offline.db.SavedArticleEntryDao

/**
 * The main Room database class for the OSRSWiki application.
 * It includes entities for saved articles and their offline assets.
 */
@Database(
    entities = [
        SavedArticleEntry::class,
        OfflineAsset::class
        // Add other entities here if they exist or are created later
    ],
    version = 1, // Start with version 1. Increment on schema changes.
    exportSchema = true // Recommended to export schema for version control and migrations.
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun savedArticleEntryDao(): SavedArticleEntryDao
    abstract fun offlineAssetDao(): OfflineAssetDao
    // Add other DAOs here

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DATABASE_NAME = "osrswiki-app.db"

        fun getInstance(context: Context): AppDatabase {
            // Multiple threads can ask for the database at the same time, ensure we only initialize it once
            // by using synchronized. Only one thread may enter a synchronized block at a time.
            return INSTANCE ?: synchronized(this) {
                //coholde instance to allow smart cast to AppDatabase
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                // Add migrations here if needed for future schema changes
                // .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                // Return instance
                instance
            }
        }
    }
}

