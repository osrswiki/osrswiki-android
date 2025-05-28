package com.omiyawaki.osrswiki.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omiyawaki.osrswiki.offline.db.ArticleSaveStatus // Import if ArticleSaveStatus is in this package or a subpackage accessible

/**
 * Migration from database version 1 to version 2.
 * Adds the 'status' column to the 'saved_article_entries' table.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // It's good practice to use the enum's actual name in case it changes,
        // but for SQL, the string literal is fine.
        // Assuming ArticleSaveStatus.PENDING.name is "PENDING"
        db.execSQL("ALTER TABLE saved_article_entries ADD COLUMN status TEXT NOT NULL DEFAULT '${ArticleSaveStatus.PENDING.name}'")
    }
}

// Add other migrations here as needed, e.g., MIGRATION_2_3, etc.
