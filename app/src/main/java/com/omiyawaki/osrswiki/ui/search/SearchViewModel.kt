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
import com.omiyawaki.osrswiki.data.SearchRepository // MODIFIED: Import the correct SearchRepository
import com.omiyawaki.osrswiki.data.db.entity.ArticleMetaEntity // Added import for mapping
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
import kotlinx.coroutines.flow.map // map is used for PagingData and List transformation
import kotlinx.coroutines.launch

/**
 * Data class representing a search result item with cleaned snippet for the UI.
 */
data class CleanedSearchResultItem(
    val id: String, // pageid as String
    val title: String,
    val snippet: String // Cleaned snippet
)

/**
 * UI state for the Search screen, primarily for messages not directly tied to PagingData or results.
 */
data class SearchScreenUiState(
    val messageResId: Int? = null,
    val currentQuery: String? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val searchRepository: SearchRepository) : ViewModel() { // Type here will now match

    private companion object {
        private const val TAG = "SearchViewModel"
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    private val _currentQuery = MutableStateFlow<String?>(null)

    private val _screenUiState = MutableStateFlow(SearchScreenUiState(messageResId = R.string.search_enter_query_prompt))
    val screenUiState: StateFlow<SearchScreenUiState> = _screenUiState.asStateFlow()

    val searchResultsFlow: Flow<PagingData<CleanedSearchResultItem>> = _currentQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { query ->
            if (query.isNullOrBlank()) {
                _screenUiState.value = SearchScreenUiState(messageResId = R.string.search_enter_query_prompt, currentQuery = query)
                emptyFlow()
            } else {
                _screenUiState.value = SearchScreenUiState(messageResId = null, currentQuery = query)
                Log.d(TAG, "Fetching online results for query: '$query'")
                searchRepository.getSearchResultStream(query) // This is from data.SearchRepository
                    .map { pagingData: PagingData<NetworkSearchResult> ->
                        pagingData.map { networkResult ->
                            mapNetworkResultToCleanedItem(networkResult)
                        }
                    }
            }
        }
        .cachedIn(viewModelScope)

    private val _offlineSearchResults = MutableStateFlow<List<CleanedSearchResultItem>>(emptyList())
    val offlineSearchResults: StateFlow<List<CleanedSearchResultItem>> = _offlineSearchResults.asStateFlow()

    init {
        viewModelScope.launch {
            _currentQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { query ->
                    Log.d(TAG, "Preparing for offline title search for query: '$query'")
                    // MODIFIED: Call searchOfflineArticles and expect Flow<List<ArticleMetaEntity>>
                    searchRepository.searchOfflineArticles(query ?: "")
                }
                .map { articleMetaList -> // MODIFIED: Add mapping step
                    articleMetaList.map { articleMeta ->
                        mapArticleMetaToCleanedItem(articleMeta)
                    }
                }
                .collect { results -> // results is now List<CleanedSearchResultItem>
                    _offlineSearchResults.value = results
                    Log.d(TAG, "Offline title search results updated: ${results.size} items for query: '${_currentQuery.value}'")
                }
        }
    }

    fun performSearch(query: String) {
        val trimmedQuery = query.trim()
        Log.d(TAG, "Search query submitted: '$trimmedQuery'")
        _currentQuery.value = trimmedQuery
    }

    private fun mapNetworkResultToCleanedItem(networkResult: NetworkSearchResult): CleanedSearchResultItem {
        val rawSnippet = networkResult.snippet
        val cleanSnippet = rawSnippet?.let { htmlContent ->
              val afterFromHtml = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY).toString()
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

    // ADDED: Helper function to map ArticleMetaEntity to CleanedSearchResultItem
    private fun mapArticleMetaToCleanedItem(articleMeta: ArticleMetaEntity): CleanedSearchResultItem {
        return CleanedSearchResultItem(
            id = articleMeta.pageId.toString(), // Assuming pageId is non-null Int in ArticleMetaEntity
            title = articleMeta.title,
            snippet = "" // No snippet from metadata-only search
        )
    }
}

// ViewModelFactory remains the same, but the 'repository' it passes will now match SearchViewModel's expectation
class SearchViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            val osrsWikiApplication = application as? OSRSWikiApplication
                ?: throw IllegalStateException("Application context must be OSRSWikiApplication")
            val repository = osrsWikiApplication.searchRepository // This now provides com.omiyawaki.osrswiki.data.SearchRepository
            return SearchViewModel(repository) as T // This should now match
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}