package com.omiyawaki.osrswiki.database // Updated package

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.omiyawaki.osrswiki.database.ArticleMetaEntity // Updated import
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleMetaDao {

    /**
     * Inserts a new article's metadata.
     * Since 'id' is auto-generated, this will always create a new row.
     * Ensure 'pageId' uniqueness through application logic if needed before calling insert,
     * or use getMetaByPageId and then update/insert logic as implemented in ArticleRepository.
     *
     * @param entity The article metadata to insert.
     * @return The row ID of the newly inserted entity.
     */
    @Insert
    suspend fun insert(entity: ArticleMetaEntity): Long

    /**
     * Updates an existing article's metadata.
     * Relies on the 'id' field of the entity to find the row to update.
     *
     * @param entity The article metadata to update.
     */
    @Update
    suspend fun update(entity: ArticleMetaEntity)

    /**
     * Deletes an article's metadata.
     * Relies on the 'id' field (or other primary key info) of the entity.
     *
     * @param entity The article metadata to delete.
     */
    @Delete
    suspend fun delete(entity: ArticleMetaEntity)

    /**
     * Retrieves article metadata by its unique pageId.
     * This is a suspend function for one-shot queries.
     *
     * @param pageId The unique ID of the wiki page.
     * @return The ArticleMetaEntity if found, null otherwise.
     */
    @Query("SELECT * FROM article_meta WHERE pageId = :pageId LIMIT 1")
    suspend fun getMetaByPageId(pageId: Int): ArticleMetaEntity?

    /**
     * Retrieves article metadata by its unique pageId as a Flow.
     * Useful for observing changes reactively.
     *
     * @param pageId The unique ID of the wiki page.
     * @return A Flow emitting the ArticleMetaEntity if found, or null.
     */
    @Query("SELECT * FROM article_meta WHERE pageId = :pageId LIMIT 1")
    fun getMetaByPageIdFlow(pageId: Int): Flow<ArticleMetaEntity?>

    /**
     * Retrieves article metadata by its exact canonical title.
     * This is a suspend function for one-shot queries.
     *
     * @param title The canonical title of the article.
     * @return The ArticleMetaEntity if found, null otherwise.
     */
    @Query("SELECT * FROM article_meta WHERE title = :title LIMIT 1")
    suspend fun getMetaByExactTitle(title: String): ArticleMetaEntity?

    /**
     * Searches for article metadata where the title matches the given query (case-insensitive LIKE).
     * Results are ordered by title.
     *
     * @param query The search query (should include '%' wildcards if needed, e.g., "%searchQuery%").
     * @return A list of matching ArticleMetaEntity objects.
     */
    @Query("SELECT * FROM article_meta WHERE title LIKE :query ORDER BY title ASC")
    suspend fun searchByTitle(query: String): List<ArticleMetaEntity>

}
