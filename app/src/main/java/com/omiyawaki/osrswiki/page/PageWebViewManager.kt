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
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import com.omiyawaki.osrswiki.settings.Prefs

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
    private val onRenderProgress: (Int) -> Unit,
    private val onRenderProcessGone: () -> Unit = {}
) {
    private val logTag = "PageLoadTrace"
    private val consoleTag = "WebViewConsole"
    private val managerTag = "PageWebViewManager"
    private var renderStartTime: Long = 0
    private var pageLoaded = false
    private var managedWebViewClient: WebViewClient? = null
    private var renderAborted = false
    private var isDisposed = false
    private var renderGeneration = 0
    private val readinessTracker = PageRenderReadinessTracker()
    private val mainHandler = Handler(Looper.getMainLooper())

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
                val callbackGeneration = renderGeneration
                webView.post {
                    if (isDisposed || callbackGeneration != renderGeneration) {
                        return@post
                    }
                    if (readinessTracker.onStylingScriptsComplete()) {
                        logRenderReadyBudget("stylingScriptsComplete")
                        renderCallback.onPageReadyForDisplay()
                    }
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

    fun restoreManagedWebViewClient() {
        if (isDisposed) {
            return
        }
        managedWebViewClient?.let { webView.webViewClient = it }
    }

    fun dispose() {
        if (isDisposed) {
            return
        }
        isDisposed = true
        renderAborted = true
        renderGeneration += 1
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { webView.stopLoading() }
        runCatching { webView.webChromeClient = null }
        runCatching { webView.webViewClient = WebViewClient() }
        runCatching { webView.removeJavascriptInterface("RenderTimeline") }
        runCatching { webView.removeJavascriptInterface("ClipboardBridge") }
        jsInterfaceName?.let { name ->
            runCatching { webView.removeJavascriptInterface(name) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
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
            
            // Mobile viewport settings for proper rendering
            loadWithOverviewMode = true  // Fits page content to screen width
            useWideViewPort = true       // Enables viewport meta tag support
        }

        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = true
        webView.scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_INSET
        webView.isScrollbarFadingEnabled = false

        val pageClient = object : AppWebViewClient(linkHandler) {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (isDisposed) {
                    return
                }
                Log.d(logTag, "--> WebView Event: onPageStarted() called. URL: $url")
                super.onPageStarted(view, url, favicon)
            }
            
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (isDisposed) {
                    return null
                }
                val urlString = request.url.toString()
                val localAssetResponse = assetLoader.shouldInterceptRequest(request.url)
                if (localAssetResponse != null) {
                    Log.i(logTag, " -> INTERCEPT [HIT] in WebViewAssetLoader for: $urlString")
                    return localAssetResponse
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (isDisposed) {
                    return
                }
                val elapsedTime = System.currentTimeMillis() - renderStartTime
                Log.d(logTag, "--> WebView Event: onPageFinished() called. Elapsed: ${elapsedTime}ms. URL: $url")
                Log.d(logTag, "--> WebView Content Info: Title: '${view?.title}', Progress: ${view?.progress}%")
                
                // Check if page content is actually loaded
                val callbackGeneration = renderGeneration
                view?.evaluateJavascript("document.body ? document.body.innerHTML.length : 0") { result ->
                    if (isDisposed || callbackGeneration != renderGeneration) {
                        return@evaluateJavascript
                    }
                    Log.d(logTag, "--> WebView Content Length: $result characters")
                    if (result == "0") {
                        Log.w(logTag, "--> WARNING: WebView finished loading but body content is empty!")
                    }
                }

                injectArticlePostLoadStyles(view)

                pageLoaded = true
                renderCallback.onWebViewLoadFinished()
                if (readinessTracker.onMainFrameLoadFinished()) {
                    logRenderReadyBudget("onPageFinished")
                    renderCallback.onPageReadyForDisplay()
                }
                
                super.onPageFinished(view, url)
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                if (isDisposed) {
                    return true
                }
                val didCrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail?.didCrash() == true
                renderAborted = true
                return handleRenderProcessGoneForRecovery(didCrash) {
                    onRenderProcessGone()
                }
            }
        }
        managedWebViewClient = pageClient
        webView.webViewClient = pageClient
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (isDisposed) {
                    return
                }
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
                if (isDisposed) {
                    return
                }
                super.onReceivedTitle(view, title)
                title?.let { onTitleReceived(it) }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (isDisposed) {
                    return true
                }
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
                if (isDisposed) {
                    return false
                }
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

    private fun injectArticlePostLoadStyles(view: WebView?) {
        if (isDisposed) {
            return
        }
        view?.evaluateJavascript("""
            (function() {
                if (document.getElementById('osrs-post-load-style-fixes')) {
                    return;
                }
                const style = document.createElement('style');
                style.id = 'osrs-post-load-style-fixes';
                style.textContent = `
                    html body .mw-parser-output a.external,
                    html body .mw-parser-output a.external.text,
                    html body .mw-parser-output span.plainlinks a.external,
                    html body .mw-parser-output span.plainlinks a.external.text {
                        padding-right: 0 !important;
                        margin-right: 0 !important;
                        background-image: none !important;
                    }
                    .products-list {
                        border-collapse: collapse !important;
                        table-layout: auto !important;
                    }
                    .products-list td:first-child {
                        visibility: visible !important;
                        text-align: center !important;
                        overflow: visible !important;
                        padding: 2.88px 5.76px !important;
                    }
                    .products-list .inventory-image,
                    .products-list .inventory-image a,
                    .products-list .inventory-image img {
                        visibility: visible !important;
                        display: inline !important;
                        opacity: 1 !important;
                        position: static !important;
                    }
                    .products-list .inventory-image img {
                        object-fit: contain !important;
                        vertical-align: middle !important;
                        margin: 0 !important;
                        padding: 0 !important;
                        border: 0 none !important;
                        background-color: transparent !important;
                        float: none !important;
                    }
                `;
                document.head.appendChild(style);
            })();
        """.trimIndent(), null)
    }

    fun finalizeAndRevealPage(scrollY: Int = 0, onComplete: () -> Unit) {
        if (isDisposed) {
            return
        }
        if (scrollY > 0) {
            webView.alpha = 0f
        }
        applyTableCollapsePreference {
            applyReaderTextScale {
                applyThemeColors(webView) {
                    restoreScrollThenReveal(scrollY, onComplete)
                }
            }
        }
    }

    private fun restoreScrollThenReveal(scrollY: Int, onComplete: () -> Unit) {
        if (isDisposed) {
            return
        }
        if (scrollY <= 0) {
            webView.alpha = 1f
            revealBody(onComplete)
            return
        }
        val callbackGeneration = renderGeneration
        Log.d(logTag, "restoreScrollThenReveal target=$scrollY contentHeight=${webView.contentHeight} scale=${webView.scale}")
        fun applyNativeScroll() {
            webView.scrollTo(0, scrollY)
        }
        fun contentCanHoldScroll(): Boolean {
            val contentPx = (webView.contentHeight * webView.scale).toInt()
            return contentPx - webView.height >= scrollY
        }
        fun finishRestore() {
            if (isDisposed || callbackGeneration != renderGeneration) {
                return
            }
            applyNativeScroll()
            webView.alpha = 1f
            Log.d(logTag, "restoreScrollThenReveal applied native=${webView.scrollY} target=$scrollY")
            onComplete()
        }
        fun waitUntilScrollable(attemptsLeft: Int) {
            if (isDisposed || callbackGeneration != renderGeneration) {
                return
            }
            applyNativeScroll()
            if ((webView.scrollY >= scrollY - 4 && contentCanHoldScroll()) || attemptsLeft <= 0) {
                finishRestore()
                return
            }
            webView.postDelayed({ waitUntilScrollable(attemptsLeft - 1) }, 32)
        }
        revealBody {
            waitUntilScrollable(48)
        }
    }

    /** Applies the latest preference to both freshly built and previously cached HTML. */
    fun refreshReaderTextScale() {
        applyReaderTextScale()
    }

    /** Reconciles existing disclosure DOM after returning from Appearance without rebuilding. */
    fun refreshTableCollapsePreference() {
        applyTableCollapsePreference()
    }

    /** Swaps the live floor-number body class after the Appearance override changes. */
    fun refreshFloorNumberingPreference() {
        if (isDisposed) return
        val callbackGeneration = renderGeneration
        webView.evaluateJavascript(
            PageHtmlBuilder.floorNumberingRuntimeScript(
                osrsArticleFloorConvention.resolved().bodyClass
            )
        ) {
            if (!isDisposed && callbackGeneration == renderGeneration) {
                Unit
            }
        }
    }

    private fun applyTableCollapsePreference(onFinished: () -> Unit = {}) {
        if (isDisposed) return
        val callbackGeneration = renderGeneration
        webView.evaluateJavascript(
            PageHtmlBuilder.tableCollapseRuntimeScript(Prefs.isCollapseTablesEnabled)
        ) {
            if (!isDisposed && callbackGeneration == renderGeneration) {
                onFinished()
            }
        }
    }

    private fun applyReaderTextScale(onFinished: () -> Unit = {}) {
        if (isDisposed) {
            return
        }
        val callbackGeneration = renderGeneration
        webView.evaluateJavascript(
            PageHtmlBuilder.readerTextScaleRuntimeScript(Prefs.readerTextScale)
        ) {
            if (!isDisposed && callbackGeneration == renderGeneration) {
                onFinished()
            }
        }
    }

    private fun applyThemeColors(view: WebView?, onFinished: () -> Unit) {
        if (isDisposed) {
            return
        }
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
            if (isDisposed) {
                return@evaluateJavascript
            }
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
        if (isDisposed) {
            return
        }
        pageLoaded = false
        renderAborted = false
        readinessTracker.reset()
        val callbackGeneration = ++renderGeneration
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
        mainHandler.postDelayed({
            if (!isDisposed && callbackGeneration == renderGeneration && !renderAborted && readinessTracker.forceReadyForDisplay()) {
                logRenderReadyBudget("timeoutFallback")
                Log.w(logTag, "Render readiness timeout; revealing article fallback.")
                renderCallback.onPageReadyForDisplay()
            }
        }, RENDER_READY_TIMEOUT_MS)
        Log.d(logTag, "<<< Returned from webView.loadDataWithBaseURL().")
    }

    private fun logRenderReadyBudget(reason: String) {
        val elapsed = System.currentTimeMillis() - renderStartTime
        val status = if (elapsed <= RENDER_READY_BUDGET_MS) "met" else "missed"
        Log.d(logTag, "ArticleRenderBudget: readyReason=$reason elapsedMs=$elapsed budgetMs=$RENDER_READY_BUDGET_MS status=$status")
    }

    private fun revealBody(onComplete: () -> Unit) {
        if (isDisposed) {
            return
        }
        val revealBodyJs = "document.body.style.visibility = 'visible';"
        val callbackGeneration = renderGeneration
        webView.evaluateJavascript(revealBodyJs) {
            // This completion handler for evaluateJavascript runs after the JS has executed.
            if (isDisposed || callbackGeneration != renderGeneration) {
                return@evaluateJavascript
            }
            onComplete()
        }
    }

    companion object {
        private const val RENDER_READY_BUDGET_MS = 8_000L
        private const val RENDER_READY_TIMEOUT_MS = RENDER_READY_BUDGET_MS

        fun handleRenderProcessGoneForRecovery(
            didCrash: Boolean,
            onRecoveryRequested: () -> Unit
        ): Boolean {
            val crashDetails = if (didCrash) {
                "CRASHED"
            } else {
                "KILLED_BY_OS_OR_OTHER"
            }
            Log.e("PageLoadTrace", "WebView renderer process gone. Reason: $crashDetails")
            onRecoveryRequested()
            return true
        }
    }
}
