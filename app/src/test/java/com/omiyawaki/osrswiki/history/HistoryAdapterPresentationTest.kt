package com.omiyawaki.osrswiki.history

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ItemHistoryEntryRichBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HistoryAdapterPresentationTest {
    @Test
    fun historyRowDecodesNestedTitleAndSnippetEntities() {
        val binding = inflatedBinding()
        val adapter = HistoryAdapter(onItemClick = {})
        adapter.EntryViewHolder(binding).bind(
            historyEntry(
                displayText = "Wyrmscraig &amp;amp; Sailing Changes",
                snippet = "Latest &amp;amp; <b>greatest</b> update",
                thumbnailUrl = null
            )
        )

        assertEquals("Wyrmscraig & Sailing Changes", binding.pageTitleText.text.toString())
        assertEquals("Latest & greatest update", binding.pageSnippetText.text.toString())
        assertEquals(View.VISIBLE, binding.pageSnippetText.visibility)
        assertEquals(View.GONE, binding.pageThumbnail.visibility)
    }

    @Test
    fun historyThumbnailContractKeepsAnimatedDrawableLoadingEnabled() {
        val source = File("src/main/java/com/omiyawaki/osrswiki/history/HistoryAdapter.kt").readText()

        assertTrue(source.contains(".load(historyEntry.thumbnailUrl)"))
        assertTrue(source.contains("pageThumbnail.visibility = View.VISIBLE"))
        assertFalse("asBitmap would freeze GIF thumbnails", source.contains(".asBitmap()"))
    }

    @Test
    fun markupOnlyHistorySnippetDoesNotReserveAnEmptyLine() {
        val binding = inflatedBinding()
        val adapter = HistoryAdapter(onItemClick = {})
        adapter.EntryViewHolder(binding).bind(
            historyEntry(
                displayText = "Amulet",
                snippet = "<br>",
                thumbnailUrl = null
            )
        )

        assertEquals(View.GONE, binding.pageSnippetText.visibility)
        assertEquals("", binding.pageSnippetText.text.toString())
    }

    private fun inflatedBinding(): ItemHistoryEntryRichBinding {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_OSRSWiki_OSRSDark
        )
        return ItemHistoryEntryRichBinding.inflate(
            LayoutInflater.from(context),
            FrameLayout(context),
            false
        )
    }

    private fun historyEntry(
        displayText: String,
        snippet: String?,
        thumbnailUrl: String?
    ) = HistoryEntry(
        wikiUrl = "/w/Wyrmscraig",
        displayText = displayText,
        pageId = 1,
        apiPath = "Wyrmscraig",
        source = HistoryEntry.SOURCE_NEWS,
        snippet = snippet,
        thumbnailUrl = thumbnailUrl
    )
}
