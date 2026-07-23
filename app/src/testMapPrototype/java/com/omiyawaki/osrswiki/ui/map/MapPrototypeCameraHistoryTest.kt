package com.omiyawaki.osrswiki.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPrototypeCameraHistoryTest {
    @Test
    fun historyEqualityIncludesBearingAndTilt() {
        val baseline = snapshot(bearing = 0.0, tilt = 0.0)
        val rotated = snapshot(bearing = 25.0, tilt = 0.0)
        val tilted = snapshot(bearing = 0.0, tilt = 12.0)

        assertFalse(baseline.approximatelyEquals(rotated))
        assertFalse(baseline.approximatelyEquals(tilted))
        assertTrue(baseline.approximatelyEquals(snapshot(bearing = 0.00001, tilt = 0.00001)))
    }

    @Test
    fun navigationSnapshotCarriesFullPoseIntoCameraState() {
        val snapshot = snapshot(bearing = 73.5, tilt = 18.25)
        val camera = snapshot.cameraState()

        assertEquals(snapshot.latitude, camera.latitude, 0.0)
        assertEquals(snapshot.longitude, camera.longitude, 0.0)
        assertEquals(snapshot.zoom, camera.zoom, 0.0)
        assertEquals(73.5, camera.bearing, 0.0)
        assertEquals(18.25, camera.tilt, 0.0)
        assertEquals(snapshot.surfaceId, camera.surfaceId)
    }

    private fun snapshot(
        bearing: Double,
        tilt: Double
    ) = osrsMapNavigationSnapshot(
        latitude = -18.0,
        longitude = 23.0,
        zoom = 6.0,
        bearing = bearing,
        tilt = tilt,
        surfaceId = "gielinor-surface",
        query = "Falador",
        resultId = "search:falador",
        statusText = "Centered on Falador",
        statusContentDescription = "Centered on Falador"
    )
}
