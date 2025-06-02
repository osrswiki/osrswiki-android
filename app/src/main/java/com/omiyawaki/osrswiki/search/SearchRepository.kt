package com.omiyawaki.osrswiki.search

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.database.OfflinePageFts // <<< New import
import com.omiyawaki.osrswiki.database.OfflinePageFtsDao // <<< New import
import com.omiyawaki.osrswiki.network.SearchResult
import com.omiyawaki.osrswiki.network.WikiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

private const val DEFAULT_SEARCH_RESULTS_PAGE_SIZE = 20
private const val TAG = "SearchRepository"

/**
 * Repository for fetching search results from the OSRS Wiki API, local database metadata,
 * and the new offline Full-Text Search index.
 */
class SearchRepository(
    private val apiService: WikiApiService,
    private val articleMetaDao: ArticleMetaDao,
    private val offlinePageFtsDao: OfflinePageFtsDao // <<< ADDED: DAO for FTS
) {

    /**
     * Performs an online search for articles on the OSRS Wiki using Jetpack Paging 3.
     */
    fun getOnlineSearchResultStream(query: String): Flow<PagingData<SearchResult>> { // Renamed for clarity
        Log.d(TAG, "getOnlineSearchResultStream called with query: $query")
        return Pager(
            config = PagingConfig(
                pageSize = DEFAULT_SEARCH_RESULTS_PAGE_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                SearchPagingSource(
                    apiService = apiService,
                    query = query,
                    articleMetaDao = articleMetaDao // SearchPagingSource might also benefit from FTS info if you want to mark online results that are also FTS-indexed
                )
            }
        ).flow
    }

    /**
     * Performs a metadata search for locally saved articles by their titles using a LIKE query.
     * (This is your existing offline search based on ArticleMetaEntity)
     */
    fun searchOfflineArticlesByTitle(query: String): Flow<List<ArticleMetaEntity>> { // Renamed for clarity
        val trimmedQuery = query.trim()
        Log.d(TAG, "searchOfflineArticlesByTitle called with query: $trimmedQuery")

        if (trimmedQuery.isEmpty()) {
            Log.d(TAG, "Search query is empty, returning empty list for title search.")
            return flowOf(emptyList())
        }

        val searchQuery = "%$trimmedQuery%"
        return flow {
            try {
                Log.d(TAG, "Executing offline title search with LIKE query: $searchQuery")
                // Assuming articleMetaDao.searchByTitle is suspend or you want to wrap it
                val results = articleMetaDao.searchByTitle(searchQuery)
                Log.d(TAG, "Offline title search returned ${results.size} results.")
                emit(results)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching offline articles by title: ${e.message}", e)
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Performs a Full-Text Search (FTS) on locally saved offline page content.
     *
     * @param query The user-provided search term.
     * @return A Flow of a list of matching [OfflinePageFts] items.
     */
    fun searchOfflineFtsContent(query: String): Flow<List<OfflinePageFts>> {
        val trimmedQuery = query.trim()
        Log.d(TAG, "searchOfflineFtsContent called with query: $trimmedQuery")

        if (trimmedQuery.isEmpty()) {
            Log.d(TAG, "FTS query is empty, returning empty list.")
            return flowOf(emptyList())
        }

        // For FTS, the query usually doesn't need explicit '%' wildcards,
        // but you can add them if your FTS tokenizer/query syntax benefits from it.
        // SQLite FTS MATCH operator handles prefix searches, phrase searches etc.
        // We might need to append '*' for prefix matching if not default: e.g., "$trimmedQuery*"
        // For now, let's pass the trimmed query directly.
        return flow {
            try {
                Log.d(TAG, "Executing FTS offline content search with query: $trimmedQuery")
                val results = offlinePageFtsDao.searchAll(trimmedQuery) // Using the FTS DAO
                Log.d(TAG, "FTS offline content search returned ${results.size} results.")
                emit(results)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching FTS offline content: ${e.message}", e)
                emit(emptyList()) // Emit an empty list in case of error
            }
        }.flowOn(Dispatchers.IO) // Ensure database access is on the IO dispatcher
    }
}