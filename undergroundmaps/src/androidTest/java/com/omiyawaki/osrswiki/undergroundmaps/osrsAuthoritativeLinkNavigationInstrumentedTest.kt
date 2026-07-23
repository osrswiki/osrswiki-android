package com.omiyawaki.osrswiki.undergroundmaps

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class osrsAuthoritativeLinkNavigationInstrumentedTest {
    @Test
    fun surfaceLinkUsesAuthoritativeAncientCavernEndpointAndExposesDiagnostics() {
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            val initial = awaitDiagnostics(scenario) {
                it.activeRealmId != null && it.sourceId != null
            }
            assumeTrue(
                "Pinned full-release assets are required for the endpoint traversal contract",
                initial.manifestRealmCount == 1097
            )

            scenario.onActivity { activity ->
                assertTrue(activity.selectRealmForTesting(OSRS_SURFACE_REALM_ID))
            }
            awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_SURFACE_REALM_ID &&
                    it.sourceId != null &&
                    it.switchCompletedAtNanos != null &&
                    it.renderMarker.startsWith("map-idle@")
            }

            scenario.onActivity { activity ->
                assertTrue(activity.selectAuthoritativeLinkForTesting(OSRS_ANCIENT_CAVERN_LINK_ID))
            }
            val linked = awaitDiagnostics(scenario) {
                it.selectedLinkId == OSRS_ANCIENT_CAVERN_LINK_ID &&
                    it.activeRealmId == OSRS_ANCIENT_CAVERN_REALM_ID &&
                    it.activePlane == 1 &&
                    it.linkAppliedMarker?.startsWith("camera-applied-") == true &&
                    it.renderMarker.startsWith("map-idle@")
            }

            assertEquals(1764, linked.linkTargetGameX)
            assertEquals(5367, linked.linkTargetGameY)
            assertEquals(1, linked.linkTargetPlane)
            assertEquals(OSRS_ANCIENT_CAVERN_REALM_ID, linked.linkTargetRealmId)
            assertEquals(1, linked.linkMatchingLayoutCount)
            assertNotNull(linked.linkMappedLatitude)
            assertNotNull(linked.linkMappedLongitude)
            assertEquals(linked.linkMappedLatitude!!, linked.cameraLatitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(linked.linkMappedLongitude!!, linked.cameraLongitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(linked.linkMappedZoom!!, linked.cameraZoom!!, OSRS_CAMERA_EPSILON)
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
        throw AssertionError("Timed out waiting for endpoint navigation diagnostics; latest=$latest")
    }

    private companion object {
        const val OSRS_SURFACE_REALM_ID = "surface-gielinor"
        const val OSRS_ANCIENT_CAVERN_REALM_ID = "cache-world-map:ancient-cavern"
        const val OSRS_ANCIENT_CAVERN_LINK_ID = "intermap-0137"
        const val OSRS_TEST_TIMEOUT_NANOS = 20_000_000_000L
        const val OSRS_TEST_POLL_MILLIS = 100L
        const val OSRS_CAMERA_EPSILON = 1e-8
    }
}
