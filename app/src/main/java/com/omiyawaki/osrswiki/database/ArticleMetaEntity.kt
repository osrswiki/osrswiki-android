package com.omiyawaki.osrswiki.database // Updated package

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "article_meta",
    indices = [Index(value = ["title"], unique = false)] // Example index for faster title searches
)
@Suppress("unused")
data class ArticleMetaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

        val pageId: Int, // Wiki page ID (non-null for saved articles)
    val title: String, // Article title
    val wikiUrl: String, // Full URL to the article on the OSRS Wiki
    val localFilePath: String, // Absolute path to the downloaded content file
    val lastFetchedTimestamp: Long, // When the article was downloaded/updated
    val revisionId: Long?, // Optional: Wiki revision ID
    val categories: String? // Optional: Comma-separated list of categories, or store in a separate related table
)
