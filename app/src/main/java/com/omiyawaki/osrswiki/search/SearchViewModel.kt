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
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.network.SearchResult as NetworkSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.util.Locale // For lowercase comparison

// Assuming CleanedSearchResultItem is defined in its own file and imported correctly:
// import com.omiyawaki.osrswiki.search.CleanedSearchResultItem

/**
 * UI state for the Search screen.
 */
data class SearchScreenUiState(
    val messageResId: Int? = null,
    val messageArg: String? = null,
    val showOfflineIndicator: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchRepository: SearchRepository,
    val isOnline: StateFlow<Boolean>
) : ViewModel() {

    private companion object {
        private const val TAG = "SearchViewModel" // Ensure TAG is defined for logging
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val FTS_SNIPPET_MAX_LENGTH = 160
        private const val SNIPPET_WINDOW_RADIUS = 70
    }

    private val _currentQuery = MutableStateFlow<String?>(null)
    val currentQuery: StateFlow<String?> = _currentQuery.asStateFlow()

    private val _screenUiState = MutableStateFlow(SearchScreenUiState())
    val screenUiState: StateFlow<SearchScreenUiState> = _screenUiState.asStateFlow()

    val onlineSearchResultsFlow: Flow<PagingData<CleanedSearchResultItem>> = _currentQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isNullOrBlank() || !isOnline.value) {
                flowOf(PagingData.empty())
            } else {
                Log.d(TAG, "Fetching online results for query: '$query'")
                searchRepository.getOnlineSearchResultStream(query)
                    .map { pagingData: PagingData<NetworkSearchResult> ->
                        pagingData.map { networkResult: NetworkSearchResult ->
                            mapNetworkResultToCleanedItem(networkResult)
                        }
                    }
            }
        }
        .cachedIn(viewModelScope)

    private val offlineTitleResultsFlow: Flow<List<CleanedSearchResultItem>> = _currentQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val trimmedQuery = query?.trim()
            if (trimmedQuery.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                Log.d(TAG, "Fetching offline title search for query: '$trimmedQuery'")
                searchRepository.searchOfflineArticlesByTitle(trimmedQuery)
                    .map { articleMetaList ->
                        articleMetaList.map { articleMeta ->
                            mapArticleMetaToCleanedItem(articleMeta)
                        }
                    }
            }
        }

    private val ftsResultsFlow: Flow<List<CleanedSearchResultItem>> = _currentQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val trimmedQuery = query?.trim()
            if (trimmedQuery.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                Log.d(TAG, "Fetching FTS offline content search for query: '$trimmedQuery'")
                searchRepository.searchOfflineFtsContent(trimmedQuery)
                    .map { ftsResultList ->
                        ftsResultList.map { ftsResult ->
                            mapOfflinePageFtsToCleanedItem(ftsResult, trimmedQuery) // Pass query
                        }
                    }
            }
        }

    val combinedOfflineResultsList: StateFlow<List<CleanedSearchResultItem>> =
        offlineTitleResultsFlow.combine(ftsResultsFlow) { titleResults, ftsRes ->
            val combined = mutableListOf<CleanedSearchResultItem>()
            combined.addAll(ftsRes)
            val ftsIds = ftsRes.map { it.id }.toSet()
            combined.addAll(titleResults.filterNot { ftsIds.contains(it.id) })
            Log.d(TAG, "Combined offline results: ${combined.size} items")
            combined
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            _currentQuery.combine(isOnline) { query, online -> Pair(query, online) }
                .debounce(SEARCH_DEBOUNCE_MS)
                .collect { (query, online) ->
                    val trimmedQuery = query?.trim()
                    if (trimmedQuery.isNullOrBlank()) {
                        _screenUiState.value = SearchScreenUiState(
                            messageResId = R.string.search_enter_query_prompt,
                            showOfflineIndicator = !online
                        )
                    } else {
                        _screenUiState.value = SearchScreenUiState(
                            messageArg = trimmedQuery,
                            showOfflineIndicator = !online
                        )
                    }
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
            Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY).toString()
                .replace('\n', ' ').trim()
        } ?: ""
        return CleanedSearchResultItem(
            id = networkResult.pageid.toString(),
            title = networkResult.title,
            snippet = cleanSnippet,
            isFtsResult = false
        )
    }

    private fun mapArticleMetaToCleanedItem(articleMeta: ArticleMetaEntity): CleanedSearchResultItem {
        return CleanedSearchResultItem(
            id = articleMeta.pageId.toString(),
            title = articleMeta.title,
            snippet = "Offline (Title Match)",
            isFtsResult = false
        )
    }

    private fun mapOfflinePageFtsToCleanedItem(ftsResult: OfflinePageFts, query: String?): CleanedSearchResultItem {
        val ftsUrl = ftsResult.url
        Log.d(TAG, "mapOfflinePageFtsToCleanedItem - URL: $ftsUrl, Received Query: '$query'. Setting empty snippet for now.")

        // No longer generating KWIC snippet; will be handled by ViewHolder visibility
        val displaySnippet = "" // Intentionally empty for FTS results for now

        return CleanedSearchResultItem(
            id = ftsUrl,
            title = ftsResult.title, // Title is assumed to be cleaned during indexing
            snippet = displaySnippet,
            isFtsResult = true
        )
    }
} // <<< End of SearchViewModel class

@Suppress("UNCHECKED_CAST")
class SearchViewModelFactory(
    private val application: Application,
    private val isOnlineFlow: StateFlow<Boolean>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            val osrsWikiApplication = application as? OSRSWikiApp
                ?: throw IllegalStateException("Application context must be OSRSWikiApplication")
            val repository = osrsWikiApplication.searchRepository
            return SearchViewModel(repository, isOnlineFlow) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
} // <<< End of SearchViewModelFactory class