package com.omiyawaki.osrswiki.database // Updated package

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a downloaded offline asset (e.g., HTML file, image) associated with one or more saved articles.
 * This entity facilitates de-duplication of assets; a single asset can be referenced by multiple
 * SavedArticleEntry items if they share the same original URL.
 */
@Entity(
    tableName = "offline_assets",
    indices = [Index(value = ["original_url"], unique = true)]
)
data class OfflineAsset(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,

    // The original web URL of the asset. Indexed and unique to prevent duplicates.
    @ColumnInfo(name = "original_url")
    val originalUrl: String,

    // The local file system path where the asset is stored.
    @ColumnInfo(name = "local_file_path")
    var localFilePath: String,

    // A comma-separated string of SavedArticleEntry IDs that use this asset.
    // E.g., "1,5,23". This allows many-to-many relationship simulation.
    // Management of this string (adding/removing IDs) will be handled by the repository/use cases.
    @ColumnInfo(name = "used_by_article_ids")
    var usedByArticleIds: String,

    // Timestamp of when the asset was downloaded or last verified, in milliseconds since epoch.
    @ColumnInfo(name = "download_timestamp")
    var downloadTimestamp: Long = System.currentTimeMillis()
)
