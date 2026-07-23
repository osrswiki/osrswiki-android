package com.omiyawaki.osrswiki.page.cache

import android.util.Log
import java.util.LinkedHashMap

/**
 * A bounded in-memory cache for storing downloaded page assets.
 *
 * This allows the WebView to intercept requests and serve content directly from
 * memory without letting image-heavy browsing grow the process heap without
 * bound.
 */
object AssetCache {
    private const val TAG = "AssetCache"
    private const val DEFAULT_MAX_BYTES = 8 * 1024 * 1024L
    private const val DEFAULT_MAX_ENTRIES = 256

    private var maxBytes = DEFAULT_MAX_BYTES
    private var maxEntries = DEFAULT_MAX_ENTRIES
    private var totalBytes = 0L
    private val cache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {}

    /**
     * Stores an asset in the cache.
     *
     * @param url The URL of the asset, used as the cache key.
     * @param data The asset content as a byte array.
     */
    @Synchronized
    fun put(url: String, data: ByteArray) {
        if (data.size > maxBytes) {
            cache.remove(url)?.let { totalBytes -= it.size }
            Log.d(TAG, "Skipping oversized asset (${data.size} bytes): $url")
            return
        }

        cache.remove(url)?.let { totalBytes -= it.size }
        cache[url] = data
        totalBytes += data.size
        trimToLimits()
    }

    /**
     * Retrieves an asset from the cache.
     *
     * @param url The URL of the asset to retrieve.
     * @return The asset content as a byte array, or null if not found.
     */
    @Synchronized
    fun get(url: String): ByteArray? {
        return cache[url]
    }

    /**
     * Clears all entries from the cache.
     *
     * This should be called when a page load is finished or cancelled
     * to free up memory.
     */
    @Synchronized
    fun clear() {
        cache.clear()
        totalBytes = 0L
    }

    @Synchronized
    fun stats(): AssetCacheStats {
        return AssetCacheStats(entryCount = cache.size, totalBytes = totalBytes, maxBytes = maxBytes)
    }

    @Synchronized
    fun configureForTests(maxBytes: Long, maxEntries: Int) {
        this.maxBytes = maxBytes
        this.maxEntries = maxEntries
        clear()
    }

    @Synchronized
    fun resetLimitsForTests() {
        maxBytes = DEFAULT_MAX_BYTES
        maxEntries = DEFAULT_MAX_ENTRIES
        clear()
    }

    private fun trimToLimits() {
        val iterator = cache.entries.iterator()
        while ((totalBytes > maxBytes || cache.size > maxEntries) && iterator.hasNext()) {
            val eldest = iterator.next()
            totalBytes -= eldest.value.size
            iterator.remove()
        }
    }
}

data class AssetCacheStats(
    val entryCount: Int,
    val totalBytes: Long,
    val maxBytes: Long
)
