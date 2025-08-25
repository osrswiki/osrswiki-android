package com.omiyawaki.osrswiki.search

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding

class ColorTestActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inflate the search result item layout
        val binding = ItemSearchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Test with plain text (no search query)
        testPlainText(binding)
        
        // Test with highlighted text
        testHighlightedText(binding)
    }
    
    private fun testPlainText(binding: ItemSearchResultBinding) {
        Log.d("ColorTest", "=== TESTING PLAIN TEXT ===")
        
        binding.searchItemTitle.text = "Test Title"
        binding.searchItemSnippet.text = "Test snippet text"
        
        // Apply the same font as in adapter
        binding.searchItemTitle.applyAlegreyaHeadline()
        
        logColors("Plain", binding.searchItemTitle, binding.searchItemSnippet)
    }
    
    private fun testHighlightedText(binding: ItemSearchResultBinding) {
        Log.d("ColorTest", "=== TESTING HIGHLIGHTED TEXT ===")
        
        // This would simulate what the adapter does with SpannableString
        // For now, just log the state
        
        logColors("Highlighted", binding.searchItemTitle, binding.searchItemSnippet)
    }
    
    private fun logColors(scenario: String, titleView: TextView, snippetView: TextView) {
        val titleColor = titleView.currentTextColor
        val snippetColor = snippetView.currentTextColor
        
        Log.d("ColorTest", "$scenario - Title color: #${Integer.toHexString(titleColor)}")
        Log.d("ColorTest", "$scenario - Snippet color: #${Integer.toHexString(snippetColor)}")
        
        if (titleColor != snippetColor) {
            Log.e("ColorTest", "🔴 MISMATCH in $scenario!")
            Log.e("ColorTest", "   Difference: ${titleColor - snippetColor}")
        } else {
            Log.d("ColorTest", "✅ Colors match in $scenario")
        }
    }
}

// Extension function to match what's in the adapter
fun TextView.applyAlegreyaHeadline() {
    // This would call the actual FontUtil method
    // For testing, we'll just log
    Log.d("ColorTest", "Applied Alegreya font to TextView")
}