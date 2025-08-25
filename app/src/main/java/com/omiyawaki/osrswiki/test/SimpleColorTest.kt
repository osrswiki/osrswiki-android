package com.omiyawaki.osrswiki.test

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
import com.omiyawaki.osrswiki.search.CleanedSearchResultItem
import com.omiyawaki.osrswiki.search.SearchAdapter

object SimpleColorTest {
    
    fun runTest(context: Context) {
        Log.e("ColorTest", "=== SIMPLE COLOR TEST ===")
        
        // Create the view binding
        val binding = ItemSearchResultBinding.inflate(
            android.view.LayoutInflater.from(context), null, false
        )
        
        // Create test item
        val testItem = CleanedSearchResultItem(
            id = "1",
            title = "Oak logs",
            snippet = "Oak logs are logs obtained from cutting oak trees",
            thumbnailUrl = null
        )
        
        // Create adapter and view holder
        val listener = object : SearchAdapter.OnItemClickListener {
            override fun onItemClick(item: CleanedSearchResultItem) {}
        }
        val viewHolder = SearchAdapter.SearchResultViewHolder(binding, listener)
        
        // Bind with search query to trigger highlighting
        viewHolder.bind(testItem, "log")
        
        // Force layout
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        binding.root.layout(0, 0, binding.root.measuredWidth, binding.root.measuredHeight)
        
        // Capture the rendered view
        val bitmap = Bitmap.createBitmap(
            binding.root.width, 
            binding.root.height, 
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        binding.root.draw(canvas)
        
        // Analyze the actual rendered colors
        analyzeRenderedColors(bitmap, binding)
        
        // Also check the text spans directly
        analyzeTextSpans(binding.searchItemTitle, binding.searchItemSnippet)
    }
    
    private fun analyzeRenderedColors(bitmap: Bitmap, binding: ItemSearchResultBinding) {
        Log.e("ColorTest", "=== RENDERED COLORS ===")
        
        // Get positions of title and snippet
        val titleTop = binding.searchItemTitle.top
        val titleLeft = binding.searchItemTitle.left
        val snippetTop = binding.searchItemSnippet.top
        val snippetLeft = binding.searchItemSnippet.left
        
        // Sample colors from the rendered bitmap
        // Sample from the beginning of text (non-highlighted)
        val titleColor = bitmap.getPixel(titleLeft + 10, titleTop + 10)
        val snippetColor = bitmap.getPixel(snippetLeft + 10, snippetTop + 10)
        
        Log.e("ColorTest", "Title pixel color: #${Integer.toHexString(titleColor)}")
        Log.e("ColorTest", "Snippet pixel color: #${Integer.toHexString(snippetColor)}")
        
        // Remove alpha for comparison
        val titleRGB = titleColor and 0x00FFFFFF
        val snippetRGB = snippetColor and 0x00FFFFFF
        
        if (titleRGB != snippetRGB) {
            Log.e("ColorTest", "🔴 RENDERED COLORS DON'T MATCH!")
            Log.e("ColorTest", "  Title RGB: #${Integer.toHexString(titleRGB)}")
            Log.e("ColorTest", "  Snippet RGB: #${Integer.toHexString(snippetRGB)}")
            Log.e("ColorTest", "  Difference: ${Math.abs(titleRGB - snippetRGB)}")
        } else {
            Log.e("ColorTest", "✅ Rendered colors match")
        }
    }
    
    private fun analyzeTextSpans(titleView: TextView, snippetView: TextView) {
        Log.e("ColorTest", "=== TEXT SPAN ANALYSIS ===")
        
        val titleText = titleView.text
        val snippetText = snippetView.text
        
        Log.e("ColorTest", "Title text type: ${titleText?.javaClass?.simpleName}")
        Log.e("ColorTest", "Snippet text type: ${snippetText?.javaClass?.simpleName}")
        
        if (snippetText is Spanned) {
            val spans = snippetText.getSpans(0, snippetText.length, ForegroundColorSpan::class.java)
            Log.e("ColorTest", "Snippet has ${spans.size} color span(s)")
            
            if (spans.isEmpty()) {
                Log.e("ColorTest", "🔴 NO COLOR SPANS - snippet will use default color!")
            } else {
                // Check if entire text is covered by spans
                var coveredRanges = mutableListOf<Pair<Int, Int>>()
                spans.forEach { span ->
                    val start = snippetText.getSpanStart(span)
                    val end = snippetText.getSpanEnd(span)
                    val color = span.foregroundColor
                    Log.e("ColorTest", "  Span: [$start-$end] color=#${Integer.toHexString(color)}")
                    coveredRanges.add(Pair(start, end))
                }
                
                // Check for gaps
                var fullyCovered = true
                for (i in 0 until snippetText.length) {
                    if (!coveredRanges.any { it.first <= i && i < it.second }) {
                        Log.e("ColorTest", "🔴 Character at position $i is NOT covered by any span!")
                        fullyCovered = false
                        break
                    }
                }
                
                if (fullyCovered) {
                    Log.e("ColorTest", "✅ Entire text is covered by color spans")
                } else {
                    Log.e("ColorTest", "🔴 Some text is NOT covered by spans - will use default color!")
                }
            }
        }
        
        // Check currentTextColor
        Log.e("ColorTest", "Title currentTextColor: #${Integer.toHexString(titleView.currentTextColor)}")
        Log.e("ColorTest", "Snippet currentTextColor: #${Integer.toHexString(snippetView.currentTextColor)}")
    }
}