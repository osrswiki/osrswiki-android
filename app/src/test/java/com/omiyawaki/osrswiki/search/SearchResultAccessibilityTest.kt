package com.omiyawaki.osrswiki.search

import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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

    @Test
    fun titlePrefixAndPreviewUseDifferentHighlightPolicies() {
        val binding = inflatedBinding()
        val holder = SearchAdapter.SearchResultViewHolder(
            binding = binding,
            listener = object : SearchAdapter.OnItemClickListener {
                override fun onItemClick(item: CleanedSearchResultItem) = Unit
            }
        )

        holder.bind(
            item = CleanedSearchResultItem(
                id = "2",
                title = "Barbarian Village",
                snippet = "The barbarian village has anvils.",
                thumbnailUrl = null
            ),
            searchQuery = "barbarian v"
        )

        val title = binding.searchItemTitle.text as Spanned
        val titleRanges = title.getSpans(0, title.length, ForegroundColorSpan::class.java)
            .map { title.getSpanStart(it) to title.getSpanEnd(it) }
        assertEquals(listOf(0 to 11), titleRanges)

        val snippet = binding.searchItemSnippet.text as Spanned
        val snippetRanges = snippet.getSpans(0, snippet.length, ForegroundColorSpan::class.java)
            .map { snippet.getSpanStart(it) to snippet.getSpanEnd(it) }
        assertEquals(listOf(4 to 13), snippetRanges)
    }

    @Test
    fun boundResultUsesDecodedDisplayTitle() {
        val binding = inflatedBinding()
        val holder = SearchAdapter.SearchResultViewHolder(
            binding = binding,
            listener = object : SearchAdapter.OnItemClickListener {
                override fun onItemClick(item: CleanedSearchResultItem) = Unit
            }
        )

        holder.bind(
            item = CleanedSearchResultItem(
                id = "3",
                title = "Wyrmscraig &amp;amp; Sailing Changes",
                snippet = "Latest update",
                thumbnailUrl = null
            ),
            searchQuery = null
        )

        assertEquals("Wyrmscraig & Sailing Changes", binding.searchItemTitle.text.toString())
        assertEquals(
            "Wyrmscraig & Sailing Changes. Latest update. Opens article.",
            binding.root.contentDescription.toString()
        )
    }

    @Test
    fun emptySnippetReservesTwoLineHeightSoEnrichDoesNotGrowTheRow() {
        val binding = inflatedBinding()
        val holder = SearchAdapter.SearchResultViewHolder(
            binding = binding,
            listener = object : SearchAdapter.OnItemClickListener {
                override fun onItemClick(item: CleanedSearchResultItem) = Unit
            }
        )
        val titleOnly = CleanedSearchResultItem(
            id = "11",
            title = "Update:Blank intro",
            snippet = "",
            thumbnailUrl = null
        )
        holder.bind(titleOnly, searchQuery = null)
        assertEquals(View.VISIBLE, binding.searchItemSnippet.visibility)
        assertTrue(binding.searchItemSnippet.minLines >= 2)
        assertEquals(2, binding.searchItemSnippet.maxLines)
        val emptyHeight = measuredRowHeight(binding)

        holder.bind(
            titleOnly.copy(snippet = "Diango is giving out hats in Draynor Village this week."),
            searchQuery = null
        )
        assertEquals(View.VISIBLE, binding.searchItemSnippet.visibility)
        assertEquals(emptyHeight, measuredRowHeight(binding))
    }

    @Test
    fun longTitleStaysOneLineAndEllipsizes() {
        val binding = inflatedBinding()
        val holder = SearchAdapter.SearchResultViewHolder(
            binding = binding,
            listener = object : SearchAdapter.OnItemClickListener {
                override fun onItemClick(item: CleanedSearchResultItem) = Unit
            }
        )
        holder.bind(
            item = CleanedSearchResultItem(
                id = "19",
                title = "The Official OSRS Podcast Episode 19 With Mod Archie",
                snippet = "Today might be his birthday.",
                thumbnailUrl = null
            ),
            searchQuery = null
        )
        assertEquals(1, binding.searchItemTitle.maxLines)
        assertEquals(android.text.TextUtils.TruncateAt.END, binding.searchItemTitle.ellipsize)
    }

    private fun measuredRowHeight(binding: ItemSearchResultBinding): Int {
        val width = android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY)
        val height = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        binding.root.measure(width, height)
        return binding.root.measuredHeight
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
