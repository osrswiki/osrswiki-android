package com.omiyawaki.osrswiki.test

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
import com.omiyawaki.osrswiki.search.CleanedSearchResultItem
import com.omiyawaki.osrswiki.search.SearchAdapter
import java.io.File
import java.io.FileOutputStream

object PixelColorTest {
    
    fun runPixelTest(context: Context, recyclerView: RecyclerView?) {
        Log.e("PixelTest", "=== PIXEL COLOR TEST STARTING ===")
        
        if (recyclerView == null) {
            Log.e("PixelTest", "RecyclerView is null, cannot test")
            return
        }
        
        // Wait for layout
        recyclerView.post {
            captureAndAnalyzeSearchResults(recyclerView)
        }
    }
    
    private fun captureAndAnalyzeSearchResults(recyclerView: RecyclerView) {
        val itemCount = recyclerView.adapter?.itemCount ?: 0
        Log.e("PixelTest", "Found $itemCount items in RecyclerView")
        
        if (itemCount == 0) {
            Log.e("PixelTest", "No items to test")
            return
        }
        
        // Analyze first visible item
        val firstViewHolder = recyclerView.findViewHolderForAdapterPosition(0)
        if (firstViewHolder != null && firstViewHolder.itemView.width > 0) {
            analyzeSearchItem(firstViewHolder.itemView, 0)
        }
        
        // Analyze second item if available
        if (itemCount > 1) {
            val secondViewHolder = recyclerView.findViewHolderForAdapterPosition(1)
            if (secondViewHolder != null && secondViewHolder.itemView.width > 0) {
                analyzeSearchItem(secondViewHolder.itemView, 1)
            }
        }
    }
    
    private fun analyzeSearchItem(itemView: View, position: Int) {
        Log.e("PixelTest", "")
        Log.e("PixelTest", "=== ANALYZING ITEM $position ===")
        
        // Find title and snippet TextViews
        val titleView = itemView.findViewById<TextView>(R.id.search_item_title)
        val snippetView = itemView.findViewById<TextView>(R.id.search_item_snippet)
        
        if (titleView == null || snippetView == null) {
            Log.e("PixelTest", "Could not find title or snippet views")
            return
        }
        
        // Get the text content
        val titleText = titleView.text.toString()
        val snippetText = snippetView.text.toString()
        Log.e("PixelTest", "Title: ${titleText.take(30)}...")
        Log.e("PixelTest", "Snippet: ${snippetText.take(50)}...")
        
        // Create bitmap of the item view
        val bitmap = Bitmap.createBitmap(itemView.width, itemView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        itemView.draw(canvas)
        
        // Save bitmap for inspection
        saveBitmap(bitmap, "item_${position}.png", itemView.context)
        
        // Get view positions relative to parent
        val titleLocation = IntArray(2)
        val snippetLocation = IntArray(2)
        val itemLocation = IntArray(2)
        
        titleView.getLocationInWindow(titleLocation)
        snippetView.getLocationInWindow(snippetLocation)
        itemView.getLocationInWindow(itemLocation)
        
        // Calculate relative positions
        val titleRelativeY = titleLocation[1] - itemLocation[1] + titleView.height / 2
        val snippetRelativeY = snippetLocation[1] - itemLocation[1] + snippetView.height / 2
        
        // Sample colors from title (left side to avoid potential highlights)
        val titleX = 50  // Sample from left side
        val titlePixel = if (titleRelativeY in 0 until bitmap.height && titleX < bitmap.width) {
            bitmap.getPixel(titleX, titleRelativeY)
        } else {
            Log.e("PixelTest", "Title position out of bounds: x=$titleX, y=$titleRelativeY")
            0
        }
        
        // Sample colors from snippet (multiple points to find non-highlighted text)
        val snippetSamples = mutableListOf<Int>()
        val samplePoints = listOf(50, 100, 150, 200, 250) // Multiple x positions
        
        for (x in samplePoints) {
            if (snippetRelativeY in 0 until bitmap.height && x < bitmap.width) {
                val pixel = bitmap.getPixel(x, snippetRelativeY)
                snippetSamples.add(pixel)
            }
        }
        
        // Analyze colors
        Log.e("PixelTest", "Title pixel (x=50): #${Integer.toHexString(titlePixel)}")
        Log.e("PixelTest", "Title RGB: R=${Color.red(titlePixel)}, G=${Color.green(titlePixel)}, B=${Color.blue(titlePixel)}")
        
        Log.e("PixelTest", "Snippet samples:")
        snippetSamples.forEachIndexed { index, pixel ->
            val x = samplePoints[index]
            Log.e("PixelTest", "  x=$x: #${Integer.toHexString(pixel)} (R=${Color.red(pixel)}, G=${Color.green(pixel)}, B=${Color.blue(pixel)})")
        }
        
        // Find the darkest snippet sample (likely non-highlighted text)
        val darkestSnippet = snippetSamples.minByOrNull { 
            Color.red(it) + Color.green(it) + Color.blue(it) 
        } ?: 0
        
        Log.e("PixelTest", "Darkest snippet pixel: #${Integer.toHexString(darkestSnippet)}")
        
        // Compare colors
        val titleRGB = titlePixel and 0x00FFFFFF
        val snippetRGB = darkestSnippet and 0x00FFFFFF
        
        if (titleRGB != snippetRGB) {
            val rDiff = Math.abs(Color.red(titlePixel) - Color.red(darkestSnippet))
            val gDiff = Math.abs(Color.green(titlePixel) - Color.green(darkestSnippet))
            val bDiff = Math.abs(Color.blue(titlePixel) - Color.blue(darkestSnippet))
            
            Log.e("PixelTest", "🔴 COLORS DON'T MATCH!")
            Log.e("PixelTest", "  Title RGB: #${Integer.toHexString(titleRGB)}")
            Log.e("PixelTest", "  Snippet RGB: #${Integer.toHexString(snippetRGB)}")
            Log.e("PixelTest", "  Difference: R=$rDiff, G=$gDiff, B=$bDiff")
        } else {
            Log.e("PixelTest", "✅ Colors match!")
        }
        
        // Also check programmatic colors for comparison
        Log.e("PixelTest", "Programmatic currentTextColor:")
        Log.e("PixelTest", "  Title: #${Integer.toHexString(titleView.currentTextColor)}")
        Log.e("PixelTest", "  Snippet: #${Integer.toHexString(snippetView.currentTextColor)}")
    }
    
    private fun saveBitmap(bitmap: Bitmap, filename: String, context: Context) {
        try {
            val file = File(context.externalCacheDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.e("PixelTest", "Bitmap saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("PixelTest", "Failed to save bitmap", e)
        }
    }
}