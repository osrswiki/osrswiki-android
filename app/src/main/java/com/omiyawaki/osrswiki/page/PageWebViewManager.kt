package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.net.Uri
import android.os.Build
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import com.omiyawaki.osrswiki.page.cache.WikiPageCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface RenderCallback {
    fun onWebViewLoadFinished()
    fun onPageReadyForDisplay()
}

class PageWebViewManager(
    private val webView: WebView,
    private val linkHandler: PageLinkHandler,
    private val onTitleReceived: (String) -> Unit,
    private val jsInterface: Any?,
    private val jsInterfaceName: String?,
    private val renderCallback: RenderCallback,
    private val onRenderProgress: (Int) -> Unit
) {
    private val logTag = "PageLoadTrace"
    private val consoleTag = "WebViewConsole"
    private val managerTag = "PageWebViewManager"
    private var renderStartTime: Long = 0
    private var pageLoaded = false
    private val pageCache = WikiPageCache.getInstance(webView.context)

    private val localAssetDomain = "appassets.androidplatform.net"

    private val assetLoader = WebViewAssetLoader.Builder()
        .setDomain(localAssetDomain)
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(webView.context))
        .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(webView.context))
        .build()

    private inner class RenderTimelineLogger {
        @JavascriptInterface
        fun log(message: String) {
            val elapsed = System.currentTimeMillis() - renderStartTime
            Log.d(logTag, "JS TIMELINE [${elapsed}ms]: $message")

            if (message == "Event: StylingScriptsComplete") {
                webView.post {
                    renderCallback.onPageReadyForDisplay()
                }
            }
        }
    }

    private inner class ClipboardBridge {
        @JavascriptInterface
        fun writeText(text: String): Boolean {
            return try {
                val clipboardManager = webView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("OSRS Wiki", text)
                clipboardManager.setPrimaryClip(clipData)
                Log.d("$consoleTag-CLIPBOARD", "Successfully copied text via Android bridge: $text")
                true
            } catch (e: Exception) {
                Log.e("$consoleTag-CLIPBOARD", "Failed to copy text via Android bridge: ${e.message}")
                false
            }
        }
        
        @JavascriptInterface
        fun readText(): String {
            return try {
                val clipboardManager = webView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                    Log.d("$consoleTag-CLIPBOARD", "Successfully read text via Android bridge")
                    text
                } else {
                    Log.d("$consoleTag-CLIPBOARD", "No text available in clipboard")
                    ""
                }
            } catch (e: Exception) {
                Log.e("$consoleTag-CLIPBOARD", "Failed to read text via Android bridge: ${e.message}")
                ""
            }
        }
    }

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        if (jsInterface != null && jsInterfaceName != null) {
            webView.addJavascriptInterface(jsInterface, jsInterfaceName)
        }
        webView.addJavascriptInterface(RenderTimelineLogger(), "RenderTimeline")
        webView.addJavascriptInterface(ClipboardBridge(), "ClipboardBridge")


        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Enable necessary permissions for iframe clipboard access
            allowUniversalAccessFromFileURLs = true
            allowFileAccessFromFileURLs = true
            // Allow popups and new windows for share functionality
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            // Enable focus management for better iframe interaction
            setNeedInitialFocus(true)
        }

        webView.webViewClient = object : AppWebViewClient(linkHandler) {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                Log.d(logTag, "--> WebView Event: onPageStarted() called. URL: $url")
                super.onPageStarted(view, url, favicon)
            }
            
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val urlString = request.url.toString()
                val localAssetResponse = assetLoader.shouldInterceptRequest(request.url)
                if (localAssetResponse != null) {
                    Log.i(logTag, " -> INTERCEPT [HIT] in WebViewAssetLoader for: $urlString")
                    return localAssetResponse
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val elapsedTime = System.currentTimeMillis() - renderStartTime
                Log.d(logTag, "--> WebView Event: onPageFinished() called. Elapsed: ${elapsedTime}ms. URL: $url")
                Log.d(logTag, "--> WebView Content Info: Title: '${view?.title}', Progress: ${view?.progress}%")
                
                // Check if page content is actually loaded
                view?.evaluateJavascript("document.body ? document.body.innerHTML.length : 0") { result ->
                    Log.d(logTag, "--> WebView Content Length: $result characters")
                    if (result == "0") {
                        Log.w(logTag, "--> WARNING: WebView finished loading but body content is empty!")
                    }
                }
                
                // If this is a direct wiki page load, inject app-specific features and cache the page
                if (url?.contains("oldschool.runescape.wiki") == true && view != null) {
                    injectAppFeatures(view)
                    cacheCurrentPage(view, url)
                    
                    // For direct loading, immediately mark as ready for display
                    // This will trigger the completion sequence and hide the loading overlay
                    pageLoaded = true
                    renderCallback.onPageReadyForDisplay()
                } else {
                    // For HTML-based loading (if any remains), use the old flow
                    pageLoaded = true
                    renderCallback.onWebViewLoadFinished()
                }
                
                super.onPageFinished(view, url)
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                val crashDetails = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail?.didCrash() == true) {
                    "CRASHED"
                } else {
                    "KILLED_BY_OS_OR_OTHER"
                }
                Log.e(logTag, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                Log.e(logTag, "!!! WebView RENDERER PROCESS GONE. Reason: $crashDetails")
                Log.e(logTag, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                return true
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                val elapsed = System.currentTimeMillis() - renderStartTime
                val cappedProgress = newProgress.coerceAtMost(95)
                Log.d(logTag, "--> WebView Progress: ${newProgress}%. Capped at ${cappedProgress}%. Elapsed: ${elapsed}ms.")
                
                // For direct loading, map WebView progress (0-100%) to UI progress (10-95%)
                // Start from 10% (initial state) and cap at 95% to leave room for completion
                val uiProgress = 10 + (cappedProgress * 0.85).toInt()
                if (uiProgress < 100) {
                    onRenderProgress(uiProgress)
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let { onTitleReceived(it) }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    val message = "[${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}] ${consoleMessage.message()}"
                    
                    // Log clipboard-related errors with higher visibility
                    if (message.contains("clipboard", ignoreCase = true) || 
                        message.contains("copy", ignoreCase = true) ||
                        message.contains("navigator.clipboard", ignoreCase = true)) {
                        Log.e("$consoleTag-CLIPBOARD", "CLIPBOARD ERROR: $message")
                    }
                    
                    when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> Log.e(consoleTag, message)
                        ConsoleMessage.MessageLevel.WARNING -> Log.w(consoleTag, message)
                        else -> Log.i(consoleTag, message)
                    }
                }
                return true
            }

            /**
             * Intercepts window.open() calls from cross-origin iframes, including
             * the "Watch on YouTube" button in YouTube embedded videos.
             */
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                Log.d(managerTag, "onCreateWindow called - intercepting new window request")
                
                // Create a temporary WebView to receive the URL from the window.open() request
                val tempWebView = WebView(view!!.context)
                
                // Set a WebViewClient on the temporary WebView to intercept the navigation
                tempWebView.webViewClient = object : WebViewClient() {
                    @Suppress("DEPRECATION") // For older Android versions
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        Log.d(managerTag, "Intercepted new window URL: $url")
                        // Pass the URL to our existing link handler to open externally
                        linkHandler.processUri(Uri.parse(url))
                        // Clean up the temporary WebView
                        view.destroy()
                        return true
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        Log.d(managerTag, "Intercepted new window URL: ${request.url}")
                        // Pass the URL to our existing link handler to open externally
                        linkHandler.processUri(request.url)
                        // Clean up the temporary WebView
                        view.destroy()
                        return true
                    }
                }

                // The transport mechanism needs a WebView to be sent back
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()

                // Return true to indicate we have handled the new window creation
                return true
            }
        }
        
        // Enable focus management for iframe interactions
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.setBackgroundColor(Color.TRANSPARENT)
    }

    fun finalizeAndRevealPage(onComplete: () -> Unit) {
        applyThemeColors(webView) {
            revealBody(onComplete)
        }
    }

    private fun applyThemeColors(view: WebView?, onFinished: () -> Unit) {
        val context = view?.context ?: return
        val themeColors = mapOf(
            "--colorsurface" to getThemeColor(context, com.google.android.material.R.attr.colorSurface, "#FFFFFF"),
            "--coloronsurface" to getThemeColor(context, com.google.android.material.R.attr.colorOnSurface, "#000000"),
            "--colorsurfacevariant" to getThemeColor(context, com.google.android.material.R.attr.colorSurfaceVariant, "#E0E0E0"),
            "--coloronsurfacevariant" to getThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, "#424242"),
            "--colorprimarycontainer" to getThemeColor(context, com.google.android.material.R.attr.colorPrimaryContainer, "#D0C0A0"),
            "--coloronprimarycontainer" to getThemeColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer, "#000000")
        )

        val jsObjectString = themeColors.entries.joinToString(separator = ",\n") {
            "    '${it.key}': '${it.value}'"
        }

        val script = """
        (function() {
            const themeColors = {
            $jsObjectString
            };
            for (const [key, value] of Object.entries(themeColors)) {
                document.documentElement.style.setProperty(key, value);
            }
        })();
        """.trimIndent()

        Log.d(managerTag, "Applying theme colors via JavaScript.")
        view.evaluateJavascript(script) {
            Log.d(managerTag, "Theme colors applied. Proceeding to reveal body.")
            onFinished()
        }
    }

    private fun getThemeColor(context: Context, attrId: Int, fallback: String): String {
        val typedValue = TypedValue()
        if (!context.theme.resolveAttribute(attrId, typedValue, true)) {
            Log.e(managerTag, "Failed to resolve theme attribute ID #$attrId")
            return fallback
        }
        val color: Int = try {
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                typedValue.data
            } else {
                ContextCompat.getColor(context, typedValue.resourceId)
            }
        } catch (e: Resources.NotFoundException) {
            Log.e(managerTag, "Color resource not found for attribute ID #$attrId", e)
            return fallback
        }
        return String.format("#%06X", (0xFFFFFF and color))
    }

    fun render(fullHtml: String) {
        pageLoaded = false
        renderStartTime = System.currentTimeMillis()
        Log.d(logTag, "==> Event: render() called. Starting timer.")
        Log.d(logTag, "==> HTML Preview (first 200 chars): ${fullHtml.take(200)}...")
        Log.d(logTag, "==> HTML contains <body>: ${fullHtml.contains("<body")}")
        Log.d(logTag, "==> HTML contains content: ${fullHtml.contains("content", ignoreCase = true)}")
        
        // MediaWiki will now load modules naturally from the correct domain
        
        val baseUrl = "https://$localAssetDomain/"
        Log.d(logTag, ">>> Calling webView.loadDataWithBaseURL()... (HTML size: ${fullHtml.length} chars, BaseURL: $baseUrl)")
        
        
        webView.loadDataWithBaseURL(
            baseUrl,
            fullHtml,
            "text/html",
            "UTF-8",
            null
        )
        Log.d(logTag, "<<< Returned from webView.loadDataWithBaseURL().")
    }

    // New method for direct wiki page loading
    fun loadUrlDirectly(url: String) {
        pageLoaded = false
        renderStartTime = System.currentTimeMillis()
        Log.d(logTag, "==> Event: loadUrlDirectly() called for: $url")
        
        webView.loadUrl(url)
        Log.d(logTag, "<<< Returned from webView.loadUrl().")
    }


    private fun revealBody(onComplete: () -> Unit) {
        val revealBodyJs = "document.body.style.visibility = 'visible';"
        webView.evaluateJavascript(revealBodyJs) {
            // This completion handler for evaluateJavascript runs after the JS has executed.
            onComplete()
        }
    }
    
    /**
     * Inject app-specific CSS and JavaScript features into a directly loaded wiki page
     */
    private fun injectAppFeatures(webView: WebView?) {
        webView ?: return
        
        Log.d(logTag, "Injecting app-specific features into wiki page")
        
        // Inject app-specific CSS for theme integration and mobile optimization
        val appCssInjection = """
            (function() {
                const style = document.createElement('style');
                style.textContent = `
                    /* App-specific theme integration */
                    .theme-osrs-dark .mw-body,
                    .theme-osrs-dark .vector-menu-content {
                        background: var(--colorsurface, #1a1a1a) !important;
                        color: var(--coloronsurface, #ffffff) !important;
                    }
                    
                    /* Mobile optimization for app */
                    @media (max-width: 720px) {
                        .infobox {
                            width: 100% !important;
                            max-width: none !important;
                        }
                        
                        .navbox {
                            font-size: 0.9em;
                        }
                    }
                    
                    /* Enhanced clipboard functionality styling */
                    .clipboard-success {
                        background: rgba(0, 255, 0, 0.1);
                        transition: background 0.3s ease;
                    }
                `;
                document.head.appendChild(style);
            })();
        """.trimIndent()
        
        // Inject clipboard bridge integration for wiki content
        val clipboardBridgeIntegration = """
            (function() {
                // Enhance existing copy functionality with our native bridge
                if (window.ClipboardBridge && window.ClipboardBridge.writeText) {
                    // Override any existing clipboard functionality to use our bridge
                    if (navigator.clipboard && navigator.clipboard.writeText) {
                        const originalWriteText = navigator.clipboard.writeText;
                        navigator.clipboard.writeText = function(text) {
                            try {
                                if (window.ClipboardBridge.writeText(text)) {
                                    return Promise.resolve();
                                } else {
                                    return originalWriteText.call(this, text);
                                }
                            } catch (e) {
                                return originalWriteText.call(this, text);
                            }
                        };
                    }
                }
            })();
        """.trimIndent()
        
        // Execute the injections
        webView.evaluateJavascript(appCssInjection) { 
            Log.d(logTag, "App CSS injection completed")
        }
        
        webView.evaluateJavascript(clipboardBridgeIntegration) { 
            Log.d(logTag, "Clipboard bridge integration completed")
        }
    }
    
    /**
     * Cache the current page HTML for offline access
     */
    private fun cacheCurrentPage(webView: WebView, url: String) {
        webView.evaluateJavascript("document.documentElement.outerHTML") { html ->
            // Remove JavaScript wrapper quotes and unescape the HTML
            val cleanHtml = html?.let { 
                if (it.startsWith("\"") && it.endsWith("\"")) {
                    it.substring(1, it.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                        .replace("\\\\", "\\")
                } else {
                    it
                }
            }
            
            if (!cleanHtml.isNullOrEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    pageCache.cachePage(url, cleanHtml)
                }
            }
        }
    }
}
