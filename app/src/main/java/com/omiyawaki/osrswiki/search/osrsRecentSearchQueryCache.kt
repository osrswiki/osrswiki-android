package com.omiyawaki.osrswiki.search

import com.omiyawaki.osrswiki.network.SearchResult
import com.omiyawaki.osrswiki.network.osrsWikiApiStewardship

data class osrsCachedSearchFirstPage(
    val items: List<SearchResult>,
    val nextKey: Any?
)

/**
 * Short in-memory LRU of recent first-page search results. Typing the same
 * query again must not wait on the wiki; stale in-flight work is cancelled
 * separately by the ViewModel.
 */
class osrsRecentSearchQueryCache<V>(
    private val maxEntries: Int = osrsWikiApiStewardship.SEARCH_QUERY_CACHE_SIZE
) {
    private val lock = Any()
    private val values = object : LinkedHashMap<String, V>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean {
            return size > maxEntries
        }
    }

    fun get(key: String): V? = synchronized(lock) { values[key] }

    fun put(key: String, value: V) {
        synchronized(lock) {
            values[key] = value
        }
    }

    fun size(): Int = synchronized(lock) { values.size }

    companion object {
        fun key(scope: String, query: String): String =
            "$scope\n${query.trim().lowercase()}"
    }
}
