package com.omiyawaki.osrswiki.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cache warming mechanism for MediaWiki modules.
 * 
 * This class proactively fetches and caches modules that will be needed
 * for pages, improving performance by having modules ready before the
 * WebView requests them.
 */
class ModuleCacheWarmer private constructor(context: Context) {
    
    companion object {
        private const val TAG = "ModuleCacheWarmer"
        private const val BASE_LOAD_URL = "https://oldschool.runescape.wiki/load.php"
        
        @Volatile
        private var INSTANCE: ModuleCacheWarmer? = null
        
        fun getInstance(context: Context): ModuleCacheWarmer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModuleCacheWarmer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val moduleCache = NetworkModuleCache.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Warm the cache with modules needed for a page.
     * 
     * @param pageModules List of module names from RLPAGEMODULES
     * @param isMobile Whether this is for mobile skin (affects some parameters)
     */
    fun warmCacheForPage(pageModules: List<String>, isMobile: Boolean = true) {
        if (pageModules.isEmpty()) {
            Log.d(TAG, "No modules to warm cache for")
            return
        }
        
        scope.launch {
            try {
                Log.d(TAG, "Starting cache warming for ${pageModules.size} modules")
                
                // Group modules into batches for efficient loading
                val batches = createModuleBatches(pageModules)
                
                for ((index, batch) in batches.withIndex()) {
                    val loadUrl = buildLoadUrl(batch, isMobile)
                    
                    // Check if already cached
                    if (moduleCache.isCached(loadUrl)) {
                        Log.d(TAG, "Batch ${index + 1}/${batches.size} already cached: ${batch.take(3).joinToString(",")}")
                        continue
                    }
                    
                    Log.d(TAG, "Warming cache for batch ${index + 1}/${batches.size}: ${batch.take(3).joinToString(",")}")
                    
                    val response = fetchModuleBatch(loadUrl)
                    if (response != null) {
                        moduleCache.cacheResponse(loadUrl, response)
                        Log.d(TAG, "Successfully cached batch ${index + 1} (${response.length} bytes)")
                    } else {
                        Log.w(TAG, "Failed to fetch batch ${index + 1}")
                    }
                }
                
                Log.d(TAG, "Cache warming completed for ${pageModules.size} modules")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during cache warming: ${e.message}")
            }
        }
    }
    
    /**
     * Group modules into batches for efficient loading.
     * MediaWiki's ResourceLoader can handle multiple modules in one request.
     */
    private fun createModuleBatches(modules: List<String>): List<List<String>> {
        val batchSize = 10 // Load up to 10 modules per request
        return modules.chunked(batchSize)
    }
    
    /**
     * Build a load.php URL for a batch of modules.
     */
    private fun buildLoadUrl(modules: List<String>, isMobile: Boolean): String {
        val modulesParam = modules.joinToString("|") { URLEncoder.encode(it, "UTF-8") }
        
        val params = mutableMapOf(
            "modules" to modulesParam,
            "only" to "scripts",
            "skin" to if (isMobile) "minerva" else "vector",
            "debug" to "false",
            "lang" to "en-gb",
            "version" to "" // Let MediaWiki handle versioning
        )
        
        val queryString = params.map { (key, value) -> 
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}" 
        }.joinToString("&")
        
        return "$BASE_LOAD_URL?$queryString"
    }
    
    /**
     * Fetch a batch of modules from the network.
     */
    private suspend fun fetchModuleBatch(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "OSRSWiki-Android")
            connection.setRequestProperty("Accept", "application/javascript, */*")
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Successfully fetched module batch (${response.length} bytes)")
                response
            } else {
                Log.w(TAG, "Failed to fetch module batch: HTTP ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching module batch: ${e.message}")
            null
        }
    }
    
    /**
     * Warm cache with commonly needed modules.
     * This can be called on app startup to pre-cache essential modules.
     */
    fun warmCacheWithEssentials() {
        val essentialModules = listOf(
            "jquery",
            "mediawiki.base",
            "mediawiki.util",
            "mediawiki.page.ready",
            "ext.gadget.rsw-util",
            "ext.gadget.GECharts",
            "ext.gadget.tooltips"
        )
        
        Log.d(TAG, "Warming cache with essential modules")
        warmCacheForPage(essentialModules)
    }
    
    /**
     * Get cache warming statistics.
     */
    suspend fun getWarmingStats(): WarmingStats = withContext(Dispatchers.IO) {
        val cacheStats = moduleCache.getCacheStats()
        WarmingStats(
            totalCachedModules = cacheStats.fileCount,
            totalCacheSize = cacheStats.totalSizeMB
        )
    }
}

/**
 * Cache warming statistics data class.
 */
data class WarmingStats(
    val totalCachedModules: Int,
    val totalCacheSize: Long
)