package com.omiyawaki.osrswiki.search

import android.app.Application
import android.text.Html
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.network.SearchResult as NetworkSearchResult
import com.omiyawaki.osrswiki.page.DownloadProgress
import com.omiyawaki.osrswiki.page.PageAssetDownloader
import com.omiyawaki.osrswiki.page.PageHtmlBuilder
import com.omiyawaki.osrswiki.page.preemptive.PreloadedPage
import com.omiyawaki.osrswiki.page.preemptive.PreloadedPageCache
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.util.StringUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchRepository: SearchRepository,
    val isOnline: StateFlow<Boolean>,
    private val pageAssetDownloader: PageAssetDownloader,
    private val pageHtmlBuilder: PageHtmlBuilder
) : ViewModel() {

    private val _currentQuery = MutableStateFlow<String?>(null)
    val currentQuery: StateFlow<String?> = _currentQuery.asStateFlow()

    private var preemptiveLoadJob: Job? = null

    val onlineSearchResultsFlow: Flow<PagingData<CleanedSearchResultItem>> = _currentQuery
        .debounce(300L)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isNullOrBlank() || !isOnline.value) {
                flowOf(PagingData.empty())
            } else {
                searchRepository.getOnlineSearchResultStream(query)
                    .map { pagingData ->
                        pagingData.map { networkResult ->
                            mapNetworkResultToCleanedItem(networkResult)
                        }
                    }
            }
        }
        .cachedIn(viewModelScope)

    val combinedOfflineResultsList: StateFlow<List<CleanedSearchResultItem>> = _currentQuery
        .debounce(300L)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val trimmedQuery = query?.trim()
            if (trimmedQuery.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                searchRepository.searchOfflineArticlesByTitle(trimmedQuery).combine(
                    searchRepository.searchOfflineFtsContent(trimmedQuery)
                ) { titleResults, ftsResults ->
                    val ftsItems = ftsResults.map { mapOfflinePageFtsToCleanedItem(it) }
                    val titleItems = titleResults.map { mapArticleMetaToCleanedItem(it) }
                    val ftsIds = ftsItems.map { it.id }.toSet()
                    (ftsItems + titleItems.filterNot { ftsIds.contains(it.id) })
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun performSearch(query: String) {
        preemptiveLoadJob?.cancel()
        PreloadedPageCache.clear()
        _currentQuery.value = query.trim()
    }

    fun saveCurrentQuery() {
        val queryToSave = _currentQuery.value
        if (!queryToSave.isNullOrBlank()) {
            viewModelScope.launch {
                searchRepository.insertRecentSearch(queryToSave)
            }
        }
    }

    fun preemptivelyLoadTopResult(item: CleanedSearchResultItem) {
        val pageId = item.id.toIntOrNull() ?: return
        if (preemptiveLoadJob?.isActive == true) return

        preemptiveLoadJob = viewModelScope.launch {
            // Use page ID URL for downloading, but will be converted to canonical format later
            val pageUrl = "https://oldschool.runescape.wiki/?curid=$pageId"
            pageAssetDownloader.downloadPriorityAssets(pageId, pageUrl)
                .collect { progress ->
                    if (progress is DownloadProgress.Success) {
                        val result = progress.result
                        val collapseTablesEnabled = Prefs.isCollapseTablesEnabled
                        val finalHtml = pageHtmlBuilder.buildFullHtmlDocument(
                            result.parseResult.displaytitle ?: "",
                            result.processedHtml,
                            OSRSWikiApp.instance.getCurrentTheme(),
                            collapseTablesEnabled
                        )
                        // Use canonical URL format for consistency
                        val canonicalUrl = "https://oldschool.runescape.wiki/w/${result.parseResult.title.replace(" ", "_")}"
                        PreloadedPageCache.put(PreloadedPage(
                            pageId = result.parseResult.pageid,
                            finalHtml = finalHtml,
                            plainTextTitle = result.parseResult.title,
                            displayTitle = result.parseResult.displaytitle,
                            wikiUrl = canonicalUrl,
                            revisionId = result.parseResult.revid,
                            lastFetchedTimestamp = System.currentTimeMillis()
                        ))
                    }
                }
        }
    }

    private fun mapNetworkResultToCleanedItem(networkResult: NetworkSearchResult): CleanedSearchResultItem {
        val cleanTitle = StringUtil.extractMainTitle(networkResult.title)
        val cleanSnippet = networkResult.snippet?.let { snippet ->
            // Preserve HTML highlighting tags for search term highlighting
            if (snippet.contains("searchmatch")) {
                // Keep HTML tags for search highlighting, just clean up whitespace
                snippet.trim()
                    .replace('\u00A0', ' ') // Replace non-breaking spaces with regular spaces  
                    .replace("\\s+".toRegex(), " ") // Replace multiple whitespace with single space
                    .trim() // Final trim after cleanup
            } else {
                // Strip HTML for non-highlighted snippets (legacy behavior)
                StringUtil.fromHtml(snippet).toString()
                    .trim()
                    .replace('\u00A0', ' ') // Replace non-breaking spaces with regular spaces
                    .replace("\\s+".toRegex(), " ") // Replace multiple whitespace with single space
                    .trim() // Final trim after cleanup
            }
        } ?: ""
        return CleanedSearchResultItem(
            id = networkResult.pageid.toString(),
            title = cleanTitle,
            snippet = cleanSnippet,
            thumbnailUrl = networkResult.thumbnailUrl, // Pass the URL from the network result.
            isFtsResult = false
        )
    }

    private fun mapArticleMetaToCleanedItem(articleMeta: ArticleMetaEntity): CleanedSearchResultItem {
        return CleanedSearchResultItem(
            id = articleMeta.pageId.toString(),
            title = articleMeta.title,
            snippet = "Offline (Title Match)",
            thumbnailUrl = null, // Offline title matches have no thumbnail.
            isFtsResult = false
        )
    }

    private fun mapOfflinePageFtsToCleanedItem(ftsResult: OfflinePageFts): CleanedSearchResultItem {
        return CleanedSearchResultItem(
            id = ftsResult.url,
            title = ftsResult.title,
            snippet = "",
            thumbnailUrl = null, // FTS results have no thumbnail.
            isFtsResult = true
        )
    }
}

@Suppress("UNCHECKED_CAST")
class SearchViewModelFactory(
    private val application: Application,
    private val isOnlineFlow: StateFlow<Boolean>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            val osrsWikiApplication = application as? OSRSWikiApp ?: throw IllegalStateException("Application context must be OSRSWikiApplication")
            return SearchViewModel(
                osrsWikiApplication.searchRepository,
                isOnlineFlow,
                osrsWikiApplication.pageAssetDownloader,
                osrsWikiApplication.pageHtmlBuilder
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
