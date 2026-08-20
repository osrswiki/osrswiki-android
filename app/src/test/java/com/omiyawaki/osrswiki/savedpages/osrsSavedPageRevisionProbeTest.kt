package com.omiyawaki.osrswiki.savedpages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class osrsSavedPageRevisionProbeTest {
    @Test
    fun queryUrlAsksOnlyForRevisionIds() {
        val url = osrsSavedPageRevisionProbe.queryUrl("Abyssal whip")
        assertTrue(url.contains("action=query"))
        assertTrue(url.contains("prop=revisions"))
        assertTrue(url.contains("rvprop=ids"))
        assertTrue(url.contains("titles=Abyssal+whip") || url.contains("titles=Abyssal%20whip"))
        assertFalse(url.contains("action=parse"))
    }

    @Test
    fun remoteRevisionReadsMediawikiQueryPayload() {
        val json = """
            {"query":{"pages":[{"title":"Varrock","revisions":[{"revid":12345}]}]}}
        """.trimIndent()
        val remote = osrsSavedPageRevisionProbe.remoteRevision(json, "Varrock")
        assertEquals("Varrock", remote?.pageTitle)
        assertEquals(12345L, remote?.revisionId)
    }

    @Test
    fun remoteRevisionIgnoresMissingPages() {
        val json = """
            {"query":{"pages":[{"title":"Missing page","missing":true}]}}
        """.trimIndent()
        assertNull(osrsSavedPageRevisionProbe.remoteRevision(json, "Missing page"))
    }

    @Test
    fun snapshotNeedsRefreshWhenLocalRevisionIsUnknownOrStale() {
        assertTrue(osrsSavedPageRevisionProbe.snapshotNeedsRefresh(null, 10L))
        assertTrue(osrsSavedPageRevisionProbe.snapshotNeedsRefresh(0L, 10L))
        assertTrue(osrsSavedPageRevisionProbe.snapshotNeedsRefresh(9L, 10L))
        assertFalse(osrsSavedPageRevisionProbe.snapshotNeedsRefresh(10L, 10L))
    }
}
