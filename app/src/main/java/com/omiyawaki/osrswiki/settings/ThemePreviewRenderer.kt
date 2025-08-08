package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.util.LruCache
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.news.model.UpdateItem
import com.omiyawaki.osrswiki.news.model.AnnouncementItem
import com.omiyawaki.osrswiki.news.model.OnThisDayItem
import com.omiyawaki.osrswiki.news.model.PopularPageItem
import com.omiyawaki.osrswiki.news.ui.FeedItem
import com.omiyawaki.osrswiki.news.ui.NewsFeedAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Generates dynamic theme previews by rendering actual app layouts with different themes.
 * Uses both memory and disk caching for optimal performance.
 */
object ThemePreviewRenderer {
    
    private const val TARGET_W_DP = 110
    private const val TARGET_H_DP = 72
    private const val CACHE_SIZE = 3 // light, dark, split
    private const val TAG = "ThemePreviewRenderer"
    
    // Memory cache for immediate access
    private val memoryCache = LruCache<String, Bitmap>(CACHE_SIZE)
    
    /**
     * Gets a theme preview bitmap, using cache when possible.
     * @param context Application context
     * @param theme Theme resource ID to apply
     * @param themeKey Unique key for this theme ("light", "dark", "auto")
     */
    suspend fun getPreview(
        context: Context,
        @StyleRes theme: Int,
        themeKey: String
    ): Bitmap = withContext(Dispatchers.Default) {
        // Check memory cache first
        memoryCache.get(themeKey)?.let { return@withContext it }
        
        // Check disk cache
        val cacheDir = File(context.cacheDir, "theme_previews").apply { mkdirs() }
        val cacheKey = "v${BuildConfig.VERSION_CODE}-$themeKey.webp"
        val cachedFile = File(cacheDir, cacheKey)
        
        if (cachedFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(cachedFile.absolutePath)
            if (bitmap != null) {
                memoryCache.put(themeKey, bitmap)
                return@withContext bitmap
            }
        }
        
        // Generate new preview
        val newBitmap = when (themeKey) {
            "auto" -> generateSplitPreview(context)
            else -> generateSinglePreview(context, theme)
        }
        
        // Cache in memory
        memoryCache.put(themeKey, newBitmap)
        
        // Save to disk cache
        try {
            cachedFile.outputStream().use { 
                newBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) 
            }
        } catch (e: Exception) {
            // Disk caching failed, but we still have the bitmap
        }
        
        newBitmap
    }
    
    /**
     * Generates a single theme preview by inflating the host layout with real homepage components.
     */
    private suspend fun generateSinglePreview(
        context: Context,
        @StyleRes theme: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        Log.d(TAG, "generateSinglePreview: Starting preview generation for theme $theme")
        
        try {
            // EXPERT 1's SOLUTION: Move all view operations to Main thread
            val root = withContext(Dispatchers.Main.immediate) {
                Log.d(TAG, "generateSinglePreview: [MAIN THREAD] Creating themed context")
                val themedContext = ContextThemeWrapper(context, theme)
                
                Log.d(TAG, "generateSinglePreview: [MAIN THREAD] Creating inflater")
                // Use cloneInContext to fix theme inflation bugs (Expert 1's recommendation)
                val inflater = LayoutInflater.from(context).cloneInContext(themedContext)
                
                Log.d(TAG, "generateSinglePreview: [MAIN THREAD] Inflating layout")
                val root = inflater.inflate(R.layout.theme_preview_host, null, false)
                
                Log.d(TAG, "generateSinglePreview: [MAIN THREAD] Setting up stub data")
                // Attempt to populate with content, but continue even if it fails
                val contentSuccess = setupStubData(root)
                Log.d(TAG, "generateSinglePreview: [MAIN THREAD] Content setup ${if (contentSuccess) "succeeded" else "failed, using fallback"}")
                
                Log.d(TAG, "generateSinglePreview: [MAIN THREAD] Starting measure and layout")
                // Measure at standard phone dimensions
                val specW = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
                val specH = View.MeasureSpec.makeMeasureSpec(2280, View.MeasureSpec.EXACTLY)
                
                root.measure(specW, specH)
                Log.d(TAG, "generateSinglePreview: [MAIN THREAD] Measured view: ${root.measuredWidth}x${root.measuredHeight}")
                
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                Log.d(TAG, "generateSinglePreview: [MAIN THREAD] Layout complete - returning to background thread")
                
                root // Return prepared root view to Default thread
            }
            
            // EXPERT 1's SOLUTION: Only bitmap creation stays on background thread
            Log.d(TAG, "generateSinglePreview: [BACKGROUND] Creating bitmap from prepared view")
            // Create full-size bitmap
            val fullBitmap = Bitmap.createBitmap(
                root.measuredWidth, 
                root.measuredHeight, 
                Bitmap.Config.ARGB_8888
            )
            Log.d(TAG, "generateSinglePreview: [BACKGROUND] Created bitmap ${fullBitmap.width}x${fullBitmap.height}")
            
            val canvas = Canvas(fullBitmap)
            Log.d(TAG, "generateSinglePreview: [BACKGROUND] Drawing to canvas")
            root.draw(canvas)
            Log.d(TAG, "generateSinglePreview: [BACKGROUND] Draw complete")
            
            Log.d(TAG, "generateSinglePreview: [BACKGROUND] Starting bitmap processing")
            // Crop out status bar area (top 72px) and scale to preview size
            val cropHeight = fullBitmap.height - dpToPx(24, context)
            Log.d(TAG, "generateSinglePreview: [BACKGROUND] Cropping to height $cropHeight (from ${fullBitmap.height})")
            
            val croppedBitmap = Bitmap.createBitmap(
                fullBitmap, 
                0, 
                dpToPx(24, context), 
                fullBitmap.width, 
                cropHeight
            )
            Log.d(TAG, "generateSinglePreview: [BACKGROUND] Created cropped bitmap ${croppedBitmap.width}x${croppedBitmap.height}")
            
            // Scale proportionally based on width to maintain aspect ratio
            val targetWidth = dpToPx(TARGET_W_DP, context)
            val targetHeight = dpToPx(TARGET_H_DP, context)
            Log.d(TAG, "generateSinglePreview: [BACKGROUND] Target dimensions ${targetWidth}x${targetHeight}")
            
            // Calculate scale factor based on width
            val scale = targetWidth.toFloat() / croppedBitmap.width.toFloat()
            val scaledHeight = (croppedBitmap.height * scale).toInt()
            Log.d(TAG, "generateSinglePreview: [BACKGROUND] Scale factor $scale, scaled height $scaledHeight")
            
            // Scale proportionally (maintains aspect ratio)
            val proportionalBitmap = Bitmap.createScaledBitmap(
                croppedBitmap,
                targetWidth,
                scaledHeight,
                true
            )
            Log.d(TAG, "generateSinglePreview: [BACKGROUND] Created scaled bitmap ${proportionalBitmap.width}x${proportionalBitmap.height}")
            
            // Top-align crop if scaled height exceeds target
            val finalBitmap = if (scaledHeight > targetHeight) {
                Log.d(TAG, "generateSinglePreview: [BACKGROUND] Final crop needed, height $scaledHeight > target $targetHeight")
                Bitmap.createBitmap(
                    proportionalBitmap,
                    0,
                    0,
                    targetWidth,
                    targetHeight
                )
            } else {
                Log.d(TAG, "generateSinglePreview: [BACKGROUND] No final crop needed")
                proportionalBitmap
            }
            
            Log.d(TAG, "generateSinglePreview: [BACKGROUND] Final bitmap ${finalBitmap.width}x${finalBitmap.height}")
            
            // Clean up intermediate bitmaps
            fullBitmap.recycle()
            croppedBitmap.recycle()
            if (finalBitmap !== proportionalBitmap) {
                proportionalBitmap.recycle()
            }
            Log.d(TAG, "generateSinglePreview: [BACKGROUND] Cleaned up intermediate bitmaps")
            
            Log.d(TAG, "generateSinglePreview: SUCCESS - returning final bitmap")
            finalBitmap
        } catch (e: Exception) {
            Log.e(TAG, "generateSinglePreview: FAILED - Exception during preview generation", e)
            // Return fallback bitmap on error
            val fallback = generateFallbackBitmap(context)
            Log.d(TAG, "generateSinglePreview: Returning fallback bitmap ${fallback.width}x${fallback.height}")
            fallback
        }
    }
    
    /**
     * Generates the "Follow System" split preview showing light theme on left, dark on right.
     */
    suspend fun generateSplitPreview(context: Context): Bitmap = withContext(Dispatchers.Default) {
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
     * Creates a simple fallback bitmap when generation fails.
     */
    private fun generateFallbackBitmap(context: Context): Bitmap {
        val width = dpToPx(TARGET_W_DP, context)
        val height = dpToPx(TARGET_H_DP, context)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Fill with neutral color
        canvas.drawColor(Color.parseColor("#E0E0E0"))
        
        return bitmap
    }
    
    /**
     * Converts dp to pixels.
     */
    private fun dpToPx(dp: Int, context: Context): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    /**
     * Sets up content for theme preview. Attempts to populate with realistic home page content,
     * but falls back to basic layout if complex content fails.
     */
    private fun setupStubData(root: View): Boolean {
        Log.d(TAG, "setupStubData: Starting content setup")
        
        try {
            // Hide progress indicator and error states
            root.findViewById<View>(R.id.progressBarNews)?.let { progress ->
                progress.visibility = View.GONE
                Log.d(TAG, "setupStubData: Hidden progress indicator")
            }
            
            root.findViewById<View>(R.id.textViewNewsError)?.let { error ->
                error.visibility = View.GONE
                Log.d(TAG, "setupStubData: Hidden error text")
            }
            
            // Set page title for the header - use the same string as the real app (Home)
            root.findViewById<TextView>(R.id.page_title)?.let { title ->
                title.text = root.context.getString(R.string.nav_news)  // This resolves to "Home"
                Log.d(TAG, "setupStubData: Set page title to '${title.text}'")
            }
            
            // Ensure search text placeholder is visible 
            root.findViewById<TextView>(R.id.search_text)?.let { searchText ->
                searchText.text = "Search OSRSWiki"
                Log.d(TAG, "setupStubData: Set search text")
            }
            
            // Attempt to populate news RecyclerView with realistic sample data
            root.findViewById<RecyclerView>(R.id.recyclerViewNews)?.let { recyclerView ->
                Log.d(TAG, "setupStubData: Found RecyclerView, attempting to populate")
                
                try {
                    recyclerView.visibility = View.VISIBLE
                    
                    // Create sample data that represents typical home page content
                    val sampleFeedItems = createSampleFeedData()
                    Log.d(TAG, "setupStubData: Created sample feed items: ${sampleFeedItems.size}")
                    
                    // Set up adapter with sample data (using empty click handlers for preview)
                    val adapter = NewsFeedAdapter(
                        onUpdateItemClicked = { /* No-op for preview */ },
                        onLinkClicked = { /* No-op for preview */ }
                    )
                    Log.d(TAG, "setupStubData: Created NewsFeedAdapter")
                    
                    // Configure RecyclerView
                    recyclerView.layoutManager = LinearLayoutManager(root.context)
                    Log.d(TAG, "setupStubData: Set layout manager")
                    
                    recyclerView.adapter = adapter
                    Log.d(TAG, "setupStubData: Set adapter on RecyclerView")
                    
                    // Populate with sample data
                    adapter.setItems(sampleFeedItems)
                    Log.d(TAG, "setupStubData: Set items on adapter - SUCCESS")
                    
                    return true
                    
                } catch (e: Exception) {
                    Log.e(TAG, "setupStubData: Failed to populate RecyclerView with complex content", e)
                    // Fall back to empty but visible RecyclerView
                    setupFallbackContent(recyclerView)
                    return false
                }
            } ?: run {
                Log.w(TAG, "setupStubData: RecyclerView not found in layout")
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
        Log.d(TAG, "setupFallbackContent: Setting up empty RecyclerView fallback")
        try {
            recyclerView.visibility = View.VISIBLE
            recyclerView.layoutManager = null
            recyclerView.adapter = null
            Log.d(TAG, "setupFallbackContent: Set empty RecyclerView state")
        } catch (e: Exception) {
            Log.e(TAG, "setupFallbackContent: Even fallback failed", e)
            recyclerView.visibility = View.GONE
        }
    }
    
    /**
     * Sets up basic fallback content when everything else fails.
     */
    private fun setupBasicFallbackContent(root: View) {
        Log.d(TAG, "setupBasicFallbackContent: Setting up minimal fallback")
        try {
            // Just ensure basic visibility states
            root.findViewById<View>(R.id.progressBarNews)?.visibility = View.GONE
            root.findViewById<View>(R.id.textViewNewsError)?.visibility = View.GONE
            root.findViewById<RecyclerView>(R.id.recyclerViewNews)?.visibility = View.GONE
            Log.d(TAG, "setupBasicFallbackContent: Set basic visibility states")
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
                imageUrl = "",
                articleUrl = "/w/Update:Varlamore_%26_Summer_Sweep_Up"
            ),
            UpdateItem(
                title = "Varlamore: Combat Beta",
                snippet = "This week in Varlamore brings improvements to combat...",
                imageUrl = "",
                articleUrl = "/w/Update:Varlamore:_Combat_Beta"
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
     * Clears all caches. Should be called when themes are updated.
     */
    fun clearCache(context: Context) {
        memoryCache.evictAll()
        
        // Clear disk cache
        val cacheDir = File(context.cacheDir, "theme_previews")
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }
}