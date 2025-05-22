package com.omiyawaki.osrswiki.ui.search

import android.app.Application // Added for Application context in Factory
import android.text.Html
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.omiyawaki.osrswiki.OSRSWikiApplication // Added for casting Application to access repositories
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.data.SearchRepository
// RetrofitClient and WikiApiService imports removed as factory no longer instantiates them
import com.omiyawaki.osrswiki.network.SearchResult as NetworkSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Data class representing a search result item with cleaned snippet for the UI.
 */
data class CleanedSearchResultItem(
    val id: String, // pageid as String
    val title: String,
    val snippet: String // Cleaned snippet
)

/**
 * UI state for the Search screen, primarily for messages not directly tied to PagingData.
 * PagingData loading/error states will be observed from PagingDataAdapter.loadStateFlow.
 */
data class SearchScreenUiState(
    val messageResId: Int? = null,
    val currentQuery: String? = null // To reflect the active search query in UI if needed
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val searchRepository: SearchRepository) : ViewModel() {

    private companion object {
        private const val TAG = "SearchViewModel"
        private const val SEARCH_DEBOUNCE_MS = 300L // Debounce time for search queries
    }

    private val _currentQuery = MutableStateFlow<String?>(null)

    // UI state for messages or general screen state (e.g. initial prompt)
    private val _screenUiState = MutableStateFlow(SearchScreenUiState(messageResId = R.string.search_enter_query_prompt))
    val screenUiState: StateFlow<SearchScreenUiState> = _screenUiState.asStateFlow()

    val searchResultsFlow: Flow<PagingData<CleanedSearchResultItem>> = _currentQuery
        .debounce(SEARCH_DEBOUNCE_MS) // Add debounce to avoid too many API calls while typing
        .flatMapLatest { query ->
            if (query.isNullOrBlank()) {
                _screenUiState.value = SearchScreenUiState(messageResId = R.string.search_enter_query_prompt, currentQuery = query)
                emptyFlow() // Emit PagingData.empty() or just emptyFlow if adapter handles null PagingData
            } else {
                _screenUiState.value = SearchScreenUiState(messageResId = null, currentQuery = query) // Clear message, search is active
                Log.d(TAG, "Fetching results for query: ''")
                searchRepository.getSearchResultStream(query)
                    .map { pagingData: PagingData<NetworkSearchResult> ->
                        pagingData.map { networkResult ->
                            mapNetworkResultToCleanedItem(networkResult)
                        }
                    }
            }
        }
        .cachedIn(viewModelScope) // Cache the PagingData in viewModelScope

    /**
     * Called by the UI to initiate or update a search.
     */
    fun performSearch(query: String) {
        val trimmedQuery = query.trim()
        if (_currentQuery.value != trimmedQuery) { // Only update if query actually changed
            Log.d(TAG, "Search query submitted: ''")
            _currentQuery.value = trimmedQuery
        }
    }

    /**
     * Maps a network SearchResult to a CleanedSearchResultItem, including snippet cleaning.
     */
    private fun mapNetworkResultToCleanedItem(networkResult: NetworkSearchResult): CleanedSearchResultItem {
        val rawSnippet = networkResult.snippet
        val cleanSnippet = rawSnippet?.let { htmlContent ->
            val afterFromHtml = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY).toString()
            val afterNewlineReplace = afterFromHtml.replace('\n', ' ')
            val finalSnippet = afterNewlineReplace.trim()
            finalSnippet
        } ?: ""

        return CleanedSearchResultItem(
            id = networkResult.pageid.toString(), // pageid is Int from network.SearchResult
            title = networkResult.title,
            snippet = cleanSnippet
        )
    }
}

// Updated ViewModelFactory to accept Application and use dependencies from OSRSWikiApplication
class SearchViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            // Ensure the application is OSRSWikiApplication to access custom properties
            val osrsWikiApplication = application as? OSRSWikiApplication
                ?: throw IllegalStateException("Application context must be OSRSWikiApplication")

            // Retrieve the singleton SearchRepository from the Application class
            val repository = osrsWikiApplication.searchRepository
            return SearchViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
