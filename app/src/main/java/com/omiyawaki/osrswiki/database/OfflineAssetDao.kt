package com.omiyawaki.osrswiki.database // Updated package

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
// No import for OfflineAsset needed if it's in the same 'database' package.

/**
 * Data Access Object (DAO) for the [OfflineAsset] entity.
 * Provides methods to interact with the offline_assets table in the database.
 */
@Dao
interface OfflineAssetDao {

    /**
     * Inserts a new offline asset into the database.
     * If an asset with the same original_url already exists, this operation is ignored.
     * @param offlineAsset The asset to insert.
     * @return The row ID of the newly inserted asset, or -1 if ignored.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(offlineAsset: OfflineAsset): Long

    /**
     * Updates an existing offline asset in the database.
     * @param offlineAsset The asset to update.
     */
    @Update
    suspend fun update(offlineAsset: OfflineAsset)

    /**
     * Deletes a specific offline asset from the database.
     * Note: Business logic should ensure an asset is only deleted if no longer
     * referenced by any SavedArticleEntry.
     * @param offlineAsset The asset to delete.
     */
    @Delete
    suspend fun delete(offlineAsset: OfflineAsset)

    /**
     * Retrieves a specific offline asset by its ID.
     * @param id The ID of the asset to retrieve.
     * @return A Flow emitting the [OfflineAsset] or null if not found.
     */
    @Query("SELECT * FROM offline_assets WHERE id = :id")
    fun getAssetById(id: Long): Flow<OfflineAsset?>

    /**
     * Retrieves a specific offline asset by its original URL.
     * Useful because original_url is unique.
     * @param originalUrl The original URL of the asset.
     * @return A Flow emitting the [OfflineAsset] or null if not found.
     */
    @Query("SELECT * FROM offline_assets WHERE original_url = :originalUrl")
    fun getAssetByOriginalUrl(originalUrl: String): Flow<OfflineAsset?>

    /**
     * Retrieves all offline assets associated with a specific SavedArticleEntry ID.
     * This query checks if the articleId is present in the comma-separated 'used_by_article_ids' string.
     * The articleId parameter should be the string representation of the ID.
     * @param articleId The string representation of the SavedArticleEntry ID.
     * @return A Flow emitting a list of matching [OfflineAsset] items.
     */
    @Query("SELECT * FROM offline_assets WHERE (',' || used_by_article_ids || ',') LIKE ('%,' || :articleId || ',%')")
    fun getAssetsForArticleEntry(articleId: String): Flow<List<OfflineAsset>>

    /**
     * Retrieves all offline assets from the database.
     * @return A Flow emitting a list of all [OfflineAsset] items.
     */
    @Query("SELECT * FROM offline_assets ORDER BY download_timestamp DESC")
    fun getAllAssets(): Flow<List<OfflineAsset>>

    /**
     * Deletes all assets from the offline_assets table.
     * Use with caution.
     */
    @Query("DELETE FROM offline_assets")
    suspend fun clearAllAssets()
}
