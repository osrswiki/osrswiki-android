package com.omiyawaki.osrswiki.database // Updated package

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
// No import for SavedArticleEntry needed if it's in the same 'database' package.

/**
 * Data Access Object (DAO) for the [SavedArticleEntry] entity.
 * Provides methods to interact with the saved_article_entries table in the database.
 */
@Dao
interface SavedArticleEntryDao {

    /**
     * Inserts a new saved article entry into the database.
     * If an entry with the same primary key already exists, it will be replaced.
     * @param savedArticleEntry The entry to insert.
     * @return The row ID of the newly inserted entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(savedArticleEntry: SavedArticleEntry): Long

    /**
     * Updates an existing saved article entry in the database.
     * @param savedArticleEntry The entry to update.
     */
    @Update
    suspend fun update(savedArticleEntry: SavedArticleEntry)

    /**
     * Deletes a specific saved article entry from the database.
     * @param savedArticleEntry The entry to delete.
     */
    @Delete
    suspend fun delete(savedArticleEntry: SavedArticleEntry)

    /**
     * Retrieves a specific saved article entry by its ID.
     * Returns a Flow that emits the entry if found, or null otherwise.
     * @param id The ID of the entry to retrieve.
     * @return A Flow emitting the [SavedArticleEntry] or null.
     */
    @Query("SELECT * FROM saved_article_entries WHERE id = :id")
    fun getEntryById(id: Long): Flow<SavedArticleEntry?>

    /**
     * Retrieves all saved article entries from the database, ordered by timestamp in descending order (newest first).
     * Returns a Flow that emits the list of entries whenever the data changes.
     * @return A Flow emitting a list of all [SavedArticleEntry] items.
     */
    @Query("SELECT * FROM saved_article_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<SavedArticleEntry>>

    /**
     * Searches for saved article entries where the normalized title or snippet contains the given query string.
     * The search is case-insensitive due to the nature of LIKE combined with pre-normalized text.
     * The caller is responsible for adding wildcard characters (e.g., '%') to the query string if needed.
     * For example, to find entries containing "term", pass "%term%".
     * Results are ordered by timestamp in descending order.
     * @param queryString The string to search for in normalized_article_title or snippet.
     * @return A Flow emitting a list of matching [SavedArticleEntry] items.
     */
    @Query("SELECT * FROM saved_article_entries WHERE normalized_article_title LIKE :queryString OR snippet LIKE :queryString ORDER BY timestamp DESC")
    fun searchArticles(queryString: String): Flow<List<SavedArticleEntry>>

    /**
     * Deletes all entries from the saved_article_entries table.
     * Use with caution.
     */
    @Query("DELETE FROM saved_article_entries")
    suspend fun clearAllEntries()

    /**
     * Updates the status of a specific saved article entry.
     * @param id The ID of the entry to update.
     * @param newStatus The new status string (should correspond to a value from [ArticleSaveStatus].name).
     */
    @Query("UPDATE saved_article_entries SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: Long, newStatus: String)
}
