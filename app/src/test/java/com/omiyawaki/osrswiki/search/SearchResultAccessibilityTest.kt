package com.omiyawaki.osrswiki.search

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SearchResultAccessibilityTest {

    @Test
    fun boundResultUsesSingleUsefulRowAccessibilityLabel() {
        val binding = inflatedBinding()
        val holder = SearchAdapter.SearchResultViewHolder(
            binding = binding,
            listener = object : SearchAdapter.OnItemClickListener {
                override fun onItemClick(item: CleanedSearchResultItem) = Unit
            }
        )

        holder.bind(
            item = CleanedSearchResultItem(
                id = "12158",
                title = "Clue scroll",
                snippet = "A <span class=\"searchmatch\">clue</span> scroll lists hints.",
                thumbnailUrl = "https://example.com/thumb.png"
            ),
            searchQuery = "Clue scroll"
        )

        assertEquals(
            "Clue scroll. A clue scroll lists hints. Opens article.",
            binding.root.contentDescription?.toString()
        )
        assertTrue(binding.root.isFocusable)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, binding.root.importantForAccessibility)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, binding.searchItemTitle.importantForAccessibility)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, binding.searchItemSnippet.importantForAccessibility)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, binding.searchItemThumbnail.importantForAccessibility)
        assertNull(binding.searchItemThumbnail.contentDescription)
    }

    private fun inflatedBinding(): ItemSearchResultBinding {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_OSRSWiki_OSRSDark
        )
        val parent = FrameLayout(context)
        return ItemSearchResultBinding.inflate(
            LayoutInflater.from(context),
            parent,
            false
        )
    }
}
