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
    fun firstViewAloneDoesNotMarkPageReadyForDisplay() {
        val tracker = PageRenderReadinessTracker()

        assertFalse(tracker.onFirstViewComplete())
        assertFalse(tracker.isReadyForDisplay)
    }

    @Test
    fun readySignalFiresOnceAfterLoadAndFirstViewAreBothComplete() {
        val tracker = PageRenderReadinessTracker()

        assertFalse(tracker.onMainFrameLoadFinished())
        assertTrue(tracker.onFirstViewComplete())
        assertTrue(tracker.isReadyForDisplay)
        assertFalse(tracker.onFirstViewComplete())
        assertFalse(tracker.onMainFrameLoadFinished())
    }

    @Test
    fun readySignalCanArriveWhenFirstViewCompletesBeforeMainFrameLoad() {
        val tracker = PageRenderReadinessTracker()

        assertFalse(tracker.onFirstViewComplete())
        assertTrue(tracker.onMainFrameLoadFinished())
        assertTrue(tracker.isReadyForDisplay)
    }

    @Test
    fun stylingCompleteRemainsALateFallbackForFirstView() {
        val tracker = PageRenderReadinessTracker()

        assertFalse(tracker.onMainFrameLoadFinished())
        assertTrue(tracker.onStylingScriptsComplete())
        assertTrue(tracker.isReadyForDisplay)
    }

    @Test
    fun resetStartsANewRenderCycle() {
        val tracker = PageRenderReadinessTracker()

        tracker.onMainFrameLoadFinished()
        tracker.onFirstViewComplete()

        tracker.reset()

        assertFalse(tracker.isReadyForDisplay)
        assertFalse(tracker.onMainFrameLoadFinished())
        assertTrue(tracker.onFirstViewComplete())
    }

    @Test
    fun forcedReadyPreventsLaterDuplicateReadySignal() {
        val tracker = PageRenderReadinessTracker()

        assertTrue(tracker.forceReadyForDisplay())
        assertFalse(tracker.forceReadyForDisplay())
        assertFalse(tracker.onMainFrameLoadFinished())
        assertFalse(tracker.onFirstViewComplete())
        assertFalse(tracker.onStylingScriptsComplete())
    }
}
