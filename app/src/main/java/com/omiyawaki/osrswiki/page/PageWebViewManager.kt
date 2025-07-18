package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import com.omiyawaki.osrswiki.bridge.JavaScriptActionHandler
import com.omiyawaki.osrswiki.util.asset.AssetReader

class PageWebViewManager(
    private val webView: WebView,
    private val linkHandler: PageLinkHandler,
    private val onPageReady: () -> Unit,
    private val onTitleReceived: (String) -> Unit,
    private val jsInterface: Any?,
    private val jsInterfaceName: String?
) {
    private val managerTag = "PageWebViewManager"
    private val consoleTag = "WebViewConsole"

    private val localAssetDomain = "appassets.androidplatform.net"

    private val assetLoader = WebViewAssetLoader.Builder()
        .setDomain(localAssetDomain)
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(webView.context))
        .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(webView.context))
        .build()

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        if (jsInterface != null && jsInterfaceName != null) {
            webView.addJavascriptInterface(jsInterface, jsInterfaceName)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.webViewClient = object : AppWebViewClient(linkHandler) {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                triggerInitializers(view)
                revealBody()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
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

    private fun applyThemeColors(view: WebView?) {
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
        view?.evaluateJavascript(script, null)
    }

    private fun getThemeColor(context: Context, attrId: Int, fallback: String): String {
        val typedValue = TypedValue()
        if (!context.theme.resolveAttribute(attrId, typedValue, true)) {
            Log.e(managerTag, "Failed to resolve theme attribute ID #$attrId")
            return fallback
        }
        val color: Int
        try {
            color = if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
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

    private fun triggerInitializers(view: WebView?) {
        applyThemeColors(view)
        // The collapsible_content.js script now self-initializes AND is responsible
        // for calling the switcher's initializer. This call is no longer needed here.
        // view?.evaluateJavascript("initializeInfoboxSwitcher();", null)
    }

    fun render(fullHtml: String) {
        val processedHtml = fullHtml

        // CSS asset paths
        val cssPaths = listOf(
            JavaScriptActionHandler.getCollapsibleTableCssPath(),
            JavaScriptActionHandler.getInfoboxSwitcherCssPath()
        )
        val cssTags = cssPaths.joinToString(separator = "\n") { path ->
            "<link rel=\"stylesheet\" type=\"text/css\" href=\"https://$localAssetDomain/assets/$path\" />"
        }

        // Add all necessary JS files, including the new scroll interceptor.
        val jsPaths = listOf(
            JavaScriptActionHandler.getInfoboxSwitcherBootstrapJsPath(),
            JavaScriptActionHandler.getInfoboxSwitcherJsPath(),
            "web/collapsible_content.js",
            "web/horizontal_scroll_interceptor.js" // Add the scroll interceptor script
        )
        val jsTags = jsPaths.joinToString(separator = "\n") { path ->
            "<script src=\"https://$localAssetDomain/assets/$path\"></script>"
        }

        val finalHtml = processedHtml.replaceFirst(
            "<head>",
            "<head>\n<link rel=\"stylesheet\" href=\"https://appassets.androidplatform.net/assets/styles/fonts.css\">\n$cssTags\n$jsTags"
        )

        val baseUrl = "https://$localAssetDomain/"

        Log.d(managerTag, "Loading content with base URL: $baseUrl")
        webView.loadDataWithBaseURL(
            baseUrl,
            finalHtml,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun revealBody() {
        val revealBodyJs = "document.body.style.visibility = 'visible';"
        webView.evaluateJavascript(revealBodyJs) {
            onPageReady()
        }
    }
}
