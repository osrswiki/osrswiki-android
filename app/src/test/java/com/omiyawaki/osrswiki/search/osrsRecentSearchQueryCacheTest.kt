package com.omiyawaki.osrswiki.search

import com.omiyawaki.osrswiki.network.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class osrsRecentSearchQueryCacheTest {
    @Test
    fun evictsLeastRecentlyUsedAfterSixteenEntries() {
        val cache = osrsRecentSearchQueryCache<osrsCachedSearchFirstPage>(maxEntries = 16)
        repeat(16) { index ->
            cache.put(
                osrsRecentSearchQueryCache.key("all", "q$index"),
                osrsCachedSearchFirstPage(
                    items = listOf(SearchResult(ns = 0, title = "T$index", pageid = index)),
                    nextKey = 20
                )
            )
        }
        assertEquals(16, cache.size())
        cache.get(osrsRecentSearchQueryCache.key("all", "q0"))
        cache.put(
            osrsRecentSearchQueryCache.key("all", "newest"),
            osrsCachedSearchFirstPage(items = emptyList(), nextKey = null)
        )
        assertEquals(16, cache.size())
        assertNull(cache.get(osrsRecentSearchQueryCache.key("all", "q1")))
        assertEquals(
            "T0",
            cache.get(osrsRecentSearchQueryCache.key("all", "q0"))?.items?.single()?.title
        )
    }
}
