package com.omiyawaki.osrswiki.offline.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a saved OSRS Wiki article's metadata in the local database.
 * This entity stores information necessary for displaying and searching saved articles.
 */
@Entity(
    tableName = "saved_article_entries",
    indices = [Index(value = ["normalized_article_title"])]
)
data class SavedArticleEntry(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,

    @ColumnInfo(name = "article_title")
    var articleTitle: String,

    // Normalized title for accent-insensitive and case-insensitive searching.
    // This should be derived from articleTitle when an entry is created or updated.
    @ColumnInfo(name = "normalized_article_title")
    var normalizedArticleTitle: String,

    // An optional short description or snippet from the article.
    // Can be used for display in search results or as additional search context.
    @ColumnInfo(name = "snippet")
    var snippet: String? = null,

    // Timestamp of when the article was saved, in milliseconds since epoch.
    // Defaults to the current time when the object is instantiated.
    @ColumnInfo(name = "timestamp")
    var timestamp: Long = System.currentTimeMillis()
) {
    // It's good practice to have a constructor for when Room instantiates the object,
    // and another for when the application creates a new entry (where ID might not be set yet).
    // However, with default values and var properties, a single primary constructor
    // often suffices unless more complex logic is needed during construction.

    // Example of how normalizedArticleTitle might be set (logic would be outside the entity,
    // typically where the entity instance is created before being saved to the database).
    // constructor(
    //     articleTitle: String,
    //     snippet: String? = null,
    //     // id and timestamp have defaults
    // ) : this(
    //     articleTitle = articleTitle,
    //     normalizedArticleTitle = normalizeTitle(articleTitle), // Assuming a utility function
    //     snippet = snippet
    // )
}

