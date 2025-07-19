package com.omiyawaki.osrswiki.page.cache

import java.util.concurrent.ConcurrentHashMap

/**
 * A simple in-memory cache for storing downloaded page assets.
 *
 * This singleton object holds asset data (like images, CSS) in a thread-safe map,
 * with the asset URL as the key. This allows the WebView to intercept requests
 * and serve content directly from memory, avoiding redundant network calls.
 */
object AssetCache {
    private val cache = ConcurrentHashMap<String, ByteArray>()

    /**
     * Stores an asset in the cache.
     *
     * @param url The URL of the asset, used as the cache key.
     * @param data The asset content as a byte array.
     */
    fun put(url: String, data: ByteArray) {
        cache[url] = data
    }

    /**
     * Retrieves an asset from the cache.
     *
     * @param url The URL of the asset to retrieve.
     * @return The asset content as a byte array, or null if not found.
     */
    fun get(url: String): ByteArray? {
        return cache[url]
    }

    /**
     * Clears all entries from the cache.
     *
     * This should be called when a page load is finished or cancelled
     * to free up memory.
     */
    fun clear() {
        cache.clear()
    }
}
