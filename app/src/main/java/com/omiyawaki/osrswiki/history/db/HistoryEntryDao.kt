package com.omiyawaki.osrswiki.history.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface HistoryEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(historyEntry: HistoryEntry): Long

    @Update
    suspend fun updateEntry(historyEntry: HistoryEntry)

    /**
     * Upserts a history entry.
     * If an entry with the same page_wikiUrl and a very recent timestamp exists,
     * it updates the timestamp of the existing entry. Otherwise, it inserts a new entry.
     * This helps prevent spamming history with rapid re-visits of the same page.
     */
    @Transaction
    suspend fun upsertEntry(entry: HistoryEntry) {
        // Check for an existing entry for the same URL within the last N minutes (e.g., 5 minutes)
        // This logic is similar to Wikipedia's to prevent rapid duplicate entries.
        val fiveMinutesAgo = Date(System.currentTimeMillis() - (5 * 60 * 1000))
        val recentEntry = findEntryByUrlAndTimestampAfter(entry.pageTitle.wikiUrl, fiveMinutesAgo)

        if (recentEntry != null) {
            // Update the timestamp of the recent entry to the current time
            recentEntry.timestamp = entry.timestamp // Use the new entry's timestamp (which should be current)
            recentEntry.source = entry.source // Also update source, in case it changed
            updateEntry(recentEntry)
        } else {
            // No recent entry, or different page, so insert the new one
            insertEntry(entry)
        }
    }

    @Query("SELECT * FROM history_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries WHERE page_wikiUrl = :wikiUrl ORDER BY timestamp DESC")
    fun findEntriesByUrl(wikiUrl: String): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries WHERE page_wikiUrl = :wikiUrl AND timestamp > :minTimestamp LIMIT 1")
    suspend fun findEntryByUrlAndTimestampAfter(wikiUrl: String, minTimestamp: Date): HistoryEntry?

    @Query("SELECT * FROM history_entries WHERE id = :id")
    suspend fun findEntryById(id: Long): HistoryEntry?

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM history_entries WHERE page_wikiUrl = :wikiUrl")
    suspend fun deleteEntriesByUrl(wikiUrl: String)

    @Query("DELETE FROM history_entries")
    suspend fun deleteAllEntries()

    // Example: Query to get entries for a specific date range
    // @Query("SELECT * FROM history_entries WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    // fun getEntriesBetweenDates(startDate: Date, endDate: Date): Flow<List<HistoryEntry>>

    // Example: Query to search history by title (case-insensitive)
    // @Query("SELECT * FROM history_entries WHERE page_displayText LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    // fun searchHistoryByTitle(query: String): Flow<List<HistoryEntry>>
}
