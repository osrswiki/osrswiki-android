package com.omiyawaki.osrswiki.search

/**
 * Data class representing a search result item for the UI.
 */
data class CleanedSearchResultItem(
    val id: String, // pageid as String
    val title: String,
    val snippet: String,
    val thumbnailUrl: String?, // Add nullable field for the image URL.
    val isFtsResult: Boolean = false
)
