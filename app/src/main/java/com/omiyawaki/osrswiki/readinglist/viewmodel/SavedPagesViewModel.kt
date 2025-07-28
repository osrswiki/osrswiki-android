package com.omiyawaki.osrswiki.readinglist.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.repository.SavedPagesRepository
// import dagger.hilt.android.lifecycle.HiltViewModel // Removed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    
    // Context needed for deleting offline objects
    private var applicationContext: android.content.Context? = null
    
    fun setApplicationContext(context: android.content.Context) {
        applicationContext = context.applicationContext
    }

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
        Log.d(TAG, "searchSavedPages: Called with query='$query'")
        
        if (query.isBlank()) {
            Log.d(TAG, "searchSavedPages: Query is blank, clearing results")
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "searchSavedPages: Starting search in coroutine")
            _isSearching.value = true
            try {
                val trimmedQuery = query.trim()
                Log.d(TAG, "searchSavedPages: Calling repository with trimmed query='$trimmedQuery'")
                val results = withContext(Dispatchers.IO) {
                    savedPagesRepository.searchSavedPages(trimmedQuery)
                }
                Log.d(TAG, "searchSavedPages: Repository returned ${results.size} results")
                _searchResults.value = results
            } catch (e: Exception) {
                Log.e(TAG, "searchSavedPages: Search error", e)
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
                Log.d(TAG, "searchSavedPages: Search completed")
            }
        }
    }

    /**
     * Clears the current search results.
     */
    fun clearSearchResults() {
        Log.d(TAG, "clearSearchResults: Clearing search results")
        _searchResults.value = emptyList()
    }

    /**
     * Deletes a single saved page including its offline data.
     */
    fun deleteSavedPage(page: ReadingListPage) {
        Log.d(TAG, "deleteSavedPage: Deleting page '${page.displayTitle}'")
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = applicationContext 
                    ?: throw IllegalStateException("Application context not set")
                savedPagesRepository.deleteSavedPage(page, context)
                Log.d(TAG, "deleteSavedPage: Successfully deleted page '${page.displayTitle}'")
            } catch (e: Exception) {
                Log.e(TAG, "deleteSavedPage: Error deleting page '${page.displayTitle}'", e)
            }
        }
    }

    companion object {
        private const val TAG = "SavedPagesViewModel"
    }
}