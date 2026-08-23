package com.omiyawaki.osrswiki.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StringUtilTest {
    @Test
    fun extractMainTitleDecodesEntitiesInsideMediaWikiTitleMarkup() {
        assertEquals(
            "Wyrmscraig & Sailing Changes",
            StringUtil.extractMainTitle(
                "<span class=\"mw-page-title-main\">Wyrmscraig &amp; Sailing Changes</span>"
            )
        )
    }

    @Test
    fun extractMainTitleDecodesPlainStoredHistoryTitle() {
        assertEquals(
            "Wyrmscraig & Sailing Changes",
            StringUtil.extractMainTitle("Wyrmscraig &amp; Sailing Changes")
        )
    }

    @Test
    fun extractMainTitleDecodesDoubleEncodedFeedTitle() {
        assertEquals(
            "Wyrmscraig & Sailing Changes",
            StringUtil.extractMainTitle("Wyrmscraig &amp;amp; Sailing Changes")
        )
    }

    @Test
    fun fixedPointDisplayNormalizationDecodesNestedHistoryMarkup() {
        assertEquals(
            "Wyrmscraig & Sailing Changes",
            StringUtil.decodeHtmlToFixedPoint("Wyrmscraig &amp;amp; <b>Sailing Changes</b>")
        )
    }

    @Test
    fun extractMainTitlePreservesCalculatorNamespaceFromMediaWikiMarkup() {
        assertEquals(
            "Calculator:Agility/Agility arena tickets",
            StringUtil.extractMainTitle(
                """<span class="mw-page-title-namespace">Calculator</span>""" +
                    """<span class="mw-page-title-separator">:</span>""" +
                    """<span class="mw-page-title-main">Agility/Agility arena tickets</span>"""
            )
        )
    }

    @Test
    fun extractMainTitleKeepsPlainCalculatorTitle() {
        assertEquals(
            "Calculator:Agility/Agility arena tickets",
            StringUtil.extractMainTitle("Calculator:Agility/Agility arena tickets")
        )
    }
}

