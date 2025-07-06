package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.theme.Theme

class PageWebViewManager(
    private val webView: WebView,
    private val linkHandler: PageLinkHandler,
    private val onPageReady: () -> Unit,
    private val onTitleReceived: (String) -> Unit
) {
    private val managerTag = "PageWebViewManager"
    private val consoleTag = "WebViewConsole"

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.webViewClient = object : AppWebViewClient(linkHandler) {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (Prefs.isCollapseTablesEnabled) {
                    // This call is likely not needed if the JS runs on its own,
                    // but we leave it here, commented out, for reference.
                    // view?.evaluateJavascript("window.osrswiki.makeTablesCollapsible();", null)
                }
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
                    val message = "[${it.sourceId()}:${it.lineNumber()}] ${it.message()}"
                    when (it.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> Log.e(consoleTag, message)
                        ConsoleMessage.MessageLevel.WARNING -> Log.w(consoleTag, message)
                        else -> Log.i(consoleTag, message)
                    }
                }
                return true
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.setBackgroundColor(Color.TRANSPARENT)
    }

    fun render(fullHtml: String, baseUrl: String?, theme: Theme) {
        // --- DIAGNOSTIC LOGGING ---
        Log.d("PageWebViewManager", "Loading HTML content: $fullHtml")
        // --- END LOGGING ---
        val finalBaseUrl = baseUrl ?: WikiSite.OSRS_WIKI.url()
        webView.loadDataWithBaseURL(finalBaseUrl, fullHtml, "text/html", "UTF-8", null)
    }

    private fun revealBody() {
        val revealBodyJs = "document.body.style.visibility = 'visible';"
        webView.evaluateJavascript(revealBodyJs) {
            onPageReady()
        }
    }
}
