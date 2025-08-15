package com.omiyawaki.osrswiki.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleMetaDao {
    // Methods required by PageLocalDataSource
    @Query("SELECT * FROM article_meta WHERE title = :title LIMIT 1")
    suspend fun getMetaByExactTitle(title: String): ArticleMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meta: ArticleMetaEntity)

    @Update
    suspend fun update(meta: ArticleMetaEntity)

    @Delete
    suspend fun delete(meta: ArticleMetaEntity)

    @Query("SELECT * FROM article_meta WHERE pageId = :pageId LIMIT 1")
    fun getMetaByPageIdFlow(pageId: Int): Flow<ArticleMetaEntity?>

    // Methods required by search functionality (SearchPagingSource, SearchRepository)
    @Query("SELECT * FROM article_meta WHERE pageId = :pageId LIMIT 1")
    suspend fun getMetaByPageId(pageId: Int): ArticleMetaEntity?

    @Query("SELECT * FROM article_meta WHERE pageId IN (:pageIds)")
    suspend fun getMetasByPageIds(pageIds: List<Int>): List<ArticleMetaEntity>

    @Query("SELECT * FROM article_meta WHERE title LIKE :query")
    suspend fun searchByTitle(query: String): List<ArticleMetaEntity>
}
