package com.omiyawaki.osrswiki.page

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsArticleInteractiveSwipeTest {

    @Test
    fun verticalMoveDisqualifiesAndDoesNotTrack() {
        val swipe = osrsArticleInteractiveSwipe(touchSlop = 16)
        assertNull(swipe.onMove(4f, 8f))
        assertNull(swipe.onMove(20f, 80f))
        assertTrue(swipe.locked)
        assertFalse(swipe.isTracking)
        assertNull(swipe.axis)
    }

    @Test
    fun stronglyHorizontalMoveLocksBackAndReportsProgress() {
        val swipe = osrsArticleInteractiveSwipe(touchSlop = 16)
        assertEquals(osrsArticleInteractiveSwipe.Axis.BACK, swipe.onMove(80f, 8f))
        assertTrue(swipe.isTracking)
        assertEquals(0.4f, swipe.progress(80f, 200f), 0.001f)
        assertTrue(swipe.shouldCommit(80f, 0f, 200f))
        assertEquals(Gravity.START, swipe.gravity())
    }

    @Test
    fun stronglyHorizontalMoveLocksContents() {
        val swipe = osrsArticleInteractiveSwipe(touchSlop = 16)
        assertEquals(osrsArticleInteractiveSwipe.Axis.CONTENTS, swipe.onMove(-90f, 10f))
        assertEquals(Gravity.END, swipe.gravity())
        assertFalse(swipe.shouldCommit(-40f, 0f, 280f))
        assertTrue(swipe.shouldCommit(-120f, 0f, 280f))
        assertTrue(swipe.shouldCommit(-40f, -900f, 280f))
    }

    @Test
    fun contentsPeekPlacesClosedEndDrawerOntoTheScreen() {
        assertEquals(280f, osrsArticleInteractiveSwipe.contentsPeekTranslationX(280f, 0f), 0.001f)
        assertEquals(0f, osrsArticleInteractiveSwipe.contentsPeekTranslationX(280f, 1f), 0.001f)
        assertEquals(140f, osrsArticleInteractiveSwipe.contentsPeekTranslationX(280f, 0.5f), 0.001f)
    }

    @Test
    fun diagonalBelowDominanceDoesNotBecomeASwipe() {
        val swipe = osrsArticleInteractiveSwipe(touchSlop = 8)
        assertNull(swipe.onMove(30f, 20f))
        assertTrue(swipe.locked)
        assertFalse(swipe.isTracking)
    }

    @Test
    fun openContentsRightSwipeDismissesInsteadOfStartingBack() {
        val swipe = osrsArticleInteractiveSwipe(touchSlop = 16)
        assertEquals(
            osrsArticleInteractiveSwipe.Axis.CONTENTS,
            swipe.onMove(80f, 8f, contentsOpen = true)
        )
        assertEquals(0.714f, swipe.progress(80f, 280f), 0.01f)
        assertFalse(swipe.shouldCommit(80f, 0f, 280f))
        assertTrue(swipe.shouldCommit(120f, 0f, 280f))
        assertTrue(swipe.shouldCommit(40f, 900f, 280f))
        assertEquals(Gravity.END, swipe.gravity())
        assertTrue(swipe.contentsOpenAtStart)
    }

    @Test
    fun settleInterpolatorContinuesFromReleaseSpeedInsteadOfDumpingAtTheEnd() {
        val remaining = osrsArticleInteractiveSwipe.remainingPx(0.35f, 1080f)
        val duration = osrsArticleInteractiveSwipe.remainingCommitDurationMs(0.35f, 0f, 1080f, 1f)
        val interpolator = osrsArticleInteractiveSwipe.settleInterpolator(0f, remaining, duration)
        val early = interpolator.getInterpolation(0.12f)
        val mid = interpolator.getInterpolation(0.5f)
        assertTrue("A rest release must keep moving early instead of pausing. early=$early", early in 0.04f..0.28f)
        assertTrue("Half the settle must still be travelling, not already finished. mid=$mid", mid in 0.38f..0.82f)
        assertTrue(interpolator.getInterpolation(1f) >= 0.99f)
    }

    @Test
    fun remainingCommitDurationStaysContinuousInsteadOfSnapping() {
        val slow = osrsArticleInteractiveSwipe.remainingCommitDurationMs(0.35f, 200f, 1080f)
        val flicked = osrsArticleInteractiveSwipe.remainingCommitDurationMs(0.35f, 1800f, 1080f)
        val almostDone = osrsArticleInteractiveSwipe.remainingCommitDurationMs(0.92f, 200f, 1080f)
        assertTrue(
            "A slow release must still finish in the short native settle window. slow=$slow",
            slow <= osrsArticleInteractiveSwipe.SETTLE_MAX_DURATION_MS
        )
        assertTrue(slow >= osrsArticleInteractiveSwipe.SETTLE_MIN_DURATION_MS)
        assertTrue(flicked <= slow)
        assertTrue(almostDone < slow)
        assertTrue(almostDone >= osrsArticleInteractiveSwipe.SETTLE_MIN_DURATION_MS)
    }

    @Test
    fun programmaticContentsToggleUsesAShortDrawerDuration() {
        assertEquals(
            osrsArticleInteractiveSwipe.CONTENTS_PROGRAMMATIC_DURATION_MS,
            osrsArticleInteractiveSwipe.contentsToggleDurationMs(0f, 280f, 1f)
        )
        assertTrue(
            osrsArticleInteractiveSwipe.contentsToggleDurationMs(0f, 280f, 1f) <
                osrsArticleInteractiveSwipe.remainingCommitDurationMs(0f, 0f, 280f, 1f)
        )
    }

    @Test
    fun systemBackEdgesAreTheInsetBandsOnly() {
        assertFalse(osrsArticleSystemBackEdge.contains(90f, 1080f, 48, 48))
        assertTrue(osrsArticleSystemBackEdge.contains(20f, 1080f, 48, 48))
        assertTrue(osrsArticleSystemBackEdge.contains(1060f, 1080f, 48, 48))
        assertFalse(osrsArticleSystemBackEdge.contains(20f, 1080f, 0, 0))
    }

    @Test
    fun openContentsLeftSwipeKeepsDrawerFullyOpen() {
        val swipe = osrsArticleInteractiveSwipe(touchSlop = 16)
        assertEquals(
            osrsArticleInteractiveSwipe.Axis.CONTENTS,
            swipe.onMove(-90f, 10f, contentsOpen = true)
        )
        assertEquals(1f, swipe.progress(-90f, 280f), 0.001f)
        assertFalse(swipe.shouldCommit(-90f, -900f, 280f))
    }
}
