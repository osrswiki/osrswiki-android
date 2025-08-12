package com.omiyawaki.osrswiki.page

import android.graphics.Bitmap
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.omiyawaki.osrswiki.page.cache.AssetCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream

open class AppWebViewClient(private val linkHandler: LinkHandler) : WebViewClient() {
    private val logTag = "PageLoadTrace"
    private lateinit var cdnRedirector: UniversalCdnRedirector

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
        
        // Initialize CDN redirector if needed
        if (!::cdnRedirector.isInitialized) {
            cdnRedirector = UniversalCdnRedirector.getInstance(view.context)
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
        
        // 2. Check for CDN redirection using automated mapping
        try {
            val cdnResponse = runBlocking {
                cdnRedirector.shouldRedirectRequest(request)
            }
            if (cdnResponse != null) {
                return cdnResponse
            }
        } catch (e: Exception) {
            Log.w(logTag, "CDN redirector error for $url: ${e.message}")
        }

        // 3. Fallback to default behavior (local assets via WebViewAssetLoader or network)
        Log.w(logTag, "  -> INTERCEPT [MISS] in AssetCache and CDN mapping for: $url")
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
}
