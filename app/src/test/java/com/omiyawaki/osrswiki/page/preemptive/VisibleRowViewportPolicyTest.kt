package com.omiyawaki.osrswiki.page.preemptive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleRowViewportPolicyTest {
    @Test
    fun attachedPrefetchChildOutsideViewportIsNotVisible() {
        assertFalse(
            VisibleRowViewportPolicy.intersectsViewport(
                isShown = true,
                viewportLeft = 0,
                viewportTop = 100,
                viewportRight = 1080,
                viewportBottom = 1800,
                childLeft = 0,
                childTop = 1850,
                childRight = 1080,
                childBottom = 2050
            )
        )
    }

    @Test
    fun partiallyClippedActualRowCountsAsVisibleButHiddenRowDoesNot() {
        assertTrue(
            VisibleRowViewportPolicy.intersectsViewport(
                true, 0, 100, 1080, 1800,
                0, 1750, 1080, 1900
            )
        )
        assertFalse(
            VisibleRowViewportPolicy.intersectsViewport(
                false, 0, 100, 1080, 1800,
                0, 1750, 1080, 1900
            )
        )
    }

    @Test
    fun groupedHomeFixtureSelectsTwoVisibleChildrenAndSuppressesAttachedOffscreenChild() {
        data class Child(val index: Int, val left: Int, val right: Int)
        val children = listOf(
            Child(0, 0, 320),
            Child(1, 320, 640),
            Child(2, 1_100, 1_420)
        )

        val visible = children.filter { child ->
            VisibleRowViewportPolicy.intersectsViewport(
                isShown = true,
                viewportLeft = 0,
                viewportTop = 0,
                viewportRight = 1_080,
                viewportBottom = 400,
                childLeft = child.left,
                childTop = 0,
                childRight = child.right,
                childBottom = 300
            )
        }.map(Child::index)

        assertEquals(listOf(0, 1), visible)
    }
}
