package com.omiyawaki.osrswiki.search

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchColorTest {
    
    @Test
    fun testSearchResultColors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Create binding
        val binding = ItemSearchResultBinding.inflate(
            android.view.LayoutInflater.from(context)
        )
        
        // Create test data
        val testItem = CleanedSearchResultItem(
            id = "1",
            title = "Test Dragon Item",
            snippet = "This is a test snippet about dragons and other things",
            thumbnailUrl = null
        )
        
        // Create adapter
        val onItemClickListener = object : SearchAdapter.OnItemClickListener {
            override fun onItemClick(item: CleanedSearchResultItem) {}
        }
        val adapter = SearchAdapter(onItemClickListener)
        
        // Create view holder
        val viewHolder = SearchAdapter.SearchResultViewHolder(binding, onItemClickListener)
        
        // Test without search query
        Log.d("TEST", "=== Testing without search query ===")
        viewHolder.bind(testItem, null)
        val titleColorNoQuery = binding.searchItemTitle.currentTextColor
        val snippetColorNoQuery = binding.searchItemSnippet.currentTextColor
        Log.d("TEST", "No query - Title: #${Integer.toHexString(titleColorNoQuery)}")
        Log.d("TEST", "No query - Snippet: #${Integer.toHexString(snippetColorNoQuery)}")
        
        // Test with search query
        Log.d("TEST", "=== Testing with search query ===")
        viewHolder.bind(testItem, "dragon")
        val titleColorWithQuery = binding.searchItemTitle.currentTextColor
        val snippetColorWithQuery = binding.searchItemSnippet.currentTextColor
        Log.d("TEST", "With query - Title: #${Integer.toHexString(titleColorWithQuery)}")
        Log.d("TEST", "With query - Snippet: #${Integer.toHexString(snippetColorWithQuery)}")
        
        // Check if colors match
        if (titleColorNoQuery != snippetColorNoQuery) {
            Log.e("TEST", "🔴 MISMATCH without query!")
        }
        if (titleColorWithQuery != snippetColorWithQuery) {
            Log.e("TEST", "🔴 MISMATCH with query!")
        }
    }
}