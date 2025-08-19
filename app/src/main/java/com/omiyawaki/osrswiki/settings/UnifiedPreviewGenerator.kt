package com.omiyawaki.osrswiki.settings

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.os.Build
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.delay
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
    ): PreviewResult {
        
        Log.i(TAG, "Starting sequential HTML generation for all preview states")
        val startTime = System.currentTimeMillis()
        
        // Load Varrock article HTML content first
        val htmlContent = loadVarrockArticleHtml(context)
        if (htmlContent == null) {
            Log.e(TAG, "Failed to load Varrock article HTML content")
            throw Exception("Failed to load Varrock article HTML content")
        }
        
        return withContext(Dispatchers.Main) {
            // Generate all 4 preview states sequentially using separate HTML documents
            val results = mutableMapOf<String, Bitmap>()
            val htmlBuilder = PageHtmlBuilder(context)
            
            try {
                Log.i(TAG, "Generating table preview: Light theme, Collapsed tables")
                results["table-light-collapsed"] = generateSinglePreview(
                    context, htmlBuilder, htmlContent, Theme.OSRS_LIGHT, true
                )
                
                Log.i(TAG, "Generating table preview: Light theme, Expanded tables")  
                results["table-light-expanded"] = generateSinglePreview(
                    context, htmlBuilder, htmlContent, Theme.OSRS_LIGHT, false
                )
                
                Log.i(TAG, "Generating table preview: Dark theme, Collapsed tables")
                results["table-dark-collapsed"] = generateSinglePreview(
                    context, htmlBuilder, htmlContent, Theme.OSRS_DARK, true
                )
                
                Log.i(TAG, "Generating table preview: Dark theme, Expanded tables")
                results["table-dark-expanded"] = generateSinglePreview(
                    context, htmlBuilder, htmlContent, Theme.OSRS_DARK, false
                )
                
                // Generate theme previews using ThemePreviewRenderer  
                Log.i(TAG, "Generating theme previews")
                val themeLight = generateThemePreview(context, Theme.OSRS_LIGHT)
                val themeDark = generateThemePreview(context, Theme.OSRS_DARK)
                
                val totalTime = System.currentTimeMillis() - startTime
                Log.i("StartupTiming", "SEQUENTIAL_GENERATION_COMPLETE total_duration=${totalTime}ms")
                
                PreviewResult(
                    tableCollapsedLight = results["table-light-collapsed"]!!,
                    tableExpandedLight = results["table-light-expanded"]!!,
                    tableCollapsedDark = results["table-dark-collapsed"]!!,
                    tableExpandedDark = results["table-dark-expanded"]!!,
                    themeLight = themeLight,
                    themeDark = themeDark
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Sequential preview generation failed", e)
                throw e
            }
        }
    }
    
    /**
     * Generates a single preview for a specific theme and collapse state
     */
    private suspend fun generateSinglePreview(
        context: Context,
        htmlBuilder: PageHtmlBuilder,
        htmlContent: String,
        theme: Theme,
        collapsed: Boolean
    ): Bitmap = suspendCancellableCoroutine { continuation ->
        
        Log.i(TAG, "Generating preview for theme: ${theme.tag}, collapsed: $collapsed")
        var isCompleted = false // Prevent double resume
        
        // Generate HTML for this specific state
        val stateSpecificHtml = htmlBuilder.buildFullHtmlDocument("Varrock", htmlContent, theme, collapsed)
        
        // Create WebView for this state
        val webView = createConfiguredWebView(context, theme)
        
        val webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (isCompleted) return
                
                Log.i(TAG, "Page finished loading for ${theme.tag}-${if (collapsed) "collapsed" else "expanded"}")
                
                // Wait a bit for complete rendering before capture
                view.postDelayed({
                    if (isCompleted) return@postDelayed
                    
                    try {
                        val bitmap = captureWebViewBitmap(view)
                        Log.i(TAG, "Successfully captured preview: ${theme.tag}-${if (collapsed) "collapsed" else "expanded"}")
                        isCompleted = true
                        continuation.resume(bitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to capture preview: ${theme.tag}-${if (collapsed) "collapsed" else "expanded"}", e)
                        if (!isCompleted) {
                            isCompleted = true
                            continuation.resumeWithException(e)
                        }
                    }
                }, 1000) // Wait 1 second for CSS/theme to fully apply
            }
            
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (isCompleted) return
                
                Log.e(TAG, "WebView error for ${theme.tag}: ${error?.description}")
                isCompleted = true
                continuation.resumeWithException(Exception("WebView error: ${error?.description}"))
            }
        }
        
        webView.webViewClient = webViewClient
        webView.loadDataWithBaseURL(
            "file:///android_asset/",
            stateSpecificHtml,
            "text/html",
            "UTF-8",
            null
        )
        
        // Handle cancellation
        continuation.invokeOnCancellation {
            Log.i(TAG, "Preview generation cancelled for ${theme.tag}")
            if (!isCompleted) {
                isCompleted = true
                try {
                    webView.destroy()
                } catch (e: Exception) {
                    Log.w(TAG, "Error destroying WebView during cancellation", e)
                }
            }
        }
    }
    
    /**
     * Creates a properly configured WebView for preview generation
     */
    private fun createConfiguredWebView(context: Context, theme: Theme): WebView {
        // Use themed context for proper WebView styling
        val themeRes = when (theme) {
            Theme.OSRS_LIGHT -> R.style.Theme_OSRSWiki_OSRSLight
            Theme.OSRS_DARK -> R.style.Theme_OSRSWiki_OSRSDark
        }
        val themedContext = ContextThemeWrapper(context, themeRes)
        
        return WebView(themedContext).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowFileAccessFromFileURLs = true
            settings.allowUniversalAccessFromFileURLs = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            
            // Enable proper rendering for screenshot capture
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                WebView.enableSlowWholeDocumentDraw()
            }
            
            // Set dimensions for consistent preview size
            val dm = context.resources.displayMetrics
            val width = dm.widthPixels
            val height = dm.heightPixels
            
            // Layout the WebView for proper measurement
            measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, width, height)
        }
    }
    
    /**
     * Captures WebView content as bitmap
     */
    private fun captureWebViewBitmap(webView: WebView): Bitmap {
        val width = webView.width
        val height = webView.contentHeight
        
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "WebView has invalid dimensions: ${width}x${height}")
            throw Exception("WebView has invalid dimensions for capture")
        }
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        webView.draw(canvas)
        
        Log.d(TAG, "Captured WebView bitmap: ${width}x${height}")
        return bitmap
    }
    
    /**
     * Generates a theme preview using ThemePreviewRenderer
     */
    private suspend fun generateThemePreview(context: Context, theme: Theme): Bitmap {
        Log.i(TAG, "Generating theme preview for: ${theme.tag}")
        
        return try {
            // Use the existing ThemePreviewRenderer to generate theme previews
            val themeRes = when (theme) {
                Theme.OSRS_LIGHT -> R.style.Theme_OSRSWiki_OSRSLight
                Theme.OSRS_DARK -> R.style.Theme_OSRSWiki_OSRSDark
            }
            val themeKey = theme.tag.replace("osrs_", "") // "light" or "dark"
            
            val themePreview = ThemePreviewRenderer.getPreview(context, themeRes, themeKey)
            Log.i(TAG, "Successfully generated theme preview for: ${theme.tag}")
            themePreview
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate theme preview for ${theme.tag}", e)
            // Fallback: create a simple colored bitmap
            createFallbackThemePreview(context, theme)
        }
    }
    
    /**
     * Creates a simple fallback theme preview if the main generation fails
     */
    private fun createFallbackThemePreview(context: Context, theme: Theme): Bitmap {
        val dm = context.resources.displayMetrics
        val width = dm.widthPixels / 3 // Smaller size for preview
        val height = dm.heightPixels / 4
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Fill with theme-appropriate color
        val backgroundColor = when (theme) {
            Theme.OSRS_LIGHT -> Color.WHITE
            Theme.OSRS_DARK -> Color.BLACK
        }
        canvas.drawColor(backgroundColor)
        
        // Add some text to indicate the theme
        val paint = Paint().apply {
            color = when (theme) {
                Theme.OSRS_LIGHT -> Color.BLACK
                Theme.OSRS_DARK -> Color.WHITE
            }
            textSize = 48f
            isAntiAlias = true
        }
        
        val text = theme.tag.replace("osrs_", "").capitalize()
        canvas.drawText(text, width / 2f - 50f, height / 2f, paint)
        
        Log.i(TAG, "Created fallback theme preview for: ${theme.tag}")
        return bitmap
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
                // Enhanced for API 31 compatibility
                window.setTheme = function(theme) {
                    console.log('UnifiedPreviewGen: Setting theme to ' + theme);
                    document.documentElement.dataset.theme = theme;
                    
                    // API 31 fix: Apply explicit CSS overrides for theme consistency
                    var style = document.getElementById('api-compat-theme-override');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = 'api-compat-theme-override';
                        document.head.appendChild(style);
                    }
                    
                    // Apply theme-specific CSS overrides for API 31 compatibility
                    if (theme === 'osrs_dark') {
                        style.textContent = `
                            body { background-color: #28221d !important; color: #f4eaea !important; }
                            .wikitable, table { background-color: #3a2f29 !important; border-color: #5d4e42 !important; }
                            .wikitable th, table th { background-color: #4a3f39 !important; color: #f4eaea !important; }
                            .wikitable td, table td { background-color: #2a241f !important; color: #f4eaea !important; border-color: #5d4e42 !important; }
                        `;
                    } else {
                        style.textContent = `
                            body { background-color: #ffffff !important; color: #222222 !important; }
                            .wikitable, table { background-color: #f8f9fa !important; border-color: #a2a9b1 !important; }
                            .wikitable th, table th { background-color: #eaecf0 !important; color: #222222 !important; }
                            .wikitable td, table td { background-color: #ffffff !important; color: #222222 !important; border-color: #a2a9b1 !important; }
                        `;
                    }
                    
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
                // Enhanced for API 31 compatibility with more explicit visual state changes
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
                    
                    // API 31 fix: Apply more explicit table collapse styles
                    var collapseStyle = document.getElementById('api-compat-collapse-override');
                    if (!collapseStyle) {
                        collapseStyle = document.createElement('style');
                        collapseStyle.id = 'api-compat-collapse-override';
                        document.head.appendChild(collapseStyle);
                    }
                    
                    // Apply table collapse changes - try multiple CSS class patterns
                    var collapsibleSelectors = [
                        '.collapsible', '.mw-collapsible', '.wikitable.collapsible', 
                        '.mw-collapsible-content', '.navbox', 'table.collapsible'
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
                                    // For tables, hide tbody/rows directly
                                    if (element.tagName === 'TABLE') {
                                        var tbody = element.querySelector('tbody');
                                        if (tbody && tbody.rows.length > 1) {
                                            // Hide all but first row (header)
                                            for (var i = 1; i < tbody.rows.length; i++) {
                                                tbody.rows[i].style.display = 'none';
                                            }
                                        }
                                    }
                                } else {
                                    element.classList.remove('collapsed');
                                    element.classList.remove('mw-collapsed');
                                    // Show content
                                    if (element.querySelector('.mw-collapsible-content')) {
                                        element.querySelector('.mw-collapsible-content').style.display = '';
                                    }
                                    // For tables, show all rows
                                    if (element.tagName === 'TABLE') {
                                        var tbody = element.querySelector('tbody');
                                        if (tbody) {
                                            for (var i = 0; i < tbody.rows.length; i++) {
                                                tbody.rows[i].style.display = '';
                                            }
                                        }
                                    }
                                }
                            });
                        }
                    });
                    
                    // Apply CSS-based collapse for API 31 compatibility
                    if (collapsed) {
                        collapseStyle.textContent = `
                            .mw-collapsible.collapsed > *:not(.mw-collapsible-toggle) { display: none !important; }
                            .collapsible.collapsed tbody tr:not(:first-child) { display: none !important; }
                            .wikitable.collapsed tbody tr:not(:first-child) { display: none !important; }
                        `;
                    } else {
                        collapseStyle.textContent = `
                            .mw-collapsible > * { display: block !important; }
                            .collapsible tbody tr { display: table-row !important; }
                            .wikitable tbody tr { display: table-row !important; }
                        `;
                    }
                    
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
     * Starts the proper capture sequence that generates HTML documents for each theme
     * instead of trying to change themes with JavaScript
     */
    /* DISABLED - has compilation errors
    private fun startProperCaptureSequence(
        htmlContent: String,
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
        Log.i(TAG, "Initial light theme page load completed in ${initialLoadTime}ms - starting proper theme-based capture sequence")
        
        // PROPER APPROACH: Generate separate HTML documents for each theme
        Log.i(TAG, "Capturing light theme, expanded tables (current WebView)")
        
        val htmlBuilder = PageHtmlBuilder(context)
        val results = mutableMapOf<String, Bitmap?>()
        
        // Capture current light theme, expanded state first (already loaded)
        captureState(webView, container, fullScreenW, fullScreenH, "table-light-expanded", context, "osrs_light") { bitmap ->
            results["table-light-expanded"] = bitmap
            Log.i("StartupTiming", "UNIFIED_CAPTURE_1 table-light-expanded duration=immediate")
            
            // Generate light theme, collapsed tables HTML
            Log.i(TAG, "Generating light theme, collapsed tables HTML")
            val lightCollapsedHtml = htmlBuilder.buildFullHtmlDocument("Varrock", htmlContent, Theme.OSRS_LIGHT, true)
            
            renderAndCapture(webView, lightCollapsedHtml, container, fullScreenW, fullScreenH, "table-light-collapsed", context) { bitmap ->
                results["table-light-collapsed"] = bitmap
                
                // Generate dark theme, collapsed tables HTML
                Log.i(TAG, "Generating dark theme, collapsed tables HTML")
                val darkCollapsedHtml = htmlBuilder.buildFullHtmlDocument("Varrock", htmlContent, Theme.OSRS_DARK, true)
                
                renderAndCapture(webView, darkCollapsedHtml, container, fullScreenW, fullScreenH, "table-dark-collapsed", context) { bitmap ->
                    results["table-dark-collapsed"] = bitmap
                    
                    // Generate dark theme, expanded tables HTML
                    Log.i(TAG, "Generating dark theme, expanded tables HTML")
                    val darkExpandedHtml = htmlBuilder.buildFullHtmlDocument("Varrock", htmlContent, Theme.OSRS_DARK, false)
                    
                    renderAndCapture(webView, darkExpandedHtml, container, fullScreenW, fullScreenH, "table-dark-expanded", context) { bitmap ->
                        results["table-dark-expanded"] = bitmap
                        
                        // Generate theme previews using appropriate HTML
                        Log.i(TAG, "Generating theme previews")
                        val lightThemeHtml = htmlBuilder.buildFullHtmlDocument("Varrock", htmlContent, Theme.OSRS_LIGHT, false)
                        val darkThemeHtml = htmlBuilder.buildFullHtmlDocument("Varrock", htmlContent, Theme.OSRS_DARK, false)
                        
                        renderAndCapture(webView, lightThemeHtml, container, fullScreenW, fullScreenH, "theme-light", context) { bitmap ->
                            results["theme-light"] = bitmap
                            
                            renderAndCapture(webView, darkThemeHtml, container, fullScreenW, fullScreenH, "theme-dark", context) { bitmap ->
                                results["theme-dark"] = bitmap
                                
                                // All captures complete - finish
                                val unifiedDuration = System.currentTimeMillis() - startTime
                                Log.i("StartupTiming", "UNIFIED_GENERATION_COMPLETE total_duration=${unifiedDuration}ms")
                                
                                cleanup(webView, container, rootView)
                                
                                val previewResult = PreviewResult(
                                    tableExpandedLight = results["table-light-expanded"] ?: throw Exception("Failed to capture light expanded preview"),
                                    tableCollapsedLight = results["table-light-collapsed"] ?: throw Exception("Failed to capture light collapsed preview"), 
                                    tableCollapsedDark = results["table-dark-collapsed"] ?: throw Exception("Failed to capture dark collapsed preview"),
                                    tableExpandedDark = results["table-dark-expanded"] ?: throw Exception("Failed to capture dark expanded preview"),
                                    themeLight = results["theme-light"] ?: throw Exception("Failed to capture light theme preview"),
                                    themeDark = results["theme-dark"] ?: throw Exception("Failed to capture dark theme preview")
                                )
                                
                                continuation.resume(previewResult)
                            }
                        }
                    }
                }
            }
        }
    }
    */

    /**
     * Waits for WebView content to be ready before capturing
     */
    private suspend fun waitForWebViewContentReady(webView: WebView, callback: () -> Unit) {
        // Simple implementation - wait for a short delay for content to render
        delay(1000)
        callback()
    }

    /**
     * Renders HTML in WebView and captures screenshot
     */
    private suspend fun renderAndCapture(
        webView: WebView,
        html: String,
        container: ViewGroup,
        width: Int,
        height: Int,
        captureId: String,
        context: Context,
        callback: (Bitmap?) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null
            )
            
            // Wait for content to load then capture
            delay(1500)
            
            val bitmap = try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)?.apply {
                    val canvas = Canvas(this)
                    webView.draw(canvas)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture $captureId", e)
                null
            }
            
            callback(bitmap)
        }
    }

    /**
     * Captures current WebView state
     */
    private suspend fun captureState(
        webView: WebView,
        container: ViewGroup,
        width: Int,
        height: Int,
        captureId: String,
        context: Context,
        callback: (Bitmap?) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            delay(500) // Brief wait for any animations to settle
            
            val bitmap = try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)?.apply {
                    val canvas = Canvas(this)
                    webView.draw(canvas)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture state $captureId", e)
                null
            }
            
            callback(bitmap)
        }
    }

    /**
     * Clean up WebView and container
     */
    private fun cleanup(webView: WebView, container: ViewGroup, rootView: ViewGroup) {
        try {
            webView.stopLoading()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
            
            container.removeAllViews()
            rootView.removeView(container)
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup warning", e)
        }
    }
}
