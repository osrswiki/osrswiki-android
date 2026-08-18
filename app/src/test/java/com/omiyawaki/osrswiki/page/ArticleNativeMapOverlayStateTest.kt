package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleNativeMapOverlayStateTest {
    @Test
    fun openingBeforeMeasurementIsRetainedUntilOverlayCanBeCreated() {
        val state = ArticleNativeMapOverlayState()
        state.recordDesiredVisibility("edgeville", true)

        val measured = state.recordMeasurement(
            id = "edgeville",
            bounds = ArticleNativeMapBounds(top = 120f, start = 16f, width = 240f, height = 132f),
            initiallyVisible = false
        )

        assertTrue(measured.desiredVisible == true)
        assertEquals(120f, measured.bounds?.top)
    }

    @Test
    fun fourTeleportationMapsKeepIndependentLatestBoundsAcrossCollapseAndReopen() {
        val state = ArticleNativeMapOverlayState()
        val ids = listOf("edgeville", "karamja", "draynor", "al-kharid")

        ids.forEachIndexed { index, id ->
            state.recordMeasurement(
                id,
                ArticleNativeMapBounds(
                    top = 200f + index * 160f,
                    start = 144f,
                    width = 168f,
                    height = 132f
                ),
                initiallyVisible = true
            )
        }
        ids.forEach { state.recordDesiredVisibility(it, false) }

        ids.forEachIndexed { index, id ->
            state.recordMeasurement(
                id,
                ArticleNativeMapBounds(
                    top = 260f + index * 174f,
                    start = 151f + index,
                    width = 164f + index,
                    height = 136f + index
                ),
                initiallyVisible = true
            )
        }

        ids.forEach { id ->
            assertFalse(state.record(id)?.desiredVisible == true)
            state.recordDesiredVisibility(id, true)
        }

        ids.forEachIndexed { index, id ->
            val record = state.record(id)!!
            assertTrue(record.desiredVisible == true)
            assertEquals(260f + index * 174f, record.bounds?.top)
            assertEquals(151f + index, record.bounds?.start)
            assertEquals(164f + index, record.bounds?.width)
            assertEquals(136f + index, record.bounds?.height)
        }
    }
}
