package com.omiyawaki.osrswiki.search

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.network.SearchResult
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.network.model.GeneratedSearchApiResponse
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.CancellationException

private const val THUMBNAIL_SIZE = 240

/**
 * Namespace-scoped Search paging. Empty-query browse uses generator=recentchanges
 * newest-first; typed queries use generator=search with gsrnamespace.
 */
class osrsScopedSearchPagingSource(
    private val apiService: WikiApiService,
    private val query: String,
    private val scope: osrsSearchScope,
    private val articleMetaDao: ArticleMetaDao
) : PagingSource<String, SearchResult>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, SearchResult> {
        val namespace = scope.namespace
        if (namespace == null) {
            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty() && !scope.emptyQueryBrowsesNewest) {
            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }

        return try {
            val response = if (trimmed.isEmpty()) {
                apiService.generatedRecentChanges(
                    namespace = namespace,
                    limit = params.loadSize,
                    continueToken = params.key,
                    thumbSize = THUMBNAIL_SIZE
                )
            } else {
                apiService.generatedNamespacedSearch(
                    query = SearchQueryPolicy.networkQuery(trimmed),
                    namespace = namespace,
                    limit = params.loadSize,
                    offset = params.key?.toIntOrNull() ?: 0,
                    thumbSize = THUMBNAIL_SIZE
                )
            }
            val page = pageFrom(response)
            page
        } catch (exception: IOException) {
            Log.e("osrsScopedSearchPaging", "IOException during scoped search", exception)
            LoadResult.Error(exception)
        } catch (exception: HttpException) {
            Log.e("osrsScopedSearchPaging", "HttpException during scoped search", exception)
            LoadResult.Error(exception)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e("osrsScopedSearchPaging", "Generic Exception during scoped search", exception)
            LoadResult.Error(exception)
        }
    }

    private suspend fun pageFrom(
        response: GeneratedSearchApiResponse
    ): LoadResult.Page<String, SearchResult> {
        val searchResults = response.query?.pages.orEmpty()
            .filter { result -> scope.namespace == null || result.ns == scope.namespace }
            .map { result ->
                val preview = result.snippet?.trim().orEmpty()
                if (preview.isNotEmpty()) result else result.copy(snippet = result.extract)
            }
        if (searchResults.isEmpty()) {
            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }
        val pageIds = searchResults.map { it.pageid }
        val offlinePageIds = articleMetaDao.getMetasByPageIds(pageIds).map { it.pageId }.toSet()
        val enhanced = searchResults.map { it.copy(isOfflineAvailable = offlinePageIds.contains(it.pageid)) }
        val nextKey = if (query.trim().isEmpty()) {
            response.continuation?.grccontinue
        } else {
            response.continuation?.gsroffset?.toString()
        }
        val prevKey = null
        return LoadResult.Page(
            data = enhanced,
            prevKey = prevKey,
            nextKey = nextKey
        )
    }

    override fun getRefreshKey(state: PagingState<String, SearchResult>): String? = null
}
