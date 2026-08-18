package com.omiyawaki.osrswiki.page

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SrcsetParserTest {
    @Test
    fun dataUriCommaRemainsInsideOneCandidateAndRelativeArtworkStillRewrites() {
        val srcset =
            "data:image/svg+xml,%3Csvg%3E,%3C/svg%3E 1x, /images/real-art.png 2x"

        assertEquals(
            listOf("data:image/svg+xml,%3Csvg%3E,%3C/svg%3E", "/images/real-art.png"),
            SrcsetParser.urls(srcset)
        )
        assertEquals(
            "data:image/svg+xml,%3Csvg%3E,%3C/svg%3E 1x, " +
                "https://oldschool.runescape.wiki/images/real-art.png 2x",
            SrcsetParser.rewriteUrls(srcset) { url ->
                if (url.startsWith('/')) "https://oldschool.runescape.wiki$url" else url
            }
        )
    }

    @Test
    fun articlePreprocessingUsesSharedTokenizerInsteadOfCommaSplit() {
        val source = File(
            "src/main/java/com/omiyawaki/osrswiki/page/PageAssetDownloader.kt"
        ).readText()
        val method = source.substringAfter("private fun makeSrcsetAbsolute")
            .substringBefore("private fun deferLargeArticleTableImages")

        assertTrue(method.contains("SrcsetParser.rewriteUrls"))
        assertFalse(method.contains("split(\",\")"))
    }
}
