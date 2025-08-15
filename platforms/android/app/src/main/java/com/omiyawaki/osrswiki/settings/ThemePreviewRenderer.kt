package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.util.DisplayMetrics
import android.util.Log
import android.util.LruCache
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.os.Build
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.image.SynchronousImageLoader
import com.omiyawaki.osrswiki.settings.preview.CompositePreviewSource
import com.omiyawaki.osrswiki.settings.preview.PreviewTileComposer
import com.omiyawaki.osrswiki.news.model.UpdateItem
import com.omiyawaki.osrswiki.news.model.AnnouncementItem
import com.omiyawaki.osrswiki.news.model.OnThisDayItem
import com.omiyawaki.osrswiki.news.model.PopularPageItem
import com.omiyawaki.osrswiki.news.ui.FeedItem
import com.omiyawaki.osrswiki.news.ui.NewsFeedAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.min
import java.io.File
import android.content.res.Configuration

/**
 * Expert's solution: Creates a robust scaled Context using createConfigurationContext.
 * This ensures LayoutInflater properly uses the scaled density for dp/sp values.
 */
fun Context.withScaledDensity(scale: Float): Context {
    val cfg = Configuration(resources.configuration)
    cfg.densityDpi = (cfg.densityDpi * scale).toInt()
    return createConfigurationContext(cfg)
}

/**
 * Generates dynamic theme previews by rendering actual app layouts with different themes.
 * Uses both memory and disk caching for optimal performance.
 */
object ThemePreviewRenderer {
    
    private const val TARGET_W_DP = 96 // Base width for preview (≥48dp accessibility requirement)
    private const val TAG = "ThemePreviewRenderer"
    
    // EXPERT GUIDANCE: Memory-based LruCache targeting ≤1/8 process heap (~5MB for 3 themes)
    // Each 400px × 800px bitmap ≈ 1.28MB, so 3 themes = ~4MB total
    private val memoryCache = object : LruCache<String, Bitmap>(getMaxCacheSize()) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            // Return the size in bytes (width × height × 4 bytes per pixel for ARGB)
            return bitmap.byteCount
        }
    }
    
    /**
     * Calculate max cache size as 1/8 of available heap memory (expert recommendation)
     */
    private fun getMaxCacheSize(): Int {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt() // KB
        return maxMemory / 8 // Use 1/8th of available memory for cache
    }
    
    /**
     * Gets the actual device screen pixel dimensions.
     * Returns Pair(widthPx, heightPx) respecting current device orientation.
     */
    private fun getDeviceScreenPixels(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val width: Int
        val height: Int
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use modern API for Android R+ - respect actual orientation
            val bounds = windowManager.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
        } else {
            // Fallback for older Android versions - respect actual orientation
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            width = displayMetrics.widthPixels
            height = displayMetrics.heightPixels
        }
        
        Log.d(TAG, "Native device screen pixels (actual orientation): ${width}x${height}")
        return Pair(width, height)
    }

    /**
     * Gets the app content area bounds excluding system UI (status bar, navigation bar).
     * Returns Pair(widthPx, heightPx) of the actual displayable content area respecting orientation.
     */
    private fun getAppContentBounds(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val width: Int
        val height: Int
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use modern API for Android R+ with WindowInsets
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars()
            )
            val bounds = metrics.bounds
            
            // Calculate content area by subtracting system UI insets - respect actual orientation
            val contentWidth = bounds.width() - insets.left - insets.right
            val contentHeight = bounds.height() - insets.top - insets.bottom
            
            width = contentWidth
            height = contentHeight
            
            Log.d(TAG, "App content bounds (API 30+): ${width}x${height}, insets: left=${insets.left}, right=${insets.right}, top=${insets.top}, bottom=${insets.bottom}")
        } else {
            // Fallback for older Android versions - respect actual orientation
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            
            val fullWidth = displayMetrics.widthPixels
            val fullHeight = displayMetrics.heightPixels
            
            // Estimate system UI size based on orientation
            val density = displayMetrics.density
            val isLandscape = fullWidth > fullHeight
            
            if (isLandscape) {
                // Landscape: status bar on side, nav bar on side or bottom
                val estimatedSystemUIWidth = (24 * density).roundToInt()  // Status bar width
                val estimatedSystemUIHeight = (48 * density).roundToInt() // Nav bar height
                width = fullWidth - estimatedSystemUIWidth
                height = fullHeight - estimatedSystemUIHeight
            } else {
                // Portrait: status bar on top, nav bar on bottom
                val estimatedSystemUIHeight = ((24 + 48) * density).roundToInt()  // Status + nav bar
                width = fullWidth
                height = fullHeight - estimatedSystemUIHeight
            }
            
            Log.d(TAG, "App content bounds (legacy): ${width}x${height}, isLandscape=$isLandscape")
        }
        
        return Pair(width, height)
    }

    /**
     * Gets the app content aspect ratio for realistic theme previews.
     * Uses content area (excluding system UI) for accurate aspect ratio.
     * Returns height/width ratio (e.g., 2.0 for a 2:1 content area).
     */
    private fun getDeviceAspectRatio(context: Context): Float {
        val (width, height) = getAppContentBounds(context)
        val aspectRatio = height / width.toFloat()
        
        Log.d(TAG, "App content dimensions: ${width}x${height}, aspect ratio: $aspectRatio")
        
        // Clamp to reasonable bounds to handle edge cases
        return aspectRatio.coerceIn(1.5f, 2.5f)
    }
    
    /**
     * Calculates dynamic preview dimensions based on device aspect ratio.
     * Returns Pair(widthDp, heightDp)
     */
    private fun getPreviewDimensions(context: Context): Pair<Int, Int> {
        val aspectRatio = getDeviceAspectRatio(context)
        val width = TARGET_W_DP
        val height = (width * aspectRatio).toInt()
        
        Log.d(TAG, "Preview dimensions: ${width}dp x ${height}dp (aspect ratio: $aspectRatio)")
        return Pair(width, height)
    }
    
    /**
     * Generates a display configuration identifier for cache keys.
     * This includes screen dimensions, density, and orientation to distinguish between 
     * folded/unfolded states and portrait/landscape orientations.
     */
    private fun getDisplayConfigurationId(context: Context): String {
        val (width, height) = getAppContentBounds(context)
        val density = context.resources.displayMetrics.densityDpi
        val orientation = if (width > height) "landscape" else "portrait"
        return "${width}x${height}-${density}dpi-${orientation}"
    }
    
    /**
     * Gets a theme preview bitmap, using configuration-aware cache when possible.
     * @param context Application context
     * @param theme Theme resource ID to apply
     * @param themeKey Unique key for this theme ("light", "dark", "auto")
     */
    suspend fun getPreview(
        context: Context,
        @StyleRes theme: Int,
        themeKey: String
    ): Bitmap = withContext(Dispatchers.Default) {
        val configId = getDisplayConfigurationId(context)
        val fullCacheKey = "$themeKey-$configId"
        
        Log.d(TAG, "🔧 FOLDABLE: getPreview called for themeKey='$themeKey', configId='$configId', theme=$theme")
        
        try {
            // Check memory cache first with configuration-aware key
            memoryCache.get(fullCacheKey)?.let { cached ->
                Log.d(TAG, "🔧 FOLDABLE: Found cached bitmap for '$fullCacheKey' - ${cached.width}×${cached.height}")
                return@withContext cached 
            }
        
            // Check disk cache with configuration-aware key
            val cacheDir = File(context.cacheDir, "theme_previews").apply { mkdirs() }
            val cacheKey = "v${BuildConfig.VERSION_CODE}-$fullCacheKey.webp"
            val cachedFile = File(cacheDir, cacheKey)
            
            if (cachedFile.exists()) {
                Log.d(TAG, "🔧 FOLDABLE: Found disk cached file for '$fullCacheKey'")
                val bitmap = BitmapFactory.decodeFile(cachedFile.absolutePath)
                if (bitmap != null && !bitmap.isRecycled) {
                    Log.d(TAG, "🔧 FOLDABLE: Loaded disk cached bitmap - ${bitmap.width}×${bitmap.height}")
                    memoryCache.put(fullCacheKey, bitmap)
                    return@withContext bitmap
                } else {
                    Log.w(TAG, "🔧 FOLDABLE: Disk cached bitmap was null or recycled, deleting cache file")
                    cachedFile.delete()
                }
            }
            
            // Generate new preview
            Log.d(TAG, "🔧 FOLDABLE: Generating new bitmap for '$themeKey' with config '$configId'")
            val newBitmap = when (themeKey) {
                "auto" -> {
                    Log.d(TAG, "🔧 FOLDABLE: Calling generateSplitPreview for auto theme")
                    generateSplitPreview(context)
                }
                else -> {
                    Log.d(TAG, "🔧 FOLDABLE: Calling generateSinglePreview for theme $theme")
                    generateSinglePreview(context, theme)
                }
            }
            
            if (newBitmap.isRecycled) {
                Log.e(TAG, "🔧 FOLDABLE: Generated bitmap is recycled! This is a bug.")
                return@withContext generateFallbackBitmap(context)
            }
        
            // Cache in memory with configuration-aware key
            memoryCache.put(fullCacheKey, newBitmap)
            Log.d(TAG, "🔧 FOLDABLE: Cached new bitmap in memory for '$fullCacheKey'")
            
            // Save to disk cache with error handling
            try {
                cachedFile.outputStream().use { stream ->
                    val compressed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        newBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream)
                    } else {
                        @Suppress("DEPRECATION")
                        newBitmap.compress(Bitmap.CompressFormat.WEBP, 100, stream)
                    }
                    if (compressed) {
                        Log.d(TAG, "🔧 FOLDABLE: Saved bitmap to disk cache for '$fullCacheKey'")
                    } else {
                        Log.w(TAG, "🔧 FOLDABLE: Failed to compress bitmap for disk cache")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "🔧 FOLDABLE: Disk caching failed for '$fullCacheKey': ${e.message}")
            }
            
            Log.d(TAG, "🔧 FOLDABLE: Successfully generated ${newBitmap.width}×${newBitmap.height} bitmap for '$fullCacheKey'")
            newBitmap
        } catch (e: Exception) {
            Log.e(TAG, "🔧 FOLDABLE: getPreview FAILED for '$themeKey' with config '$configId'", e)
            Log.e(TAG, "Exception details: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            generateFallbackBitmap(context)
        }
    }
    
    /**
     * Generates a single theme preview by inflating the host layout with real homepage components.
     * Simplified version that removes complex scaling pipeline to fix gray preview issue.
     */
    private suspend fun generateSinglePreview(
        context: Context,
        @StyleRes theme: Int
    ): Bitmap = withContext(Dispatchers.Main) {
        
        try {
            Log.d(TAG, "🔧 SIMPLIFIED: Starting generateSinglePreview for theme=$theme")
            
            // Create themed context (no complex density scaling)
            val themedContext = ContextThemeWrapper(context, theme)
            
            // Inflate the preview layout
            val inflater = LayoutInflater.from(themedContext)
            val host = FrameLayout(themedContext)
            val root = inflater.inflate(R.layout.theme_preview_host, host, false)
            host.addView(root)
            
            Log.d(TAG, "🔧 SIMPLIFIED: Layout inflated successfully")
            
            // Set up content using the existing content source system
            val contentSource = CompositePreviewSource()
            setupStubData(root, contentSource)
            
            Log.d(TAG, "🔧 SIMPLIFIED: Stub data setup completed")
            
            // Get target dimensions (96dp × 96dp square container)
            val dm = context.resources.displayMetrics
            val targetPxW = (96 * dm.density).roundToInt()
            val targetPxH = (96 * dm.density).roundToInt()
            
            // Use device screen size for initial render (simple approach)
            val (deviceWidth, deviceHeight) = getAppContentBounds(context)
            
            Log.d(TAG, "🔧 SIMPLIFIED: targetContainer=${targetPxW}×${targetPxH}, deviceBounds=${deviceWidth}×${deviceHeight}")
            
            // Configure RecyclerView for preview rendering
            val rv = root.findViewById<RecyclerView>(R.id.recyclerViewNews)
            rv?.let { recyclerView ->
                recyclerView.itemAnimator = null
                recyclerView.isNestedScrollingEnabled = false
                (recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.isItemPrefetchEnabled = false
            }
            
            // Simple measurement and layout
            val wSpec = View.MeasureSpec.makeMeasureSpec(deviceWidth, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(deviceHeight, View.MeasureSpec.EXACTLY)
            host.measure(wSpec, hSpec)
            host.layout(0, 0, deviceWidth, deviceHeight)
            
            Log.d(TAG, "🔧 SIMPLIFIED: Layout completed - measured=${host.measuredWidth}×${host.measuredHeight}")
            
            // Render to bitmap
            val rendered = Bitmap.createBitmap(deviceWidth, deviceHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(rendered)
            host.draw(canvas)
            
            Log.d(TAG, "🔧 SIMPLIFIED: Initial render completed - ${rendered.width}×${rendered.height}")
            
            // Use proper proportional scaling - no cropping, just fit to square container
            val final = scaleProportionallyToSquare(rendered, targetPxW, themedContext)
            rendered.recycle()
            
            // Set proper density to prevent ImageView auto-scaling
            final.density = DisplayMetrics.DENSITY_DEFAULT
            
            Log.d(TAG, "🔧 SIMPLIFIED: Final preview generated - ${final.width}×${final.height}")
            
            final
        } catch (e: Exception) {
            Log.e(TAG, "🔧 SIMPLIFIED: FAILED - Exception during preview generation", e)
            Log.e(TAG, "Exception details: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            
            // Return fallback bitmap on error
            val fallback = generateFallbackBitmap(context)
            Log.d(TAG, "🔧 SIMPLIFIED: Returning fallback bitmap ${fallback.width}x${fallback.height}")
            fallback
        }
    }
    
    /**
     * Generates the "Follow System" split preview showing light theme on left, dark on right.
     */
    suspend fun generateSplitPreview(context: Context): Bitmap = withContext(Dispatchers.Main) {
        try {
            val lightBitmap = generateSinglePreview(context, R.style.Theme_OSRSWiki_OSRSLight)
            val darkBitmap = generateSinglePreview(context, R.style.Theme_OSRSWiki_OSRSDark)
            
            val width = lightBitmap.width
            val height = lightBitmap.height
            val splitBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(splitBitmap)
            
            // Draw left half (light theme)
            val leftRect = Rect(0, 0, width / 2, height)
            canvas.drawBitmap(lightBitmap, leftRect, leftRect, null)
            
            // Draw right half (dark theme)
            val rightSrcRect = Rect(width / 2, 0, width, height)
            val rightDestRect = Rect(width / 2, 0, width, height)
            canvas.drawBitmap(darkBitmap, rightSrcRect, rightDestRect, null)
            
            // Draw divider line
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#999999") // Gray divider
                strokeWidth = dpToPx(1, context).toFloat()
            }
            canvas.drawLine(
                (width / 2).toFloat(), 
                0f, 
                (width / 2).toFloat(), 
                height.toFloat(), 
                dividerPaint
            )
            
            // Clean up
            lightBitmap.recycle()
            darkBitmap.recycle()
            
            splitBitmap
        } catch (e: Exception) {
            generateFallbackBitmap(context)
        }
    }
    
    /**
     * Scales screen content to fill square container with top-aligned cropping.
     * Similar to table previews - scales to fit width and crops from top for "fit-to-width" behavior.
     */
    private fun scaleProportionallyToSquare(src: Bitmap, squareSize: Int, themedContext: Context): Bitmap {
        val sourceWidth = src.width.toFloat()
        val sourceHeight = src.height.toFloat()
        val targetWidth = squareSize
        val targetHeight = squareSize
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat() // 1.0 for square
        val sourceRatio = sourceWidth / sourceHeight
        
        Log.d(TAG, "🔧 FIT_TO_WIDTH: Source ${src.width}×${src.height} (ratio=$sourceRatio) → Square ${squareSize}×${squareSize}")
        
        // Calculate scaling factor to fill the target container (same logic as table previews)
        val scale = if (sourceRatio > targetRatio) {
            // Source is wider - scale to fit height, crop width
            targetHeight / sourceHeight
        } else {
            // Source is taller - scale to fit width, crop height  
            targetWidth / sourceWidth
        }
        
        // Calculate scaled dimensions
        val scaledWidth = (sourceWidth * scale).roundToInt()
        val scaledHeight = (sourceHeight * scale).roundToInt()
        
        Log.d(TAG, "🔧 FIT_TO_WIDTH: Scale factor=$scale, scaled=${scaledWidth}×${scaledHeight}")
        
        // Create scaled bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(src, scaledWidth, scaledHeight, true)
        
        // Calculate crop offsets - center horizontally, top-aligned vertically
        val cropX = maxOf(0, (scaledWidth - targetWidth) / 2)
        val cropY = 0 // Start from top to show header content (like table previews)
        
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
        
        croppedBitmap.density = DisplayMetrics.DENSITY_DEFAULT
        
        Log.d(TAG, "🔧 FIT_TO_WIDTH: Final preview ${croppedBitmap.width}×${croppedBitmap.height} - fit to width with top alignment")
        
        return croppedBitmap
    }
    
    /**
     * Expert's cropToAspect function - crops bitmap to target aspect ratio with top bias.
     * This fixes aspect ratio mismatch between rendered content and display container.
     */
    private fun cropToAspect(src: Bitmap, dstW: Int, dstH: Int, topBias: Float = 0.33f): Bitmap {
        val srcRatio = src.width / src.height.toFloat()
        val dstRatio = dstW / dstH.toFloat()         // 96/192 = 0.5 (2:1 container ratio)
        
        return if (srcRatio > dstRatio) {
            // Source is wider - crop width (center crop)
            val newW = (src.height * dstRatio).roundToInt()
            val x = (src.width - newW) / 2
            Bitmap.createBitmap(src, x, 0, newW, src.height)
        } else {
            // Source is taller - crop height (top-biased to show header + content)
            val newH = (src.width / dstRatio).roundToInt()
            val y = ((src.height - newH) * topBias).roundToInt().coerceAtLeast(0)
            Bitmap.createBitmap(src, 0, y, src.width, min(newH, src.height - y))
        }
    }
    
    /**
     * Creates a fallback bitmap with theme-appropriate color when generation fails.
     */
    private fun generateFallbackBitmap(context: Context): Bitmap {
        // Use fixed 96dp × 96dp square dimensions
        val size = (96 * context.resources.displayMetrics.density).roundToInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Try to get current theme's surface color, fallback to neutral gray
        val surfaceColor = try {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurface, 
                typedValue, 
                true
            )
            typedValue.data
        } catch (e: Exception) {
            Color.parseColor("#E0E0E0") // Neutral gray fallback
        }
        
        canvas.drawColor(surfaceColor)
        
        // Set proper density to prevent scaling
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
     * Gets the theme-appropriate surface color for pillar-boxing in previews.
     */
    private fun getThemeSurfaceColor(context: Context): Int {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(
            com.google.android.material.R.attr.colorSurfaceContainerHigh, 
            typedValue, 
            true
        )
        return typedValue.data
    }
    
    /**
     * Sets up content for theme preview using expert's dynamic content strategy.
     * Uses live home feed data for authentic "what you see is what you get" previews.
     */
    private suspend fun setupStubData(root: View, contentSource: CompositePreviewSource): Boolean {
        
        try {
            // Hide progress indicator and error states
            root.findViewById<View>(R.id.progressBarNews)?.let { progress ->
                progress.visibility = View.GONE
            }
            
            root.findViewById<View>(R.id.textViewNewsError)?.let { error ->
                error.visibility = View.GONE
            }
            
            // Set page title for the header - use the same string as the real app (Home)
            root.findViewById<TextView>(R.id.page_title)?.let { title ->
                title.text = root.context.getString(R.string.nav_news)  // This resolves to "Home"
            }
            
            // Ensure search text placeholder is visible 
            root.findViewById<TextView>(R.id.search_text)?.let { searchText ->
                searchText.text = "Search OSRSWiki"
            }
            
            // Attempt to populate news RecyclerView with realistic sample data
            root.findViewById<RecyclerView>(R.id.recyclerViewNews)?.let { recyclerView ->
                
                try {
                    recyclerView.visibility = View.VISIBLE
                    
                    // Expert's dynamic content: Get authentic current home feed data
                    val feedItems = contentSource.getPreviewItems(limit = 3)
                    
                    // Set up adapter with dynamic data (using synchronous image loader and empty click handlers)
                    val adapter = NewsFeedAdapter(
                        imageLoader = SynchronousImageLoader(root.context),
                        onUpdateItemClicked = { /* No-op for preview */ },
                        onLinkClicked = { /* No-op for preview */ }
                    )
                    
                    // Configure RecyclerView
                    recyclerView.layoutManager = LinearLayoutManager(root.context)
                    
                    // EXPERT RECOMMENDATION: Disable animations to avoid deferred posts
                    recyclerView.itemAnimator = null
                    
                    recyclerView.adapter = adapter
                    
                    // Populate with authentic current content
                    adapter.setItems(feedItems)
                    
                    return true
                    
                } catch (e: Exception) {
                    Log.e(TAG, "setupStubData: Failed to populate RecyclerView with complex content", e)
                    // Fall back to empty but visible RecyclerView
                    setupFallbackContent(recyclerView)
                    return false
                }
            } ?: run {
                    return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "setupStubData: Overall setup failed", e)
            // Try to at least show basic content
            setupBasicFallbackContent(root)
            return false
        }
    }
    
    /**
     * Sets up fallback content when complex RecyclerView population fails.
     */
    private fun setupFallbackContent(recyclerView: RecyclerView) {
        try {
            recyclerView.visibility = View.VISIBLE
            recyclerView.layoutManager = null
            recyclerView.adapter = null
        } catch (e: Exception) {
            Log.e(TAG, "setupFallbackContent: Even fallback failed", e)
            recyclerView.visibility = View.GONE
        }
    }
    
    /**
     * Sets up basic fallback content when everything else fails.
     */
    private fun setupBasicFallbackContent(root: View) {
        try {
            // Just ensure basic visibility states
            root.findViewById<View>(R.id.progressBarNews)?.visibility = View.GONE
            root.findViewById<View>(R.id.textViewNewsError)?.visibility = View.GONE
            root.findViewById<RecyclerView>(R.id.recyclerViewNews)?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "setupBasicFallbackContent: Even basic fallback failed", e)
        }
    }
    
    /**
     * Creates representative sample data for the news feed that matches typical home page content.
     */
    private fun createSampleFeedData(): List<FeedItem> {
        // Sample recent updates (like Varlamore & Summer Sweep)
        val sampleUpdates = listOf(
            UpdateItem(
                title = "Varlamore & Summer Sweep Up",
                snippet = "This week brings continued improvements to Varlamore and the...",
                imageUrl = "https://oldschool.runescape.wiki/images/Main_page_-_Varlamore_%26_Summer_Sweep-Up_Combat_Tweaks.png",
                articleUrl = "/w/Update:Varlamore_%26_Summer_Sweep_Up"
            ),
            UpdateItem(
                title = "Varlamore: The Final Dawn",
                snippet = "This week in Varlamore brings improvements to combat...",
                imageUrl = "https://oldschool.runescape.wiki/images/Main_page_-_Varlamore_The_Final_Dawn_Out_Now.png",
                articleUrl = "/w/Update:Varlamore:_The_Final_Dawn"
            )
        )
        
        // Sample announcement
        val sampleAnnouncement = AnnouncementItem(
            date = "1 June 2025",
            content = "The wiki has reached 100,000 uploaded files with <a href=\"/w/File:Quill_Caps_-_202.png\">File:Quill Caps - 202.png</a>! Help upload even more by documenting sound effects at <a href=\"/w/RuneScape:SFX\">RuneScape:SFX</a>."
        )
        
        // Sample "On this day" events
        val sampleOnThisDay = OnThisDayItem(
            title = "On this day...",
            events = listOf(
                "• 2024 – Community: <a href=\"/w/Update:The_Future_of_Deathmatch...\">The Future of Deathmatch...</a>",
                "• 2022 – Community: <a href=\"/w/Update:Updated_Proposed_Mobile_...\">Updated Proposed Mobile ...</a>",
                "• 2019 – Game update: <a href=\"/w/Update:Smithing_and_Silver_Craft...\">Smithing and Silver Craft...</a>",
                "• 2019 – Community: <a href=\"/w/Poll_Blog:_Elf_Graphics,_Cry...\">Poll Blog: Elf Graphics, Cry...</a>"
            )
        )
        
        return listOf(
            FeedItem.Updates(sampleUpdates),
            FeedItem.Announcement(sampleAnnouncement),
            FeedItem.OnThisDay(sampleOnThisDay)
        )
    }
    
    /**
     * Gets the preview dimensions that will be used for the given context.
     * This allows the UI to size containers appropriately.
     * Returns Pair(widthDp, heightDp)
     */
    fun getPreviewDimensionsForUI(context: Context): Pair<Int, Int> {
        return getPreviewDimensions(context)
    }
    
    /**
     * Clears all caches. Should be called when themes are updated.
     */
    fun clearCache(context: Context) {
        Log.d(TAG, "🔧 FOLDABLE: Clearing all theme preview caches")
        memoryCache.evictAll()
        
        // Clear disk cache
        val cacheDir = File(context.cacheDir, "theme_previews")
        if (cacheDir.exists()) {
            val files = cacheDir.listFiles()
            files?.forEach { file ->
                val deleted = file.delete()
                Log.d(TAG, "🔧 FOLDABLE: Deleted cache file ${file.name}: $deleted")
            }
            Log.d(TAG, "🔧 FOLDABLE: Cleared ${files?.size ?: 0} cache files")
        }
    }
    
    /**
     * Clears cache for a specific display configuration.
     * This is called when device fold state changes to remove previews for the old configuration.
     */
    fun clearCacheForConfiguration(context: Context, configId: String) {
        Log.d(TAG, "🔧 FOLDABLE: Clearing cache for configuration: $configId")
        
        // Clear memory cache entries for this configuration
        val keysToRemove = mutableListOf<String>()
        val snapshot = memoryCache.snapshot()
        for (key in snapshot.keys) {
            if (key.endsWith("-$configId")) {
                keysToRemove.add(key)
            }
        }
        
        for (key in keysToRemove) {
            memoryCache.remove(key)
            Log.d(TAG, "🔧 FOLDABLE: Removed memory cache entry: $key")
        }
        
        // Clear disk cache files for this configuration
        val cacheDir = File(context.cacheDir, "theme_previews")
        if (cacheDir.exists()) {
            val files = cacheDir.listFiles()
            files?.forEach { file ->
                if (file.name.contains("-$configId.webp")) {
                    val deleted = file.delete()
                    Log.d(TAG, "🔧 FOLDABLE: Deleted config-specific cache file ${file.name}: $deleted")
                }
            }
        }
    }
    
    /**
     * Called when device configuration changes (e.g., fold/unfold).
     * Clears cache for the old configuration and triggers preview regeneration.
     */
    fun onConfigurationChanged(context: Context) {
        val newConfigId = getDisplayConfigurationId(context)
        Log.d(TAG, "🔧 FOLDABLE: Configuration changed to: $newConfigId")
        
        // Get all current configuration IDs in cache to find the old ones
        val snapshot = memoryCache.snapshot()
        val currentConfigIds = mutableSetOf<String>()
        
        for (key in snapshot.keys) {
            val parts = key.split("-")
            if (parts.size >= 4) { // format: "theme-widthxheight-densitydpi-orientation"
                val configPart = parts.subList(1, parts.size).joinToString("-")
                currentConfigIds.add(configPart)
            }
        }
        
        // Clear cache for all old configurations (keep only current one)
        for (oldConfigId in currentConfigIds) {
            if (oldConfigId != newConfigId) {
                clearCacheForConfiguration(context, oldConfigId)
            }
        }
        
        Log.d(TAG, "🔧 FOLDABLE: Configuration change handling completed")
    }
}