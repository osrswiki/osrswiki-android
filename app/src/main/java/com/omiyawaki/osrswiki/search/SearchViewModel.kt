package com.omiyawaki.osrswiki.search

import android.app.Application
import android.text.Html
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.data.SearchRepository
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.network.SearchResult as NetworkSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
// import kotlinx.coroutines.flow.emptyFlow // No longer needed for this specific use case
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf // <<< ADDED IMPORT
import kotlinx.coroutines.launch

/**
 * Data class representing a search result item with cleaned snippet for the UI.
 */

/**
 * UI state for the Search screen, primarily for messages not directly tied to PagingData or results.
 */
data class SearchScreenUiState(
    val messageResId: Int? = null,
    val currentQuery: String? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Suppress("unused")
class SearchViewModel(private val searchRepository: SearchRepository) : ViewModel() {

@Suppress("unused")
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
                flowOf(PagingData.empty()) // <<< MODIFIED: Emit PagingData.empty() to clear results
            } else {
                _screenUiState.value = SearchScreenUiState(messageResId = null, currentQuery = query)
                Log.d(TAG, "Fetching online results for query: '$query'")
                searchRepository.getSearchResultStream(query)
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
                    searchRepository.searchOfflineArticles(query ?: "")
                }
                .map { articleMetaList ->
                    articleMetaList.map { articleMeta ->
                        mapArticleMetaToCleanedItem(articleMeta)
                    }
                }
                .collect { results ->
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

    private fun mapArticleMetaToCleanedItem(articleMeta: ArticleMetaEntity): CleanedSearchResultItem {
        return CleanedSearchResultItem(
            id = articleMeta.pageId.toString(),
            title = articleMeta.title,
            snippet = ""
        )
    }
}

@Suppress("unused")
class SearchViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            val osrsWikiApplication = application as? OSRSWikiApp
                ?: throw IllegalStateException("Application context must be OSRSWikiApplication")
            val repository = osrsWikiApplication.searchRepository
            return SearchViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
