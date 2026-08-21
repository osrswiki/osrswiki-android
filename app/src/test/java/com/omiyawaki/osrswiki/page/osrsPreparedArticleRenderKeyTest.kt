package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmRequest
import com.omiyawaki.osrswiki.theme.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsPreparedArticleRenderKeyTest {
    @Test
    fun themeAndReaderOptionsArePartOfIdentity() {
        val request = ArticlePrewarmRequest(pageId = 123, title = "Amulet of glory")
        val light = osrsPreparedArticleRenderKey.from(
            request,
            Theme.OSRS_LIGHT,
            collapseTables = false,
            wrapTableCells = false,
            readerTextScale = 1.0f
        )
        val dark = osrsPreparedArticleRenderKey.from(
            request,
            Theme.OSRS_DARK,
            collapseTables = false,
            wrapTableCells = false,
            readerTextScale = 1.0f
        )
        val collapsed = osrsPreparedArticleRenderKey.from(
            request,
            Theme.OSRS_LIGHT,
            collapseTables = true,
            wrapTableCells = false,
            readerTextScale = 1.0f
        )
        assertFalse(light == dark)
        assertFalse(light == collapsed)
        assertTrue(light.matchesPage(request))
        assertTrue(dark.matchesPage(ArticlePrewarmRequest(title = "Amulet of glory")))
        assertEquals("Amulet of glory", light.normalizedTitle)
    }
}
