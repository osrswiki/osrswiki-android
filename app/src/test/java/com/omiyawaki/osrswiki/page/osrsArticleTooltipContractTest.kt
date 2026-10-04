package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class osrsArticleTooltipContractTest {
    @Test
    fun originalTooltipScriptIsWiredAndDoesNotLoadWikiGadgets() {
        val script = asset("web/osrs_article_tooltips.js")
        val builder = source("page/PageHtmlBuilder.kt")

        assertTrue(script.contains("js-tooltip-click"))
        assertTrue(script.contains("data-tooltip-name"))
        assertTrue(script.contains("data-tooltip-for"))
        assertTrue(script.contains("js-tooltip-wrapper"))
        assertFalse(script.contains("ext.gadget."))
        assertFalse(script.contains("MediaWiki:Gadget"))
        assertTrue(builder.contains("web/osrs_article_tooltips.js"))
    }

    private fun asset(path: String): String = assetFile(path).readText()

    private fun source(path: String): String {
        val file = File("src/main/java/com/omiyawaki/osrswiki", path).takeIf { it.exists() }
            ?: File("app/src/main/java/com/omiyawaki/osrswiki", path)
        return file.readText()
    }

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets", path),
        File("app/src/main/assets", path)
    ).firstOrNull { it.exists() } ?: error("Missing Android asset: $path")
}
