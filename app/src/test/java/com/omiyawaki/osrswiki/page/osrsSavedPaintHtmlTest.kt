package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsSavedPaintHtmlTest {
    @Test
    fun detectsFullDocumentAndExtractsBodyWithoutPageHeader() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Varrock</title></head>
            <body class="">
            <h1 class="page-header">Varrock</h1>
            <p id="history">The history section.</p>
            </body>
            </html>
        """.trimIndent()
        assertTrue(osrsSavedPaintHtml.isFullDocument(html))
        val body = osrsSavedPaintHtml.extractBodyForToc(html)
        assertFalse(body.contains("page-header"))
        assertTrue(body.contains("The history section."))
        assertFalse(osrsSavedPaintHtml.isFullDocument("<p>body only</p>"))
    }

    @Test
    fun appliesLiveThemeWrapScaleAndInlinesStylesheets() {
        val html = """
            <html class=""><head>
            <link rel="stylesheet" href="https://appassets.androidplatform.net/assets/styles/themes.css">
            <style id="osrs-reader-text-scale-style">:root { --osrs-article-user-text-scale: 1.000; }</style>
            </head><body class="">hello</body>
        """.trimIndent()
        val inlined = osrsSavedPaintHtml.inlineLinkedFirstPaintCss(html) { path ->
            assertEquals("styles/themes.css", path)
            "body{color:red}"
        }
        assertTrue(inlined.contains("data-osrs-inline-css=\"styles/themes.css\""))
        assertTrue(inlined.contains("body{color:red}"))
        assertFalse(inlined.contains("appassets.androidplatform.net/assets/styles/themes.css"))

        val live = osrsSavedPaintHtml.applyingLivePreferences(
            inlined,
            isDark = true,
            wrapEnabled = true,
            scaleCssValue = "1.15",
            bottomChromePx = 96
        )
        assertTrue(live.contains("theme-osrs-dark"))
        assertTrue(live.contains("osrs-table-cells-wrap"))
        assertTrue(live.contains("--osrs-article-user-text-scale: 1.15"))
        assertTrue(live.contains("osrs-article-live-chrome"))
        assertTrue(live.contains("--osrs-article-bottom-chrome: 96px"))
    }
}
