package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTextScaleInjectionTest {

    @Test
    fun bootstrapUsesTheSharedArticleScalarContract() {
        val bootstrap = PageHtmlBuilder.readerTextScaleBootstrap(1.25f)

        assertTrue(bootstrap.contains("--osrs-article-user-text-scale: 1.25"))
        assertTrue(bootstrap.contains("setProperty('--osrs-article-user-text-scale', '1.25')"))
        assertFalse(bootstrap.contains("--osrs-reader-text-scale"))
    }

    @Test
    fun bootstrapAndRuntimeValuesClampToReaderBounds() {
        assertTrue(PageHtmlBuilder.readerTextScaleBootstrap(0.10f).contains("0.85"))
        assertTrue(PageHtmlBuilder.readerTextScaleRuntimeScript(9.0f).contains("1.40"))
        assertTrue(PageHtmlBuilder.readerTextScaleRuntimeScript(Float.NaN).contains("1.00"))
    }

    @Test
    fun runtimeScriptUpdatesCachedHtmlAndRemainsBalanced() {
        val script = PageHtmlBuilder.readerTextScaleRuntimeScript(1.10f)

        assertTrue(script.contains("document.getElementById('osrs-reader-text-scale-style')"))
        assertTrue(script.contains("document.documentElement.style.setProperty"))
        assertEquals(1, Regex("\\(function\\(\\) \\{").findAll(script).count())
        assertEquals(1, Regex("\\}\\)\\(\\);").findAll(script).count())
        assertEquals(1, Regex("if \\(!style\\) \\{").findAll(script).count())
        assertFalse(script.contains("if (!style) {\n                    if (!style)"))
    }
}
