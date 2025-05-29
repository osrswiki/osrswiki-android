package com.omiyawaki.osrswiki.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
// import androidx.room.migration.Migration // Now used via DatabaseMigrations object
// import androidx.sqlite.db.SupportSQLiteDatabase // Now used via DatabaseMigrations object
import com.omiyawaki.osrswiki.OSRSWikiApp

@Database(
    entities = [
        ArticleMetaEntity::class,
        SavedArticleEntry::class, // Added new entity
        OfflineAsset::class       // Added new entity
    ],
    version = 7, // Incremented version from 6 to 7
    exportSchema = false // Or true if you want to export schemas, recommend true for production
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun articleMetaDao(): ArticleMetaDao
    abstract fun savedArticleEntryDao(): SavedArticleEntryDao // Added DAO for SavedArticleEntry
    abstract fun offlineAssetDao(): OfflineAssetDao       // Added DAO for OfflineAsset

    companion object {
        private const val DATABASE_NAME = "osrs_wiki_database.db"

        // Migrations are now managed in DatabaseMigrations.kt
        // Example:
        // val MIGRATION_X_Y = object : Migration(X, Y) {
        //     override fun migrate(database: SupportSQLiteDatabase) {
        //         // Your migration SQL here
        //     }
        // }

        val instance: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(
                OSRSWikiApp.instance.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
            .addMigrations(DatabaseMigrations.MIGRATION_6_7) // Added MIGRATION_6_7
            // .fallbackToDestructiveMigration() // Re-evaluate if this is desired long-term vs specific migrations
                                                // Keeping for now as per original, but MIGRATION_6_7 should handle this upgrade path.
            .fallbackToDestructiveMigration(dropAllTables = true) // Retained from original logic
            // Avoid .allowMainThreadQueries() unless absolutely necessary
            .build()
        }
    }
}
