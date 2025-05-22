package com.omiyawaki.osrswiki.ui.search

import android.app.Application // Added for Application context in Factory
import android.os.Build
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
import com.omiyawaki.osrswiki.data.model.ArticleFtsSearchResult // Added import for FTS result type
import com.omiyawaki.osrswiki.network.SearchResult as NetworkSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch // Added import for error handling in flows
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch // Added import for viewModelScope.launch if not implicitly available

/**
 * Data class representing a search result item with cleaned snippet for the UI.
 * This can be used for both network and FTS results.
 */
data class CleanedSearchResultItem(
    val id: String, // pageid as String
    val title: String,
    val snippet: String // Cleaned snippet
)

/**
 * UI state for the Search screen, primarily for messages not directly tied to PagingData or FTS results.
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

    // Flow for online search results (PagingData)
    val searchResultsFlow: Flow<PagingData<CleanedSearchResultItem>> = _currentQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { query ->
            if (query.isNullOrBlank()) {
                _screenUiState.value = SearchScreenUiState(messageResId = R.string.search_enter_query_prompt, currentQuery = query)
                emptyFlow()
            } else {
                _screenUiState.value = SearchScreenUiState(messageResId = null, currentQuery = query)
                Log.d(TAG, "Fetching online results for query: '$query'") // Fixed log
                searchRepository.getSearchResultStream(query)
                    .map { pagingData: PagingData<NetworkSearchResult> ->
                        pagingData.map { networkResult ->
                            mapNetworkResultToCleanedItem(networkResult)
                        }
                    }
            }
        }
        .cachedIn(viewModelScope)

    // StateFlow for offline FTS search results (List)
    private val _ftsSearchResults = MutableStateFlow<List<CleanedSearchResultItem>>(emptyList())
    val ftsSearchResults: StateFlow<List<CleanedSearchResultItem>> = _ftsSearchResults.asStateFlow()

    init {
        viewModelScope.launch {
            _currentQuery
                .debounce(SEARCH_DEBOUNCE_MS) // Debounce FTS search as well
                .flatMapLatest { query ->
                    if (query.isNullOrBlank()) {
                        // When query is blank, emit an empty list for FTS results immediately
                        // or could emit a special state if needed.
                        MutableStateFlow(emptyList<ArticleFtsSearchResult>())
                    } else {
                        Log.d(TAG, "Fetching FTS results for query: '$query'")
                        searchRepository.searchFtsOffline(query)
                            .catch { e ->
                                Log.e(TAG, "Error fetching FTS results for query '$query': ${e.message}", e)
                                emit(emptyList<ArticleFtsSearchResult>()) // Emit empty list on error
                            }
                    }
                }
                .map { ftsResultList ->
                    ftsResultList.map { ftsResult -> mapFtsResultToCleanedItem(ftsResult) }
                }
                .collect { cleanedFtsResults ->
                    _ftsSearchResults.value = cleanedFtsResults
                    Log.d(TAG, "FTS results updated: ${cleanedFtsResults.size} items for query: '${_currentQuery.value}'")
                }
        }
    }

    /**
     * Called by the UI to initiate or update a search.
     * This will trigger both online and FTS searches via the _currentQuery StateFlow.
     */
    fun performSearch(query: String) {
        val trimmedQuery = query.trim()
        // No need to check if _currentQuery.value != trimmedQuery here,
        // as StateFlow handles distinct emissions. If it's the same, subscribers won't be re-triggered excessively.
        // Debounce in the collectors will handle rapid changes.
        Log.d(TAG, "Search query submitted: '$trimmedQuery'") // Fixed log
        _currentQuery.value = trimmedQuery
    }

    /**
     * Maps a network SearchResult to a CleanedSearchResultItem.
     */
    private fun mapNetworkResultToCleanedItem(networkResult: NetworkSearchResult): CleanedSearchResultItem {
        val rawSnippet = networkResult.snippet
        val cleanSnippet = rawSnippet?.let { htmlContent ->
            val afterFromHtml = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(htmlContent).toString()
            }
            val afterNewlineReplace = afterFromHtml.replace('\n', ' ')
            val finalSnippet = afterNewlineReplace.trim()
            finalSnippet
        } ?: ""

        return CleanedSearchResultItem(
            id = networkResult.pageid.toString(),
            title = networkResult.title,
            snippet = cleanSnippet
        )
    }

    /**
     * Maps an ArticleFtsSearchResult to a CleanedSearchResultItem.
     * FTS snippets may contain <b> tags for highlighting.
     */
    private fun mapFtsResultToCleanedItem(ftsResult: ArticleFtsSearchResult): CleanedSearchResultItem {
        // The FTS snippet contains <b> tags which Html.fromHtml can parse.
        val cleanSnippet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(ftsResult.snippet, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(ftsResult.snippet).toString()
        }
        return CleanedSearchResultItem(
            id = ftsResult.pageId.toString(),
            title = ftsResult.title,
            snippet = cleanSnippet.replace('\n', ' ').trim()
        )
    }
}

// Updated ViewModelFactory to accept Application and use dependencies from OSRSWikiApplication
class SearchViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            val osrsWikiApplication = application as? OSRSWikiApplication
                ?: throw IllegalStateException("Application context must be OSRSWikiApplication")
            val repository = osrsWikiApplication.searchRepository
            return SearchViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
