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

    @Insert
    suspend fun insert(entity: ArticleMetaEntity): Long

    @Update
    suspend fun update(entity: ArticleMetaEntity)

    @Delete
    suspend fun delete(entity: ArticleMetaEntity)

    @Query("SELECT * FROM article_meta WHERE pageId = :pageId LIMIT 1")
    suspend fun getMetaByPageId(pageId: Int): ArticleMetaEntity?

    @Query("SELECT * FROM article_meta WHERE pageId = :pageId LIMIT 1")
    fun getMetaByPageIdFlow(pageId: Int): Flow<ArticleMetaEntity?>

    // MODIFIED QUERY with COLLATE NOCASE
    @Query("SELECT * FROM article_meta WHERE title = :title COLLATE NOCASE LIMIT 1")
    suspend fun getMetaByExactTitle(title: String): ArticleMetaEntity?

    @Query("SELECT * FROM article_meta WHERE title LIKE :query ORDER BY title ASC")
    suspend fun searchByTitle(query: String): List<ArticleMetaEntity>

}
