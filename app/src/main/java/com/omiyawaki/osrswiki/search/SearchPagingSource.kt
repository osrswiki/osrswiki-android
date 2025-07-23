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
private const val PAGE_ID_API_LIMIT = 50 // MediaWiki API limit for pageids

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
            // Step 1: Fetch initial search results using prefix search.
            // This response contains titles and page IDs, but no snippets or thumbnails.
            val response = apiService.prefixSearchArticles(query = query, offset = currentOffset, limit = limit)
            val searchResults = response.query?.prefixsearch ?: emptyList()

            // If we have no results, we can stop here.
            if (searchResults.isEmpty()) {
                return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            }

            val allPageIds = searchResults.map { it.pageid.toString() }

            // Step 2: Fetch extracts (snippets) and thumbnails for the retrieved page IDs.
            // These calls are chunked to respect the API's limit of 50 IDs per request.
            val extractsMap = mutableMapOf<Int, String>()
            val thumbnailsMap = mutableMapOf<String, String?>()

            allPageIds.chunked(PAGE_ID_API_LIMIT).forEach { chunkOfIds ->
                val idString = chunkOfIds.joinToString("|")
                // Fetch extracts for the current chunk.
                try {
                    val extractsResponse = apiService.getPageExtracts(pageIds = idString)
                    extractsResponse.query?.pages?.forEach { pageExtract ->
                        pageExtract.extract?.let { extractsMap[pageExtract.pageid] = it }
                    }
                } catch (e: Exception) {
                    // Log but don't fail the whole search if snippets are missing.
                    Log.e("SearchPagingSource", "Failed to fetch extracts chunk: ${e.message}")
                }

                // Fetch thumbnails for the current chunk.
                try {
                    val imageResponse = apiService.getPageThumbnails(pageIds = idString, thumbSize = THUMBNAIL_SIZE)
                    imageResponse.query?.pages?.mapValues { it.value.thumbnail?.source }?.let {
                        thumbnailsMap.putAll(it)
                    }
                } catch (e: Exception) {
                    // Log but don't fail the whole search if thumbnails are missing.
                    Log.e("SearchPagingSource", "Failed to fetch thumbnail chunk: ${e.message}")
                }
            }

            // Step 3: Combine the initial results with the fetched extracts and thumbnails.
            val combinedResults = searchResults.map { result ->
                result.copy(
                    snippet = extractsMap[result.pageid] ?: "",
                    thumbnailUrl = thumbnailsMap[result.pageid.toString()]
                )
            }

            // Step 4: Enhance results with offline status from the local database.
            val enhancedSearchResults = combinedResults.map { searchResult ->
                val articleMeta = articleMetaDao.getMetaByPageId(searchResult.pageid)
                val isOffline = articleMeta != null && articleMeta.localFilePath.isNotEmpty()
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
            // Re-throw the exception to allow coroutines to handle cancellation gracefully.
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
