package com.omiyawaki.osrswiki.page.preemptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticlePrewarmPolicyTest {
    @Test
    fun unmeteredForegroundAllowsTwoAndConstrainedAllowsOne() {
        val unmetered = ArticlePrewarmPolicy.evaluate(signals(networkConstrained = false))
        val constrained = ArticlePrewarmPolicy.evaluate(signals(networkConstrained = true))

        assertTrue(unmetered.allowsPrewarm)
        assertEquals(2, unmetered.maxConcurrent)
        assertTrue(constrained.allowsPrewarm)
        assertEquals(1, constrained.maxConcurrent)
    }

    @Test
    fun backgroundOfflinePowerAndThermalSignalsSuppressSpeculation() {
        assertEquals(
            ArticlePrewarmSuppression.APP_BACKGROUND,
            ArticlePrewarmPolicy.evaluate(signals(appInForeground = false)).suppression
        )
        assertEquals(
            ArticlePrewarmSuppression.NETWORK_UNAVAILABLE,
            ArticlePrewarmPolicy.evaluate(signals(networkAvailable = false)).suppression
        )
        assertEquals(
            ArticlePrewarmSuppression.POWER_SAVE,
            ArticlePrewarmPolicy.evaluate(signals(powerSave = true)).suppression
        )
        val thermal = ArticlePrewarmPolicy.evaluate(signals(thermallyConstrained = true))
        assertEquals(ArticlePrewarmSuppression.THERMAL, thermal.suppression)
        assertFalse(thermal.allowsPrewarm)
        val disabled = ArticlePrewarmPolicy.evaluate(signals(debugDisabled = true))
        assertEquals(ArticlePrewarmSuppression.DEBUG_DISABLED, disabled.suppression)
        assertFalse(disabled.allowsPrewarm)
    }

    private fun signals(
        appInForeground: Boolean = true,
        networkAvailable: Boolean = true,
        networkConstrained: Boolean = false,
        powerSave: Boolean = false,
        thermallyConstrained: Boolean = false,
        debugDisabled: Boolean = false
    ) = ArticlePrewarmSignals(
        appInForeground,
        networkAvailable,
        networkConstrained,
        powerSave,
        thermallyConstrained,
        debugDisabled
    )
}
