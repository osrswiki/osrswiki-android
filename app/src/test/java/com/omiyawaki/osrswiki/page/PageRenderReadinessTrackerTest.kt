package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRenderReadinessTrackerTest {

    @Test
    fun loadFinishedAloneDoesNotMarkPageReadyForDisplay() {
        val tracker = PageRenderReadinessTracker()

        assertFalse(tracker.onMainFrameLoadFinished())
        assertFalse(tracker.isReadyForDisplay)
    }

    @Test
    fun stylingCompleteAloneDoesNotMarkPageReadyForDisplay() {
        val tracker = PageRenderReadinessTracker()

        assertFalse(tracker.onStylingScriptsComplete())
        assertFalse(tracker.isReadyForDisplay)
    }

    @Test
    fun readySignalFiresOnceAfterLoadAndStylingAreBothComplete() {
        val tracker = PageRenderReadinessTracker()

        assertFalse(tracker.onMainFrameLoadFinished())
        assertTrue(tracker.onStylingScriptsComplete())
        assertTrue(tracker.isReadyForDisplay)
        assertFalse(tracker.onStylingScriptsComplete())
        assertFalse(tracker.onMainFrameLoadFinished())
    }

    @Test
    fun readySignalCanArriveWhenStylingCompletesBeforeMainFrameLoad() {
        val tracker = PageRenderReadinessTracker()

        assertFalse(tracker.onStylingScriptsComplete())
        assertTrue(tracker.onMainFrameLoadFinished())
        assertTrue(tracker.isReadyForDisplay)
    }

    @Test
    fun resetStartsANewRenderCycle() {
        val tracker = PageRenderReadinessTracker()

        tracker.onMainFrameLoadFinished()
        tracker.onStylingScriptsComplete()

        tracker.reset()

        assertFalse(tracker.isReadyForDisplay)
        assertFalse(tracker.onMainFrameLoadFinished())
        assertTrue(tracker.onStylingScriptsComplete())
    }

    @Test
    fun forcedReadyPreventsLaterDuplicateReadySignal() {
        val tracker = PageRenderReadinessTracker()

        assertTrue(tracker.forceReadyForDisplay())
        assertFalse(tracker.forceReadyForDisplay())
        assertFalse(tracker.onMainFrameLoadFinished())
        assertFalse(tracker.onStylingScriptsComplete())
    }
}
