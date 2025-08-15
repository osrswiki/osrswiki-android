package com.omiyawaki.osrswiki.search

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.network.SearchResult
import com.omiyawaki.osrswiki.network.Thumbnail
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.network.model.SearchApiResult
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
            // Step 1: Use MediaWiki Search API for intelligent ranking and guaranteed snippets.
            val response = apiService.searchPages(
                query = query,
                limit = limit,
                offset = currentOffset
            )

            val searchApiResults = response.query?.search ?: emptyList()
            
            // Debug logging for API response
            Log.d("SearchPagingSource", "Search API response for query '$query': ${searchApiResults.size} results")
            searchApiResults.forEachIndexed { index, result ->
                val hasSnippet = !result.snippet.isNullOrBlank()
                Log.d("SearchPagingSource", "Result $index: '${result.title}' (pageId=${result.pageid}) - snippet: ${if (hasSnippet) "present (${result.snippet?.length} chars)" else "missing/empty"}")
                if (result.title.contains("Logs", ignoreCase = true)) {
                    Log.d("SearchPagingSource", "*** LOGS PAGE DEBUG: snippet='${result.snippet}', snippet.isNullOrBlank()=${result.snippet.isNullOrBlank()}")
                }
            }

            // Map SearchApiResult to SearchResult with HTML stripping for snippets (Phase 1 pragmatic approach)
            var searchResults = searchApiResults.map { apiResult ->
                SearchResult(
                    ns = apiResult.ns,
                    title = apiResult.title,
                    pageid = apiResult.pageid,
                    index = searchApiResults.indexOf(apiResult) + 1, // Maintain order from search API
                    size = apiResult.size,
                    wordcount = apiResult.wordcount,
                    // Phase 2: Keep HTML for search term highlighting in UI
                    snippet = apiResult.snippet?.trim(),
                    timestamp = apiResult.timestamp,
                    thumbnail = null, // Search API doesn't provide thumbnails directly
                    isOfflineAvailable = false // Will be set later
                )
            }

            if (searchResults.isEmpty()) {
                return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            }

            // Step 2: Fetch thumbnails for search results using a separate API call
            // MediaWiki API has a 50-page limit for pageids parameter
            if (searchResults.isNotEmpty()) {
                try {
                    val pagesToFetch = searchResults.take(50) // Respect API limit of 50 pages
                    val pageIds = pagesToFetch.joinToString("|") { it.pageid.toString() }
                    val thumbnailResponse = apiService.getPageThumbnails(pageIds, THUMBNAIL_SIZE)
                    
                    // Create a map of pageid to thumbnail for quick lookup
                    // PageImagesApiResponse uses Map<String, PageImagesPageInfo> structure
                    val thumbnailMap = thumbnailResponse.query?.pages?.values?.associate { pageInfo ->
                        pageInfo.pageid to pageInfo.thumbnail?.let { pageThumbnail ->
                            // Convert PageThumbnail to Thumbnail for SearchResult compatibility
                            Thumbnail(source = pageThumbnail.source)
                        }
                    } ?: emptyMap()
                    
                    // Update search results with thumbnails
                    searchResults = searchResults.map { result ->
                        val thumbnail = thumbnailMap[result.pageid]
                        result.copy(thumbnail = thumbnail)
                    }
                    
                    Log.d("SearchPagingSource", "Added thumbnails for ${thumbnailMap.size}/${pagesToFetch.size} results (limited to first 50 due to API constraint)")
                } catch (e: Exception) {
                    Log.w("SearchPagingSource", "Thumbnail fetch failed: ${e.message}")
                    // Continue without thumbnails - not critical for functionality
                }
            }

            // Step 3: Efficiently enhance results with offline status from the local database.
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
