package com.omiyawaki.osrswiki.ui.article

data class ArticleUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val imageUrl: String? = null, // Expected by ViewModel/Fragment

    val pageId: Int? = null,     // Nullable for initial/error states
    val title: String? = null,    // Nullable for initial/error states
    val htmlContent: String? = null, // Nullable for initial/error states

    val wikiUrl: String? = null,
    val revisionId: Long? = null,
    val lastFetchedTimestamp: Long? = null,
    val localFilePath: String? = null,
    val isCurrentlyOffline: Boolean = false
)
