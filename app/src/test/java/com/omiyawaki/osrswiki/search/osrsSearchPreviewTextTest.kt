package com.omiyawaki.osrswiki.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class osrsSearchPreviewTextTest {
    @Test
    fun skipsCopyrightBoilerplateAndUsesFirstContentParagraph() {
        val html = """
            <p>This official news post is copied verbatim from the Old School RuneScape website.</p>
            <p>The Crusader Classic event starts tomorrow in the Grand Exchange.</p>
        """.trimIndent()
        assertEquals(
            "The Crusader Classic event starts tomorrow in the Grand Exchange.",
            osrsSearchPreviewText.fromHtml(html)
        )
    }

    @Test
    fun plainExtractWithoutIntroStillYieldsPreview() {
        val extract = "  Mortimer has returned to Varrock after a long absence from the city.  "
        assertEquals(
            "Mortimer has returned to Varrock after a long absence from the city.",
            osrsSearchPreviewText.fromPlainExtract(extract)
        )
    }

    @Test
    fun emptyAndBoilerplateExtractsYieldNullUntilHtmlFallback() {
        assertNull(osrsSearchPreviewText.fromPlainExtract("This official news post is copied verbatim."))
        assertNull(osrsSearchPreviewText.fromPlainExtract("   "))
        val html = """<div class="infobox"></div><p>Players can now claim the reward from Diango.</p>"""
        assertEquals(
            "Players can now claim the reward from Diango.",
            osrsSearchPreviewText.fromHtml(html)
        )
    }

    @Test
    fun skipsImageFallbackChromeAndUsesNextParagraph() {
        val html = """
            <p>If you can't see the asset above, click here.</p>
            <p>We'll be adding regional servers for South Africa, Japan and Australia.</p>
        """.trimIndent()
        assertEquals(
            "We'll be adding regional servers for South Africa, Japan and Australia.",
            osrsSearchPreviewText.fromHtml(html)
        )
    }

    @Test
    fun tableOfContentsHeadingsAreNotUsableCandidates() {
        assertNull(osrsSearchPreviewText.fromCandidates("Contents", "Changelog", null))
        assertEquals(
            "The Grand Exchange now supports bulk offers.",
            osrsSearchPreviewText.fromHtml("<div>Contents</div><p>The Grand Exchange now supports bulk offers.</p>")
        )
    }

    @Test
    fun skipsWikiTocMarkupAndPrefersBodySentence() {
        val html = """
            <div id="toc" class="toc"><ul><li>1 Wyrmscraig</li><li>2 Access</li></ul></div>
            <h2>Wyrmscraig</h2>
            <p>Players can now reach Wyrmscraig from the eastern coast after finishing Fallen From Grace.</p>
        """.trimIndent()
        assertEquals(
            "Players can now reach Wyrmscraig from the eastern coast after finishing Fallen From Grace.",
            osrsSearchPreviewText.fromHtml(html)
        )
        assertNull(osrsSearchPreviewText.fromPlainExtract("1 Wyrmscraig 2 Access to Wyrmscraig 3 Fallen From Grace"))
        assertNull(
            osrsSearchPreviewText.fromPlainExtract(
                "1 Changelog - June 3rd 1.1 Gathering QoL Improvements 1.2 Sailing Changes"
            )
        )
    }

    @Test
    fun chromeOnlySnippetsAreNotUsableCandidates() {
        assertNull(osrsSearchPreviewText.fromCandidates("CLICK HERE TO SHOW THIS CONTENT", null))
        assertEquals(
            "The Grand Exchange now supports bulk offers.",
            osrsSearchPreviewText.fromCandidates(
                "CLICK HERE TO SHOW THIS CONTENT",
                "The Grand Exchange now supports bulk offers."
            )
        )
    }

    @Test
    fun divOnlyCopyrightPageStillYieldsLaterSentence() {
        val html = """
            <div>This official news post is copied verbatim from the Old School RuneScape website. It was added on 26 May 2026.</div>
            <div>Time to huddle around the campfire as we share some updates with you.</div>
        """.trimIndent()
        assertEquals(
            "Time to huddle around the campfire as we share some updates with you.",
            osrsSearchPreviewText.fromHtml(html)
        )
    }
}
