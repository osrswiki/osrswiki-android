package com.omiyawaki.osrswiki.ui.map

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapPrototypeOverviewAccessibilityTest {
    @Test
    fun nodeExposesClickAndFourDirectionalActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = osrsMapPrototypeOverviewView(context)
        view.configure(
            mapProvider = { null },
            onInteractionStarted = {},
            onInteractionFinished = {},
            onCenterRequested = { _, _ -> }
        )
        val node = AccessibilityNodeInfo.obtain()
        view.onInitializeAccessibilityNodeInfo(node)
        val ids = node.actionList.map { it.id }.toSet()

        assertTrue(view.isClickable)
        assertTrue(view.isFocusable)
        assertTrue(view.contentDescription.toString().contains("directional scroll actions"))
        assertTrue(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id in ids)
        assertTrue(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id in ids)
        assertTrue(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id in ids)
        assertTrue(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id in ids)
        assertTrue(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id in ids)
        node.recycle()
    }

    @Test
    fun clickAndDirectionalActionsPerformRealNavigationCallbacks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val centers = mutableListOf<Pair<Double, Double>>()
        var starts = 0
        var finishes = 0
        val view = osrsMapPrototypeOverviewView(context)
        view.configure(
            mapProvider = { null },
            onInteractionStarted = { starts++ },
            onInteractionFinished = { finishes++ },
            onCenterRequested = { x, y -> centers += x to y }
        )

        assertTrue(
            view.performAccessibilityAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id,
                null
            )
        )
        assertTrue(
            view.performAccessibilityAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id,
                null
            )
        )

        assertEquals(2, centers.size)
        assertEquals(2, starts)
        assertEquals(2, finishes)
        assertTrue("right action must change longitude/game X", centers[1].first > centers[0].first)
    }
}
