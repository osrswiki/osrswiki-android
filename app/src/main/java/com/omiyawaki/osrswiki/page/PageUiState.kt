package com.omiyawaki.osrswiki.page

data class PageUiState(
    val isLoading: Boolean = true,
    val progress: Int? = null,
    val progressText: String? = null,
    val error: String? = null,
    val imageUrl: String? = null, // Expected by ViewModel/Fragment

    val pageId: Int? = null,   // Nullable for initial/error states
    val title: String? = null,    // This can contain HTML and is used for display (e.g., in WebView headers)
    val plainTextTitle: String? = null, // Guaranteed plain text version of the title for logic and API calls
    val htmlContent: String? = null, // Nullable for initial/error states

    val wikiUrl: String? = null,
    val revisionId: Long? = null,
    val lastFetchedTimestamp: Long? = null,
    val localFilePath: String? = null,
    val isCurrentlyOffline: Boolean = false,
    val isDirectLoading: Boolean = false  // Flag to indicate direct wiki page loading vs HTML building
)
