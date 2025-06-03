package com.omiyawaki.osrswiki.page

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.omiyawaki.osrswiki.util.log.L // Assuming L is your logging utility, adjust if needed

open class AppWebViewClient(private val linkHandler: LinkHandler) : WebViewClient() { // Added "open" keyword

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (uri != null) {
            L.d("AppWebViewClient: Intercepted URL click: ${uri}")

            // Pass the URI to the LinkHandler for processing.
            // The LinkHandler will determine if it's an internal or external link
            // and then call the appropriate method (onInternalArticleLinkClicked or onExternalLinkClicked).
            linkHandler.processUri(uri)

            // Return true to indicate that the application has handled the URL.
            // The WebView should not proceed with loading this URL itself.
            return true
        } else {
            L.w("AppWebViewClient: URI from WebResourceRequest was null. Allowing WebView to handle.")
            // If the URI is null for some reason, let the WebView try to handle it.
            return super.shouldOverrideUrlLoading(view, request)
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        L.d("AppWebViewClient: Page loading started: $url")
        // TODO: Implement any onPageStarted logic if needed (e.g., show progress bar)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        L.d("AppWebViewClient: Page loading finished: $url")
        // TODO: Implement any onPageFinished logic if needed (e.g., hide progress bar, inject JavaScript)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            L.e("AppWebViewClient: Error loading main frame: ${request.url}, Error: ${error?.description}")
            // TODO: Handle main frame loading errors (e.g., show an error page)
        }
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true) {
            L.e("AppWebViewClient: HTTP error loading main frame: ${request.url}, Status: ${errorResponse?.statusCode}")
            // TODO: Handle HTTP errors for the main frame
        }
    }
}
