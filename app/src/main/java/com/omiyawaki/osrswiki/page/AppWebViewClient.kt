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
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.page.cache.AssetCache
import com.omiyawaki.osrswiki.network.NetworkModuleCache
import com.omiyawaki.osrswiki.savedpages.ReadingListOfflineAssetResolver
import com.omiyawaki.osrswiki.savedpages.osrsArticleViewAssetStore
import java.io.ByteArrayInputStream

open class AppWebViewClient(private val linkHandler: LinkHandler) : WebViewClient() {
    private val logTag = "PageLoadTrace"
    private lateinit var cdnRedirector: UniversalCdnRedirector
    private lateinit var moduleCache: NetworkModuleCache
    private lateinit var readingListAssetResolver: ReadingListOfflineAssetResolver
    private val requestDiagnostics = WebViewRequestDiagnostics(logTag)

    companion object {
        private const val LOCAL_ASSET_HOST = "appassets.androidplatform.net"
        private const val WIKI_MODULE_HOST = "oldschool.runescape.wiki"

        internal fun osrsShouldOverrideMainFrameNavigation(request: WebResourceRequest): Boolean {
            return request.isForMainFrame
        }

        internal fun osrsShouldOpenExternalUriWithoutUserGesture(request: WebResourceRequest): Boolean {
            return request.isForMainFrame && !request.hasGesture() && osrsIsExternalMediaHost(request.url.host)
        }

        internal fun osrsIsExternalMediaHost(host: String?): Boolean {
            val normalized = host?.lowercase() ?: return false
            return normalized == "youtu.be" ||
                normalized == "youtube.com" ||
                normalized.endsWith(".youtube.com") ||
                normalized == "youtube-nocookie.com" ||
                normalized.endsWith(".youtube-nocookie.com")
        }

        internal fun normalizeModuleCacheUrl(url: String): String {
            return rewriteLocalAssetHost(url, pathPrefix = "/load.php", exactPath = true)
        }

        internal fun normalizeWikiStaticUrl(url: String): String {
            return rewriteLocalAssetHost(url, pathPrefix = "/images/", exactPath = false)
        }

        internal fun normalizeWikiApiUrl(url: String): String {
            return osrsWikiWebViewUrl.rewriteToWiki(url)
        }

        private fun rewriteLocalAssetHost(
            url: String,
            pathPrefix: String,
            exactPath: Boolean
        ): String {
            return try {
                val uri = Uri.parse(url)
                val path = uri.path ?: return url
                val matches = if (exactPath) path == pathPrefix else path.startsWith(pathPrefix)
                if (uri.host == LOCAL_ASSET_HOST && matches) {
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

        if (!osrsShouldOverrideMainFrameNavigation(request)) {
            Log.d(logTag, "Allowing subframe/embed navigation in WebView: $uri")
            return false
        }

        if (osrsShouldOpenExternalUriWithoutUserGesture(request)) {
            Log.i(logTag, "Ignoring ungestured main-frame media navigation without opening an external app: $uri")
            return true
        }

        if (uri.toString().contains("youtube.com", ignoreCase = true)) {
            Log.i(logTag, "YOUTUBE URL DETECTED: $uri - attempting to process as external link")
        }

        linkHandler.processUri(uri)
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()

        osrsWikiWebViewProxy.intercept(request, view.context)?.let { proxied ->
            Log.i(logTag, "  -> INTERCEPT [HIT] wiki calculator/API proxy for: $url")
            return proxied
        }
        
        // Initialize CDN redirector and module cache if needed
        if (!::cdnRedirector.isInitialized) {
            cdnRedirector = UniversalCdnRedirector.getInstance(view.context)
        }
        if (!::moduleCache.isInitialized) {
            moduleCache = NetworkModuleCache.getInstance(view.context)
        }
        if (!::readingListAssetResolver.isInitialized) {
            readingListAssetResolver = ReadingListOfflineAssetResolver(
                view.context.applicationContext,
                AppDatabase.instance.offlineObjectDao()
            )
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
        
        // 2. Explicit reading-list saves own durable media independently of the process-memory
        // cache. This exact-URL lookup keeps images/GIFs available after restart and offline.
        readingListAssetResolver.open(url)?.let { savedAsset ->
            Log.i(logTag, "  -> INTERCEPT [HIT] in reading-list asset store for: $url")
            return WebResourceResponse(savedAsset.mimeType, savedAsset.encoding, savedAsset.stream)
        }

        // 3. Check NetworkModuleCache for MediaWiki load.php requests
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
        
        // 4. Check for CDN redirection using automated mapping
        try {
            val cdnResponse = cdnRedirector.shouldRedirectRequest(request)
            if (cdnResponse != null) {
                return cdnResponse
            }
        } catch (e: Exception) {
            Log.w(logTag, "CDN redirector error for $url: ${e.message}")
        }

        // 5. Capture live wiki media into a session store so first save can copy instead of GET.
        if (
            !request.isForMainFrame &&
            request.method.equals("GET", ignoreCase = true) &&
            osrsArticleViewAssetStore.isEligible(url)
        ) {
            osrsArticleViewAssetStore.install(view.context)
            osrsArticleViewAssetStore.openWebResponse(url)?.let { sessionAsset ->
                Log.i(logTag, "  -> INTERCEPT [HIT] in article-view session store for: $url")
                return sessionAsset
            }
        }

        // 6. Fallback to default behavior (local assets via WebViewAssetLoader or network)
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
