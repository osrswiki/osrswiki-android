package com.omiyawaki.osrswiki.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
// import androidx.room.migration.Migration // Uncomment if you add migrations
// import androidx.sqlite.db.SupportSQLiteDatabase // Uncomment if you add migrations
import com.omiyawaki.osrswiki.OSRSWikiApplication
import com.omiyawaki.osrswiki.data.db.dao.ArticleMetaDao
import com.omiyawaki.osrswiki.data.db.entity.ArticleMetaEntity

@Database(
    entities = [ArticleMetaEntity::class],
    version = 6, // Keep current version, increment when schema changes
    exportSchema = false // Or true if you want to export schemas
)
abstract class OSRSWikiDatabase : RoomDatabase() {

    abstract fun articleMetaDao(): ArticleMetaDao

    companion object {
        private const val DATABASE_NAME = "osrs_wiki_database.db"

        // Example Migration (uncomment and adapt if needed)
        // val MIGRATION_6_7 = object : Migration(6, 7) {
        //     override fun migrate(database: SupportSQLiteDatabase) {
        //         // Your migration SQL here, e.g.:
        //         // database.execSQL("ALTER TABLE ArticleMetaEntity ADD COLUMN new_column TEXT")
        //     }
        // }

        val instance: OSRSWikiDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(
                OSRSWikiApplication.instance.applicationContext, // Use application context from OSRSWikiApplication
                OSRSWikiDatabase::class.java,
                DATABASE_NAME
            )
            // Add migrations here when you have schema changes
            // .addMigrations(MIGRATION_6_7) // Example
            .fallbackToDestructiveMigration(dropAllTables = true) // Specify behavior clearly // Handles schema changes by deleting and recreating if no migration path found
            // Avoid .allowMainThreadQueries() unless absolutely necessary
            .build()
        }
    }
}
