package com.omiyawaki.osrswiki.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.omiyawaki.osrswiki.data.db.dao.ArticleMetaDao
import com.omiyawaki.osrswiki.data.db.entity.ArticleMetaEntity

@Database(
    entities = [ArticleMetaEntity::class],
    version = 6,
    exportSchema = false
)
@Suppress("unused")
abstract class OSRSWikiDatabase : RoomDatabase() {

    abstract fun articleMetaDao(): ArticleMetaDao

@Suppress("unused")
companion object {
        @Volatile
        private var INSTANCE: OSRSWikiDatabase? = null

        private const val DATABASE_NAME = "osrs_wiki_database.db"

        fun getInstance(context: Context): OSRSWikiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OSRSWikiDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration(dropAllTables = true) // Updated this line
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
