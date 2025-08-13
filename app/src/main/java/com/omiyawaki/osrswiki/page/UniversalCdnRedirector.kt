package com.omiyawaki.osrswiki.page

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Universal CDN redirector that redirects external JavaScript library requests 
 * to local assets based on CDN mapping.
 * 
 * Note: CDN mapping functionality is currently disabled as automation tools were removed.
 * This class maintains interface compatibility but returns empty mappings.
 */
class UniversalCdnRedirector(private val context: Context) {
    private val logTag = "UniversalCdnRedirector"
    private var cdnMapping: Map<String, Map<String, String>>? = null
    private var lastLoadAttempt = 0L
    private val reloadInterval = 30_000L // 30 seconds
    
    /**
     * Load CDN mapping. Returns empty mapping since automation tools were removed.
     * This method is kept for interface compatibility.
     */
    private suspend fun loadCdnMapping(): Map<String, Map<String, String>> {
        val currentTime = System.currentTimeMillis()
        
        // Return cached mapping if available and recent
        if (cdnMapping != null && (currentTime - lastLoadAttempt) < reloadInterval) {
            return cdnMapping!!
        }
        
        lastLoadAttempt = currentTime
        
        // Return empty mapping since automation tools and network_trace.json were removed
        cdnMapping = emptyMap()
        return emptyMap()
    }
    
    /**
     * Check if a request should be redirected to a local asset.
     * Returns the WebResourceResponse for the local asset if redirect applies, null otherwise.
     */
    suspend fun shouldRedirectRequest(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val host = request.url.host ?: return null
        val path = request.url.path ?: return null
        
        // Extract filename from path
        val filename = path.split("/").lastOrNull()?.split("?")?.firstOrNull()
        if (filename.isNullOrBlank()) {
            return null
        }
        
        // Load CDN mapping
        val mapping = loadCdnMapping()
        
        // Check if this host and file are in our CDN mapping
        val hostMapping = mapping[host] ?: return null
        val localAssetPath = hostMapping[filename] ?: return null
        
        try {
            // Load the local asset
            val assetStream = context.assets.open(localAssetPath)
            
            // Determine MIME type based on file extension
            val mimeType = when {
                filename.endsWith(".js") -> "application/javascript"
                filename.endsWith(".css") -> "text/css"
                filename.endsWith(".json") -> "application/json"
                else -> "application/octet-stream"
            }
            
            Log.i(logTag, "CDN REDIRECT: $url -> $localAssetPath")
            
            return WebResourceResponse(
                mimeType,
                "UTF-8",
                assetStream
            )
            
        } catch (e: Exception) {
            Log.w(logTag, "Failed to load local asset for CDN redirect: $localAssetPath - ${e.message}")
            return null
        }
    }
    
    /**
     * Get statistics about the loaded CDN mapping for debugging.
     */
    suspend fun getMappingStats(): String {
        val mapping = loadCdnMapping()
        val totalHosts = mapping.size
        val totalFiles = mapping.values.sumOf { it.size }
        
        val hostDetails = mapping.entries.take(5).joinToString(", ") { (host, files) ->
            "$host(${files.size})"
        }
        
        return "CDN Mapping: $totalHosts hosts, $totalFiles files. Examples: $hostDetails"
    }
    
    companion object {
        @Volatile
        private var instance: UniversalCdnRedirector? = null
        
        fun getInstance(context: Context): UniversalCdnRedirector {
            return instance ?: synchronized(this) {
                instance ?: UniversalCdnRedirector(context.applicationContext).also { instance = it }
            }
        }
    }
}