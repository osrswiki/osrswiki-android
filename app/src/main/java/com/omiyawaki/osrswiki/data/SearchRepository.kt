package com.omiyawaki.osrswiki.data

import android.util.Log // Added import for Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.omiyawaki.osrswiki.data.db.dao.ArticleMetaDao
import com.omiyawaki.osrswiki.data.db.entity.ArticleMetaEntity
import com.omiyawaki.osrswiki.data.paging.SearchPagingSource
import com.omiyawaki.osrswiki.network.SearchResult
import com.omiyawaki.osrswiki.network.WikiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow // Added import for flow builder
import kotlinx.coroutines.flow.flowOf // Added import for flowOf
import kotlinx.coroutines.flow.flowOn
// Removed: import kotlinx.coroutines.flow.map (if not used elsewhere)

// Define a default page size for PagingConfig.
private const val DEFAULT_SEARCH_RESULTS_PAGE_SIZE = 20
private const val TAG = "SearchRepository" // Added TAG for logging

/**
 * Repository for fetching search results from the OSRS Wiki API and local database.
 * This class abstracts the data source (network, local DB metadata search) from the ViewModel.
 */
class SearchRepository(
    private val apiService: WikiApiService,
    private val articleMetaDao: ArticleMetaDao
) {

    /**
     * Performs a search for articles on the OSRS Wiki using Jetpack Paging 3 (network search).
     * Results will be enhanced with offline availability status.
     *
     * @param query The search term.
     * @return A Flow of PagingData containing SearchResult items.
     */
    fun getSearchResultStream(query: String): Flow<PagingData<SearchResult>> {
        Log.d(TAG, "getSearchResultStream called with query: $query")
        return Pager(
            config = PagingConfig(
                pageSize = DEFAULT_SEARCH_RESULTS_PAGE_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                SearchPagingSource(apiService = apiService, query = query, articleMetaDao = articleMetaDao)
            }
        ).flow
    }

    /**
     * Performs a search for locally saved articles by their titles using a LIKE query.
     *
     * @param query The user-provided search term.
     * @return A Flow of a list of matching [ArticleMetaEntity] items.
     */
    fun searchOfflineArticles(query: String): Flow<List<ArticleMetaEntity>> {
        val trimmedQuery = query.trim()
        Log.d(TAG, "searchOfflineArticles called with query: $trimmedQuery")

        if (trimmedQuery.isEmpty()) {
            Log.d(TAG, "Search query is empty, returning empty list.")
            return flowOf(emptyList()) // Return a flow emitting an empty list
        }

        val searchQuery = "%$trimmedQuery%"
        return flow {
            try {
                Log.d(TAG, "Executing offline search with LIKE query: $searchQuery")
                val results = articleMetaDao.searchByTitle(searchQuery)
                Log.d(TAG, "Offline search returned ${results.size} results.")
                emit(results)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching offline articles: ${e.message}", e)
                emit(emptyList()) // Emit an empty list in case of error
            }
        }.flowOn(Dispatchers.IO) // Ensure database access is on the IO dispatcher
    }
}