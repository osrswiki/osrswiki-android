package com.omiyawaki.osrswiki.page // Ensure this package is correct

/**
 * A Plain Old Kotlin Object (POKO) to hold the UI state for the article page.
 * It is directly instantiated by PageFragment and updated by PageContentLoader.
 */
class PageViewModel {
    // This will hold the current state of the article page.
    // Initialized with isLoading = true as per PageUiState's default.
    var uiState: PageUiState = PageUiState()
        // If reactive updates are needed directly from the ViewModel in the future,
        // this could be changed to a MutableStateFlow. For now, direct mutation
        // by the loader and manual UI update by the Fragment is closer to Wikipedia's pattern.
}
