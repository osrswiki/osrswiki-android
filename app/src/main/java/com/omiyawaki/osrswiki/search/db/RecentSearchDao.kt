package com.omiyawaki.osrswiki.search.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the recent_searches table.
 */
@Dao
interface RecentSearchDao {
    /**
     * Inserts a recent search. If the search query already exists, it replaces the
     * existing entry, effectively updating its timestamp.
     *
     * @param recentSearch The search entry to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recentSearch: RecentSearch)

    /**
     * Retrieves all recent searches from the table, ordered by the most recent first.
     *
     * @return A flow that emits the list of all recent searches.
     */
    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC")
    fun getAll(): Flow<List<RecentSearch>>

    /**
     * Deletes all entries from the recent_searches table.
     */
    @Query("DELETE FROM recent_searches")
    suspend fun clearAll()
}
