package com.omiyawaki.osrswiki.page

class PageRenderReadinessTracker {
    private var mainFrameLoadFinished = false
    private var firstViewComplete = false
    var isReadyForDisplay: Boolean = false
        private set

    fun reset() {
        mainFrameLoadFinished = false
        firstViewComplete = false
        isReadyForDisplay = false
    }

    fun onMainFrameLoadFinished(): Boolean {
        mainFrameLoadFinished = true
        return maybeMarkReady()
    }

    fun onFirstViewComplete(): Boolean {
        firstViewComplete = true
        return maybeMarkReady()
    }

    /** Late fallback if first-viewport never arrives; collapse/map still emit this. */
    fun onStylingScriptsComplete(): Boolean = onFirstViewComplete()

    fun forceReadyForDisplay(): Boolean {
        if (isReadyForDisplay) {
            return false
        }
        isReadyForDisplay = true
        return true
    }

    private fun maybeMarkReady(): Boolean {
        if (isReadyForDisplay || !mainFrameLoadFinished || !firstViewComplete) {
            return false
        }
        isReadyForDisplay = true
        return true
    }
}
