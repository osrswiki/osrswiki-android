package com.omiyawaki.osrswiki.savedpages

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingListAssetExtractorAndroidRuntimeTest {
    @Test
    fun androidRegexRuntimeSkipsFontFaceAndKeepsArtwork() {
        val stylesheetUrl = "https://oldschool.runescape.wiki/css/article.css"
        val css = """
            @font-face {
                font-family: 'Article UI';
                src: url('../fonts/article-ui.woff2') format('woff2');
            }
            .panel { background-image: url('../images/panel.png'); }
        """.trimIndent()

        assertEquals(
            listOf("https://oldschool.runescape.wiki/images/panel.png"),
            ReadingListAssetUrlExtractor.extractCss(css, stylesheetUrl)
        )
    }
}
