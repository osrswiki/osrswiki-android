package com.omiyawaki.osrswiki.ui.map

interface osrsMapPrototypePerformanceCollector {
    fun beginSession()
    fun markPhase(name: String, detail: String = "")
    fun <T> measureCpuSpan(name: String, detail: String = "", block: () -> T): T
    fun markForNextWindowFrame(name: String, detail: String = "")
    fun markFirstTerrain(mapEncodingMs: Double, mapRenderingMs: Double, fully: Boolean)
    fun markFirstCompleteSemantics(renderedCount: Int)
    fun recordMapLibreFrame(fully: Boolean, encodingMs: Double, renderingMs: Double, drawCalls: Int)
    fun recordWindowFrame(sample: osrsMapPrototypeWindowFrameSample)
    fun elapsedMs(): Double
}

object osrsMapPrototypePerformance {
    @Volatile
    private var collector: osrsMapPrototypePerformanceCollector? = null

    fun install(value: osrsMapPrototypePerformanceCollector) {
        collector = value
    }

    fun beginSession() {
        collector?.beginSession()
    }

    fun markPhase(name: String, detail: String = "") {
        collector?.markPhase(name, detail)
    }

    fun <T> measureCpuSpan(name: String, detail: String = "", block: () -> T): T {
        val activeCollector = collector ?: return block()
        return activeCollector.measureCpuSpan(name, detail, block)
    }

    fun markForNextWindowFrame(name: String, detail: String = "") {
        collector?.markForNextWindowFrame(name, detail)
    }

    fun markFirstTerrain(mapEncodingMs: Double, mapRenderingMs: Double, fully: Boolean) {
        collector?.markFirstTerrain(mapEncodingMs, mapRenderingMs, fully)
    }

    fun markFirstCompleteSemantics(renderedCount: Int) {
        collector?.markFirstCompleteSemantics(renderedCount)
    }

    fun recordMapLibreFrame(
        fully: Boolean,
        encodingMs: Double,
        renderingMs: Double,
        drawCalls: Int
    ) {
        collector?.recordMapLibreFrame(fully, encodingMs, renderingMs, drawCalls)
    }

    fun recordWindowFrame(sample: osrsMapPrototypeWindowFrameSample) {
        collector?.recordWindowFrame(sample)
    }

    fun elapsedMs(): Double = collector?.elapsedMs() ?: 0.0
}

data class osrsMapPrototypeWindowFrameSample(
    val totalMs: Double,
    val deadlineMs: Double,
    val inputHandlingMs: Double,
    val animationMs: Double,
    val layoutMeasureMs: Double,
    val drawMs: Double,
    val syncMs: Double,
    val commandIssueMs: Double,
    val swapBuffersMs: Double
) {
    val uiThreadWorkMs: Double
        get() = inputHandlingMs + animationMs + layoutMeasureMs + drawMs + syncMs

    val rendererSubmissionMs: Double
        get() = commandIssueMs + swapBuffersMs

    val measuredPhaseCompositeMs: Double
        get() = uiThreadWorkMs + rendererSubmissionMs

    val deadlineMissed: Boolean
        get() = deadlineMs > 0.0 && totalMs >= deadlineMs
}
