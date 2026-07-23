package com.omiyawaki.osrswiki.undergroundmaps

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class osrsMapStyleReloadInstrumentedTest {
    @Test
    fun styleReloadReinstallsVerifiedSourceAndPreservesRealmCameraAndFloor() {
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            val before = awaitDiagnostics(scenario) {
                it.activeRealmId != null &&
                    it.sourceId != null &&
                    it.stagedAssetSha256 != null &&
                    it.renderMarker?.startsWith("map-idle@") == true
            }

            scenario.onActivity { activity ->
                assertTrue(activity.reloadStyleForTesting())
            }

            val after = awaitDiagnostics(scenario) {
                it.styleGeneration > before.styleGeneration &&
                    it.sourceId != null &&
                    it.renderMarker?.startsWith("map-idle@") == true
            }

            assertEquals(before.activeRealmId, after.activeRealmId)
            assertEquals(before.activePlane, after.activePlane)
            assertEquals(before.stagedAssetSha256, after.stagedAssetSha256)
            assertEquals(before.cameraLatitude!!, after.cameraLatitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(before.cameraLongitude!!, after.cameraLongitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(before.cameraZoom!!, after.cameraZoom!!, OSRS_CAMERA_EPSILON)
            assertEquals(before.cameraBearing!!, after.cameraBearing!!, OSRS_CAMERA_EPSILON)
            assertNotEquals(before.sourceId, after.sourceId)
            assertNotNull(after.layerId)
            assertEquals(after.manifestRealmCount, after.selectorRealmCount)
            assertTrue(after.manifestAssetNonblank == true)
        }
    }

    private fun awaitDiagnostics(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        predicate: (osrsMapDiagnostics) -> Boolean
    ): osrsMapDiagnostics {
        val deadline = System.nanoTime() + OSRS_TEST_TIMEOUT_NANOS
        var latest: osrsMapDiagnostics? = null
        while (System.nanoTime() < deadline) {
            scenario.onActivity { activity -> latest = activity.debugStateForTesting() }
            latest?.let { if (predicate(it)) return it }
            Thread.sleep(OSRS_TEST_POLL_MILLIS)
        }
        throw AssertionError("Timed out waiting for MapLibre diagnostics; latest=$latest")
    }

    private companion object {
        const val OSRS_TEST_TIMEOUT_NANOS = 20_000_000_000L
        const val OSRS_TEST_POLL_MILLIS = 100L
        const val OSRS_CAMERA_EPSILON = 1e-9
    }
}
