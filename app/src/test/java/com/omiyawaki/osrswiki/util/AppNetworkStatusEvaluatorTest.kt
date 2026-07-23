package com.omiyawaki.osrswiki.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNetworkStatusEvaluatorTest {

    @Test
    fun networkLostKeepsAppOnlineWhenAnotherActiveNetworkStillHasInternet() {
        val evaluator = AppNetworkStatusEvaluator(
            debugAlwaysOnline = false,
            isCurrentlyConnected = { true }
        )

        assertTrue(evaluator.statusAfterLostNetwork())
    }

    @Test
    fun networkLostMarksAppOfflineWhenNoActiveNetworkHasInternet() {
        val evaluator = AppNetworkStatusEvaluator(
            debugAlwaysOnline = false,
            isCurrentlyConnected = { false }
        )

        assertFalse(evaluator.statusAfterLostNetwork())
    }

    @Test
    fun debugBuildStaysOnlineForTransientConnectivityCallbacks() {
        val evaluator = AppNetworkStatusEvaluator(
            debugAlwaysOnline = true,
            isCurrentlyConnected = { false }
        )

        assertTrue(evaluator.initialStatus())
        assertTrue(evaluator.statusAfterLostNetwork())
        assertTrue(evaluator.statusAfterCapabilitiesChanged(hasInternetCapability = false))
    }
}
