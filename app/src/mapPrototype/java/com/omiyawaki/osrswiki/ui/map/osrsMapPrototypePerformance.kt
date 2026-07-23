package com.omiyawaki.osrswiki.ui.map

import android.os.Debug
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class osrsMapPrototypePerformanceCollectorImpl(
    private val candidateId: String
) : osrsMapPrototypePerformanceCollector {
    private companion object {
        const val TAG = "MapPrototypeFrameMetrics"
    }

    private val enabled = AtomicBoolean(false)
    private val frameIndex = AtomicInteger(0)
    private val markerSequence = AtomicInteger(0)
    private val pendingMarkers = ConcurrentLinkedQueue<PendingMarker>()
    private val firstTerrain = AtomicBoolean(false)
    private val firstSemantics = AtomicBoolean(false)
    private val firstComplete = AtomicBoolean(false)
    @Volatile private var sessionStartedNs = 0L

    override fun beginSession() {
        sessionStartedNs = SystemClock.elapsedRealtimeNanos()
        frameIndex.set(0)
        pendingMarkers.clear()
        firstTerrain.set(false)
        firstSemantics.set(false)
        firstComplete.set(false)
        enabled.set(true)
        markerSequence.set(0)
        logMarker("session_begin", "candidate=$candidateId")
    }

    override fun markPhase(name: String, detail: String) {
        if (!enabled.get()) return
        logMarker(name, detail)
    }

    override fun <T> measureCpuSpan(name: String, detail: String, block: () -> T): T {
        val wallStartNs = SystemClock.elapsedRealtimeNanos()
        val cpuStartNs = Debug.threadCpuTimeNanos()
        Trace.beginSection("MapPrototype:$name")
        return try {
            block()
        } finally {
            Trace.endSection()
            if (enabled.get()) {
                val wallMs = (SystemClock.elapsedRealtimeNanos() - wallStartNs) / 1_000_000.0
                val cpuMs = (Debug.threadCpuTimeNanos() - cpuStartNs) / 1_000_000.0
                Log.i(
                    TAG,
                    "event=cpu_span span=$name elapsed_ms=${format(elapsedMs())} " +
                        "wall_ms=${format(wallMs)} thread_cpu_ms=${format(cpuMs)}" +
                        detail.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
                )
            }
        }
    }

    override fun markForNextWindowFrame(name: String, detail: String) {
        if (!enabled.get()) return
        val requestedAfterFrame = frameIndex.get()
        val marker = PendingMarker(
            sequence = markerSequence.incrementAndGet(),
            name = name,
            elapsedMs = elapsedMs(),
            requestedAfterFrame = requestedAfterFrame,
            detail = detail
        )
        pendingMarkers += marker
        logMarker(
            "${name}_requested",
            "marker_sequence=${marker.sequence} requested_after_frame=$requestedAfterFrame " +
                "expected_frame_min=${requestedAfterFrame + 1} expected_frame_max=${requestedAfterFrame + 2}" +
                detail.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
        )
    }

    override fun markFirstTerrain(mapEncodingMs: Double, mapRenderingMs: Double, fully: Boolean) {
        if (!enabled.get() || !fully || !firstTerrain.compareAndSet(false, true)) return
        markForNextWindowFrame(
            "first_terrain",
            "map_encoding_ms=$mapEncodingMs map_rendering_ms=$mapRenderingMs fully=true"
        )
        maybeMarkComplete()
    }

    override fun markFirstCompleteSemantics(renderedCount: Int) {
        if (!enabled.get() || renderedCount <= 0 || !firstSemantics.compareAndSet(false, true)) return
        markForNextWindowFrame("first_complete_semantics", "rendered_feature_count=$renderedCount")
        maybeMarkComplete()
    }

    override fun recordMapLibreFrame(
        fully: Boolean,
        encodingMs: Double,
        renderingMs: Double,
        drawCalls: Int
    ) {
        if (!enabled.get()) return
        Log.i(
            TAG,
            "event=maplibre_frame elapsed_ms=${format(elapsedMs())} fully=$fully " +
                "encoding_ms=${format(encodingMs)} rendering_ms=${format(renderingMs)} draw_calls=$drawCalls"
        )
    }

    override fun recordWindowFrame(sample: osrsMapPrototypeWindowFrameSample) {
        if (!enabled.get()) return
        val index = frameIndex.incrementAndGet()
        val markers = buildList {
            while (true) add(pendingMarkers.poll() ?: break)
        }
        if (index <= 48 || markers.isNotEmpty()) {
            Log.i(
                TAG,
                "event=window_frame frame=$index elapsed_ms=${format(elapsedMs())} " +
                    "total_ms=${format(sample.totalMs)} deadline_ms=${format(sample.deadlineMs)} " +
                    "ui_thread_work_ms=${format(sample.uiThreadWorkMs)} " +
                    "renderer_submission_ms=${format(sample.rendererSubmissionMs)} " +
                    "measured_phase_composite_ms=${format(sample.measuredPhaseCompositeMs)} " +
                    "input_ms=${format(sample.inputHandlingMs)} animation_ms=${format(sample.animationMs)} " +
                    "layout_ms=${format(sample.layoutMeasureMs)} draw_ms=${format(sample.drawMs)} " +
                    "sync_ms=${format(sample.syncMs)} command_ms=${format(sample.commandIssueMs)} " +
                    "swap_ms=${format(sample.swapBuffersMs)} deadline_missed=${sample.deadlineMissed} " +
                    "renderer_command_over_100ms=${sample.commandIssueMs > 100.0}"
            )
        }
        markers.forEach { marker ->
            val expectedWindow = index in (marker.requestedAfterFrame + 1)..(marker.requestedAfterFrame + 2)
            Log.i(
                TAG,
                "event=frame_marker marker=${marker.name} marker_elapsed_ms=${format(marker.elapsedMs)} " +
                    "marker_sequence=${marker.sequence} requested_after_frame=${marker.requestedAfterFrame} " +
                    "frame=$index expected_window_match=$expectedWindow " +
                    "frame_total_ms=${format(sample.totalMs)} " +
                    "frame_ui_thread_work_ms=${format(sample.uiThreadWorkMs)} " +
                    "frame_renderer_submission_ms=${format(sample.rendererSubmissionMs)} " +
                    "frame_measured_phase_composite_ms=${format(sample.measuredPhaseCompositeMs)} " +
                    "frame_deadline_ms=${format(sample.deadlineMs)} deadline_missed=${sample.deadlineMissed}" +
                    marker.detail.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
            )
        }
    }

    override fun elapsedMs(): Double {
        val start = sessionStartedNs
        return if (start == 0L) 0.0 else (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
    }

    private fun maybeMarkComplete() {
        if (firstTerrain.get() && firstSemantics.get() && firstComplete.compareAndSet(false, true)) {
            markForNextWindowFrame("first_complete_map", "terrain=true semantics=true")
        }
    }

    private fun logMarker(name: String, detail: String) {
        Log.i(
            TAG,
            "event=app_marker marker=$name elapsed_ms=${format(elapsedMs())}" +
                detail.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
        )
    }

    private fun format(value: Double): String = String.format(java.util.Locale.US, "%.3f", value)

    private data class PendingMarker(
        val sequence: Int,
        val name: String,
        val elapsedMs: Double,
        val requestedAfterFrame: Int,
        val detail: String
    )
}
