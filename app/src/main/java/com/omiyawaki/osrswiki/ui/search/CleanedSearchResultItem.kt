package com.omiyawaki.osrswiki.ui.search

/**
 * Data class representing a search result item with cleaned snippet for the UI.
 */
data class CleanedSearchResultItem(
    val id: String, // pageid as String
    val title: String,
    val snippet: String // Cleaned snippet
)
