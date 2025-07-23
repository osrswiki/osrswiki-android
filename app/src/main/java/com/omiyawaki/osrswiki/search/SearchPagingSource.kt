package com.omiyawaki.osrswiki.search

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.network.SearchResult
import com.omiyawaki.osrswiki.network.WikiApiService
import retrofit2.HttpException
import java.io.IOException

private const val OSRS_WIKI_STARTING_PAGE_OFFSET = 0
private const val DEFAULT_NETWORK_PAGE_SIZE = 20
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
            // Step 1: Fetch initial search results
            val response = apiService.searchArticles(query = query, offset = currentOffset, limit = limit)
            val searchResults = response.query?.search ?: emptyList()

            // Step 2: Fetch thumbnails in batches to respect API limits
            val resultsWithThumbnails = if (searchResults.isNotEmpty()) {
                val allPageIds = searchResults.map { it.pageid.toString() }
                val thumbnailsMap = mutableMapOf<String, String?>()

                // Split page IDs into chunks of 50 and fetch each chunk
                allPageIds.chunked(PAGE_ID_API_LIMIT).forEach { chunkOfIds ->
                    val idString = chunkOfIds.joinToString("|")
                    try {
                        val imageResponse = apiService.getPageThumbnails(pageIds = idString, thumbSize = THUMBNAIL_SIZE)
                        val chunkThumbnails = imageResponse.query?.pages?.mapValues { it.value.thumbnail?.source }
                        if (chunkThumbnails != null) {
                            thumbnailsMap.putAll(chunkThumbnails)
                        }
                    } catch (e: Exception) {
                        Log.e("SearchPagingSource", "Failed to fetch thumbnail chunk: ${e.message}")
                    }
                }

                // Combine original results with their new thumbnail URLs
                searchResults.map { result ->
                    result.copy(thumbnailUrl = thumbnailsMap[result.pageid.toString()])
                }
            } else {
                searchResults
            }

            // Step 3: Enhance results with offline status
            val enhancedSearchResults = resultsWithThumbnails.map { searchResult ->
                val articleMeta = articleMetaDao.getMetaByPageId(searchResult.pageid)
                val isOffline = articleMeta != null && articleMeta.localFilePath.isNotEmpty()
                searchResult.copy(isOfflineAvailable = isOffline)
            }

            val nextKey = if (enhancedSearchResults.size < limit) null else currentOffset + enhancedSearchResults.size
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
