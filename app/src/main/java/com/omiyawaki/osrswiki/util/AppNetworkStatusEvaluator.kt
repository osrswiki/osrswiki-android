package com.omiyawaki.osrswiki.util

class AppNetworkStatusEvaluator(
    private val debugAlwaysOnline: Boolean,
    private val isCurrentlyConnected: () -> Boolean
) {
    fun initialStatus(): Boolean {
        return debugAlwaysOnline || isCurrentlyConnected()
    }

    fun statusAfterAvailableNetwork(): Boolean {
        return true
    }

    fun statusAfterLostNetwork(): Boolean {
        return debugAlwaysOnline || isCurrentlyConnected()
    }

    fun statusAfterCapabilitiesChanged(hasInternetCapability: Boolean): Boolean {
        return debugAlwaysOnline || hasInternetCapability
    }
}
