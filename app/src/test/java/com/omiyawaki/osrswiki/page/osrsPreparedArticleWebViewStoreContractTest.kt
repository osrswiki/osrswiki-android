package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class osrsPreparedArticleWebViewStoreContractTest {
    @Test
    fun prewarmHostsDoNotCompositeIntoTabUi() {
        val source = File("src/main/java/com/omiyawaki/osrswiki/page/osrsPreparedArticleWebViewStore.kt").readText()
        assertTrue(source.contains("PREWARM_COMPOSITE_ALPHA = 0f"))
        assertTrue(source.contains("PREWARM_OFFSCREEN_TRANSLATION_PX"))
        assertTrue(source.contains("stashOffscreenFromTabUi()"))
        assertTrue(source.contains("content.addView(host, 0)"))
        assertFalse(source.contains("alpha = 0.01f"))
        assertFalse(source.contains("?.addView(host)"))
    }
}
