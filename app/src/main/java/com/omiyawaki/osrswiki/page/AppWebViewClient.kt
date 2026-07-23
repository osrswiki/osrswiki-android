package com.omiyawaki.osrswiki.page

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.omiyawaki.osrswiki.page.cache.AssetCache
import com.omiyawaki.osrswiki.network.NetworkModuleCache
import java.io.ByteArrayInputStream

open class AppWebViewClient(private val linkHandler: LinkHandler) : WebViewClient() {
    private val logTag = "PageLoadTrace"
    private lateinit var cdnRedirector: UniversalCdnRedirector
    private lateinit var moduleCache: NetworkModuleCache
    private val requestDiagnostics = WebViewRequestDiagnostics(logTag)

    companion object {
        private const val LOCAL_ASSET_HOST = "appassets.androidplatform.net"
        private const val WIKI_MODULE_HOST = "oldschool.runescape.wiki"

        internal fun normalizeModuleCacheUrl(url: String): String {
            return try {
                val uri = Uri.parse(url)
                if (uri.host == LOCAL_ASSET_HOST && uri.path == "/load.php") {
                    uri.buildUpon()
                        .scheme("https")
                        .authority(WIKI_MODULE_HOST)
                        .build()
                        .toString()
                } else {
                    url
                }
            } catch (e: Exception) {
                url
            }
        }
    }

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
        
        // Initialize CDN redirector and module cache if needed
        if (!::cdnRedirector.isInitialized) {
            cdnRedirector = UniversalCdnRedirector.getInstance(view.context)
        }
        if (!::moduleCache.isInitialized) {
            moduleCache = NetworkModuleCache.getInstance(view.context)
        }
        
        // 1. First check AssetCache for existing cached resources
        val cachedAsset = AssetCache.get(url)
        if (cachedAsset != null) {
            Log.i(logTag, "  -> INTERCEPT [HIT] in AssetCache for: $url")
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(url)
            ) ?: "application/octet-stream" // Default MIME type if lookup fails

            return WebResourceResponse(
                mimeType,
                "UTF-8",
                ByteArrayInputStream(cachedAsset)
            )
        }
        
        // 2. Check NetworkModuleCache for MediaWiki load.php requests
        if (moduleCache.shouldCache(url)) {
            val moduleUrl = normalizeModuleCacheUrl(url)
            try {
                val cachedResponse = moduleCache.getCachedResponseIfPresent(moduleUrl)
                if (cachedResponse != null) {
                    Log.i(logTag, "  -> INTERCEPT [HIT] in NetworkModuleCache for: $moduleUrl")
                    return javascriptResponse(cachedResponse)
                }

                if (!isNetworkAvailable(view.context)) {
                    requestDiagnostics.logOfflineMiss(moduleUrl)
                    return javascriptResponse("")
                }

                Log.d(logTag, "  -> INTERCEPT [MISS] in NetworkModuleCache, allowing WebView network load: $moduleUrl")
                return null
            } catch (e: Exception) {
                Log.w(logTag, "NetworkModuleCache error for $moduleUrl: ${e.message}")
                return javascriptResponse("")
            }
        }
        
        // 3. Check for CDN redirection using automated mapping
        try {
            val cdnResponse = cdnRedirector.shouldRedirectRequest(request)
            if (cdnResponse != null) {
                return cdnResponse
            }
        } catch (e: Exception) {
            Log.w(logTag, "CDN redirector error for $url: ${e.message}")
        }

        // 4. Fallback to default behavior (local assets via WebViewAssetLoader or network)
        Log.d(logTag, "  -> INTERCEPT [MISS] in local caches for: $url")
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
    
    private fun javascriptResponse(source: String): WebResourceResponse {
        return WebResourceResponse(
            "application/javascript",
            "UTF-8",
            ByteArrayInputStream(source.toByteArray())
        )
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

private class WebViewRequestDiagnostics(private val tag: String) {
    private val offlineMissCounts = mutableMapOf<String, Int>()

    fun logOfflineMiss(url: String) {
        val count = (offlineMissCounts[url] ?: 0) + 1
        offlineMissCounts[url] = count
        when (count) {
            1 -> Log.i(tag, "  -> INTERCEPT [OFFLINE] no cached module response for: $url")
            2 -> Log.d(tag, "  -> INTERCEPT [OFFLINE] repeated miss suppressed for: $url")
            else -> Unit
        }
    }
}
