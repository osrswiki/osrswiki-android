package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.DisplayMetrics
import android.util.Log
import android.util.LruCache
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.omiyawaki.osrswiki.views.ObservableWebView
import android.util.TypedValue
import android.content.res.Resources
import androidx.annotation.StyleRes
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.page.PageHtmlBuilder
import com.omiyawaki.osrswiki.page.PageRepository
import com.omiyawaki.osrswiki.page.PageUiState
import com.omiyawaki.osrswiki.page.PageWebViewManager
import com.omiyawaki.osrswiki.page.PageLinkHandler
import com.omiyawaki.osrswiki.page.RenderCallback
import com.omiyawaki.osrswiki.page.PageAssetDownloader
import com.omiyawaki.osrswiki.page.DownloadProgress
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.Result
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import java.io.File

/**
 * Generates table collapse preview by rendering the Varrock article with tables in specific state.
 * Each card shows a single state (collapsed or expanded) rather than A/B comparison.
 */
object TablePreviewRenderer {
    
    private const val TARGET_W_DP = 160 // Width for individual preview
    private const val TARGET_H_DP = 120 // Height for individual preview
    private const val TAG = "TablePreviewRenderer"
    
    // Mutex to prevent concurrent preview generation (prevents DOM interference)
    private val previewGenerationMutex = Mutex()
    
    // Memory cache for table previews
    private val memoryCache = object : LruCache<String, Bitmap>(getMaxCacheSize()) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }
    
    private fun getMaxCacheSize(): Int {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt() // KB
        return maxMemory / 16 // Use smaller cache than theme previews (only 2 states vs 3)
    }
    
    /**
     * Gets a table preview bitmap showing the specific table collapse state.
     * @param context Application context
     * @param collapseTablesEnabled Whether tables should be collapsed in this preview
     */
    suspend fun getPreview(
        context: Context,
        collapseTablesEnabled: Boolean
    ): Bitmap = withContext(Dispatchers.Default) {
        val cacheKey = "table-preview-${if (collapseTablesEnabled) "collapsed" else "expanded"}-v${BuildConfig.VERSION_CODE}"
        
        Log.d(TAG, "getPreview called for collapseTablesEnabled=$collapseTablesEnabled")
        
        try {
            // Check memory cache first
            memoryCache.get(cacheKey)?.let { cached ->
                Log.d(TAG, "Found cached table preview ($cacheKey) - ${cached.width}×${cached.height}")
                return@withContext cached
            }
            
            // Check disk cache
            val cacheDir = File(context.cacheDir, "table_previews").apply { mkdirs() }
            val cachedFile = File(cacheDir, "$cacheKey.webp")
            
            if (cachedFile.exists()) {
                Log.d(TAG, "Found disk cached table preview ($cacheKey)")
                val bitmap = BitmapFactory.decodeFile(cachedFile.absolutePath)
                if (bitmap != null && !bitmap.isRecycled) {
                    Log.d(TAG, "Loaded disk cached table preview - ${bitmap.width}×${bitmap.height}")
                    memoryCache.put(cacheKey, bitmap)
                    return@withContext bitmap
                } else {
                    Log.w(TAG, "Disk cached bitmap was null or recycled, deleting cache file")
                    cachedFile.delete()
                }
            }
            
            // Serialize preview generation to prevent DOM interference
            previewGenerationMutex.withLock {
                // Generate new preview
                Log.d(TAG, "Generating new table preview for collapseTablesEnabled=$collapseTablesEnabled (serialized)")
                val newBitmap = generateSingleTablePreview(context, collapseTablesEnabled)
                
                if (newBitmap.isRecycled) {
                    Log.e(TAG, "Generated bitmap is recycled! This is a bug.")
                    return@withContext generateFallbackBitmap(context, collapseTablesEnabled)
                }
                
                // Cache in memory
                memoryCache.put(cacheKey, newBitmap)
                Log.d(TAG, "Cached new table preview in memory")
                
                // Save to disk cache
                try {
                    cachedFile.outputStream().use { stream ->
                        val compressed = newBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream)
                        if (compressed) {
                            Log.d(TAG, "Saved table preview to disk cache")
                        } else {
                            Log.w(TAG, "Failed to compress table preview for disk cache")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Disk caching failed for table preview: ${e.message}")
                }
                
                Log.d(TAG, "Successfully generated ${newBitmap.width}×${newBitmap.height} table preview")
                newBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPreview FAILED", e)
            e.printStackTrace()
            generateFallbackBitmap(context, collapseTablesEnabled)
        }
    }
    
    
    /**
     * Generates a single table preview by loading real Varrock article and applying collapse setting.
     */
    private suspend fun generateSingleTablePreview(
        context: Context,
        collapseTablesEnabled: Boolean
    ): Bitmap = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "Starting generateSingleTablePreview for collapseTablesEnabled=$collapseTablesEnabled")
            
            // Get real Varrock article HTML with proper collapse setting
            val articleHtml = loadVarrockArticleHtml(context, collapseTablesEnabled)
            
            // Render using WebView with proper callback
            val bitmap = renderHtmlWithWebView(context, articleHtml, collapseTablesEnabled)
            
            Log.d(TAG, "Single table preview generated - ${bitmap.width}×${bitmap.height}")
            bitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "generateSingleTablePreview FAILED for collapseTablesEnabled=$collapseTablesEnabled", e)
            e.printStackTrace()
            generateFallbackBitmap(context, collapseTablesEnabled)
        }
    }
    
    /**
     * Loads real Varrock article HTML using PageAssetDownloader (same as main app).
     * This includes asset downloading and proper HTML preprocessing.
     */
    private suspend fun loadVarrockArticleHtml(
        context: Context,
        collapseTablesEnabled: Boolean
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading Varrock article using PageAssetDownloader (same as main app)")
            
            // Get components exactly like PageContentLoader does
            val app = context.applicationContext as OSRSWikiApp
            val pageRepository = app.pageRepository
            val pageAssetDownloader = PageAssetDownloader(
                com.omiyawaki.osrswiki.network.OkHttpClientFactory.offlineClient,
                pageRepository
            )
            
            // Use PageAssetDownloader.downloadPriorityAssetsByTitle exactly like PageContentLoader
            val articleQueryTitle = "Varrock"
            val mobileUrl = com.omiyawaki.osrswiki.dataclient.WikiSite.OSRS_WIKI.mobileUrl(articleQueryTitle)
            Log.d(TAG, "Using PageAssetDownloader with mobile URL: $mobileUrl")
            
            // Collect from the download flow exactly like PageContentLoader does
            var processedHtml: String? = null
            val timeoutMillis = 15000L // 15 second timeout for asset download
            
            try {
                withTimeout(timeoutMillis) {
                    pageAssetDownloader.downloadPriorityAssetsByTitle(articleQueryTitle, mobileUrl).collect { progress ->
                        when (progress) {
                            is DownloadProgress.Success -> {
                                Log.d(TAG, "PageAssetDownloader Success - processed HTML length: ${progress.result.processedHtml.length}")
                                processedHtml = progress.result.processedHtml
                                // Break out of collect
                                throw kotlinx.coroutines.CancellationException("Got processed HTML")
                            }
                            is DownloadProgress.Failure -> {
                                Log.e(TAG, "PageAssetDownloader failed: ${progress.error.message}")
                                throw Exception("PageAssetDownloader failed: ${progress.error.message}")
                            }
                            else -> {
                                Log.d(TAG, "PageAssetDownloader progress: ${progress::class.simpleName}")
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (e.message == "Got processed HTML") {
                    Log.d(TAG, "Successfully got processed HTML from PageAssetDownloader")
                } else {
                    throw e
                }
            }
            
            val finalHtml = processedHtml ?: throw Exception("Failed to get processed HTML from PageAssetDownloader")
            Log.d(TAG, "Successfully loaded Varrock article with assets (${finalHtml.length} characters)")
            return@withContext finalHtml
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Varrock article via PageAssetDownloader", e)
            throw e
        }
    }
    
    /**
     * Applies table collapse state to WebView via JavaScript after page load.
     * Waits for collapsible_content.js to finish before applying state changes.
     */
    private fun applyTableCollapseState(webView: WebView, collapseTablesEnabled: Boolean, onFinished: () -> Unit) {
        val previewId = System.currentTimeMillis() % 10000 // Unique ID for this preview
        Log.d(TAG, "applyTableCollapseState DEBUG [ID:$previewId]: collapseTablesEnabled=$collapseTablesEnabled")
        Log.d(TAG, "applyTableCollapseState DEBUG [ID:$previewId]: About to select script branch...")
        
        val script = if (collapseTablesEnabled) {
            Log.d(TAG, "applyTableCollapseState DEBUG [ID:$previewId]: SELECTED COLLAPSED BRANCH (collapseTablesEnabled=true)")
            // Keep tables collapsed (they start collapsed by default)
            """
            (function() {
                function waitForContainersAndApplyState() {
                    const containers = document.querySelectorAll('.collapsible-container');
                    if (containers.length === 0) {
                        // collapsible_content.js hasn't finished yet, wait a bit more
                        setTimeout(waitForContainersAndApplyState, 50);
                        return;
                    }
                    
                    console.log('TablePreview [ID:$previewId]: Found ' + containers.length + ' containers, keeping collapsed');
                    containers.forEach(function(container, index) {
                        // Debug: Log current state before changes
                        const content = container.querySelector('.collapsible-content');
                        console.log('TablePreview: Container ' + index + ' before - classList: ' + container.className + ', content height: ' + (content ? content.style.height : 'N/A'));
                        
                        container.classList.add('collapsed');
                        // Ensure content is hidden
                        if (content) {
                            content.style.height = '0px';
                            content.style.display = 'none';
                        }
                        
                        // Debug: Log state after changes
                        console.log('TablePreview: Container ' + index + ' after - classList: ' + container.className + ', content height: ' + (content ? content.style.height : 'N/A'));
                    });
                    console.log('TablePreview: Ensured ' + containers.length + ' containers remain collapsed');
                }
                waitForContainersAndApplyState();
            })();
            """.trimIndent()
        } else {
            Log.d(TAG, "applyTableCollapseState DEBUG [ID:$previewId]: SELECTED EXPANDED BRANCH (collapseTablesEnabled=false)")
            // Expand all tables by removing collapsed class
            """
            (function() {
                function waitForContainersAndExpand() {
                    const containers = document.querySelectorAll('.collapsible-container');
                    if (containers.length === 0) {
                        // collapsible_content.js hasn't finished yet, wait a bit more
                        setTimeout(waitForContainersAndExpand, 50);
                        return;
                    }
                    
                    console.log('TablePreview [ID:$previewId]: Found ' + containers.length + ' containers, expanding all');
                    containers.forEach(function(container, index) {
                        // Debug: Log current state before changes
                        const content = container.querySelector('.collapsible-content');
                        console.log('TablePreview: Container ' + index + ' before - classList: ' + container.className + ', content height: ' + (content ? content.style.height : 'N/A'));
                        
                        // Remove collapsed class AND explicitly expand content
                        container.classList.remove('collapsed');
                        if (content) {
                            // Explicitly set expanded styles instead of relying on CSS
                            content.style.height = 'auto';
                            content.style.display = 'block';
                            content.style.overflow = 'visible';
                        }
                        
                        // Debug: Log state after changes
                        console.log('TablePreview: Container ' + index + ' after - classList: ' + container.className + ', content height: ' + (content ? content.style.height : 'N/A'));
                    });
                    console.log('TablePreview: Explicitly expanded ' + containers.length + ' containers with height=auto');
                }
                waitForContainersAndExpand();
            })();
            """.trimIndent()
        }
        
        val stateDescription = if (collapseTablesEnabled) "COLLAPSED" else "EXPANDED" 
        Log.d(TAG, "applyTableCollapseState DEBUG [ID:$previewId]: Script created, length=${script.length} chars")
        Log.d(TAG, "applyTableCollapseState DEBUG [ID:$previewId]: Script starts with: ${script.take(100)}...")
        Log.d(TAG, "Applying table collapse state: $stateDescription (collapseTablesEnabled=$collapseTablesEnabled)")
        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "Table collapse state applied [ID:$previewId]: $stateDescription, JavaScript result: $result")
            onFinished()
        }
    }
    
    /**
     * Waits for visual layout changes to be applied after DOM manipulation.
     * Uses requestAnimationFrame and getComputedStyle to detect when changes are visually rendered.
     */
    private fun waitForVisualChangesApplied(webView: WebView, expectedExpanded: Boolean, onComplete: () -> Unit) {
        val previewId = System.currentTimeMillis() % 10000
        Log.d(TAG, "waitForVisualChangesApplied [ID:$previewId]: expectedExpanded=$expectedExpanded")
        
        val checkScript = """
            (function() {
                // Use requestAnimationFrame to wait for next paint cycle
                requestAnimationFrame(function() {
                    const containers = document.querySelectorAll('.collapsible-container');
                    console.log('Visual check [ID:$previewId]: Found ' + containers.length + ' containers');
                    
                    if (containers.length === 0) {
                        window.visualChangesResult = 'NO_CONTAINERS';
                        return;
                    }
                    
                    // Check if visual changes match expected state
                    let changesApplied = true;
                    let expandedCount = 0;
                    
                    for (let i = 0; i < containers.length; i++) {
                        const container = containers[i];
                        const content = container.querySelector('.collapsible-content');
                        if (!content) continue;
                        
                        // Check computed style to verify visual rendering
                        const computedHeight = window.getComputedStyle(content).height;
                        const computedDisplay = window.getComputedStyle(content).display;
                        const isVisuallyExpanded = (computedHeight !== '0px' && computedDisplay !== 'none');
                        
                        if (isVisuallyExpanded) expandedCount++;
                        
                        console.log('Visual check [ID:$previewId]: Container ' + i + ' - height: ' + computedHeight + ', display: ' + computedDisplay + ', expanded: ' + isVisuallyExpanded);
                    }
                    
                    const allExpanded = expandedCount === containers.length;
                    const allCollapsed = expandedCount === 0;
                    const expectedState = $expectedExpanded;
                    
                    if (expectedState && allExpanded) {
                        changesApplied = true;
                        console.log('Visual check [ID:$previewId]: All containers expanded as expected');
                    } else if (!expectedState && allCollapsed) {
                        changesApplied = true;
                        console.log('Visual check [ID:$previewId]: All containers collapsed as expected');
                    } else {
                        changesApplied = false;
                        console.log('Visual check [ID:$previewId]: State mismatch - expected: ' + expectedState + ', expanded: ' + expandedCount + '/' + containers.length);
                    }
                    
                    window.visualChangesResult = changesApplied ? 'COMPLETE' : 'INCOMPLETE';
                });
            })();
        """.trimIndent()
        
        fun pollForVisualChanges(attempt: Int = 1) {
            if (attempt > 15) { // Maximum 15 attempts (750ms total)
                Log.w(TAG, "Visual changes timeout after ${attempt-1} attempts [ID:$previewId], proceeding anyway")
                onComplete()
                return
            }
            
            webView.evaluateJavascript(checkScript) { _ ->
                // Check the result
                webView.evaluateJavascript("window.visualChangesResult") { result ->
                    Log.d(TAG, "Visual changes check attempt $attempt [ID:$previewId]: $result")
                    when (result?.replace("\"", "")) {
                        "COMPLETE" -> {
                            Log.d(TAG, "Visual changes applied after $attempt attempts [ID:$previewId]")
                            onComplete()
                        }
                        "NO_CONTAINERS" -> {
                            Log.w(TAG, "No containers found for visual check [ID:$previewId], proceeding anyway")
                            onComplete()
                        }
                        else -> {
                            // Changes not applied yet, poll again in 50ms
                            webView.postDelayed({
                                pollForVisualChanges(attempt + 1)
                            }, 50)
                        }
                    }
                }
            }
        }
        
        pollForVisualChanges()
    }
    
    /**
     * Captures the preview bitmap after visual changes are applied.
     * Extracted to a separate function to simplify callback nesting.
     */
    private fun capturePreviewBitmap(
        webView: WebView, container: FrameLayout, rootView: FrameLayout,
        fullScreenW: Int, fullScreenH: Int, targetPreviewW: Int, targetPreviewH: Int,
        continuation: kotlinx.coroutines.CancellableContinuation<Bitmap>,
        context: Context, collapseTablesEnabled: Boolean
    ) {
        try {
            Log.d(TAG, "Capturing bitmap after visual changes applied")
            
            // Force layout update
            container.measure(
                View.MeasureSpec.makeMeasureSpec(fullScreenW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(fullScreenH, View.MeasureSpec.EXACTLY)
            )
            container.layout(0, 0, fullScreenW, fullScreenH)
            
            // Create and capture bitmap
            val fullBitmap = Bitmap.createBitmap(fullScreenW, fullScreenH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(fullBitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            container.draw(canvas)
            
            Log.d(TAG, "Full bitmap captured - ${fullBitmap.width}×${fullBitmap.height}")
            
            // Scale down preserving aspect ratio
            val scaledBitmap = createAspectRatioPreservingBitmap(
                fullBitmap, 
                targetPreviewW, 
                targetPreviewH
            )
            
            scaledBitmap.density = DisplayMetrics.DENSITY_DEFAULT
            
            Log.d(TAG, "Scaled preview created - ${scaledBitmap.width}×${scaledBitmap.height}")
            
            // Clean up
            rootView.removeView(container)
            webView.destroy()
            fullBitmap.recycle()
            
            continuation.resume(scaledBitmap)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing bitmap after visual changes", e)
            rootView.removeView(container)
            webView.destroy()
            continuation.resume(generateFallbackBitmap(context, collapseTablesEnabled))
        }
    }
    
    
    /**
     * Renders HTML content using PageWebViewManager to ensure identical behavior to main app.
     * This reuses the proven WebView configuration that successfully loads images.
     */
    private suspend fun renderHtmlWithWebView(
        context: Context,
        htmlContent: String,
        collapseTablesEnabled: Boolean
    ): Bitmap = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var isCompleted = false // Guard against multiple completion calls
            try {
                // Get Activity context - critical for WebView rendering
                val activity = when (context) {
                    is AppCompatActivity -> context
                    is ContextThemeWrapper -> {
                        var baseContext = context.baseContext
                        while (baseContext is ContextThemeWrapper) {
                            baseContext = baseContext.baseContext
                        }
                        baseContext as? AppCompatActivity
                    }
                    else -> null
                } ?: run {
                    Log.e(TAG, "Cannot find Activity context for WebView rendering")
                    if (!isCompleted) {
                        isCompleted = true
                        continuation.resume(generateFallbackBitmap(context, collapseTablesEnabled))
                    }
                    return@suspendCancellableCoroutine
                }
                
                Log.d(TAG, "Creating Activity-attached WebView using PageWebViewManager")
                
                // Create themed context with Activity
                val themedContext = ContextThemeWrapper(activity, R.style.Theme_OSRSWiki_OSRSLight)
                
                // Create ObservableWebView exactly like PageFragment does
                val webView = ObservableWebView(themedContext)
                
                // Get dimensions
                val dm = context.resources.displayMetrics
                val fullScreenW = dm.widthPixels
                val fullScreenH = dm.heightPixels
                val targetPreviewW = (TARGET_W_DP * dm.density).roundToInt()
                val targetPreviewH = (TARGET_H_DP * dm.density).roundToInt()
                
                Log.d(TAG, "Full-screen WebView dimensions: ${fullScreenW}×${fullScreenH}")
                Log.d(TAG, "Target preview dimensions: ${targetPreviewW}×${targetPreviewH}")
                
                // Create container and attach to Activity
                val container = FrameLayout(themedContext).apply {
                    layoutParams = FrameLayout.LayoutParams(fullScreenW, fullScreenH)
                    visibility = View.INVISIBLE // Hidden but attached
                    addView(webView, FrameLayout.LayoutParams(fullScreenW, fullScreenH))
                }
                
                val rootView = activity.findViewById<FrameLayout>(android.R.id.content)
                rootView.addView(container)
                
                // Create complete components exactly like PageFragment does
                val app = context.applicationContext as OSRSWikiApp
                val pageRepository = app.pageRepository
                val previewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
                
                // Real PageLinkHandler like PageFragment uses
                val pageLinkHandler = PageLinkHandler(
                    constructionContext = themedContext,
                    coroutineScope = previewScope, 
                    pageRepository = pageRepository,
                    theme = Theme.OSRS_LIGHT
                )
                
                // Need to create a minimal NativeMapHandler for JS interface
                // Since we can't use fragment binding, create a stub one
                val nativeMapHandler = object {
                    val jsInterface = object {
                        @android.webkit.JavascriptInterface
                        fun onMapPlaceholderMeasured(id: String, rectJson: String, mapDataJson: String) {
                            // No-op for preview - we don't need map functionality
                        }
                        
                        @android.webkit.JavascriptInterface  
                        fun setHorizontalScroll(inProgress: Boolean) {
                            // No-op for preview
                        }
                    }
                }
                
                // Variable to hold pageWebViewManager reference for callback
                var pageWebViewManagerRef: PageWebViewManager? = null
                
                val renderCallback = object : RenderCallback {
                    override fun onWebViewLoadFinished() {
                        Log.d(TAG, "PageWebViewManager: WebView load finished")
                    }
                    
                    override fun onPageReadyForDisplay() {
                        Log.d(TAG, "PageWebViewManager: Page ready for display")
                        
                        // First finalize and reveal the page (like real page loading does)
                        pageWebViewManagerRef?.finalizeAndRevealPage {
                            Log.d(TAG, "PageWebViewManager: Page finalized and revealed")
                            
                            // Apply table collapse state immediately after finalization
                            applyTableCollapseState(webView, collapseTablesEnabled) {
                                Log.d(TAG, "Table collapse state applied, now waiting for visual changes")
                                
                                // Wait for visual changes using true event-driven detection
                                waitForVisualChangesApplied(webView, !collapseTablesEnabled) {
                                    Log.d(TAG, "Visual changes applied, capturing bitmap")
                                    
                                    // Check completion guard and capture bitmap
                                    if (!isCompleted) {
                                        isCompleted = true
                                        capturePreviewBitmap(
                                            webView, container, rootView,
                                            fullScreenW, fullScreenH, targetPreviewW, targetPreviewH,
                                            continuation, context, collapseTablesEnabled
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Create PageWebViewManager exactly like PageFragment does
                val pageWebViewManager = PageWebViewManager(
                    webView = webView,
                    linkHandler = pageLinkHandler, // Use real link handler
                    onTitleReceived = { /* No-op for preview */ },
                    jsInterface = nativeMapHandler.jsInterface, // Real JS interface
                    jsInterfaceName = "OsrsWikiBridge", // Real interface name
                    renderCallback = renderCallback,
                    onRenderProgress = { /* No-op for preview */ }
                )
                
                // Set the reference for the callback to use
                pageWebViewManagerRef = pageWebViewManager
                
                Log.d(TAG, "Using PageHtmlBuilder exactly like main app (no URL manipulation)")
                
                // Use PageHtmlBuilder like the main app does - no manual URL manipulation
                // The htmlContent from PageAssetDownloader is already properly processed
                val pageHtmlBuilder = PageHtmlBuilder(themedContext)
                val fullHtml = pageHtmlBuilder.buildFullHtmlDocument("Varrock", htmlContent, Theme.OSRS_LIGHT)
                
                Log.d(TAG, "Using PageWebViewManager.render() with same base URL as main app")
                
                // Use PageWebViewManager.render() exactly like PageFragment does
                // The AssetCache is a singleton so we should have access to cached images
                pageWebViewManager.render(fullHtml)
                
                // Set up cancellation
                continuation.invokeOnCancellation {
                    Log.d(TAG, "WebView rendering cancelled")
                    isCompleted = true // Mark as completed to prevent further resume attempts
                    try {
                        rootView.removeView(container)
                        webView.destroy()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error during cancellation cleanup: ${e.message}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up PageWebViewManager", e)
                if (!isCompleted) {
                    isCompleted = true
                    continuation.resume(generateFallbackBitmap(context, collapseTablesEnabled))
                }
            }
        }
    }
    
    /**
     * Creates a preview bitmap that preserves aspect ratio and crops to fit target dimensions.
     * Similar to how image thumbnails work - no distortion, shows as much content as possible.
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
        
        // Calculate scaling factor to fill the target container
        val scale = if (sourceRatio > targetRatio) {
            // Source is wider - scale to fit height, crop width
            targetHeight.toFloat() / sourceHeight
        } else {
            // Source is taller - scale to fit width, crop height
            targetWidth.toFloat() / sourceWidth
        }
        
        // Calculate scaled dimensions
        val scaledWidth = (sourceWidth * scale).toInt()
        val scaledHeight = (sourceHeight * scale).toInt()
        
        // Create scaled bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(
            sourceBitmap, 
            scaledWidth, 
            scaledHeight, 
            true
        )
        
        // Calculate crop offsets to center the content
        val cropX = maxOf(0, (scaledWidth - targetWidth) / 2)
        val cropY = 0 // Start from top to show header content
        
        // Crop to final target size
        val croppedBitmap = Bitmap.createBitmap(
            scaledBitmap,
            cropX,
            cropY,
            minOf(targetWidth, scaledWidth),
            minOf(targetHeight, scaledHeight),
            null,
            false
        )
        
        // Clean up intermediate bitmap
        if (scaledBitmap != croppedBitmap) {
            scaledBitmap.recycle()
        }
        
        Log.d(TAG, "Aspect ratio preserved: ${sourceWidth}×${sourceHeight} -> ${scaledWidth}×${scaledHeight} -> ${croppedBitmap.width}×${croppedBitmap.height}")
        
        return croppedBitmap
    }
    
    /**
     * Creates a fallback bitmap when table preview generation fails.
     */
    private fun generateFallbackBitmap(context: Context, collapseTablesEnabled: Boolean): Bitmap {
        val dm = context.resources.displayMetrics
        val width = (TARGET_W_DP * dm.density).roundToInt()
        val height = (TARGET_H_DP * dm.density).roundToInt()
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Fill with neutral background
        canvas.drawColor(Color.parseColor("#F5F5F5"))
        
        // Draw simple text indicator
        val paint = Paint().apply {
            color = Color.parseColor("#666666")
            textSize = 16 * dm.density
            textAlign = Paint.Align.CENTER
        }
        
        val text = if (collapseTablesEnabled) "Collapsed" else "Expanded"
        canvas.drawText(
            text,
            (width / 2).toFloat(),
            (height / 2).toFloat(),
            paint
        )
        
        bitmap.density = DisplayMetrics.DENSITY_DEFAULT
        return bitmap
    }
    
    /**
     * Converts dp to pixels.
     */
    private fun dpToPx(dp: Int, context: Context): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    /**
     * Gets the preview dimensions for UI sizing.
     * Returns Pair(widthDp, heightDp) for individual preview cards.
     */
    fun getPreviewDimensionsForUI(context: Context): Pair<Int, Int> {
        return Pair(TARGET_W_DP, TARGET_H_DP) // Individual card dimensions
    }
    
    /**
     * Clears all table preview caches.
     */
    fun clearCache(context: Context) {
        Log.d(TAG, "Clearing all table preview caches")
        memoryCache.evictAll()
        
        val cacheDir = File(context.cacheDir, "table_previews")
        if (cacheDir.exists()) {
            val files = cacheDir.listFiles()
            files?.forEach { file ->
                val deleted = file.delete()
                Log.d(TAG, "Deleted table preview cache file ${file.name}: $deleted")
            }
            Log.d(TAG, "Cleared ${files?.size ?: 0} table preview cache files")
        }
    }
}