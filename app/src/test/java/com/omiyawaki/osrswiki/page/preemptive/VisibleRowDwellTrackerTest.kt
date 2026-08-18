package com.omiyawaki.osrswiki.page.preemptive

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VisibleRowDwellTrackerTest {
    @Test
    fun rowMustRemainVisibleForDwellAndDetachCancelsItsLease() = runTest {
        val started = mutableListOf<String>()
        val canceled = mutableListOf<String>()
        val tracker = VisibleRowDwellTracker<String>(backgroundScope, dwellMillis = 300L) { key ->
            started += key
            ArticlePrewarmLease { canceled += key }
        }

        tracker.updateVisible(setOf("Amulet"))
        advanceTimeBy(299L)
        runCurrent()
        assertEquals(emptyList<String>(), started)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf("Amulet"), started)

        tracker.updateVisible(emptySet())
        assertEquals(listOf("Amulet"), canceled)
    }

    @Test
    fun leavingBeforeDwellDoesNotStartPrewarm() = runTest {
        var starts = 0
        val tracker = VisibleRowDwellTracker<String>(backgroundScope, dwellMillis = 300L) {
            starts += 1
            ArticlePrewarmLease {}
        }

        tracker.updateVisible(setOf("row"))
        advanceTimeBy(150L)
        tracker.updateVisible(emptySet())
        advanceTimeBy(500L)
        runCurrent()

        assertEquals(0, starts)
    }

    @Test
    fun favorableEnvironmentRetryReschedulesRowsThatStayedVisibleWhileSuppressed() = runTest {
        var starts = 0
        val tracker = VisibleRowDwellTracker<String>(backgroundScope, dwellMillis = 300L) {
            starts += 1
            ArticlePrewarmLease {}
        }
        tracker.updateVisible(setOf("visible"))
        advanceTimeBy(300L)
        runCurrent()
        assertEquals(1, starts)

        tracker.retryVisible()
        advanceTimeBy(300L)
        runCurrent()

        assertEquals(2, starts)
    }
}
