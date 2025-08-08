package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
        try {
            val themedContext = ContextThemeWrapper(context, theme)
            // Use cloneInContext to fix theme inflation bugs (Expert 1's recommendation)
            val inflater = LayoutInflater.from(context).cloneInContext(themedContext)
            val root = inflater.inflate(R.layout.theme_preview_host, null, false)
            
            // Handle RecyclerView dependencies by showing empty state (Expert 1's tip)
            setupStubData(root)
            
            // Measure at standard phone dimensions
            val specW = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val specH = View.MeasureSpec.makeMeasureSpec(2280, View.MeasureSpec.EXACTLY)
            root.measure(specW, specH)
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            
            // Create full-size bitmap
            val fullBitmap = Bitmap.createBitmap(
                root.measuredWidth, 
                root.measuredHeight, 
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(fullBitmap)
            root.draw(canvas)
            
            // Crop out status bar area (top 72px) and scale to preview size
            val cropHeight = fullBitmap.height - dpToPx(24, context)
            val croppedBitmap = Bitmap.createBitmap(
                fullBitmap, 
                0, 
                dpToPx(24, context), 
                fullBitmap.width, 
                cropHeight
            )
            
            // Scale proportionally based on width to maintain aspect ratio
            val targetWidth = dpToPx(TARGET_W_DP, context)
            val targetHeight = dpToPx(TARGET_H_DP, context)
            
            // Calculate scale factor based on width
            val scale = targetWidth.toFloat() / croppedBitmap.width.toFloat()
            val scaledHeight = (croppedBitmap.height * scale).toInt()
            
            // Scale proportionally (maintains aspect ratio)
            val proportionalBitmap = Bitmap.createScaledBitmap(
                croppedBitmap,
                targetWidth,
                scaledHeight,
                true
            )
            
            // Top-align crop if scaled height exceeds target
            val finalBitmap = if (scaledHeight > targetHeight) {
                Bitmap.createBitmap(
                    proportionalBitmap,
                    0,
                    0,
                    targetWidth,
                    targetHeight
                )
            } else {
                proportionalBitmap
            }
            
            // Clean up intermediate bitmaps
            fullBitmap.recycle()
            croppedBitmap.recycle()
            if (finalBitmap !== proportionalBitmap) {
                proportionalBitmap.recycle()
            }
            
            finalBitmap
        } catch (e: Exception) {
            // Return fallback bitmap on error
            generateFallbackBitmap(context)
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
     * Sets up actual data for RecyclerViews and other components to show realistic home page content.
     * Populates the news RecyclerView with sample data that represents what users see on the home page.
     */
    private fun setupStubData(root: View) {
        // Hide progress indicator and error states
        root.findViewById<View>(R.id.progressBarNews)?.let { progress ->
            progress.visibility = View.GONE
        }
        
        root.findViewById<View>(R.id.textViewNewsError)?.let { error ->
            error.visibility = View.GONE
        }
        
        // Set page title for the header - News is the home screen
        root.findViewById<TextView>(R.id.page_title)?.let { title ->
            title.text = "News"
        }
        
        // Ensure search text placeholder is visible 
        root.findViewById<TextView>(R.id.search_text)?.let { searchText ->
            searchText.text = "Search OSRSWiki"
        }
        
        // Populate news RecyclerView with realistic sample data
        root.findViewById<RecyclerView>(R.id.recyclerViewNews)?.let { recyclerView ->
            recyclerView.visibility = View.VISIBLE
            
            // Create sample data that represents typical home page content
            val sampleFeedItems = createSampleFeedData()
            
            // Set up adapter with sample data (using empty click handlers for preview)
            val adapter = NewsFeedAdapter(
                onUpdateItemClicked = { /* No-op for preview */ },
                onLinkClicked = { /* No-op for preview */ }
            )
            
            // Configure RecyclerView
            recyclerView.layoutManager = LinearLayoutManager(root.context)
            recyclerView.adapter = adapter
            
            // Populate with sample data
            adapter.setItems(sampleFeedItems)
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