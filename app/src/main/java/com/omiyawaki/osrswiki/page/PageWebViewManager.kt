package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.omiyawaki.osrswiki.bridge.JavaScriptActionHandler
import com.omiyawaki.osrswiki.dataclient.WikiSite
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

                // Inject necessary scripts
                view?.evaluateJavascript(JavaScriptActionHandler.getLeafletJs(webView.context), null)
                view?.evaluateJavascript(JavaScriptActionHandler.getMapInitializerJs(webView.context), null)
                view?.evaluateJavascript(JavaScriptActionHandler.getCollapsibleContentJs(webView.context), null)

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
        // Inject all necessary CSS.
        val leafletCss = JavaScriptActionHandler.getLeafletCss(webView.context)
        val collapsibleCss = JavaScriptActionHandler.getCollapsibleContentCss(webView.context)
        val finalHtml = fullHtml.replaceFirst("<head>", "<head>\n$leafletCss\n$collapsibleCss")

        Log.d(managerTag, "Loading HTML content with injected assets.")
        
        val finalBaseUrl = baseUrl ?: WikiSite.OSRS_WIKI.url()
        webView.loadDataWithBaseURL(finalBaseUrl, finalHtml, "text/html", "UTF-8", null)
    }

    private fun revealBody() {
        val revealBodyJs = "document.body.style.visibility = 'visible';"
        webView.evaluateJavascript(revealBodyJs) {
            onPageReady()
        }
    }
}
