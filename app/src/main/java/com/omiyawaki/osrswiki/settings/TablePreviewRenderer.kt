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
import android.os.Build

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
     * @param theme The theme to use for the preview rendering
     */
    suspend fun getPreview(
        context: Context,
        collapseTablesEnabled: Boolean,
        theme: Theme
    ): Bitmap = withContext(Dispatchers.Default) {
        val cacheKey = "table-preview-${theme.tag}-${if (collapseTablesEnabled) "collapsed" else "expanded"}"
        
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
            } else {
                Log.d(TAG, "No disk cache found for $cacheKey - will generate fresh")
            }
            
            // Serialize preview generation to prevent DOM interference
            previewGenerationMutex.withLock {
                // Generate new preview
                Log.d(TAG, "Generating new table preview for collapseTablesEnabled=$collapseTablesEnabled (serialized)")
                val newBitmap = generateSingleTablePreview(context, collapseTablesEnabled, theme)
                
                if (newBitmap.isRecycled) {
                    Log.e(TAG, "Generated bitmap is recycled! This is a bug.")
                    return@withContext generateFallbackBitmap(context, collapseTablesEnabled, theme)
                }
                
                // Cache in memory
                memoryCache.put(cacheKey, newBitmap)
                Log.d(TAG, "Cached new table preview in memory")
                
                // Save to disk cache
                try {
                    cachedFile.outputStream().use { stream ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            newBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream)
                        } else {
                            @Suppress("DEPRECATION")
                            newBitmap.compress(Bitmap.CompressFormat.WEBP, 100, stream)
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
            generateFallbackBitmap(context, collapseTablesEnabled, theme)
        }
    }
    
    
    /**
     * Generates a single table preview by loading real Varrock article and applying collapse setting.
     */
    private suspend fun generateSingleTablePreview(
        context: Context,
        collapseTablesEnabled: Boolean,
        theme: Theme
    ): Bitmap = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "Starting generateSingleTablePreview for collapseTablesEnabled=$collapseTablesEnabled")
            
            // Get real Varrock article HTML with proper collapse setting
            val articleHtml = loadVarrockArticleHtml(context, collapseTablesEnabled)
            
            // Render using WebView with proper callback
            val bitmap = renderHtmlWithWebView(context, articleHtml, collapseTablesEnabled, theme)
            
            Log.d(TAG, "Single table preview generated - ${bitmap.width}×${bitmap.height}")
            bitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "generateSingleTablePreview FAILED for collapseTablesEnabled=$collapseTablesEnabled", e)
            e.printStackTrace()
            generateFallbackBitmap(context, collapseTablesEnabled, theme)
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
            
            // CONTENT INSPECTION: Verify HTML content quality
            Log.d(TAG, "Successfully loaded Varrock article with assets (${finalHtml.length} characters)")
            Log.d(TAG, "HTML content analysis:")
            Log.d(TAG, "  - Contains 'Varrock': ${finalHtml.contains("Varrock")}")
            Log.d(TAG, "  - Contains tables: ${finalHtml.contains("<table")}")
            Log.d(TAG, "  - Contains collapsible elements: ${finalHtml.contains("collapsible")}")
            Log.d(TAG, "  - HTML structure check: starts with DOCTYPE/html: ${finalHtml.trim().startsWith("<!DOCTYPE") || finalHtml.trim().startsWith("<html")}")
            
            // Log first 500 chars for content verification  
            val preview = if (finalHtml.length > 500) finalHtml.substring(0, 500) + "..." else finalHtml
            Log.d(TAG, "HTML content preview: $preview")
            
            return@withContext finalHtml
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Varrock article via PageAssetDownloader", e)
            throw e
        }
    }
    
    /**
     * Captures the preview bitmap after page is ready.
     * The collapse state was set during collapsible_content.js initialization via global JS variable.
     */
    private fun capturePreviewBitmap(
        webView: WebView, container: FrameLayout, rootView: FrameLayout,
        fullScreenW: Int, fullScreenH: Int, targetPreviewW: Int, targetPreviewH: Int,
        continuation: kotlinx.coroutines.CancellableContinuation<Bitmap>,
        context: Context, collapseTablesEnabled: Boolean, theme: Theme
    ) {
        try {
            Log.d(TAG, "Capturing bitmap for theme=${theme.tag}, collapse=$collapseTablesEnabled")
            
            // WEBVIEW LIFECYCLE INSPECTION: Check WebView state before capture
            Log.d(TAG, "WEBVIEW LIFECYCLE STATE:")
            Log.d(TAG, "  - WebView URL: ${webView.url}")
            Log.d(TAG, "  - WebView progress: ${webView.progress}%")
            Log.d(TAG, "  - WebView canGoBack: ${webView.canGoBack()}")
            Log.d(TAG, "  - WebView canGoForward: ${webView.canGoForward()}")
            Log.d(TAG, "  - WebView contentHeight: ${webView.contentHeight}")
            Log.d(TAG, "  - WebView title: '${webView.title}'")
            Log.d(TAG, "  - WebView settings javaScriptEnabled: ${webView.settings.javaScriptEnabled}")
            Log.d(TAG, "  - WebView settings domStorageEnabled: ${webView.settings.domStorageEnabled}")
            Log.d(TAG, "  - Container visibility: ${container.visibility}")
            Log.d(TAG, "  - Container alpha: ${container.alpha}")
            Log.d(TAG, "  - Container isAttachedToWindow: ${container.isAttachedToWindow}")
            Log.d(TAG, "  - Container hasWindowFocus: ${container.hasWindowFocus()}")
            
            // Execute JavaScript to inspect DOM state
            webView.evaluateJavascript("""
                (function() {
                    const stats = {
                        'bodyChildren': document.body ? document.body.children.length : 0,
                        'tables': document.querySelectorAll('table').length,
                        'collapsibleElements': document.querySelectorAll('.collapsible').length,
                        'images': document.querySelectorAll('img').length,
                        'visibleElements': document.querySelectorAll('*:not([style*="display: none"])').length,
                        'bodyScrollHeight': document.body ? document.body.scrollHeight : 0,
                        'bodyScrollWidth': document.body ? document.body.scrollWidth : 0,
                        'documentReadyState': document.readyState,
                        'windowOSRS_TABLE_COLLAPSED': typeof window.OSRS_TABLE_COLLAPSED !== 'undefined' ? window.OSRS_TABLE_COLLAPSED : 'undefined'
                    };
                    return JSON.stringify(stats);
                })();
            """.trimIndent()) { result ->
                Log.d(TAG, "JAVASCRIPT DOM INSPECTION: $result")
            }
            
            // Brief delay to allow JS evaluation to complete and be logged
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                continueWithBitmapCapture(
                    webView, container, rootView, fullScreenW, fullScreenH,
                    targetPreviewW, targetPreviewH, continuation, context, collapseTablesEnabled, theme
                )
            }, 100)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in capturePreviewBitmap setup for theme=${theme.tag}", e)
            rootView.removeView(container)
            webView.destroy()
            continuation.resume(generateFallbackBitmap(context, collapseTablesEnabled, theme))
        }
    }
    
    /**
     * Continues with bitmap capture after WebView lifecycle inspection.
     */
    private fun continueWithBitmapCapture(
        webView: WebView, container: FrameLayout, rootView: FrameLayout,
        fullScreenW: Int, fullScreenH: Int, targetPreviewW: Int, targetPreviewH: Int,
        continuation: kotlinx.coroutines.CancellableContinuation<Bitmap>,
        context: Context, collapseTablesEnabled: Boolean, theme: Theme
    ) {
        try {
            Log.d(TAG, "RENDERING PIPELINE: Starting bitmap capture process")
            
            // Force layout update
            Log.d(TAG, "RENDERING PIPELINE: Measuring and laying out container")
            container.measure(
                View.MeasureSpec.makeMeasureSpec(fullScreenW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(fullScreenH, View.MeasureSpec.EXACTLY)
            )
            container.layout(0, 0, fullScreenW, fullScreenH)
            
            Log.d(TAG, "RENDERING PIPELINE: Container laid out - width: ${container.measuredWidth}, height: ${container.measuredHeight}")
            Log.d(TAG, "RENDERING PIPELINE: WebView dimensions - width: ${webView.width}, height: ${webView.height}")
            Log.d(TAG, "RENDERING PIPELINE: WebView measured - width: ${webView.measuredWidth}, height: ${webView.measuredHeight}")
            
            // Create and capture bitmap
            Log.d(TAG, "RENDERING PIPELINE: Creating bitmap canvas")
            val fullBitmap = Bitmap.createBitmap(fullScreenW, fullScreenH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(fullBitmap)
            
            // Log canvas state before drawing
            Log.d(TAG, "RENDERING PIPELINE: Canvas created - width: ${canvas.width}, height: ${canvas.height}")
            Log.d(TAG, "RENDERING PIPELINE: Canvas density: ${canvas.density}")
            Log.d(TAG, "RENDERING PIPELINE: Canvas clipBounds: ${canvas.clipBounds}")
            
            // Fill canvas with background color and log the action
            Log.d(TAG, "RENDERING PIPELINE: Drawing white background on canvas")
            canvas.drawColor(android.graphics.Color.WHITE)
            
            // Now draw the container and inspect what gets drawn
            Log.d(TAG, "RENDERING PIPELINE: Calling container.draw(canvas) - This is where content should render")
            Log.d(TAG, "RENDERING PIPELINE: Container draw state - hasTransientState: ${container.hasTransientState()}")
            Log.d(TAG, "RENDERING PIPELINE: Container draw state - willNotDraw: ${container.willNotDraw()}")
            Log.d(TAG, "RENDERING PIPELINE: Container draw state - isDirty: ${container.isDirty}")
            
            // Inspect WebView specific draw state
            Log.d(TAG, "RENDERING PIPELINE: WebView draw state - willNotDraw: ${webView.willNotDraw()}")
            Log.d(TAG, "RENDERING PIPELINE: WebView draw state - hasTransientState: ${webView.hasTransientState()}")
            Log.d(TAG, "RENDERING PIPELINE: WebView draw state - isDirty: ${webView.isDirty}")
            Log.d(TAG, "RENDERING PIPELINE: WebView draw state - isDrawingCacheEnabled: ${webView.isDrawingCacheEnabled}")
            
            container.draw(canvas)
            
            Log.d(TAG, "RENDERING PIPELINE: container.draw(canvas) completed")
            
            // POST-DRAW CANVAS INSPECTION
            Log.d(TAG, "RENDERING PIPELINE: Inspecting canvas state after draw")
            
            // Sample a small area to check if anything was actually drawn
            val quickSampleSize = 10
            val quickPixels = IntArray(quickSampleSize * quickSampleSize)
            fullBitmap.getPixels(quickPixels, 0, quickSampleSize, 0, 0, quickSampleSize, quickSampleSize)
            val quickUniqueColors = quickPixels.distinct().size
            val quickHasNonWhite = quickPixels.any { it != android.graphics.Color.WHITE }
            
            Log.d(TAG, "RENDERING PIPELINE: Quick sample (${quickSampleSize}x${quickSampleSize}):")
            Log.d(TAG, "RENDERING PIPELINE:   - Unique colors: $quickUniqueColors")
            Log.d(TAG, "RENDERING PIPELINE:   - Has non-white pixels: $quickHasNonWhite")
            if (quickUniqueColors <= 3) {
                Log.d(TAG, "RENDERING PIPELINE:   - Quick sample colors: ${quickPixels.distinct().map { String.format("#%08X", it) }}")
            }
            
            // If quick sample shows no content, inspect WebView rendering more deeply
            if (!quickHasNonWhite) {
                Log.w(TAG, "RENDERING PIPELINE: Quick sample shows no content rendered!")
                Log.w(TAG, "RENDERING PIPELINE: Investigating WebView rendering failure...")
                
                // Try alternative drawing methods
                Log.d(TAG, "RENDERING PIPELINE: Trying WebView.draw() instead of container.draw()")
                val webViewTestBitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
                val webViewTestCanvas = Canvas(webViewTestBitmap)
                webViewTestCanvas.drawColor(android.graphics.Color.WHITE)
                webView.draw(webViewTestCanvas)
                
                // Quick check of WebView direct draw
                val webViewTestPixels = IntArray(quickSampleSize * quickSampleSize)
                webViewTestBitmap.getPixels(webViewTestPixels, 0, quickSampleSize, 0, 0, quickSampleSize, quickSampleSize)
                val webViewTestHasContent = webViewTestPixels.any { it != android.graphics.Color.WHITE }
                
                Log.d(TAG, "RENDERING PIPELINE: Direct WebView.draw() has content: $webViewTestHasContent")
                if (!webViewTestHasContent) {
                    Log.w(TAG, "RENDERING PIPELINE: Even direct WebView.draw() shows no content!")
                    Log.w(TAG, "RENDERING PIPELINE: This indicates WebView content is not ready for drawing")
                    
                    // Check if WebView needs more time
                    Log.d(TAG, "RENDERING PIPELINE: Checking if more rendering time is needed...")
                    if (webView.progress < 100) {
                        Log.w(TAG, "RENDERING PIPELINE: WebView progress is ${webView.progress}% - content may not be fully loaded")
                    }
                    if (webView.contentHeight == 0) {
                        Log.w(TAG, "RENDERING PIPELINE: WebView contentHeight is 0 - DOM may not be rendered yet")
                    }
                }
                
                webViewTestBitmap.recycle()
            }
            
            // DETAILED BITMAP CONTENT ANALYSIS
            val sampleSize = minOf(100, fullBitmap.width, fullBitmap.height)
            val pixels = IntArray(sampleSize * sampleSize)
            fullBitmap.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
            
            val colorAnalysis = analyzePixelColors(pixels)
            
            Log.d(TAG, "BITMAP HISTOGRAM ANALYSIS:")
            Log.d(TAG, "  - Full bitmap captured: ${fullBitmap.width}×${fullBitmap.height}")
            Log.d(TAG, "  - Sample area: ${sampleSize}×${sampleSize} pixels")
            Log.d(TAG, "  - Total pixels analyzed: ${colorAnalysis["totalPixels"]}")
            Log.d(TAG, "  - Unique colors: ${colorAnalysis["uniqueColors"]}")
            Log.d(TAG, "  - Dominant color: ${String.format("#%08X", colorAnalysis["dominantColor"] as Int)}")
            Log.d(TAG, "  - Dominant color percentage: ${String.format("%.1f%%", colorAnalysis["dominantPercentage"] as Float)}")
            Log.d(TAG, "  - Is monochrome: ${colorAnalysis["isMonochrome"]}")
            Log.d(TAG, "  - Light pixels (background): ${colorAnalysis["lightPixels"]}")
            Log.d(TAG, "  - Dark pixels (text): ${colorAnalysis["darkPixels"]}")
            Log.d(TAG, "  - Colored pixels (content): ${colorAnalysis["coloredPixels"]}")
            Log.d(TAG, "  - Content percentage: ${String.format("%.1f%%", colorAnalysis["contentPercentage"] as Float)}")
            Log.d(TAG, "  - Has actual content: ${colorAnalysis["hasContent"]}")
            
            val hasContent = colorAnalysis["hasContent"] as Boolean
            val contentPercentage = colorAnalysis["contentPercentage"] as Float
            val isMonochrome = colorAnalysis["isMonochrome"] as Boolean
            val uniqueColors = colorAnalysis["uniqueColors"] as Int
            
            if (!hasContent) {
                Log.w(TAG, "WARNING: Bitmap analysis indicates NO ACTUAL CONTENT for theme=${theme.tag}, collapse=$collapseTablesEnabled")
                when {
                    isMonochrome -> Log.w(TAG, "  - Reason: Image is essentially monochrome (${String.format("%.1f%%", colorAnalysis["dominantPercentage"] as Float)} dominant color)")
                    contentPercentage < 5f -> Log.w(TAG, "  - Reason: Very low content percentage ($contentPercentage%)")
                    uniqueColors < 10 -> Log.w(TAG, "  - Reason: Very few unique colors ($uniqueColors)")
                }
                Log.w(TAG, "  - This suggests WebView content did not render properly")
                Log.w(TAG, "  - Check HTML loading, CSS application, JavaScript execution, and WebView lifecycle timing")
            } else {
                Log.d(TAG, "  ✓ Content detected: $contentPercentage% content pixels with $uniqueColors unique colors")
            }
            
            // Scale down preserving aspect ratio
            val scaledBitmap = createAspectRatioPreservingBitmap(
                fullBitmap, 
                targetPreviewW, 
                targetPreviewH
            )
            
            scaledBitmap.density = DisplayMetrics.DENSITY_DEFAULT
            
            Log.d(TAG, "Scaled preview created - ${scaledBitmap.width}×${scaledBitmap.height} for theme=${theme.tag}")
            
            // Clean up
            Log.d(TAG, "RENDERING PIPELINE: Cleaning up WebView and container")
            rootView.removeView(container)
            webView.destroy()
            fullBitmap.recycle()
            
            Log.d(TAG, "RENDERING PIPELINE: Bitmap capture completed successfully for theme=${theme.tag}")
            continuation.resume(scaledBitmap)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in continueWithBitmapCapture for theme=${theme.tag}", e)
            rootView.removeView(container)
            webView.destroy()
            continuation.resume(generateFallbackBitmap(context, collapseTablesEnabled, theme))
        }
    }
    
    
    /**
     * Renders HTML content using PageWebViewManager to ensure identical behavior to main app.
     * This reuses the proven WebView configuration that successfully loads images.
     */
    private suspend fun renderHtmlWithWebView(
        context: Context,
        htmlContent: String,
        collapseTablesEnabled: Boolean,
        theme: Theme
    ): Bitmap = withContext(Dispatchers.Main) {
        // First, get Activity context before entering the suspendCancellableCoroutine
        val activity = if (context is OSRSWikiApp) {
            Log.d(TAG, "Application context detected, waiting for Activity context from pool")
            // This will suspend until an Activity becomes available (with timeout)
            context.waitForActivityContext()
        } else {
            // Legacy path: try to extract Activity from context
            when (context) {
                is AppCompatActivity -> context
                is ContextThemeWrapper -> {
                    var baseContext = context.baseContext
                    while (baseContext is ContextThemeWrapper) {
                        baseContext = baseContext.baseContext
                    }
                    baseContext as? AppCompatActivity
                }
                else -> null
            }
        }
        
        if (activity == null) {
            Log.e(TAG, "Cannot find Activity context for WebView rendering")
            return@withContext generateFallbackBitmap(context, collapseTablesEnabled, theme)
        }
        
        suspendCancellableCoroutine { continuation ->
            var isCompleted = false // Guard against multiple completion calls
            try {
                
                Log.d(TAG, "Creating Activity-attached WebView using PageWebViewManager")
                
                // THEME INSPECTION: Verify theme application
                val themeResourceId = when (theme) {
                    Theme.OSRS_DARK -> R.style.Theme_OSRSWiki_OSRSDark
                    else -> R.style.Theme_OSRSWiki_OSRSLight
                }
                val themedContext = ContextThemeWrapper(activity, themeResourceId)
                Log.d(TAG, "WebView rendering: theme=${theme.tag}, themeResourceId=$themeResourceId, collapseTablesEnabled=$collapseTablesEnabled")
                Log.d(TAG, "THEME ANALYSIS:")
                Log.d(TAG, "  - Activity class: ${activity::class.simpleName}")  
                Log.d(TAG, "  - Activity theme: ${activity.theme}")
                Log.d(TAG, "  - Themed context: $themedContext")
                Log.d(TAG, "  - Theme resource ID: $themeResourceId")
                Log.d(TAG, "  - Expected theme: ${if (theme == Theme.OSRS_DARK) "DARK" else "LIGHT"}")
                
                // Create ObservableWebView exactly like PageFragment does
                val webView = ObservableWebView(themedContext)
                
                // WEBVIEW SETUP INSPECTION
                Log.d(TAG, "WEBVIEW SETUP:")
                Log.d(TAG, "  - WebView created with themed context")
                Log.d(TAG, "  - WebView class: ${webView::class.simpleName}")
                
                // Get dimensions
                val dm = context.resources.displayMetrics
                val fullScreenW = dm.widthPixels
                val fullScreenH = dm.heightPixels
                val targetPreviewW = (TARGET_W_DP * dm.density).roundToInt()
                val targetPreviewH = (TARGET_H_DP * dm.density).roundToInt()
                
                Log.d(TAG, "DISPLAY METRICS:")
                Log.d(TAG, "  - Full-screen WebView dimensions: ${fullScreenW}×${fullScreenH}")
                Log.d(TAG, "  - Target preview dimensions: ${targetPreviewW}×${targetPreviewH}")
                Log.d(TAG, "  - Display density: ${dm.density}")
                Log.d(TAG, "  - Density DPI: ${dm.densityDpi}")
                
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
                    theme = theme
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
                        Log.d(TAG, "WEBVIEW LIFECYCLE: onWebViewLoadFinished() called for theme=${theme.tag}, collapse=$collapseTablesEnabled")
                        Log.d(TAG, "WEBVIEW LIFECYCLE: WebView progress at finish: ${webView.progress}%")
                        Log.d(TAG, "WEBVIEW LIFECYCLE: WebView URL at finish: ${webView.url}")
                        Log.d(TAG, "WEBVIEW LIFECYCLE: WebView contentHeight at finish: ${webView.contentHeight}")
                    }
                    
                    override fun onPageReadyForDisplay() {
                        Log.d(TAG, "WEBVIEW LIFECYCLE: onPageReadyForDisplay() called for theme=${theme.tag}, collapse=$collapseTablesEnabled")
                        Log.d(TAG, "WEBVIEW LIFECYCLE: This is the key callback that triggers bitmap capture")
                        
                        // Additional state inspection at this critical point
                        Log.d(TAG, "WEBVIEW LIFECYCLE: WebView state when ready for display:")
                        Log.d(TAG, "WEBVIEW LIFECYCLE:   - Progress: ${webView.progress}%")
                        Log.d(TAG, "WEBVIEW LIFECYCLE:   - ContentHeight: ${webView.contentHeight}")
                        Log.d(TAG, "WEBVIEW LIFECYCLE:   - Title: '${webView.title}'")
                        Log.d(TAG, "WEBVIEW LIFECYCLE:   - URL: ${webView.url}")
                        
                        // First finalize and reveal the page (like real page loading does)
                        pageWebViewManagerRef?.finalizeAndRevealPage {
                            Log.d(TAG, "WEBVIEW LIFECYCLE: finalizeAndRevealPage() callback invoked for theme=${theme.tag}")
                            Log.d(TAG, "WEBVIEW LIFECYCLE: This should mean the page is fully ready and styled")
                            
                            // Additional inspection after finalization
                            Log.d(TAG, "WEBVIEW LIFECYCLE: Post-finalization WebView state:")
                            Log.d(TAG, "WEBVIEW LIFECYCLE:   - Progress: ${webView.progress}%")
                            Log.d(TAG, "WEBVIEW LIFECYCLE:   - ContentHeight: ${webView.contentHeight}")
                            Log.d(TAG, "WEBVIEW LIFECYCLE:   - Width: ${webView.width}, Height: ${webView.height}")
                            
                            // Collapse state is set by global JavaScript variable during collapsible_content.js initialization
                            // No post-processing needed - capture bitmap directly
                            Log.d(TAG, "WEBVIEW LIFECYCLE: Table collapse state handled by global JS variable, proceeding to bitmap capture")
                            
                            // Smart content readiness detection instead of fixed delay
                            Log.d(TAG, "WEBVIEW LIFECYCLE: Waiting for WebView content to be fully rendered")
                            waitForWebViewContentReady(webView, maxAttempts = 20) { contentReady ->
                                if (!isCompleted) {
                                    isCompleted = true
                                    if (contentReady) {
                                        Log.d(TAG, "WEBVIEW LIFECYCLE: WebView content ready, starting bitmap capture")
                                    } else {
                                        Log.w(TAG, "WEBVIEW LIFECYCLE: WebView content not fully ready, proceeding with capture anyway")
                                    }
                                    capturePreviewBitmap(
                                        webView, container, rootView,
                                        fullScreenW, fullScreenH, targetPreviewW, targetPreviewH,
                                        continuation, context, collapseTablesEnabled, theme
                                    )
                                } else {
                                    Log.w(TAG, "WEBVIEW LIFECYCLE: Skipping bitmap capture - already completed")
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
                var fullHtml = pageHtmlBuilder.buildFullHtmlDocument("Varrock", htmlContent, theme)
                
                // JAVASCRIPT INJECTION INSPECTION
                Log.d(TAG, "JAVASCRIPT INJECTION:")
                Log.d(TAG, "  - Injecting collapse preference: $collapseTablesEnabled for theme: ${theme.tag}")
                
                // Inject global JavaScript variable to control collapsible_content.js behavior
                val collapsePreferenceScript = """
                    <script>
                        // Global variable for table collapse preference that collapsible_content.js can read
                        window.OSRS_TABLE_COLLAPSED = $collapseTablesEnabled;
                        console.log('TablePreview: Set global collapse preference to ' + window.OSRS_TABLE_COLLAPSED + ' for theme ${theme.tag}');
                        
                        // Additional debugging for WebView inspection
                        window.addEventListener('DOMContentLoaded', function() {
                            console.log('TablePreview: DOM loaded for theme ${theme.tag}');
                            console.log('TablePreview: Found tables:', document.querySelectorAll('table').length);
                            console.log('TablePreview: Found collapsible elements:', document.querySelectorAll('.collapsible').length);
                            console.log('TablePreview: OSRS_TABLE_COLLAPSED value:', window.OSRS_TABLE_COLLAPSED);
                        });
                    </script>
                """.trimIndent()
                
                // Insert the preference script right after <head> tag
                val originalHtmlLength = fullHtml.length
                fullHtml = fullHtml.replace("<head>", "<head>\n$collapsePreferenceScript")
                val newHtmlLength = fullHtml.length
                
                Log.d(TAG, "  - Script injection successful: ${newHtmlLength > originalHtmlLength}")
                Log.d(TAG, "  - HTML length change: $originalHtmlLength -> $newHtmlLength (+${newHtmlLength - originalHtmlLength} chars)")
                Log.d(TAG, "  - Script contains OSRS_TABLE_COLLAPSED: ${collapsePreferenceScript.contains("OSRS_TABLE_COLLAPSED")}")
                
                Log.d(TAG, "WEBVIEW RENDER: About to call PageWebViewManager.render() with collapse preference: $collapseTablesEnabled")
                Log.d(TAG, "WEBVIEW RENDER: HTML content length: ${fullHtml.length} characters")
                Log.d(TAG, "WEBVIEW RENDER: WebView initial state before render():")
                Log.d(TAG, "WEBVIEW RENDER:   - WebView URL: ${webView.url}")
                Log.d(TAG, "WEBVIEW RENDER:   - WebView progress: ${webView.progress}%")
                Log.d(TAG, "WEBVIEW RENDER:   - WebView contentHeight: ${webView.contentHeight}")
                
                // Use PageWebViewManager.render() exactly like PageFragment does
                // The AssetCache is a singleton so we should have access to cached images
                pageWebViewManager.render(fullHtml)
                
                Log.d(TAG, "WEBVIEW RENDER: PageWebViewManager.render() call completed")
                Log.d(TAG, "WEBVIEW RENDER: WebView state immediately after render():")
                Log.d(TAG, "WEBVIEW RENDER:   - WebView URL: ${webView.url}")
                Log.d(TAG, "WEBVIEW RENDER:   - WebView progress: ${webView.progress}%")
                Log.d(TAG, "WEBVIEW RENDER:   - WebView contentHeight: ${webView.contentHeight}")
                
                // Log render callback expectations
                Log.d(TAG, "WEBVIEW RENDER: Now waiting for render callbacks:")
                Log.d(TAG, "WEBVIEW RENDER:   1. onWebViewLoadFinished() - when HTML loading completes")
                Log.d(TAG, "WEBVIEW RENDER:   2. onPageReadyForDisplay() - when page is ready for display")
                Log.d(TAG, "WEBVIEW RENDER:   3. finalizeAndRevealPage() - when page finalization is done")
                Log.d(TAG, "WEBVIEW RENDER: Bitmap capture will occur after callback chain completes")
                
                // Set up cancellation
                continuation.invokeOnCancellation {
                    Log.d(TAG, "WEBVIEW RENDER: WebView rendering cancelled")
                    isCompleted = true // Mark as completed to prevent further resume attempts
                    try {
                        rootView.removeView(container)
                        webView.destroy()
                    } catch (e: Exception) {
                        Log.w(TAG, "WEBVIEW RENDER: Error during cancellation cleanup: ${e.message}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up PageWebViewManager", e)
                if (!isCompleted) {
                    isCompleted = true
                    continuation.resume(generateFallbackBitmap(context, collapseTablesEnabled, theme))
                }
            }
        }
    }
    
    /**
     * Waits for WebView content to be fully rendered before proceeding with bitmap capture.
     * Uses intelligent polling of WebView state instead of fixed delays.
     */
    private fun waitForWebViewContentReady(
        webView: WebView, 
        maxAttempts: Int = 20,
        callback: (Boolean) -> Unit
    ) {
        var attempts = 0
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        fun checkContentReady() {
            attempts++
            
            // Check multiple indicators of content readiness
            val progress = webView.progress
            val contentHeight = webView.contentHeight
            val hasContent = progress >= 100 && contentHeight > 0
            
            Log.d(TAG, "CONTENT READINESS CHECK $attempts/$maxAttempts: progress=$progress%, contentHeight=$contentHeight, ready=$hasContent")
            
            when {
                hasContent -> {
                    Log.d(TAG, "CONTENT READINESS: WebView content ready after $attempts attempts")
                    callback(true)
                }
                attempts >= maxAttempts -> {
                    Log.w(TAG, "CONTENT READINESS: Max attempts reached ($maxAttempts), proceeding anyway")
                    callback(false)
                }
                else -> {
                    // Wait 50ms and try again
                    handler.postDelayed({ checkContentReady() }, 50)
                }
            }
        }
        
        checkContentReady()
    }
    
    /**
     * Analyzes pixel colors using histogram approach to detect dominant colors and content.
     * More reliable than exact color matching since colors may have slight variations.
     */
    private fun analyzePixelColors(pixels: IntArray): Map<String, Any> {
        // Build color histogram
        val colorCounts = mutableMapOf<Int, Int>()
        pixels.forEach { pixel ->
            colorCounts[pixel] = colorCounts.getOrDefault(pixel, 0) + 1
        }
        
        // Sort colors by frequency
        val sortedColors = colorCounts.toList().sortedByDescending { it.second }
        val totalPixels = pixels.size
        
        // Analyze dominant colors
        val dominantColor = sortedColors.firstOrNull()
        val dominantColorCount = dominantColor?.second ?: 0
        val dominantColorPercentage = if (totalPixels > 0) (dominantColorCount.toFloat() / totalPixels * 100) else 0f
        
        // Check if image is essentially monochrome (dominant color > 90%)
        val isMonochrome = dominantColorPercentage > 90f
        
        // Categorize colors more intelligently
        var lightPixels = 0  // Light colors (likely background)
        var darkPixels = 0   // Dark colors (likely text/content)
        var coloredPixels = 0 // Actual colored content
        var transparentPixels = 0
        
        pixels.forEach { pixel ->
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 50) {
                transparentPixels++
            } else {
                val red = (pixel ushr 16) and 0xFF
                val green = (pixel ushr 8) and 0xFF
                val blue = pixel and 0xFF
                val brightness = (red + green + blue) / 3
                
                when {
                    brightness > 240 -> lightPixels++  // Very light (background-like)
                    brightness < 50 -> darkPixels++   // Very dark (text-like)
                    else -> coloredPixels++           // Actual content colors
                }
            }
        }
        
        // Calculate content indicators
        val contentPixels = darkPixels + coloredPixels
        val contentPercentage = if (totalPixels > 0) (contentPixels.toFloat() / totalPixels * 100) else 0f
        val uniqueColors = colorCounts.size
        
        // Determine if image appears to have actual content
        val hasContent = when {
            isMonochrome && dominantColorPercentage > 95f -> false  // Almost entirely one color
            contentPercentage < 5f -> false  // Less than 5% content pixels
            uniqueColors < 10 -> false  // Very few unique colors
            else -> true
        }
        
        return mapOf(
            "totalPixels" to totalPixels,
            "uniqueColors" to uniqueColors,
            "dominantColor" to (dominantColor?.first ?: android.graphics.Color.WHITE),
            "dominantColorCount" to dominantColorCount,
            "dominantPercentage" to dominantColorPercentage,
            "isMonochrome" to isMonochrome,
            "lightPixels" to lightPixels,
            "darkPixels" to darkPixels,
            "coloredPixels" to coloredPixels,
            "transparentPixels" to transparentPixels,
            "contentPixels" to contentPixels,
            "contentPercentage" to contentPercentage,
            "hasContent" to hasContent,
            "nonWhite" to contentPixels  // For backward compatibility
        )
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
    private fun generateFallbackBitmap(context: Context, collapseTablesEnabled: Boolean, theme: Theme): Bitmap {
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