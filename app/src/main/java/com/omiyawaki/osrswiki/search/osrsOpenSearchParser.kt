package com.omiyawaki.osrswiki.search

import com.omiyawaki.osrswiki.network.SearchResult
import com.omiyawaki.osrswiki.page.osrsWikiWebViewUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** MediaWiki `action=opensearch` payload: [query, titles, descriptions, urls]. */
internal object osrsOpenSearchParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(bytes: ByteArray): List<SearchResult> {
        val root = json.parseToJsonElement(bytes.decodeToString()).jsonArray
        val titles = root.getOrNull(1)?.jsonArray ?: return emptyList()
        val descriptions = root.getOrNull(2)?.jsonArray
        return titles.mapIndexed { index, titleElement ->
            val title = titleElement.jsonPrimitive.content
            val snippet = descriptions?.getOrNull(index)?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            SearchResult(
                ns = if (osrsWikiWebViewUrl.isCalculatorNamespaceTitle(title)) {
                    osrsMediaWikiNamespace.CALCULATOR
                } else {
                    osrsMediaWikiNamespace.MAIN
                },
                title = title,
                pageid = 0,
                index = index + 1,
                snippet = snippet
            )
        }
    }
}
