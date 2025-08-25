package com.omiyawaki.osrswiki.search

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SearchColorUnitTest {
    
    @Test
    fun testTextColors() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Create views
        val inflater = LayoutInflater.from(context)
        val binding = ItemSearchResultBinding.inflate(inflater)
        
        // Set text
        binding.searchItemTitle.text = "Test Title"
        binding.searchItemSnippet.text = "Test Snippet"
        
        // Get colors
        val titleColor = binding.searchItemTitle.currentTextColor
        val snippetColor = binding.searchItemSnippet.currentTextColor
        
        println("Title color: #${Integer.toHexString(titleColor)}")
        println("Snippet color: #${Integer.toHexString(snippetColor)}")
        
        // Check theme attribute
        val typedValue = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        println("colorOnSurface: #${Integer.toHexString(typedValue.data)}")
        
        // Check if they match
        assert(titleColor == snippetColor) { 
            "Colors don't match! Title: #${Integer.toHexString(titleColor)}, Snippet: #${Integer.toHexString(snippetColor)}"
        }
    }
}