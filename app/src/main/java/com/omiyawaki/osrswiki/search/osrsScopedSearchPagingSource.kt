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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
                result.copy(snippet = osrsSearchPreviewText.fromCandidates(result.snippet, result.extract))
            }
            .let { results -> enrichMissingPreviews(results) }
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

    private suspend fun enrichMissingPreviews(results: List<SearchResult>): List<SearchResult> {
        val missing = results.filter { result ->
            osrsSearchPreviewText.fromCandidates(result.snippet, result.extract) == null
        }
        if (missing.isEmpty()) return results

        val extractsById = runCatching {
            apiService.getPageExtract(missing.joinToString("|") { it.pageid.toString() })
                .query?.pages.orEmpty()
                .associate { page ->
                    page.pageid to osrsSearchPreviewText.fromPlainExtract(page.snippet)
                }
        }.getOrDefault(emptyMap())

        val afterExtracts = results.map { result ->
            val preview = extractsById[result.pageid]
            if (!preview.isNullOrBlank()) result.copy(snippet = preview) else result
        }
        val stillMissing = afterExtracts.filter { result ->
            osrsSearchPreviewText.fromCandidates(result.snippet, result.extract) == null
        }
        if (stillMissing.isEmpty()) return afterExtracts

        val parsedById = coroutineScope {
            stillMissing.map { result ->
                async {
                    result.pageid to runCatching {
                        osrsSearchPreviewText.fromHtml(
                            apiService.getArticleParseDataByPageId(result.pageid).parse?.text
                        )
                    }.getOrNull()
                }
            }.awaitAll().toMap()
        }
        return afterExtracts.map { result ->
            val preview = parsedById[result.pageid]
            if (!preview.isNullOrBlank()) result.copy(snippet = preview) else result
        }
    }

    override fun getRefreshKey(state: PagingState<String, SearchResult>): String? = null
}
