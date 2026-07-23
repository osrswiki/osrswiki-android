package com.omiyawaki.osrswiki.page

class PageRenderReadinessTracker {
    private var mainFrameLoadFinished = false
    private var stylingScriptsComplete = false
    var isReadyForDisplay: Boolean = false
        private set

    fun reset() {
        mainFrameLoadFinished = false
        stylingScriptsComplete = false
        isReadyForDisplay = false
    }

    fun onMainFrameLoadFinished(): Boolean {
        mainFrameLoadFinished = true
        return maybeMarkReady()
    }

    fun onStylingScriptsComplete(): Boolean {
        stylingScriptsComplete = true
        return maybeMarkReady()
    }

    fun forceReadyForDisplay(): Boolean {
        if (isReadyForDisplay) {
            return false
        }
        isReadyForDisplay = true
        return true
    }

    private fun maybeMarkReady(): Boolean {
        if (isReadyForDisplay || !mainFrameLoadFinished || !stylingScriptsComplete) {
            return false
        }
        isReadyForDisplay = true
        return true
    }
}
