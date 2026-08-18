package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
class osrsNorthResetCompassViewTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `bearing normalization and needle rotation match map compass semantics`() {
        assertEquals(0.0, osrsNormalizeCompassBearing(360.0), 0.0)
        assertEquals(350.0, osrsNormalizeCompassBearing(-10.0), 0.0)
        assertEquals(1.0, osrsNormalizeCompassBearing(721.0), 0.0)
        assertEquals(-23f, osrsCompassNeedleRotationDegrees(23.0), 0f)
        assertEquals(-350f, osrsCompassNeedleRotationDegrees(-10.0), 0f)
    }

    @Test
    fun `north tolerance matches prior compass visibility contract`() {
        assertTrue(osrsCompassIsFacingNorth(0.0))
        assertTrue(osrsCompassIsFacingNorth(1.0))
        assertTrue(osrsCompassIsFacingNorth(359.0))
        assertFalse(osrsCompassIsFacingNorth(1.01))
        assertFalse(osrsCompassIsFacingNorth(358.99))
    }

    @Test
    fun `outer view remains unrotated while only needle follows bearing`() {
        val compass = osrsNorthResetCompassView(context)

        compass.updateBearing(137.0)

        assertEquals(0f, compass.rotation, 0f)
        assertEquals(-137f, compass.needleRotationForTesting(), 0f)
        assertEquals(View.VISIBLE, compass.visibility)
        assertEquals(1f, compass.alpha, 0f)
        assertFalse(compass.facingNorthForTesting())
    }

    @Test
    fun `north schedules the delayed fade and movement cancels it`() {
        val compass = osrsNorthResetCompassView(context)
        compass.updateBearing(23.0)
        compass.updateBearing(0.0)

        assertTrue(compass.fadePendingForTesting())
        shadowOf(Looper.getMainLooper()).idleFor(
            OSRS_COMPASS_FADE_DELAY_MILLIS - 1,
            TimeUnit.MILLISECONDS
        )
        assertEquals(View.VISIBLE, compass.visibility)

        compass.updateBearing(45.0)

        assertFalse(compass.fadePendingForTesting())
        assertEquals(View.VISIBLE, compass.visibility)
        assertEquals(1f, compass.alpha, 0f)
    }
}
