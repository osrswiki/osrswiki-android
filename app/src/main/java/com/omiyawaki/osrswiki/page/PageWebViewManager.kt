package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.AttrRes
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.theme.Theme
import java.io.IOException

/**
 * Manages the configuration, content rendering, and styling of the WebView
 * in the PageFragment, encapsulating the complex setup and JavaScript logic.
 */
class PageWebViewManager(
    private val context: Context,
    private val webView: WebView,
    private val linkHandler: PageLinkHandler,
    private val onPageReady: () -> Unit
) {
    private val WEBVIEW_DEBUG_TAG = "PageWebViewManager"
    private var currentTheme: Theme = Theme.DEFAULT_LIGHT

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.webViewClient = object : AppWebViewClient(linkHandler) {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // When the initial HTML has been loaded, apply our dynamic styles.
                applyStylingAndRevealBody()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.i("WebViewConsole", "[${it.sourceId()}:${it.lineNumber()}] ${it.message()}")
                }
                return true
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * Renders an HTML snippet in the WebView.
     * It constructs a full HTML document, embeds the complete CSS, and loads it.
     */
    fun render(htmlSnippet: String, baseUrl: String?, pageTitle: String?, theme: Theme) {
        this.currentTheme = theme

        val backgroundColorInt = getThemeColor(R.attr.paper_color, theme)
        val backgroundColorHex = String.format("#%06X", (0xFFFFFF and backgroundColorInt))

        val cssString = try {
            context.assets.open("styles/wiki_content.css").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.e(WEBVIEW_DEBUG_TAG, "Error reading wiki_content.css from assets", e)
            "" // Use empty string as fallback
        }

        val finalHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${pageTitle ?: "OSRS Wiki"}</title>
                <style>
                    /* Critical CSS to prevent FOUC */
                    html { background-color:$backgroundColorHex !important; }
                    body { visibility:hidden; background-color:$backgroundColorHex !important; }
                </style>
                <style id="osrsWikiInjectedStyle">
                    $cssString
                </style>
            </head>
            <body>
                $htmlSnippet
            </body>
            </html>
        """.trimIndent()

        val finalBaseUrl = baseUrl ?: WikiSite.OSRS_WIKI.url()
        webView.loadDataWithBaseURL(finalBaseUrl, finalHtml, "text/html", "UTF-8", null)
    }

    /**
     * Applies the specific theme class to the body and makes it visible.
     * This is called from onPageFinished.
     */
    private fun applyStylingAndRevealBody() {
        val themeClass = when (currentTheme) {
            Theme.OSRS_DARK -> "theme-osrs-dark"
            Theme.WIKI_LIGHT -> "theme-wikipedia-light"
            Theme.WIKI_DARK -> "theme-wikipedia-dark"
            Theme.WIKI_BLACK -> "theme-wikipedia-black"
            else -> "" // OSRS_LIGHT uses the :root default, no class needed.
        }

        val applyThemeAndRevealBodyJs = """
            (function() {
                if (!document.body) return 'Error: No body element found.';
                // Remove all possible theme classes to ensure a clean slate.
                document.body.classList.remove(
                    'theme-wikipedia-light',
                    'theme-osrs-dark',
                    'theme-wikipedia-dark',
                    'theme-wikipedia-black'
                );
                // Add the new, specific theme class if it's not the default.
                if ('$themeClass') {
                    document.body.classList.add('$themeClass');
                }
                document.body.style.visibility = 'visible';
                return 'Theme and visibility applied.';
            })();
        """.trimIndent()

        webView.evaluateJavascript(applyThemeAndRevealBodyJs) { result ->
            if (result != null && result.contains("Theme and visibility applied.")) {
                onPageReady() // Notify the fragment that the page is ready and visible.
            } else {
                Log.e(WEBVIEW_DEBUG_TAG, "Failed to apply theme/reveal. Result: $result")
                // As a fallback, still try to reveal the content and notify the fragment.
                val fallbackJs = "(function(){if(document.body){document.body.style.visibility='visible';}})()"
                webView.evaluateJavascript(fallbackJs, null)
                onPageReady()
            }
        }
    }

    /**
     * Resolves a theme attribute color for a specific theme.
     * Note: This helper is context-dependent and assumes the context's theme has been set.
     */
    private fun getThemeColor(@AttrRes attrRes: Int, theme: Theme): Int {
        val newContext = context.createConfigurationContext(context.resources.configuration)
        newContext.setTheme(theme.resourceId)
        val typedValue = TypedValue()
        newContext.theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }
}
