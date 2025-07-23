package com.omiyawaki.osrswiki.search

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.network.SearchResult
import com.omiyawaki.osrswiki.network.WikiApiService
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.CancellationException

private const val OSRS_WIKI_STARTING_PAGE_OFFSET = 0
private const val THUMBNAIL_SIZE = 240

class SearchPagingSource(
    private val apiService: WikiApiService,
    private val query: String,
    private val articleMetaDao: ArticleMetaDao
) : PagingSource<Int, SearchResult>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SearchResult> {
        val currentOffset = params.key ?: OSRS_WIKI_STARTING_PAGE_OFFSET
        val limit = params.loadSize

        if (query.isBlank()) {
            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }

        return try {
            // Step 1: Make a single, combined network call.
            val response = apiService.generatedPrefixSearch(
                query = query,
                limit = limit,
                offset = currentOffset,
                thumbSize = THUMBNAIL_SIZE
            )

            val unsortedResults = response.query?.pages ?: emptyList()

            // Sort the results by the 'index' field to ensure correct relevance order.
            val searchResults = unsortedResults.sortedBy { it.index }

            if (searchResults.isEmpty()) {
                return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            }

            // Step 2: Efficiently enhance results with offline status from the local database.
            val pageIds = searchResults.map { it.pageid }
            val offlineMetas = articleMetaDao.getMetasByPageIds(pageIds)
            val offlinePageIds = offlineMetas.map { it.pageId }.toSet()

            val enhancedSearchResults = searchResults.map { searchResult ->
                val isOffline = offlinePageIds.contains(searchResult.pageid)
                // Use .copy() to create a new instance with the offline status set.
                searchResult.copy(isOfflineAvailable = isOffline)
            }

            val nextKey = if (enhancedSearchResults.size < limit) null else currentOffset + limit
            val prevKey = if (currentOffset == OSRS_WIKI_STARTING_PAGE_OFFSET) null else (currentOffset - params.loadSize).coerceAtLeast(0)

            LoadResult.Page(
                data = enhancedSearchResults,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (exception: IOException) {
            Log.e("SearchPagingSource", "IOException during search", exception)
            return LoadResult.Error(exception)
        } catch (exception: HttpException) {
            Log.e("SearchPagingSource", "HttpException during search", exception)
            return LoadResult.Error(exception)
        } catch (exception: CancellationException) {
            // This is expected when a new search supersedes the current one.
            throw exception
        } catch (exception: Exception) {
            Log.e("SearchPagingSource", "Generic Exception during search", exception)
            return LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SearchResult>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(state.config.pageSize) ?: anchorPage?.nextKey?.minus(state.config.pageSize)
        }
    }
}
