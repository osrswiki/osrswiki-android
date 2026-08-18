package com.omiyawaki.osrswiki.search

import com.omiyawaki.osrswiki.network.SearchResult
import com.omiyawaki.osrswiki.util.StringUtil
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

/** User-input normalization and conservative title-aware reranking shared by every online search. */
internal object SearchQueryPolicy {
    data class HighlightRange(val startInclusive: Int, val endExclusive: Int)

    private val officialHosts = setOf("oldschool.runescape.wiki", "www.oldschool.runescape.wiki")

    fun apiQuery(rawQuery: String): String {
        val trimmed = rawQuery.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
        if (uri?.host?.lowercase(Locale.ROOT) in officialHosts) {
            val path = uri?.path.orEmpty()
            val encodedTitle = when {
                path.startsWith("/w/") -> path.removePrefix("/w/")
                path.endsWith("/index.php") -> uri?.rawQuery
                    ?.split('&')
                    ?.firstOrNull { it.startsWith("title=") }
                    ?.substringAfter('=')
                else -> null
            }
            if (!encodedTitle.isNullOrBlank()) {
                return URLDecoder.decode(encodedTitle, StandardCharsets.UTF_8.name())
                    .replace('_', ' ')
                    .trim()
            }
        }
        return trimmed
            .replace('_', ' ')
            .replace(Regex("(?i)^(?:how\\s+to\\s+get|where\\s+to\\s+find|where\\s+is|what\\s+is)\\s+"), "")
            .replace(Regex("(?i)\\s+(?:osrs|old\\s+school\\s+runescape|wiki)(?:\\s+(?:page|article))?\\s*$"), "")
            .trim()
    }

    fun merge(
        query: String,
        prefix: List<SearchResult>,
        fulltext: List<SearchResult>
    ): List<SearchResult> {
        val byPageId = linkedMapOf<Int, SearchResult>()
        (prefix + fulltext).forEach { result ->
            val existing = byPageId[result.pageid]
            byPageId[result.pageid] = if (existing == null) {
                result.withPreviewFallback()
            } else {
                existing.enrichedWith(result)
            }
        }
        return rank(query, byPageId.values.toList())
    }

    fun rank(query: String, results: List<SearchResult>): List<SearchResult> {
        val normalizedQuery = normalize(apiQuery(query))
        if (normalizedQuery.isBlank()) return results.sortedBy { it.index }
        val queryTokens = tokens(normalizedQuery)

        return results.withIndex().sortedWith(
            compareByDescending<IndexedValue<SearchResult>> {
                relevanceScore(normalizedQuery, queryTokens, normalize(it.value.title))
            }.thenBy { it.value.index }.thenBy { it.index }
        ).map { it.value }
    }

    fun highlightTerms(query: String): List<String> = tokens(normalize(apiQuery(query)))
        .filter { it.length >= 2 }
        .distinct()

    /**
     * Titles may use the user's complete contiguous prefix, including a one-character final word.
     * This makes `barbarian v` visibly match `Barbarian V` without highlighting every `v` in a
     * result preview. Non-prefix title matches fall back to meaningful tokens.
     */
    fun titleHighlightRanges(displayText: String, query: String): List<HighlightRange> {
        val decodedText = StringUtil.decodeHtmlToFixedPoint(displayText)
        val decodedQuery = StringUtil.decodeHtmlToFixedPoint(apiQuery(query))
            .replace('_', ' ')
            .trim()
        if (decodedQuery.isNotEmpty() &&
            decodedQuery.length <= decodedText.length &&
            decodedText.regionMatches(
                thisOffset = 0,
                other = decodedQuery,
                otherOffset = 0,
                length = decodedQuery.length,
                ignoreCase = true
            )
        ) {
            return listOf(HighlightRange(0, decodedQuery.length))
        }
        return matchingRanges(decodedText, highlightTerms(query))
    }

    /** Preview text deliberately excludes one-character terms to avoid noisy incidental matches. */
    fun snippetHighlightRanges(displayText: String, query: String): List<HighlightRange> =
        matchingRanges(StringUtil.decodeHtmlToFixedPoint(displayText), highlightTerms(query))

    /** Prefix expansion improves recall for partial words without adding a second network trip. */
    fun networkQuery(rawQuery: String): String {
        val canonical = apiQuery(rawQuery)
        val terms = Regex("[\\p{L}\\p{N}]+").findAll(canonical).map { it.value }.toList()
        if (terms.isEmpty()) return canonical
        return terms.joinToString(" ") { if (it.length >= 3) "$it*" else it }
    }

    private fun SearchResult.withPreviewFallback(): SearchResult {
        val preview = firstNonBlank(snippet, extract) ?: return this
        return if (snippet == preview) this else copy(snippet = preview)
    }

    private fun SearchResult.enrichedWith(other: SearchResult): SearchResult {
        val preview = firstNonBlank(snippet, extract, other.snippet, other.extract)
        return copy(
            snippet = preview,
            extract = firstNonBlank(extract, other.extract),
            thumbnail = thumbnail ?: other.thumbnail,
            size = size ?: other.size,
            wordcount = wordcount ?: other.wordcount,
            timestamp = timestamp ?: other.timestamp
        )
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    private fun relevanceScore(query: String, queryTokens: List<String>, title: String): Int {
        if (title == query) return 100_000
        if (title.startsWith(query)) return 90_000 - (title.length - query.length).coerceAtLeast(0)
        val titleTokens = tokens(title)
        if (titleTokens.isNotEmpty() && queryTokens.size > titleTokens.size &&
            queryTokens.take(titleTokens.size) == titleTokens &&
            queryTokens.drop(titleTokens.size).all { it in setOf("guide", "page", "article", "wiki") }
        ) {
            return 89_000 - (query.length - title.length).coerceAtLeast(0)
        }
        if (queryTokens.isNotEmpty() && queryTokens.all { queryToken ->
                titleTokens.any { titleToken -> titleToken.startsWith(queryToken) }
            }) {
            return 88_000 - kotlin.math.abs(title.length - query.length)
        }
        if (query.length >= 4 && editDistanceWithin(query, title, 2)) return 85_000
        // A short title that is merely the beginning of a longer query is useful, but it must
        // never outrank a title that covers every query token (for example, "Amulet" versus
        // "Amulet of glory" for "amulet glo").
        if (title.isNotBlank() && query.startsWith(title)) return 65_000 - (query.length - title.length).coerceAtLeast(0)
        if (queryTokens.size == 1 && titleTokens.any { it.startsWith(queryTokens.first()) }) {
            return 60_000 - kotlin.math.abs(title.length - query.length)
        }
        return 0
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun tokens(value: String): List<String> = value.split(' ').filter { it.isNotBlank() }

    private fun matchingRanges(text: String, terms: List<String>): List<HighlightRange> {
        val lowered = text.lowercase(Locale.ROOT)
        val ranges = mutableListOf<HighlightRange>()
        terms.forEach { term ->
            var searchIndex = 0
            while (searchIndex < lowered.length) {
                val start = lowered.indexOf(term.lowercase(Locale.ROOT), searchIndex)
                if (start < 0) break
                val end = start + term.length
                ranges += HighlightRange(start, end)
                searchIndex = end
            }
        }
        return ranges.distinct().sortedBy { it.startInclusive }
    }

    private fun editDistanceWithin(left: String, right: String, limit: Int): Boolean {
        if (kotlin.math.abs(left.length - right.length) > limit) return false
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            var rowMinimum = current[0]
            for (j in right.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1
                )
                rowMinimum = minOf(rowMinimum, current[j + 1])
            }
            if (rowMinimum > limit) return false
            previous = current
        }
        return previous[right.length] <= limit
    }
}
