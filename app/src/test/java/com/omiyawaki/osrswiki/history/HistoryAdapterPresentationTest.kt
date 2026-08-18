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

    @Test
    fun historyUpsertKeepsExistingSnippetWhenALaterVisitOmitsIt() {
        val existing = historyEntry(
            displayText = "Money making guide",
            snippet = "There are many ways to make money in Old School RuneScape.",
            thumbnailUrl = "https://example.test/thumb.png"
        )
        val revisit = historyEntry(
            displayText = "Money making guide",
            snippet = null,
            thumbnailUrl = null
        )

        revisit.preserveExistingMetadata(existing)

        assertEquals("There are many ways to make money in Old School RuneScape.", revisit.snippet)
        assertEquals("https://example.test/thumb.png", revisit.thumbnailUrl)
        assertEquals(1, revisit.pageId)
    }

    @Test
    fun historyBackfillFillsBlankSnippetAndThumbnailFromPreviewMetadata() {
        val entry = historyEntry(
            displayText = "Falador",
            snippet = null,
            thumbnailUrl = null
        )

        assertTrue(HistoryMetadataBackfill.needsEnrichment(entry))
        assertEquals("falador", HistoryMetadataBackfill.matchKey("Falador_"))
        assertEquals(
            "money making guide",
            HistoryMetadataBackfill.matchKey("""<span class="mw-page-title-main">Money making guide</span>""")
        )
        val htmlTitle = historyEntry(
            displayText = """<span class="mw-page-title-main">Varrock Teleport</span>""",
            snippet = null,
            thumbnailUrl = null
        ).copy(apiPath = "Varrock_Teleport")
        assertEquals("Varrock Teleport", HistoryMetadataBackfill.previewTitle(htmlTitle))
        assertTrue(
            HistoryMetadataBackfill.apply(
                entry,
                extract = "  Falador is a walled city.  ",
                thumbnailUrl = "https://example.test/falador.png"
            )
        )
        assertEquals("Falador is a walled city.", entry.snippet)
        assertEquals("https://example.test/falador.png", entry.thumbnailUrl)
        assertFalse(HistoryMetadataBackfill.needsEnrichment(entry))
        assertFalse(
            HistoryMetadataBackfill.apply(
                entry,
                extract = "Replacement should not overwrite a stored snippet.",
                thumbnailUrl = "https://example.test/other.png"
            )
        )
        assertEquals("Falador is a walled city.", entry.snippet)
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
