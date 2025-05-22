package com.omiyawaki.osrswiki.data.paging // Package: com.omiyawaki.osrswiki.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.omiyawaki.osrswiki.network.SearchResult // Your actual SearchResult DTO
import com.omiyawaki.osrswiki.network.WikiApiService // Your actual API Service
import android.util.Log
import java.io.IOException
import retrofit2.HttpException

// Starting offset for the OSRS Wiki API (sroffset parameter)
private const val OSRS_WIKI_STARTING_PAGE_OFFSET = 0
// Default network page size (can be overridden by PagingConfig if needed)
// This value isn't strictly used by PagingSource itself if params.loadSize is always honored,
// but can be a reference or used in prevKey calculation.
private const val DEFAULT_NETWORK_PAGE_SIZE = 20


class SearchPagingSource(
    private val apiService: WikiApiService,
    private val query: String
) : PagingSource<Int, SearchResult>() { // PagingKey is Int (offset), Value is SearchResult DTO

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SearchResult> {
        val currentOffset = params.key ?: OSRS_WIKI_STARTING_PAGE_OFFSET
        // params.loadSize is the number of items Paging 3 wants to load.
        // Pass this as 'limit' to your API.
        val limit = params.loadSize

        if (query.isBlank()) {
            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }

        return try {
            Log.d("SearchPagingSource", "Loading for query: '$query', offset: $currentOffset, limit: $limit")
            val response = apiService.searchArticles(
                query = query,
                offset = currentOffset,
                limit = limit
            )

            val searchResults = response.query?.search ?: emptyList()
            Log.d("SearchPagingSource", "Received ${searchResults.size} results for query: '$query'")

            // Determine the next key (offset for the next page).
            // If searchResults is empty OR if the number of results returned is less than requested (limit),
            // it's likely the last page.
            val nextKey = if (searchResults.size < limit || searchResults.isEmpty()) {
                null // No more pages to load
            } else {
                // The next key is the current offset plus the number of items just loaded.
                currentOffset + searchResults.size
            }
            // For prevKey, if currentOffset is the start, there's no previous page.
            // Otherwise, calculate a potential previous offset. This needs careful consideration
            // if params.loadSize can vary. A simpler prevKey might be currentOffset - DEFAULT_NETWORK_PAGE_SIZE,
            // ensuring it's not negative.
            val prevKey = if (currentOffset == OSRS_WIKI_STARTING_PAGE_OFFSET) {
                null
            } else {
                // Calculate a plausible previous offset.
                // (currentOffset - params.loadSize) might be more accurate if load sizes are consistent.
                // Or (currentOffset - DEFAULT_NETWORK_PAGE_SIZE). Max with 0 to prevent negative offset.
                (currentOffset - DEFAULT_NETWORK_PAGE_SIZE).coerceAtLeast(0)
            }


            LoadResult.Page(
                data = searchResults,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (exception: IOException) {
            Log.e("SearchPagingSource", "IOException during search for query '$query'", exception)
            LoadResult.Error(exception)
        } catch (exception: HttpException) {
            Log.e("SearchPagingSource", "HttpException during search for query '$query'", exception)
            LoadResult.Error(exception)
        } catch (exception: Exception) {
            Log.e("SearchPagingSource", "Generic Exception during search for query '$query'", exception)
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SearchResult>): Int? {
        // Try to find the page key (offset) of the closest page to anchorPosition from
        // either the prevKey or the nextKey of that page.
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(state.config.pageSize) // pageSize here is from PagingConfig
                ?: anchorPage?.nextKey?.minus(state.config.pageSize)
        }
    }
}
