package com.omiyawaki.osrswiki.history.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(historyEntry: HistoryEntry)

    @Update
    suspend fun updateEntry(historyEntry: HistoryEntry)

    /**
     * Upserts a history entry.
     * Uses the page_wikiUrl primary key to replace existing entries with the same URL,
     * effectively updating the timestamp to the most recent visit.
     */
    suspend fun upsertEntry(entry: HistoryEntry) {
        insertEntry(entry)
    }

    @Query("SELECT * FROM history_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries WHERE page_wikiUrl = :wikiUrl")
    suspend fun findEntryByUrl(wikiUrl: String): HistoryEntry?

    @Query("SELECT * FROM history_entries WHERE page_wikiUrl = :wikiUrl ORDER BY timestamp DESC")
    fun findEntriesByUrl(wikiUrl: String): Flow<List<HistoryEntry>>

    @Query("DELETE FROM history_entries WHERE page_wikiUrl = :wikiUrl")
    suspend fun deleteEntryByUrl(wikiUrl: String)

    @Query("DELETE FROM history_entries")
    suspend fun deleteAllEntries()

    // Example: Query to get entries for a specific date range
    // @Query("SELECT * FROM history_entries WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    // fun getEntriesBetweenDates(startDate: Date, endDate: Date): Flow<List<HistoryEntry>>

    // Example: Query to search history by title (case-insensitive)
    // @Query("SELECT * FROM history_entries WHERE page_displayText LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    // fun searchHistoryByTitle(query: String): Flow<List<HistoryEntry>>
}
