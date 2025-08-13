package com.omiyawaki.osrswiki.page.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Simple cache for complete wiki page HTML responses to enable offline access.
 * This caches the full page HTML from direct wiki loads for offline viewing.
 */
class WikiPageCache private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: WikiPageCache? = null
        
        fun getInstance(context: Context): WikiPageCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WikiPageCache(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val logTag = "WikiPageCache"
    private val cacheDir = File(context.cacheDir, "wiki_pages")
    
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    /**
     * Cache a complete page response for offline access
     */
    suspend fun cachePage(url: String, htmlContent: String) = withContext(Dispatchers.IO) {
        try {
            val cacheFile = getCacheFile(url)
            cacheFile.writeText(htmlContent, Charsets.UTF_8)
            Log.d(logTag, "Cached page: $url (${htmlContent.length} bytes)")
        } catch (e: Exception) {
            Log.e(logTag, "Failed to cache page: $url", e)
        }
    }
    
    /**
     * Retrieve cached page if available
     */
    suspend fun getCachedPage(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val cacheFile = getCacheFile(url)
            if (cacheFile.exists()) {
                val content = cacheFile.readText(Charsets.UTF_8)
                Log.d(logTag, "Retrieved cached page: $url (${content.length} bytes)")
                return@withContext content
            }
        } catch (e: Exception) {
            Log.e(logTag, "Failed to retrieve cached page: $url", e)
        }
        return@withContext null
    }
    
    /**
     * Check if a page is cached
     */
    suspend fun isPageCached(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            return@withContext getCacheFile(url).exists()
        } catch (e: Exception) {
            Log.e(logTag, "Failed to check if page is cached: $url", e)
            return@withContext false
        }
    }
    
    /**
     * Clear all cached pages
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.d(logTag, "Cleared all cached pages")
        } catch (e: Exception) {
            Log.e(logTag, "Failed to clear cache", e)
        }
    }
    
    private fun getCacheFile(url: String): File {
        // Create a safe filename from URL hash
        val hash = MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.html")
    }
}