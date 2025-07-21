package com.omiyawaki.osrswiki.page.preemptive

import java.util.concurrent.atomic.AtomicReference

/**
 * A data class holding the essential content of a successfully fetched page,
 * ready for immediate rendering.
 */
data class PreloadedPage(
    val pageId: Int,
    val finalHtml: String,
    val plainTextTitle: String?,
    val displayTitle: String?,
    val wikiUrl: String,
    val revisionId: Long,
    val lastFetchedTimestamp: Long
)

/**
 * A thread-safe, in-memory cache to hold a single preloaded page.
 * The cache is designed for a one-time, consuming read.
 */
object PreloadedPageCache {
    private val preloadedPage = AtomicReference<PreloadedPage?>(null)

    /**
     * Stores a page in the cache, overwriting any existing entry.
     */
    fun put(page: PreloadedPage) {
        preloadedPage.set(page)
    }

    /**
     * Retrieves the preloaded page if it matches the given pageId.
     * This is a consuming read; the cache is cleared after retrieval.
     *
     * @param pageId The ID of the page to retrieve.
     * @return The PreloadedPage if it exists and matches the ID, otherwise null.
     */
    fun consume(pageId: Int): PreloadedPage? {
        // Atomically retrieve the current value and set the new value to null.
        // Then, check if the retrieved value matches the pageId we're looking for.
        return preloadedPage.getAndSet(null)?.takeIf { it.pageId == pageId }
    }

    /**
     * Clears any existing preloaded page from the cache. This is useful
     * when a preload operation is cancelled.
     */
    fun clear() {
        preloadedPage.set(null)
    }
}
