package com.omiyawaki.osrswiki.search

import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchColorTest {
    
    @Test
    fun testSearchResultColors() {
        val context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
            R.style.Theme_OSRSWiki_OSRSLight
        )
        
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
        
        val onItemClickListener = object : SearchAdapter.OnItemClickListener {
            override fun onItemClick(item: CleanedSearchResultItem) {}
        }
        val viewHolder = SearchAdapter.SearchResultViewHolder(binding, onItemClickListener)
        
        viewHolder.bind(testItem, null)
        val titleColorNoQuery = binding.searchItemTitle.currentTextColor
        val snippetColorNoQuery = binding.searchItemSnippet.currentTextColor
        
        viewHolder.bind(testItem, "dragon")
        val titleColorWithQuery = binding.searchItemTitle.currentTextColor
        val snippetColorWithQuery = binding.searchItemSnippet.currentTextColor
        
        assertEquals(titleColorNoQuery, snippetColorNoQuery)
        assertEquals(titleColorWithQuery, snippetColorWithQuery)
    }
}
