package com.omiyawaki.osrswiki.ui.search

data class SearchResultItem(
    val id: String, // A unique ID for the item, e.g., pageId from the API
    val title: String,
    val snippet: String? // A short description or snippet of the article content
)
