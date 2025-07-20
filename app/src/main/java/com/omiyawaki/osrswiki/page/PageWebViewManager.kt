package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

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

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        if (jsInterface != null && jsInterfaceName != null) {
            webView.addJavascriptInterface(jsInterface, jsInterfaceName)
        }
        webView.addJavascriptInterface(RenderTimelineLogger(), "RenderTimeline")


        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.webViewClient = object : AppWebViewClient(linkHandler) {
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
                pageLoaded = true
                renderCallback.onWebViewLoadFinished()
                super.onPageFinished(view, url)
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                val crashDetails = if (detail?.didCrash() == true) "CRASHED" else "KILLED_BY_OS_OR_OTHER"
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
                val uiProgress = 50 + (cappedProgress / 2)
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
                    when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> Log.e(consoleTag, message)
                        ConsoleMessage.MessageLevel.WARNING -> Log.w(consoleTag, message)
                        else -> Log.i(consoleTag, message)
                    }
                }
                return true
            }
        }
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
        val baseUrl = "https://$localAssetDomain/"
        Log.d(logTag, ">>> Calling webView.loadDataWithBaseURL()... (HTML size: ${fullHtml.length} chars)")
        webView.loadDataWithBaseURL(
            baseUrl,
            fullHtml,
            "text/html",
            "UTF-8",
            null
        )
        Log.d(logTag, "<<< Returned from webView.loadDataWithBaseURL().")
    }

    private fun revealBody(onComplete: () -> Unit) {
        val revealBodyJs = "document.body.style.visibility = 'visible';"
        webView.evaluateJavascript(revealBodyJs) {
            // This completion handler for evaluateJavascript runs after the JS has executed.
            onComplete()
        }
    }
}
