package com.omiyawaki.osrswiki.search

import com.omiyawaki.osrswiki.network.SearchResult
import com.omiyawaki.osrswiki.network.WikiApiService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * First-paint vs settled-preview split for Search rows.
 * Browse-newest (Home View more) shows titles as soon as the generator page
 * returns; extract/parse fill remaining snippets off that critical path.
 */
object osrsSearchPreviewEnricher {
    fun firstPaint(results: List<SearchResult>): List<SearchResult> {
        return results.map { result ->
            result.copy(snippet = osrsSearchPreviewText.fromCandidates(result.snippet, result.extract))
        }
    }

    suspend fun enrichMissingPreviews(
        apiService: WikiApiService,
        results: List<SearchResult>
    ): List<SearchResult> {
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
}
