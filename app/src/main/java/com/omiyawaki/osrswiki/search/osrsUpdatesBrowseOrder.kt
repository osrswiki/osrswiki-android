package com.omiyawaki.osrswiki.search

import com.omiyawaki.osrswiki.network.SearchResult

/**
 * Canonical View more / empty-query updates order: reverse chronological
 * (most recent first). Both platforms must apply this same key.
 *
 * Sort key:
 *  1. MediaWiki generator index ascending. `generator=recentchanges` with
 *     `grcdir=older` numbers the newest change index=1.
 *  2. ISO-8601 `timestamp` descending when index is missing.
 *  3. `pageid` descending (new pages receive increasing ids).
 */
internal object osrsUpdatesBrowseOrder {
    fun sort(results: List<SearchResult>): List<SearchResult> {
        return results.sortedWith(
            compareBy<SearchResult> { result ->
                if (result.index > 0) result.index else Int.MAX_VALUE
            }.thenByDescending { it.timestamp.orEmpty() }
                .thenByDescending { it.pageid }
        )
    }
}
