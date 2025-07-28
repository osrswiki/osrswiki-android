package com.omiyawaki.osrswiki.readinglist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.repository.SavedPagesRepository
// import dagger.hilt.android.lifecycle.HiltViewModel // Removed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
// import javax.inject.Inject // Removed

/**
 * ViewModel for the Saved Pages screen.
 * Retrieves and exposes the list of fully saved pages for offline viewing.
 * Also supports searching through saved pages.
 */
// @HiltViewModel // Removed
class SavedPagesViewModel constructor( // @Inject removed from constructor
    private val savedPagesRepository: SavedPagesRepository
) : ViewModel() {

    val savedPages: StateFlow<List<ReadingListPage>> =
        savedPagesRepository.getFullySavedPages()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    // Search results state
    private val _searchResults = MutableStateFlow<List<ReadingListPage>>(emptyList())
    val searchResults: StateFlow<List<ReadingListPage>> = _searchResults

    // Search loading state
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    /**
     * Searches through saved pages using the combined search functionality
     * (both title and content search).
     */
    fun searchSavedPages(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            try {
                val results = savedPagesRepository.searchSavedPages(query.trim())
                _searchResults.value = results
            } catch (e: Exception) {
                // Handle search error - could log or expose error state
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * Clears the current search results.
     */
    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }
}