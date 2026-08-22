package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmRequest
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.views.ObservableWebView
import java.lang.ref.WeakReference

internal object osrsPreparedArticleWebViewStore {
    private const val maxEntries = 2
    private const val localAssetDomain = "appassets.androidplatform.net"
    internal const val PREWARM_OFFSCREEN_TRANSLATION_PX = 100_000f
    internal const val PREWARM_COMPOSITE_ALPHA = 0f
    private val mainHandler = Handler(Looper.getMainLooper())
    private val entries = ArrayDeque<Entry>()
    private var hostRef: WeakReference<Activity>? = null
    private val dwellPins = HashMap<String, Int>()
    private val foregroundPins = HashSet<String>()
    private val preferredPins = HashSet<String>()

    fun pin(request: ArticlePrewarmRequest, foreground: Boolean = false, preferred: Boolean = false) {
        pinTokens(request).forEach { token ->
            if (preferred) {
                preferredPins.add(token)
            }
            if (foreground) {
                foregroundPins.add(token)
            } else {
                dwellPins[token] = (dwellPins[token] ?: 0) + 1
            }
        }
    }

    fun unpin(request: ArticlePrewarmRequest) {
        pinTokens(request).forEach { token ->
            val count = dwellPins[token] ?: return@forEach
            if (count > 1) {
                dwellPins[token] = count - 1
            } else {
                dwellPins.remove(token)
                if (token !in foregroundPins) {
                    preferredPins.remove(token)
                }
            }
        }
    }

    fun unpinForeground(request: ArticlePrewarmRequest) {
        pinTokens(request).forEach { token -> foregroundPins.remove(token) }
    }

    private fun isPinned(request: ArticlePrewarmRequest): Boolean {
        return pinTokens(request).any { token ->
            token in foregroundPins || (dwellPins[token] ?: 0) > 0
        }
    }

    private fun isPinned(entry: Entry): Boolean {
        return pinTokens(entry.key.pageId, entry.key.normalizedTitle).any { token ->
            token in foregroundPins || (dwellPins[token] ?: 0) > 0
        }
    }

    private fun isPreferred(entry: Entry): Boolean {
        return pinTokens(entry.key.pageId, entry.key.normalizedTitle).any { token ->
            token in foregroundPins || token in preferredPins
        }
    }

    private fun isPreferred(key: osrsPreparedArticleRenderKey): Boolean {
        return pinTokens(key.pageId, key.normalizedTitle).any { token ->
            token in foregroundPins || token in preferredPins
        }
    }

    fun markPreferred(request: ArticlePrewarmRequest) {
        preferredPins.addAll(pinTokens(request))
    }

    private fun pinTokens(request: ArticlePrewarmRequest): List<String> {
        return pinTokens(request.key.pageId, request.key.normalizedTitle)
    }

    private fun pinTokens(pageId: Int?, title: String?): List<String> {
        val tokens = linkedSetOf<String>()
        pageId?.takeIf { it > 0 }?.let { tokens += "id:$it" }
        title?.takeIf { it.isNotBlank() }?.let { tokens += "title:$it" }
        if (tokens.isEmpty()) {
            tokens += "title:"
        }
        return tokens.toList()
    }

    private data class Entry(
        val key: osrsPreparedArticleRenderKey,
        val webView: ObservableWebView,
        val contextWrapper: MutableContextWrapper,
        val host: FrameLayout,
        var isReady: Boolean,
        var isPainted: Boolean
    )

    fun rememberHost(context: Context) {
        val activity = context.findActivity() ?: return
        hostRef = WeakReference(activity)
    }

    fun preload(
        request: ArticlePrewarmRequest,
        fullHtml: String,
        theme: Theme,
        collapseTables: Boolean,
        wrapTableCells: Boolean,
        readerTextScale: Float
    ) {
        if (!osrsArticlePreloadPolicy.speculativeLiveArticlePreloadsEnabled) {
            return
        }
        if (osrsBackgroundWorkGate.isPaused) {
            mainHandler.postDelayed({
                preload(request, fullHtml, theme, collapseTables, wrapTableCells, readerTextScale)
            }, 80)
            return
        }
        if (Prefs.disableFirstViewPaintPrewarm) {
            return
        }
        val activity = hostRef?.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
        val key = osrsPreparedArticleRenderKey.from(
            request,
            theme,
            collapseTables,
            wrapTableCells,
            readerTextScale
        )
        if (entries.any { it.key == key }) {
            return
        }
        evictIfNeeded(admitting = key)
        if (entries.size >= maxEntries) {
            L.d("PreparedArticleWebView: skip preload ${key.normalizedTitle ?: key.pageId} cap is full of pinned")
            return
        }
        val metrics = activity.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val wrapper = MutableContextWrapper(activity)
        val webView = ObservableWebView(wrapper)
        val host = object : FrameLayout(activity) {
            override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean = false
        }.apply {
            layoutParams = FrameLayout.LayoutParams(width, height)
            stashOffscreenFromTabUi()
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        webView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.isClickable = false
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        webView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        host.addView(webView)
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        if (content != null) {
            content.addView(host, 0)
        }
        host.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        host.layout(0, 0, width, height)
        configureWebView(webView, key)
        val entry = Entry(
            key = key,
            webView = webView,
            contextWrapper = wrapper,
            host = host,
            isReady = false,
            isPainted = false
        )
        entries.addLast(entry)
        webView.loadDataWithBaseURL(
            "https://$localAssetDomain/",
            fullHtml,
            "text/html",
            "UTF-8",
            null
        )
        L.d("PreparedArticleWebView: preloading ${key.normalizedTitle ?: key.pageId}")
    }

    fun take(
        pageId: Int?,
        title: String?,
        theme: Theme,
        collapseTables: Boolean,
        wrapTableCells: Boolean,
        readerTextScale: Float,
        hostActivity: Activity
    ): ObservableWebView? {
        if (!osrsArticlePreloadPolicy.speculativeLiveArticlePreloadsEnabled || Prefs.disableFirstViewPaintPrewarm) {
            return null
        }
        val request = runCatching {
            ArticlePrewarmRequest(pageId = pageId, title = title)
        }.getOrNull() ?: return null
        val key = osrsPreparedArticleRenderKey.from(
            request,
            theme,
            collapseTables,
            wrapTableCells,
            readerTextScale
        )
        val index = entries.indexOfFirst {
            it.isReady &&
                it.isPainted &&
                it.key.dark == key.dark &&
                it.key.collapseTables == key.collapseTables &&
                it.key.wrapTableCells == key.wrapTableCells &&
                it.key.readerTextScale == key.readerTextScale &&
                it.key.matchesPage(request)
        }
        if (index < 0) {
            L.d("PreparedArticleWebView: miss ${key.normalizedTitle ?: key.pageId} painted=${entries.filter { it.isPainted }.map { it.key.normalizedTitle ?: it.key.pageId }}")
            return null
        }
        val entry = entries.removeAt(index)
        unpinForeground(request)
        (entry.host.parent as? ViewGroup)?.removeView(entry.host)
        entry.host.removeView(entry.webView)
        entry.contextWrapper.baseContext = hostActivity
        entry.webView.removeJavascriptInterface("OsrsWikiBridge")
        L.d("PreparedArticleWebView: handing off ${key.normalizedTitle ?: key.pageId}")
        return entry.webView
    }

    fun cancel(request: ArticlePrewarmRequest) {
        if (isPinned(request)) {
            return
        }
        val doomed = entries.filter { it.key.matchesPage(request) && !it.isPainted }
        doomed.forEach(::destroyEntry)
        entries.removeAll(doomed.toSet())
    }

    fun clear() {
        entries.toList().forEach(::destroyEntry)
        entries.clear()
    }

    private fun FrameLayout.stashOffscreenFromTabUi() {
        // 0.01 alpha on android.R.id.content composites article HTML through
        // Home/Saved/Search/Map/More. Keep a hardware layer so Chromium can
        // still raster, but never draw into the tab UI.
        alpha = PREWARM_COMPOSITE_ALPHA
        translationX = PREWARM_OFFSCREEN_TRANSLATION_PX
        translationY = PREWARM_OFFSCREEN_TRANSLATION_PX
        elevation = -1000f
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    private fun markPainted(key: osrsPreparedArticleRenderKey) {
        val entry = entries.firstOrNull { it.key == key } ?: return
        entry.isPainted = true
        entry.isReady = true
        L.d("osrsFirstViewPaintWarm: done page=${key.normalizedTitle ?: key.pageId}")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: ObservableWebView, key: osrsPreparedArticleRenderKey) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(localAssetDomain)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(webView.context.applicationContext))
            .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(webView.context.applicationContext))
            .build()
        val linkHandler = object : LinkHandler(webView.context.applicationContext) {
            override fun onInternalArticleLinkClicked(articleTitle: String, fullUri: Uri) = Unit
            override fun onExternalLinkClicked(uri: Uri) = Unit
        }
        webView.webViewClient = object : AppWebViewClient(linkHandler) {
            override fun shouldInterceptRequest(
                view: WebView,
                request: android.webkit.WebResourceRequest
            ): android.webkit.WebResourceResponse? {
                assetLoader.shouldInterceptRequest(request.url)?.let { return it }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pollPainted(view, key, attempt = 0)
            }
        }
        webView.addJavascriptInterface(PrewarmBridge(key), "OsrsWikiBridge")
        mainHandler.postDelayed({ markPainted(key) }, 15_000)
    }

    private fun pollPainted(view: WebView?, key: osrsPreparedArticleRenderKey, attempt: Int) {
        val webView = view ?: return
        val entry = entries.firstOrNull { it.key == key } ?: return
        if (entry.isPainted && entry.isReady) {
            return
        }
        webView.evaluateJavascript(
            """
            (function(){
              try {
                if (window.osrsWatchFirstViewComplete && $attempt === 0) {
                  window.osrsWatchFirstViewComplete();
                }
                if (window.__osrsFirstViewPainted && window.osrsNotifyFirstViewComplete) {
                  window.osrsNotifyFirstViewComplete();
                }
              } catch (e) {}
              return window.__osrsFirstViewPainted === true;
            })()
            """.trimIndent()
        ) { value ->
            val painted = value == "true" || value == "\"true\""
            mainHandler.post {
                if (painted) {
                    markPainted(key)
                } else if (attempt >= 150) {
                    markPainted(key)
                } else {
                    mainHandler.postDelayed({ pollPainted(webView, key, attempt + 1) }, 100)
                }
            }
        }
    }

    private fun evictIfNeeded(admitting: osrsPreparedArticleRenderKey) {
        while (entries.size >= maxEntries) {
            val unpinned = entries.firstOrNull { !isPinned(it) }
            if (unpinned != null) {
                entries.remove(unpinned)
                destroyEntry(unpinned)
                continue
            }
            val victim = if (isPreferred(admitting)) {
                entries.lastOrNull { !isPreferred(it) }
            } else {
                null
            }
            if (victim == null) {
                // Cap is full of pinned entries. Do not evict a preferred neighbor to admit another.
                break
            }
            entries.remove(victim)
            destroyEntry(victim)
        }
    }

    private fun destroyEntry(entry: Entry) {
        (entry.host.parent as? ViewGroup)?.removeView(entry.host)
        entry.host.removeView(entry.webView)
        runCatching { entry.webView.stopLoading() }
        runCatching { entry.webView.removeJavascriptInterface("OsrsWikiBridge") }
        runCatching { entry.webView.destroy() }
    }

    private class PrewarmBridge(private val key: osrsPreparedArticleRenderKey) {
        @JavascriptInterface
        fun firstViewComplete() {
            mainHandler.post { markPainted(key) }
        }

        @JavascriptInterface
        fun noteUserInteraction() = Unit

        @JavascriptInterface
        fun warmNearViewportAssets(urlsJson: String) = Unit
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return current as? Activity
}
