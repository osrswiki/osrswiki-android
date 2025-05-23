package com.omiyawaki.osrswiki.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.omiyawaki.osrswiki.data.db.dao.ArticleDao
import com.omiyawaki.osrswiki.data.db.dao.ArticleFtsDao // Added import
import com.omiyawaki.osrswiki.data.model.ArticleFtsSearchResult // Added import
import com.omiyawaki.osrswiki.data.paging.SearchPagingSource
import com.omiyawaki.osrswiki.network.SearchResult
import com.omiyawaki.osrswiki.network.WikiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn // Ensure flowOn is imported for the new FTS search method

// Define a default page size for PagingConfig.
private const val DEFAULT_SEARCH_RESULTS_PAGE_SIZE = 20

/**
 * Repository for fetching search results from the OSRS Wiki API and local FTS.
 * This class abstracts the data source (network, local DB checks, local FTS) from the ViewModel.
 */
class SearchRepository(
    private val apiService: WikiApiService,
    private val articleDao: ArticleDao,
    private val articleFtsDao: ArticleFtsDao // Added ArticleFtsDao dependency
) {

    /**
     * Performs a search for articles on the OSRS Wiki using Jetpack Paging 3 (network search).
     * Results will be enhanced with offline availability status.
     *
     * @param query The search term.
     * @return A Flow of PagingData containing SearchResult items.
     */
    fun getSearchResultStream(query: String): Flow<PagingData<SearchResult>> {
        return Pager(
            config = PagingConfig(
                pageSize = DEFAULT_SEARCH_RESULTS_PAGE_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                // Pass ArticleDao to SearchPagingSource
                SearchPagingSource(apiService = apiService, query = query, articleDao = articleDao)
            }
        ).flow
    }


    /**
     * Performs a search for articles in the local FTS table.
     *
     * @param query The search term for FTS matching.
     * @return A Flow of a list of [ArticleFtsSearchResult] items.
     */
    fun searchFtsOffline(query: String): Flow<List<ArticleFtsSearchResult>> {
        // ArticleFtsDao.searchArticles returns a Flow. Room ensures this query runs
        // on a background thread. flowOn(Dispatchers.IO) is an additional safeguard
        // to ensure any subsequent collection or minor transformations in this flow chain
        // also use the IO dispatcher.
        return articleFtsDao.searchArticles(query).flowOn(Dispatchers.IO)
    }
}
