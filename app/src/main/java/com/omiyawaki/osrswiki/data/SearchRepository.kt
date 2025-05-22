package com.omiyawaki.osrswiki.data // Package: com.omiyawaki.osrswiki.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.omiyawaki.osrswiki.data.paging.SearchPagingSource // Your PagingSource
import com.omiyawaki.osrswiki.network.SearchApiResponse // Keep if existing method is used
import com.omiyawaki.osrswiki.network.SearchResult // DTO for PagingData
import com.omiyawaki.osrswiki.network.WikiApiService
import kotlinx.coroutines.Dispatchers // Keep if existing method is used
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext // Keep if existing method is used

// Define a default page size for PagingConfig.
// This should align with what your UI expects or what is efficient for the API.
private const val DEFAULT_SEARCH_RESULTS_PAGE_SIZE = 20

/**
 * Repository for fetching search results from the OSRS Wiki API.
 * This class abstracts the data source (network) from the ViewModel.
 */
class SearchRepository(private val apiService: WikiApiService) {

    /**
     * Performs a search for articles on the OSRS Wiki using Jetpack Paging 3.
     *
     * @param query The search term.
     * @return A Flow of PagingData containing SearchResult items.
     */
    fun getSearchResultStream(query: String): Flow<PagingData<SearchResult>> {
        return Pager(
            config = PagingConfig(
                pageSize = DEFAULT_SEARCH_RESULTS_PAGE_SIZE,
                enablePlaceholders = false // Typically false for network sources
                // prefetchDistance = Can be configured if needed
                // initialLoadSize = Can be configured if needed (defaults to 3 * pageSize)
            ),
            pagingSourceFactory = {
                SearchPagingSource(apiService = apiService, query = query)
            }
        ).flow
    }

    /**
     * Performs a legacy search for articles on the OSRS Wiki (non-Paging).
     *
     * @param query The search term.
     * @param limit The maximum number of results to return.
     * @param offset The offset from which to start returning results.
     * @return A [SearchApiResponse] containing the search results.
     * Can throw an exception if the network call fails.
     */
    suspend fun searchArticles(query: String, limit: Int, offset: Int): SearchApiResponse {
        // Perform the network request on the IO dispatcher
        return withContext(Dispatchers.IO) {
            apiService.searchArticles(query = query, limit = limit, offset = offset)
        }
    }
}
