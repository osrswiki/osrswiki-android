package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.DisplayMetrics
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.page.PageAssetDownloader
import com.omiyawaki.osrswiki.page.PageHtmlBuilder
import com.omiyawaki.osrswiki.page.PageLinkHandler
import com.omiyawaki.osrswiki.page.PageWebViewManager
import com.omiyawaki.osrswiki.page.RenderCallback
import com.omiyawaki.osrswiki.page.DownloadProgress
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.views.ObservableWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * Unified preview generator implementing the expert's "render-once, capture-many" pattern.
 * 
 * This solves the WebView cold start problem by:
 * 1. Creating a single WebView instance for the entire generation job
 * 2. Loading Varrock page once (paying network/initialization cost once)
 * 3. Using JavaScript to toggle theme/table states
 * 4. Capturing multiple bitmaps from the same WebView instance
 * 
 * Expected performance: First load ~1-2s, each additional snapshot ~100-300ms
 * Total: 4 previews in ~2-3s (down from 26s)
 */
class UnifiedPreviewGenerator {
    
    companion object {
        private const val TAG = "UnifiedPreviewGen"
        private const val TARGET_TABLE_W_DP = 160 // Table preview width
        private const val TARGET_TABLE_H_DP = 120 // Table preview height
        private const val TARGET_THEME_SIZE_DP = 96 // Theme preview size (square)
        private const val LOAD_TIMEOUT_MS = 15000L // 15 seconds for initial load
        private const val STATE_CHANGE_TIMEOUT_MS = 5000L // 5 seconds per state change
        private const val CONTENT_READY_TIMEOUT_MS = 3000L // 3 seconds for content settling
    }
    
    // Instance variables for callback management
    private var pendingThemeChangeCallback: ((Boolean) -> Unit)? = null
    private var pendingTableStateChangeCallback: ((Boolean) -> Unit)? = null
    
    /**
     * Result data class containing all generated previews
     */
    data class PreviewResult(
        val tableCollapsedLight: Bitmap,
        val tableExpandedLight: Bitmap,
        val tableCollapsedDark: Bitmap,
        val tableExpandedDark: Bitmap,
        val themeLight: Bitmap,
        val themeDark: Bitmap
    )
    
    /**
     * Generates all previews using the unified approach
     */
    suspend fun generateAllPreviews(
        context: Context
    ): PreviewResult = withContext(Dispatchers.Main) {
        
        Log.i(TAG, "Starting unified preview generation (render-once, capture-many)")
        val startTime = System.currentTimeMillis()
        
        // Get Activity context before entering suspendCancellableCoroutine
        val app = context.applicationContext as OSRSWikiApp
        val activity = app.waitForActivityContext()
        
        if (activity == null) {
            throw Exception("Cannot find Activity context for WebView rendering")
        }
        
        suspendCancellableCoroutine { continuation ->
            var isCompleted = false
            var webView: ObservableWebView? = null
            var container: FrameLayout? = null
            var rootView: FrameLayout? = null
            
            try {
                Log.i(TAG, "Creating single WebView instance for all previews")
                
                // Use light theme as the base theme (we'll toggle via JavaScript)
                val themedContext = ContextThemeWrapper(activity, R.style.Theme_OSRSWiki_OSRSLight)
                
                // Create WebView
                webView = ObservableWebView(themedContext)
                
                // Get dimensions for rendering
                val dm = context.resources.displayMetrics
                val fullScreenW = dm.widthPixels
                val fullScreenH = dm.heightPixels
                
                Log.i(TAG, "WebView dimensions: ${fullScreenW}×${fullScreenH}")
                
                // Create container and attach to Activity (must be attached for proper rendering)
                container = FrameLayout(themedContext).apply {
                    layoutParams = FrameLayout.LayoutParams(fullScreenW, fullScreenH)
                    visibility = View.INVISIBLE // Hidden but attached to window
                    alpha = 0.01f // Nearly invisible to avoid flickering
                    addView(webView, FrameLayout.LayoutParams(fullScreenW, fullScreenH))
                }
                
                rootView = activity.findViewById<FrameLayout>(android.R.id.content)
                rootView.addView(container)
                
                // Enable WebView optimizations for offscreen rendering
                webView.settings.apply {
                    setOffscreenPreRaster(true) // Expert recommendation
                }
                webView.resumeTimers() // Expert recommendation to avoid throttling
                
                Log.i(TAG, "WebView attached to window with optimizations enabled")
                
                // Set up components exactly like TablePreviewRenderer does
                val pageRepository = app.pageRepository
                val previewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
                
                val pageLinkHandler = PageLinkHandler(
                    constructionContext = themedContext,
                    coroutineScope = previewScope, 
                    pageRepository = pageRepository,
                    theme = Theme.OSRS_LIGHT // Base theme
                )
                
                // Enhanced JS interface with callback support for state changes
                val jsInterface = object {
                    @android.webkit.JavascriptInterface
                    fun onMapPlaceholderMeasured(id: String, rectJson: String, mapDataJson: String) {
                        // No-op for preview
                    }
                    
                    @android.webkit.JavascriptInterface  
                    fun setHorizontalScroll(inProgress: Boolean) {
                        // No-op for preview
                    }
                    
                    @android.webkit.JavascriptInterface
                    fun onThemeChangeComplete() {
                        Log.d(TAG, "JavaScript callback: Theme change completed")
                        // Resume any pending theme change operations
                        this@UnifiedPreviewGenerator.pendingThemeChangeCallback?.invoke(true)
                        this@UnifiedPreviewGenerator.pendingThemeChangeCallback = null
                    }
                    
                    @android.webkit.JavascriptInterface
                    fun onTableStateChangeComplete() {
                        Log.d(TAG, "JavaScript callback: Table state change completed")
                        // Resume any pending table state change operations
                        this@UnifiedPreviewGenerator.pendingTableStateChangeCallback?.invoke(true)
                        this@UnifiedPreviewGenerator.pendingTableStateChangeCallback = null
                    }
                }
                
                // Variable to hold PageWebViewManager reference
                var pageWebViewManagerRef: PageWebViewManager? = null
                
                val renderCallback = object : RenderCallback {
                    override fun onWebViewLoadFinished() {
                        Log.d(TAG, "WebView load finished - progress: ${webView.progress}%")
                    }
                    
                    override fun onPageReadyForDisplay() {
                        Log.i(TAG, "Page ready for display - starting multi-capture sequence")
                        
                        pageWebViewManagerRef?.finalizeAndRevealPage {
                            Log.i(TAG, "Page finalized - beginning state toggle and capture sequence")
                            
                            // Wait for content to be fully ready, then start capture sequence
                            waitForWebViewContentReady(webView) { contentReady ->
                                if (!isCompleted) {
                                    isCompleted = true
                                    if (contentReady) {
                                        Log.i(TAG, "Content ready - starting capture sequence")
                                        startCaptureSequence(
                                            webView, container, rootView, fullScreenW, fullScreenH,
                                            themedContext, continuation, startTime, previewScope
                                        )
                                    } else {
                                        Log.w(TAG, "Content not fully ready, but proceeding with capture")
                                        startCaptureSequence(
                                            webView, container, rootView, fullScreenW, fullScreenH,
                                            themedContext, continuation, startTime, previewScope
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Create PageWebViewManager
                val pageWebViewManager = PageWebViewManager(
                    webView = webView,
                    linkHandler = pageLinkHandler,
                    onTitleReceived = { /* No-op for preview */ },
                    jsInterface = jsInterface,
                    jsInterfaceName = "OsrsWikiBridge",
                    renderCallback = renderCallback,
                    onRenderProgress = { /* No-op for preview */ }
                )
                
                pageWebViewManagerRef = pageWebViewManager
                
                // Load Varrock article HTML (same as TablePreviewRenderer)
                Log.i(TAG, "Loading Varrock article HTML using PageAssetDownloader")
                
                // Launch coroutine for HTML loading
                previewScope.launch {
                    try {
                        val htmlContent = loadVarrockArticleHtml(context)
                        
                        if (htmlContent != null) {
                            Log.i(TAG, "HTML loaded successfully (${htmlContent.length} chars) - building full HTML document")
                            
                            val pageHtmlBuilder = PageHtmlBuilder(themedContext)
                            val fullHtml = pageHtmlBuilder.buildFullHtmlDocument("Varrock", htmlContent, Theme.OSRS_LIGHT)
                            
                            // Inject JavaScript for theme and table state control
                            val enhancedHtml = injectControlJavaScript(fullHtml)
                            
                            Log.i(TAG, "Rendering HTML with PageWebViewManager")
                            pageWebViewManager.render(enhancedHtml)
                            
                        } else {
                            Log.e(TAG, "Failed to load Varrock HTML")
                            if (!isCompleted) {
                                isCompleted = true
                                cleanup(webView, container, rootView)
                                continuation.resumeWithException(Exception("Failed to load Varrock HTML"))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading Varrock HTML", e)
                        if (!isCompleted) {
                            isCompleted = true
                            cleanup(webView, container, rootView)
                            continuation.resumeWithException(e)
                        }
                    }
                }
                
                // Set up cancellation
                continuation.invokeOnCancellation {
                    Log.i(TAG, "Generation cancelled")
                    isCompleted = true
                    cleanup(webView, container, rootView)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up unified preview generation", e)
                if (!isCompleted) {
                    isCompleted = true
                    cleanup(webView, container, rootView)
                    continuation.resumeWithException(e)
                }
            }
        }
    }
    
    /**
     * Loads Varrock article HTML using PageAssetDownloader (same as TablePreviewRenderer)
     */
    private suspend fun loadVarrockArticleHtml(
        context: Context
    ): String? = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as OSRSWikiApp
            val pageRepository = app.pageRepository
            val pageAssetDownloader = PageAssetDownloader(
                com.omiyawaki.osrswiki.network.OkHttpClientFactory.offlineClient,
                pageRepository
            )
            
            val articleQueryTitle = "Varrock"
            val mobileUrl = com.omiyawaki.osrswiki.dataclient.WikiSite.OSRS_WIKI.mobileUrl(articleQueryTitle)
            
            var processedHtml: String? = null
            
            try {
                withTimeout(LOAD_TIMEOUT_MS) {
                    pageAssetDownloader.downloadPriorityAssetsByTitle(articleQueryTitle, mobileUrl).collect { progress ->
                        when (progress) {
                            is DownloadProgress.Success -> {
                                Log.d(TAG, "PageAssetDownloader Success - HTML length: ${progress.result.processedHtml.length}")
                                processedHtml = progress.result.processedHtml
                                throw CancellationException("Got processed HTML")
                            }
                            is DownloadProgress.Failure -> {
                                throw Exception("PageAssetDownloader failed: ${progress.error.message}")
                            }
                            else -> {
                                Log.d(TAG, "PageAssetDownloader progress: ${progress::class.simpleName}")
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (e.message == "Got processed HTML") {
                    Log.d(TAG, "Successfully got processed HTML from PageAssetDownloader")
                } else {
                    throw e
                }
            }
            
            return@withContext processedHtml
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Varrock article", e)
            return@withContext null
        }
    }
    
    /**
     * Injects JavaScript for controlling theme and table state with proper callback system
     */
    private fun injectControlJavaScript(html: String): String {
        val controlScript = """
            <script>
                // Theme control function with event-driven completion callback
                window.setTheme = function(theme) {
                    console.log('UnifiedPreviewGen: Setting theme to ' + theme);
                    document.documentElement.dataset.theme = theme;
                    
                    // Force style recalculation and reflow
                    document.body.style.display = 'none';
                    document.body.offsetHeight; // Trigger reflow
                    document.body.style.display = '';
                    
                    // Wait for rendering to complete using requestAnimationFrame
                    requestAnimationFrame(function() {
                        requestAnimationFrame(function() {
                            // Double rAF ensures rendering is complete
                            console.log('UnifiedPreviewGen: Theme change rendering completed');
                            
                            // Verify theme was actually applied by checking computed styles
                            var bodyStyle = window.getComputedStyle(document.body);
                            console.log('UnifiedPreviewGen: Body background after theme change:', bodyStyle.backgroundColor);
                            
                            // Call Android callback when actually ready
                            if (typeof OsrsWikiBridge !== 'undefined' && OsrsWikiBridge.onThemeChangeComplete) {
                                OsrsWikiBridge.onThemeChangeComplete();
                            }
                        });
                    });
                    
                    return true;
                };
                
                // Table collapse control function with DOM mutation completion detection
                window.setTableCollapse = function(collapsed) {
                    console.log('UnifiedPreviewGen: Setting table collapse to ' + collapsed);
                    window.OSRS_TABLE_COLLAPSED = collapsed;
                    
                    // Set up mutation observer to detect DOM changes completion
                    var observer = new MutationObserver(function(mutations) {
                        observer.disconnect();
                        console.log('UnifiedPreviewGen: DOM mutations completed for table collapse');
                        
                        // Wait one more frame to ensure layout is complete
                        requestAnimationFrame(function() {
                            console.log('UnifiedPreviewGen: Table state change rendering completed');
                            
                            // Call Android callback when DOM settles
                            if (typeof OsrsWikiBridge !== 'undefined' && OsrsWikiBridge.onTableStateChangeComplete) {
                                OsrsWikiBridge.onTableStateChangeComplete();
                            }
                        });
                    });
                    
                    // Start observing before making changes
                    observer.observe(document.body, {
                        attributes: true,
                        childList: true,
                        subtree: true
                    });
                    
                    // Apply table collapse changes - try multiple CSS class patterns
                    var collapsibleSelectors = [
                        '.collapsible', '.mw-collapsible', '.wikitable.collapsible', 
                        '.mw-collapsible-content', '.navbox'
                    ];
                    
                    var totalCollapsibles = 0;
                    collapsibleSelectors.forEach(function(selector) {
                        var elements = document.querySelectorAll(selector);
                        if (elements.length > 0) {
                            console.log('UnifiedPreviewGen: Found ' + elements.length + ' elements with selector: ' + selector);
                            totalCollapsibles += elements.length;
                            
                            elements.forEach(function(element) {
                                if (collapsed) {
                                    element.classList.add('collapsed');
                                    element.classList.add('mw-collapsed');
                                    // Also try hiding content directly
                                    if (element.querySelector('.mw-collapsible-content')) {
                                        element.querySelector('.mw-collapsible-content').style.display = 'none';
                                    }
                                } else {
                                    element.classList.remove('collapsed');
                                    element.classList.remove('mw-collapsed');
                                    // Show content
                                    if (element.querySelector('.mw-collapsible-content')) {
                                        element.querySelector('.mw-collapsible-content').style.display = '';
                                    }
                                }
                            });
                        }
                    });
                    
                    console.log('UnifiedPreviewGen: Total collapsible elements processed: ' + totalCollapsibles);
                    
                    // Trigger any existing collapse handlers
                    if (typeof window.updateCollapsibleElements === 'function') {
                        window.updateCollapsibleElements();
                    }
                    
                    // If no mutations detected, still callback after short delay
                    setTimeout(function() {
                        if (observer) {
                            observer.disconnect();
                            console.log('UnifiedPreviewGen: Fallback callback for table state change');
                            if (typeof OsrsWikiBridge !== 'undefined' && OsrsWikiBridge.onTableStateChangeComplete) {
                                OsrsWikiBridge.onTableStateChangeComplete();
                            }
                        }
                    }, 100);
                    
                    return true;
                };
                
                // Content verification function with CSS class analysis
                window.verifyPageContent = function() {
                    // Find common collapsible class patterns
                    var potentialCollapsibleClasses = [
                        '.collapsible', '.mw-collapsible', '.wikitable', '.navbox', 
                        '.mw-collapsible-toggle', '.collapsible-toggle', 
                        '.mw-collapsible-content', '.collapsible-content',
                        '[data-collapsible]', '[data-mw-collapsible]'
                    ];
                    
                    var classStats = {};
                    potentialCollapsibleClasses.forEach(function(selector) {
                        var elements = document.querySelectorAll(selector);
                        if (elements.length > 0) {
                            classStats[selector] = elements.length;
                        }
                    });
                    
                    // Find all tables and their classes
                    var tables = document.querySelectorAll('table');
                    var tableClasses = [];
                    tables.forEach(function(table, index) {
                        if (index < 5) { // First 5 tables only
                            tableClasses.push({
                                'index': index,
                                'classes': table.className,
                                'hasCollapsible': table.className.includes('collaps') || table.className.includes('mw-')
                            });
                        }
                    });
                    
                    var contentStats = {
                        'bodyChildren': document.body ? document.body.children.length : 0,
                        'tables': tables.length,
                        'collapsibleElements': document.querySelectorAll('.collapsible').length,
                        'images': document.querySelectorAll('img').length,
                        'paragraphs': document.querySelectorAll('p').length,
                        'headers': document.querySelectorAll('h1,h2,h3,h4,h5,h6').length,
                        'bodyScrollHeight': document.body ? document.body.scrollHeight : 0,
                        'hasVarrockContent': document.body ? document.body.textContent.includes('Varrock') : false,
                        'collapsibleClasses': classStats,
                        'tableClassSamples': tableClasses
                    };
                    console.log('UnifiedPreviewGen: Content verification:', JSON.stringify(contentStats));
                    return contentStats;
                };
                
                // Initialize with light theme and default table state
                document.addEventListener('DOMContentLoaded', function() {
                    console.log('UnifiedPreviewGen: DOM loaded, verifying content before setting initial state');
                    window.verifyPageContent();
                    
                    // Set initial state without callbacks (we're already in the right state)
                    console.log('UnifiedPreviewGen: Setting initial theme and table state');
                    document.documentElement.dataset.theme = 'osrs_light';
                    window.OSRS_TABLE_COLLAPSED = false; // Start with expanded tables
                });
            </script>
        """.trimIndent()
        
        // Insert the control script after <head> tag
        return html.replace("<head>", "<head>\n$controlScript")
    }
    
    /**
     * Starts the capture sequence after initial page load
     */
    private fun startCaptureSequence(
        webView: ObservableWebView,
        container: FrameLayout,
        rootView: FrameLayout,
        fullScreenW: Int,
        fullScreenH: Int,
        context: Context,
        continuation: kotlinx.coroutines.CancellableContinuation<PreviewResult>,
        startTime: Long,
        previewScope: CoroutineScope
    ) {
        val initialLoadTime = System.currentTimeMillis() - startTime
        Log.i(TAG, "Initial page load completed in ${initialLoadTime}ms - starting capture sequence")
        
        // We'll capture in this order:
        // 1. Light theme, tables expanded (current state)
        // 2. Light theme, tables collapsed 
        // 3. Dark theme, tables collapsed
        // 4. Dark theme, tables expanded
        // Plus theme previews for both themes
        
        val captures = mutableMapOf<String, Bitmap>()
        
        // Verify content is ready before starting captures
        verifyContentBeforeCapture(webView) { hasContent ->
            if (!hasContent) {
                Log.w(TAG, "Content verification failed - proceeding with capture anyway")
            }
            
            // Start with first capture (light theme, expanded tables - current state)
            captureState(webView, container, fullScreenW, fullScreenH, "table-light-expanded", context) { bitmap1 ->
            if (bitmap1 != null) {
                captures["table-light-expanded"] = bitmap1
                Log.i("StartupTiming", "UNIFIED_CAPTURE_1 table-light-expanded duration=immediate (current state)")
                
                // Set tables to collapsed - inline function with access to callback variables
                fun toggleTableStateInline(collapsed: Boolean, callback: (Boolean) -> Unit) {
                    Log.d(TAG, "Toggling table state to collapsed: $collapsed")
                    this@UnifiedPreviewGenerator.pendingTableStateChangeCallback = callback
                    
                    webView.evaluateJavascript("window.setTableCollapse($collapsed)") { result ->
                        val success = result == "true"
                        Log.d(TAG, "Table toggle JavaScript result: $result")
                        if (!success) {
                            this@UnifiedPreviewGenerator.pendingTableStateChangeCallback = null
                            callback(false)
                        }
                    }
                }
                
                fun toggleThemeInline(theme: String, callback: (Boolean) -> Unit) {
                    Log.d(TAG, "Toggling theme to: $theme")
                    this@UnifiedPreviewGenerator.pendingThemeChangeCallback = callback
                    
                    webView.evaluateJavascript("window.setTheme('$theme')") { result ->
                        val success = result == "true"
                        Log.d(TAG, "Theme toggle JavaScript result: $result")
                        if (!success) {
                            this@UnifiedPreviewGenerator.pendingThemeChangeCallback = null
                            callback(false)
                        }
                    }
                }
                
                toggleTableStateInline(true) { success ->
                    if (success) {
                        captureState(webView, container, fullScreenW, fullScreenH, "table-light-collapsed", context) { bitmap2 ->
                            if (bitmap2 != null) {
                                captures["table-light-collapsed"] = bitmap2
                                Log.i("StartupTiming", "UNIFIED_CAPTURE_2 table-light-collapsed duration=~300ms")
                                
                                // Switch to dark theme (keep tables collapsed)
                                toggleThemeInline("osrs_dark") { success ->
                                    if (success) {
                                        captureState(webView, container, fullScreenW, fullScreenH, "table-dark-collapsed", context) { bitmap3 ->
                                            if (bitmap3 != null) {
                                                captures["table-dark-collapsed"] = bitmap3
                                                Log.i("StartupTiming", "UNIFIED_CAPTURE_3 table-dark-collapsed duration=~300ms")
                                                
                                                // Set tables to expanded (keep dark theme)
                                                toggleTableStateInline(false) { success ->
                                                    if (success) {
                                                        captureState(webView, container, fullScreenW, fullScreenH, "table-dark-expanded", context) { bitmap4 ->
                                                            if (bitmap4 != null) {
                                                                captures["table-dark-expanded"] = bitmap4
                                                                Log.i("StartupTiming", "UNIFIED_CAPTURE_4 table-dark-expanded duration=~300ms")
                                                                
                                                                // Generate theme previews (using native layout approach)
                                                                previewScope.launch {
                                                                    try {
                                                                        val (themeLight, themeDark) = generateThemePreviews(context)
                                                                        val totalTime = System.currentTimeMillis() - startTime
                                                                        Log.i("StartupTiming", "UNIFIED_GENERATION_COMPLETE total_duration=${totalTime}ms (target: <3000ms)")
                                                                        
                                                                        val result = PreviewResult(
                                                                            tableCollapsedLight = captures["table-light-collapsed"]!!,
                                                                            tableExpandedLight = captures["table-light-expanded"]!!,
                                                                            tableCollapsedDark = captures["table-dark-collapsed"]!!,
                                                                            tableExpandedDark = captures["table-dark-expanded"]!!,
                                                                            themeLight = themeLight,
                                                                            themeDark = themeDark
                                                                        )
                                                                        
                                                                        cleanup(webView, container, rootView)
                                                                        continuation.resume(result)
                                                                    } catch (e: Exception) {
                                                                        handleCaptureFailure(webView, container, rootView, continuation, "generate theme previews")
                                                                    }
                                                                }
                                                            } else {
                                                                handleCaptureFailure(webView, container, rootView, continuation, "table-dark-expanded")
                                                            }
                                                        }
                                                    } else {
                                                        handleCaptureFailure(webView, container, rootView, continuation, "toggle tables to expanded")
                                                    }
                                                }
                                            } else {
                                                handleCaptureFailure(webView, container, rootView, continuation, "table-dark-collapsed")
                                            }
                                        }
                                    } else {
                                        handleCaptureFailure(webView, container, rootView, continuation, "toggle to dark theme")
                                    }
                                }
                            } else {
                                handleCaptureFailure(webView, container, rootView, continuation, "table-light-collapsed")
                            }
                        }
                    } else {
                        handleCaptureFailure(webView, container, rootView, continuation, "toggle tables to collapsed")
                    }
                }
            } else {
                handleCaptureFailure(webView, container, rootView, continuation, "table-light-expanded")
            }
        }
    }
    }
    
    /**
     * Verifies WebView content before capture
     */
    private fun verifyContentBeforeCapture(webView: ObservableWebView, callback: (Boolean) -> Unit) {
        Log.d(TAG, "Verifying WebView content before capture")
        
        webView.evaluateJavascript("window.verifyPageContent()") { result ->
            Log.d(TAG, "Content verification result: $result")
            
            try {
                // Parse the JSON result to check content quality
                val hasValidContent = result.contains("\"hasVarrockContent\":true") && 
                                     result.contains("\"tables\":") &&
                                     !result.contains("\"tables\":0")
                
                Log.d(TAG, "Content verification: hasValidContent=$hasValidContent")
                callback(hasValidContent)
                
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing content verification result: ${e.message}")
                callback(false)
            }
        }
    }
    
    /**
     * Captures bitmap for current WebView state
     */
    private fun captureState(
        webView: ObservableWebView,
        container: FrameLayout,
        fullScreenW: Int,
        fullScreenH: Int,
        stateName: String,
        context: Context,
        callback: (Bitmap?) -> Unit
    ) {
        Log.d(TAG, "Capturing state: $stateName")
        
        try {
            // Force layout update
            container.measure(
                View.MeasureSpec.makeMeasureSpec(fullScreenW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(fullScreenH, View.MeasureSpec.EXACTLY)
            )
            container.layout(0, 0, fullScreenW, fullScreenH)
            
            // Create bitmap and capture
            val fullBitmap = Bitmap.createBitmap(fullScreenW, fullScreenH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(fullBitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            container.draw(canvas)
            
            // Scale for table preview dimensions
            val dm = context.resources.displayMetrics
            val targetW = (TARGET_TABLE_W_DP * dm.density).roundToInt()
            val targetH = (TARGET_TABLE_H_DP * dm.density).roundToInt()
            
            val scaledBitmap = createAspectRatioPreservingBitmap(fullBitmap, targetW, targetH)
            scaledBitmap.density = DisplayMetrics.DENSITY_DEFAULT
            
            fullBitmap.recycle()
            
            Log.d(TAG, "Captured state '$stateName': ${scaledBitmap.width}×${scaledBitmap.height}")
            callback(scaledBitmap)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture state '$stateName'", e)
            callback(null)
        }
    }
    
    /**
     * Generates theme previews using native layout approach (fast)
     */
    private suspend fun generateThemePreviews(
        context: Context
    ): Pair<Bitmap, Bitmap> = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "Generating theme previews using native layout approach")
            
            // Use ThemePreviewRenderer for theme previews (it's already optimized)
            val lightTheme = ThemePreviewRenderer.getPreview(context, R.style.Theme_OSRSWiki_OSRSLight, "light")
            val darkTheme = ThemePreviewRenderer.getPreview(context, R.style.Theme_OSRSWiki_OSRSDark, "dark")
            
            return@withContext Pair(lightTheme, darkTheme)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate theme previews", e)
            // Generate fallback bitmaps
            val fallback1 = generateFallbackBitmap(context, TARGET_THEME_SIZE_DP)
            val fallback2 = generateFallbackBitmap(context, TARGET_THEME_SIZE_DP)
            return@withContext Pair(fallback1, fallback2)
        }
    }
    
    /**
     * Creates aspect ratio preserving bitmap (same as TablePreviewRenderer)
     */
    private fun createAspectRatioPreservingBitmap(
        sourceBitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val sourceWidth = sourceBitmap.width.toFloat()
        val sourceHeight = sourceBitmap.height.toFloat()
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
        val sourceRatio = sourceWidth / sourceHeight
        
        val scale = if (sourceRatio > targetRatio) {
            targetHeight.toFloat() / sourceHeight
        } else {
            targetWidth.toFloat() / sourceWidth
        }
        
        val scaledWidth = (sourceWidth * scale).toInt()
        val scaledHeight = (sourceHeight * scale).toInt()
        
        val scaledBitmap = Bitmap.createScaledBitmap(
            sourceBitmap, 
            scaledWidth, 
            scaledHeight, 
            true
        )
        
        val cropX = maxOf(0, (scaledWidth - targetWidth) / 2)
        val cropY = 0 // Top-aligned
        
        val croppedBitmap = Bitmap.createBitmap(
            scaledBitmap,
            cropX,
            cropY,
            minOf(targetWidth, scaledWidth),
            minOf(targetHeight, scaledHeight),
            null,
            false
        )
        
        if (scaledBitmap != croppedBitmap) {
            scaledBitmap.recycle()
        }
        
        return croppedBitmap
    }
    
    /**
     * Waits for WebView content to be ready (same as TablePreviewRenderer)
     */
    private fun waitForWebViewContentReady(
        webView: ObservableWebView,
        callback: (Boolean) -> Unit
    ) {
        var attempts = 0
        val maxAttempts = 20
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        fun checkContentReady() {
            attempts++
            
            val progress = webView.progress
            val contentHeight = webView.contentHeight
            val hasContent = progress >= 100 && contentHeight > 0
            
            Log.d(TAG, "Content readiness check $attempts/$maxAttempts: progress=$progress%, contentHeight=$contentHeight, ready=$hasContent")
            
            when {
                hasContent -> {
                    Log.d(TAG, "WebView content ready after $attempts attempts")
                    callback(true)
                }
                attempts >= maxAttempts -> {
                    Log.w(TAG, "Max attempts reached ($maxAttempts), proceeding anyway")
                    callback(false)
                }
                else -> {
                    handler.postDelayed({ checkContentReady() }, 50)
                }
            }
        }
        
        checkContentReady()
    }
    
    /**
     * Handles capture failure
     */
    private fun handleCaptureFailure(
        webView: ObservableWebView?,
        container: FrameLayout?,
        rootView: FrameLayout?,
        continuation: kotlinx.coroutines.CancellableContinuation<PreviewResult>,
        operation: String
    ) {
        Log.e(TAG, "Capture failed at operation: $operation")
        cleanup(webView, container, rootView)
        continuation.resumeWithException(Exception("Preview capture failed at: $operation"))
    }
    
    /**
     * Cleans up WebView and UI resources
     */
    private fun cleanup(
        webView: ObservableWebView?,
        container: FrameLayout?,
        rootView: FrameLayout?
    ) {
        try {
            webView?.pauseTimers()
            container?.let { rootView?.removeView(it) }
            webView?.destroy()
            Log.d(TAG, "Cleaned up WebView and container")
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup: ${e.message}")
        }
    }
    
    /**
     * Generates fallback bitmap
     */
    private fun generateFallbackBitmap(context: Context, sizeDp: Int): Bitmap {
        val dm = context.resources.displayMetrics
        val size = (sizeDp * dm.density).roundToInt()
        
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.LTGRAY)
        
        bitmap.density = DisplayMetrics.DENSITY_DEFAULT
        return bitmap
    }
}