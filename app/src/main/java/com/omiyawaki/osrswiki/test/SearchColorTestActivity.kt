package com.omiyawaki.osrswiki.test

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ActivityMainBinding
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
import com.omiyawaki.osrswiki.search.CleanedSearchResultItem
import com.omiyawaki.osrswiki.search.SearchAdapter
import java.io.File
import java.io.FileOutputStream

class SearchColorTestActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create a container
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(container)
        
        // Create test items
        val testItems = listOf(
            CleanedSearchResultItem(
                id = "1",
                title = "Oak logs",
                snippet = "Oak logs are logs obtained from cutting oak trees at level 15 Woodcutting",
                thumbnailUrl = null
            ),
            CleanedSearchResultItem(
                id = "2", 
                title = "Maple logs",
                snippet = "Kandarin Medium Diary) per log. Maple logs can be burned with a Firemaking level of 45",
                thumbnailUrl = null
            ),
            CleanedSearchResultItem(
                id = "3",
                title = "Teak logs",
                snippet = "Teak logs are obtained through the Woodcutting skill by cutting teaks with a",
                thumbnailUrl = null
            )
        )
        
        // Create adapter
        val adapter = SearchAdapter(object : SearchAdapter.OnItemClickListener {
            override fun onItemClick(item: CleanedSearchResultItem) {}
        })
        
        // Create RecyclerView
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@SearchColorTestActivity)
            this.adapter = adapter
        }
        container.addView(recyclerView)
        
        // Set search query to trigger highlighting
        adapter.updateSearchQuery("log")
        
        // Submit data
        adapter.submitData(lifecycle, androidx.paging.PagingData.from(testItems))
        
        // Wait for layout then capture
        container.post {
            captureAndAnalyzeColors(container)
        }
    }
    
    private fun captureAndAnalyzeColors(view: View) {
        // Create bitmap
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        
        // Save bitmap for inspection
        val file = File(externalCacheDir, "search_test.png")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.e("ColorTest", "Screenshot saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("ColorTest", "Failed to save screenshot", e)
        }
        
        // Analyze colors at specific positions
        analyzeSearchItemColors(bitmap)
    }
    
    private fun analyzeSearchItemColors(bitmap: Bitmap) {
        // Sample colors from known positions (approximate)
        // This would need adjustment based on actual layout
        
        // First item title area (y ~= 50)
        val titleY = 50
        val titleColor = bitmap.getPixel(100, titleY)
        
        // First item snippet area (y ~= 100) 
        val snippetY = 100
        val snippetColorNonHighlight = bitmap.getPixel(100, snippetY)
        val snippetColorHighlight = bitmap.getPixel(150, snippetY) // Where "logs" would be
        
        Log.e("ColorTest", "=== VISUAL COLOR ANALYSIS ===")
        Log.e("ColorTest", "Title color: #${Integer.toHexString(titleColor)}")
        Log.e("ColorTest", "Snippet (non-highlight): #${Integer.toHexString(snippetColorNonHighlight)}")
        Log.e("ColorTest", "Snippet (highlight): #${Integer.toHexString(snippetColorHighlight)}")
        
        // Compare alpha-removed colors
        val titleRGB = titleColor and 0x00FFFFFF
        val snippetRGB = snippetColorNonHighlight and 0x00FFFFFF
        
        if (titleRGB != snippetRGB) {
            Log.e("ColorTest", "🔴 VISUAL MISMATCH CONFIRMED!")
            Log.e("ColorTest", "Title RGB: #${Integer.toHexString(titleRGB)}")
            Log.e("ColorTest", "Snippet RGB: #${Integer.toHexString(snippetRGB)}")
        } else {
            Log.e("ColorTest", "✅ Colors match visually")
        }
    }
}