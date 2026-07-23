package com.omiyawaki.osrswiki.ui.map

import com.omiyawaki.osrswiki.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapPrototypePerformanceContractTest {
    @Test
    fun candidateIdentityComesFromTheCandidate008BuildVariant() {
        assertEquals("candidate-008", BuildConfig.MAP_PROTOTYPE_CANDIDATE_ID)
        val application = File(
            "src/mapPrototype/java/com/omiyawaki/osrswiki/test/MapPrototypeApplication.kt"
        ).readText()
        assertTrue(application.contains("BuildConfig.MAP_PROTOTYPE_CANDIDATE_ID"))
        assertFalse(application.contains("candidate-004"))
        assertFalse(application.contains("candidate-005"))
        assertFalse(application.contains("candidate-006"))
        assertFalse(application.contains("candidate-007"))
    }

    @Test
    fun uiThreadAndRendererSubmissionPhasesStaySeparatelyNamed() {
        val sample = osrsMapPrototypeWindowFrameSample(
            totalMs = 100.0,
            deadlineMs = 16.667,
            inputHandlingMs = 1.0,
            animationMs = 2.0,
            layoutMeasureMs = 3.0,
            drawMs = 4.0,
            syncMs = 5.0,
            commandIssueMs = 6.0,
            swapBuffersMs = 7.0
        )

        assertEquals(15.0, sample.uiThreadWorkMs, 0.0)
        assertEquals(13.0, sample.rendererSubmissionMs, 0.0)
        assertEquals(28.0, sample.measuredPhaseCompositeMs, 0.0)
        assertTrue(sample.deadlineMissed)
    }

    @Test
    fun logsDoNotRelabelRendererOrCompositeTimeAsAppOwned() {
        val collector = File(
            "src/mapPrototype/java/com/omiyawaki/osrswiki/ui/map/osrsMapPrototypePerformance.kt"
        ).readText()
        val facade = File(
            "src/main/java/com/omiyawaki/osrswiki/ui/map/osrsMapPrototypePerformance.kt"
        ).readText()

        assertTrue(collector.contains("ui_thread_work_ms="))
        assertTrue(collector.contains("renderer_submission_ms="))
        assertTrue(collector.contains("measured_phase_composite_ms="))
        assertTrue(collector.contains("requested_after_frame="))
        assertTrue(collector.contains("expected_window_match="))
        assertFalse(collector.contains("app_pipeline_ms"))
        assertFalse(collector.contains("candidate-004"))
        assertFalse(collector.contains("candidate-006"))
        assertFalse(collector.contains("candidate-007"))
        assertTrue(facade.contains("val activeCollector = collector ?: return block()"))
        assertFalse(facade.contains("SystemClock"))
        assertFalse(facade.contains("Trace."))
    }

    @Test
    fun settledInstrumentationBudgetCannotIncludeRendererSubmissionPhases() {
        val harness = File(
            "src/androidTestMapPrototype/java/com/omiyawaki/osrswiki/ui/map/MapPrototypeBehaviorE2eTest.kt"
        ).readText()
        val sample = harness.substringAfter("private data class SettledFrameMetricSample(")
            .substringBefore("private data class FragmentHandle")
        val settledMetrics = harness.substringAfter("private fun testSettledFrameMetrics(")
            .substringBefore("private fun waitForNavigationState(")
        val uiThreadExpression = sample.substringAfter("val uiThreadWorkMs: Double")
            .substringAfter("get() =")
            .substringBefore("\n\n")

        listOf(
            "inputHandlingMs",
            "animationMs",
            "layoutMeasureMs",
            "drawMs",
            "syncMs"
        ).forEach { phase ->
            assertTrue("UI-thread work omits $phase", uiThreadExpression.contains(phase))
        }
        assertFalse(uiThreadExpression.contains("commandIssueMs"))
        assertFalse(uiThreadExpression.contains("swapBuffersMs"))
        assertTrue(sample.contains("val rendererSubmissionMs: Double"))
        assertTrue(sample.contains("get() = commandIssueMs + swapBuffersMs"))
        assertTrue(settledMetrics.contains("ui_thread_work_durations_ms"))
        assertTrue(settledMetrics.contains("renderer_submission_durations_ms"))
        assertTrue(settledMetrics.contains("measured_phase_composite_durations_ms"))
        assertTrue(settledMetrics.contains("raw_total_durations_ms"))
        assertTrue(settledMetrics.contains("raw_total_p95_within_100ms_diagnostic"))
        assertFalse(settledMetrics.contains("app_pipeline"))
        assertFalse(settledMetrics.contains("app_owned"))
        assertFalse(settledMetrics.contains("Settled raw render p95 must remain under"))
    }
}
