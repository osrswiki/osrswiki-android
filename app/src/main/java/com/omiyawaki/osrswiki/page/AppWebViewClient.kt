package com.omiyawaki.osrswiki.page

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.omiyawaki.osrswiki.network.NetworkModuleCache
import com.omiyawaki.osrswiki.page.cache.WikiPageCache
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

open class AppWebViewClient(private val linkHandler: LinkHandler) : WebViewClient() {
    private val logTag = "PageLoadTrace"
    private lateinit var moduleCache: NetworkModuleCache
    private lateinit var pageCache: WikiPageCache

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        Log.d(logTag, "AppWebViewClient.shouldOverrideUrlLoading: ${uri}")
        
        // Add specific logging for YouTube URLs to debug the "Watch on YouTube" button
        if (uri.toString().contains("youtube.com", ignoreCase = true)) {
            Log.i(logTag, "YOUTUBE URL DETECTED: $uri - attempting to process as external link")
        }
        
        // Pass the URI to the LinkHandler for processing. The LinkHandler
        // will determine if it's an internal or external link and then
        // call the appropriate method.
        linkHandler.processUri(uri)
        // Return true to indicate that the application has handled the URL.
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        
        // Initialize caches if needed
        if (!::moduleCache.isInitialized) {
            moduleCache = NetworkModuleCache.getInstance(view.context)
        }
        if (!::pageCache.isInitialized) {
            pageCache = WikiPageCache.getInstance(view.context)
        }
        
        // 1. Check NetworkModuleCache for MediaWiki load.php requests (performance caching only)
        if (moduleCache.shouldCache(url)) {
            try {
                val cachedResponse = runBlocking {
                    moduleCache.getCachedResponse(url)
                }
                if (cachedResponse != null) {
                    Log.i(logTag, "  -> INTERCEPT [HIT] in NetworkModuleCache for: ${url}")
                    return WebResourceResponse(
                        "application/javascript",
                        "UTF-8",
                        ByteArrayInputStream(cachedResponse.toByteArray())
                    )
                } else {
                    Log.d(logTag, "  -> INTERCEPT [MISS] in NetworkModuleCache, fetching from network: ${url}")
                    
                    // Fetch from network and cache the response for future use
                    val networkResponse = runBlocking {
                        fetchAndCacheModuleResponse(url)
                    }
                    if (networkResponse != null) {
                        Log.i(logTag, "  -> INTERCEPT [FETCHED] successfully cached response for: ${url}")
                        return WebResourceResponse(
                            "application/javascript",
                            "UTF-8",
                            ByteArrayInputStream(networkResponse.toByteArray())
                        )
                    } else {
                        Log.w(logTag, "  -> INTERCEPT [FAILED] could not fetch response for: ${url}")
                    }
                }
            } catch (e: Exception) {
                Log.w(logTag, "NetworkModuleCache error for $url: ${e.message}")
            }
        }

        // 2. Fallback to default behavior (let wiki load naturally from its servers)
        return super.shouldInterceptRequest(view, request)
    }


    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d(logTag, "--> WebView Event: onPageStarted()")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // This log is for the base class. The more detailed "onPageFinished" with timing
        // is in the PageWebViewManager's anonymous class override.
        Log.d(logTag, "AppWebViewClient.onPageFinished (super class call)")
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            Log.e(logTag, "AppWebViewClient: Error loading main frame: ${request.url}, Error: ${error?.description}")
        }
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true) {
            Log.e(logTag, "AppWebViewClient: HTTP error loading main frame: ${request.url}, Status: ${errorResponse?.statusCode}")
        }
    }
    
    /**
     * Fetch a module response from the network and cache it.
     * Returns the response content if successful, null otherwise.
     */
    private suspend fun fetchAndCacheModuleResponse(url: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "OSRSWiki-Android")
            connection.setRequestProperty("Accept", "application/javascript, */*")
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Cache the response
                moduleCache.cacheResponse(url, response)
                
                Log.d(logTag, "Successfully fetched and cached module response (${response.length} bytes)")
                response
            } else {
                Log.w(logTag, "Failed to fetch module: HTTP ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(logTag, "Error fetching module from network: ${e.message}")
            null
        }
    }
}
