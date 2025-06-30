package com.omiyawaki.osrswiki.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OfflinePageFtsDao {

    @Insert
    suspend fun insertPageContent(item: OfflinePageFts)

    @Query("DELETE FROM offline_page_fts WHERE url = :url")
    suspend fun deletePageContentByUrl(url: String)

    @Query("SELECT * FROM offline_page_fts WHERE offline_page_fts MATCH :query")
    suspend fun searchAll(query: String): List<OfflinePageFts>

    // For fetching all data, perhaps for re-indexing or diagnostics
    @Query("SELECT * FROM offline_page_fts")
    suspend fun getAll(): List<OfflinePageFts>
}