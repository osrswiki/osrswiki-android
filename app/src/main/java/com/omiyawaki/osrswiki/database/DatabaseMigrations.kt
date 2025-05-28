package com.omiyawaki.osrswiki.database

import androidx.room.migration.Migration // Ensure this is uncommented or present
import androidx.sqlite.db.SupportSQLiteDatabase // Ensure this is uncommented or present

/**
 * Contains Room database migrations for the application.
 *
 * Migrations should be defined as `val MIGRATION_X_Y = object : Migration(X, Y) { ... }`
 * and then added to the Room.databaseBuilder() in AppDatabase.kt.
 *
 * This file serves as a central place for all migration definitions.
 */
object DatabaseMigrations {

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create SavedArticleEntry table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `saved_article_entries` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `article_title` TEXT NOT NULL,
                    `normalized_article_title` TEXT NOT NULL,
                    `snippet` TEXT,
                    `timestamp` INTEGER NOT NULL,
                    `status` TEXT NOT NULL
                )
            """)
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_saved_article_entries_normalized_article_title`
                ON `saved_article_entries`(`normalized_article_title`)
            """)

            // Create OfflineAsset table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `offline_assets` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `original_url` TEXT NOT NULL UNIQUE,
                    `local_file_path` TEXT NOT NULL,
                    `used_by_article_ids` TEXT NOT NULL,
                    `download_timestamp` INTEGER NOT NULL
                )
            """)
            // The UNIQUE constraint on original_url implies an index.
            // If @Index(value = ["original_url"], unique = true) is also in the entity,
            // an explicit index creation ensures it matches Room's expectation.
            database.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS `index_offline_assets_original_url`
                ON `offline_assets`(`original_url`)
            """)
        }
    }

    // Example for future migrations:
    // val MIGRATION_7_8 = object : Migration(7, 8) {
    //     override fun migrate(database: SupportSQLiteDatabase) {
    //         // database.execSQL("ALTER TABLE ...")
    //     }
    // }

    // Actual migrations (like MIGRATION_7_8) will be added here.
}
