package com.omiyawaki.osrswiki.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest

/**
 * Network-level cache for MediaWiki ResourceLoader modules.
 * 
 * This cache intercepts and stores responses from /load.php endpoints,
 * allowing MediaWiki's ResourceLoader to work naturally while providing
 * offline functionality.
 * 
 * This approach is much more maintainable than trying to reverse-engineer
 * MediaWiki's module loading system.
 */
class NetworkModuleCache private constructor(context: Context) {
    
    companion object {
        private const val TAG = "NetworkModuleCache"
        private const val CACHE_DIR = "mediawiki_modules"
        private const val MAX_CACHE_SIZE_MB = 50L
        
        @Volatile
        private var INSTANCE: NetworkModuleCache? = null
        
        fun getInstance(context: Context): NetworkModuleCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkModuleCache(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val cacheDir: File = File(context.cacheDir, CACHE_DIR).apply {
        if (!exists()) {
            mkdirs()
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Check if a response is cached for the given URL.
     */
    fun isCached(url: String): Boolean {
        val cacheKey = generateCacheKey(url)
        val cacheFile = File(cacheDir, cacheKey)
        return cacheFile.exists()
    }
    
    /**
     * Get cached response for the given URL.
     * Returns null if not cached.
     */
    suspend fun getCachedResponse(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val cacheKey = generateCacheKey(url)
            val cacheFile = File(cacheDir, cacheKey)
            
            if (cacheFile.exists()) {
                Log.d(TAG, "Cache HIT for: ${getModulesFromUrl(url)}")
                return@withContext cacheFile.readText()
            }
            
            Log.d(TAG, "Cache MISS for: ${getModulesFromUrl(url)}")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from cache: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Cache a response for the given URL.
     */
    fun cacheResponse(url: String, response: String) {
        scope.launch {
            try {
                val cacheKey = generateCacheKey(url)
                val cacheFile = File(cacheDir, cacheKey)
                
                cacheFile.writeText(response)
                Log.d(TAG, "Cached response for: ${getModulesFromUrl(url)} (${response.length} bytes)")
                
                // Clean up old cache entries if needed
                cleanupCacheIfNeeded()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to cache: ${e.message}")
            }
        }
    }
    
    /**
     * Check if this URL should be cached.
     * Only cache load.php requests for modules.
     */
    fun shouldCache(url: String): Boolean {
        return url.contains("/load.php") && 
               (url.contains("modules=") || url.contains("&modules"))
    }
    
    /**
     * Generate a cache key from a URL.
     * Uses URL parameters to create a unique, filesystem-safe key.
     */
    private fun generateCacheKey(url: String): String {
        return try {
            val urlObj = URL(url)
            val query = urlObj.query ?: ""
            
            // Create hash of the query parameters
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(query.toByteArray())
            
            // Convert to hex string
            hashBytes.joinToString("") { "%02x".format(it) } + ".js"
        } catch (e: Exception) {
            // Fallback: simple hash of the entire URL
            url.hashCode().toString() + ".js"
        }
    }
    
    /**
     * Extract module names from URL for logging.
     */
    private fun getModulesFromUrl(url: String): String {
        return try {
            val modulesParam = url.substringAfter("modules=").substringBefore("&")
            if (modulesParam.length > 50) {
                "${modulesParam.take(47)}..."
            } else {
                modulesParam
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * Clean up cache if it gets too large.
     */
    private suspend fun cleanupCacheIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val cacheSize = cacheDir.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum()
            
            val cacheSizeMB = cacheSize / (1024 * 1024)
            
            if (cacheSizeMB > MAX_CACHE_SIZE_MB) {
                Log.d(TAG, "Cache size (${cacheSizeMB}MB) exceeds limit, cleaning up...")
                
                // Delete oldest files first
                val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return@withContext
                
                var deletedSize = 0L
                val targetSize = MAX_CACHE_SIZE_MB * 0.8 * 1024 * 1024 // 80% of max size
                
                for (file in files) {
                    if (cacheSize - deletedSize <= targetSize) break
                    
                    deletedSize += file.length()
                    if (file.delete()) {
                        Log.d(TAG, "Deleted old cache file: ${file.name}")
                    }
                }
                
                Log.d(TAG, "Cache cleanup completed. Deleted ${deletedSize / (1024 * 1024)}MB")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache cleanup: ${e.message}")
        }
    }
    
    /**
     * Clear all cached responses.
     */
    fun clearCache() {
        scope.launch {
            try {
                val deletedCount = cacheDir.listFiles()?.size ?: 0
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
                Log.d(TAG, "Cache cleared. Deleted $deletedCount files.")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing cache: ${e.message}")
            }
        }
    }
    
    /**
     * Get cache statistics.
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles() ?: emptyArray()
            val totalSize = files.sumOf { it.length() }
            
            CacheStats(
                fileCount = files.size,
                totalSizeBytes = totalSize,
                totalSizeMB = totalSize / (1024 * 1024)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cache stats: ${e.message}")
            CacheStats(0, 0, 0)
        }
    }
}

/**
 * Cache statistics data class.
 */
data class CacheStats(
    val fileCount: Int,
    val totalSizeBytes: Long,
    val totalSizeMB: Long
)