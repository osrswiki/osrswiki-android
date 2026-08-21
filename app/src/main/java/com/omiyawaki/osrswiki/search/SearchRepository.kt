package com.omiyawaki.osrswiki.search

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.database.OfflinePageFtsDao
import com.omiyawaki.osrswiki.network.SearchResult
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.search.db.RecentSearch
import com.omiyawaki.osrswiki.search.db.RecentSearchDao
import com.omiyawaki.osrswiki.util.StringUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private const val DEFAULT_SEARCH_RESULTS_PAGE_SIZE = 20
private const val TAG = "SearchRepository"

/**
 * Repository for fetching search results from the OSRS Wiki API and local databases.
 */
class SearchRepository(
    private val apiService: WikiApiService,
    private val articleMetaDao: ArticleMetaDao,
    private val offlinePageFtsDao: OfflinePageFtsDao,
    private val recentSearchDao: RecentSearchDao
) {

    // --- Recent Searches ---

    fun getRecentSearches(): Flow<List<RecentSearch>> {
        return recentSearchDao.getAll().map { searches ->
            searches.map { it.copy(query = normalizeRecentSearchQuery(it.query)) }
        }
    }

    suspend fun insertRecentSearch(query: String) {
        val recentSearch = RecentSearch(
            query = normalizeRecentSearchQuery(query),
            timestamp = System.currentTimeMillis()
        )
        recentSearchDao.insert(recentSearch)
    }

    internal fun normalizeRecentSearchQuery(query: String): String =
        StringUtil.fromHtml(query).toString()
            .replace('\u00A0', ' ')
            .replace("\\s+".toRegex(), " ")
            .trim()

    suspend fun clearAllRecentSearches() {
        recentSearchDao.clearAll()
    }

    // --- Online and Offline Article Search ---

    suspend fun openSearchSuggestions(query: String): List<SearchResult> {
        val canonical = SearchQueryPolicy.apiQuery(query)
        if (canonical.isBlank()) return emptyList()
        return runCatching {
            osrsOpenSearchParser.parse(
                apiService.openSearch(canonical, 10).bytes()
            )
        }.getOrDefault(emptyList())
    }

    fun getOnlineSearchResultStream(
        query: String,
        scope: osrsSearchScope = osrsSearchScope.ALL
    ): Flow<PagingData<SearchResult>> {
        Log.d(TAG, "getOnlineSearchResultStream called with query: $query scope=$scope")
        return Pager(
            config = PagingConfig(
                pageSize = DEFAULT_SEARCH_RESULTS_PAGE_SIZE,
                prefetchDistance = DEFAULT_SEARCH_RESULTS_PAGE_SIZE / 2,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                if (scope.restrictsNamespace) {
                    osrsScopedSearchPagingSource(
                        apiService = apiService,
                        query = query,
                        scope = scope,
                        articleMetaDao = articleMetaDao
                    )
                } else {
                    SearchPagingSource(
                        apiService = apiService,
                        query = query,
                        articleMetaDao = articleMetaDao
                    )
                }
            }
        ).flow
    }

    fun searchOfflineArticlesByTitle(query: String): Flow<List<ArticleMetaEntity>> {
        val trimmedQuery = query.trim()
        Log.d(TAG, "searchOfflineArticlesByTitle called with query: $trimmedQuery")

        if (trimmedQuery.isEmpty()) {
            return flowOf(emptyList())
        }

        val searchQuery = "%$trimmedQuery%"
        return flow {
            try {
                emit(articleMetaDao.searchByTitle(searchQuery))
            } catch (e: Exception) {
                Log.e(TAG, "Error searching offline articles by title: ${e.message}", e)
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    fun searchOfflineFtsContent(query: String): Flow<List<OfflinePageFts>> {
        val trimmedQuery = query.trim()
        Log.d(TAG, "searchOfflineFtsContent called with query: $trimmedQuery")

        if (trimmedQuery.isEmpty()) {
            return flowOf(emptyList())
        }

        return flow {
            try {
                emit(offlinePageFtsDao.searchAll(trimmedQuery))
            } catch (e: Exception) {
                Log.e(TAG, "Error searching FTS offline content: ${e.message}", e)
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }
}
