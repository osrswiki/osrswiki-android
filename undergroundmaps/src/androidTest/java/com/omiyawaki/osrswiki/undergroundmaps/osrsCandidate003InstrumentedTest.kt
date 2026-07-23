package com.omiyawaki.osrswiki.undergroundmaps

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exact-release Candidate 004 checks; skipped by the small local fixture target. */
@RunWith(AndroidJUnit4::class)
class osrsCandidate004InstrumentedTest {
    @Test
    fun authoritativeLinksApplyDirectionalEndpointCoordinates() {
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            val surface = selectSurface(scenario)
            assumeTrue("Exact Candidate 006 assets are required", surface.candidate == "006")

            scenario.onActivity { activity ->
                assertTrue(activity.selectAuthoritativeLinkForTesting("intermap-0357"))
            }
            val morytania = awaitDiagnostics(scenario) { diagnostics ->
                diagnostics.activeRealmId == OSRS_MORYTANIA_REALM_ID &&
                    diagnostics.activePlane == 0 &&
                    diagnostics.switchCompletedAtNanos != null &&
                    diagnostics.selectedLinkId == "intermap-0357" &&
                    diagnostics.linkAppliedMarker?.startsWith("camera-applied-") == true &&
                    diagnostics.renderMarker.startsWith("map-idle@")
            }
            assertEndpoint(
                morytania,
                realmId = OSRS_MORYTANIA_REALM_ID,
                gameX = 3405,
                gameY = 9907,
                latitude = 84.23194746223983,
                longitude = -170.5078125,
                zoom = 6.0,
                assetSha256 = OSRS_MORYTANIA_PLANE_ZERO_SHA256
            )

            scenario.onActivity { activity ->
                assertTrue(activity.selectAuthoritativeLinkForTesting("intermap-0357"))
            }
            val returnedSurface = awaitDiagnostics(scenario) { diagnostics ->
                diagnostics.activeRealmId == OSRS_SURFACE_REALM_ID &&
                    diagnostics.activePlane == 0 &&
                    diagnostics.switchCompletedAtNanos != null &&
                    diagnostics.selectedLinkId == "intermap-0357" &&
                    diagnostics.linkAppliedMarker?.startsWith("camera-applied-") == true &&
                    diagnostics.renderMarker.startsWith("map-idle@")
            }
            assertEndpoint(
                returnedSurface,
                realmId = OSRS_SURFACE_REALM_ID,
                gameX = 3405,
                gameY = 3506,
                latitude = 75.19702129578613,
                longitude = 34.9365234375,
                zoom = 7.0
            )

            scenario.onActivity { activity ->
                assertTrue(activity.selectAuthoritativeLinkForTesting("intermap-0361"))
            }
            val distinctMorytaniaEndpoint = awaitDiagnostics(scenario) { diagnostics ->
                diagnostics.activeRealmId == OSRS_MORYTANIA_REALM_ID &&
                    diagnostics.switchCompletedAtNanos != null &&
                    diagnostics.selectedLinkId == "intermap-0361" &&
                    diagnostics.linkAppliedMarker?.startsWith("camera-applied-") == true &&
                    diagnostics.renderMarker.startsWith("map-idle@")
            }
            assertEndpoint(
                distinctMorytaniaEndpoint,
                realmId = OSRS_MORYTANIA_REALM_ID,
                gameX = 3440,
                gameY = 9886,
                latitude = 82.54060382149495,
                longitude = -145.8984375,
                zoom = 4.0,
                assetSha256 = OSRS_MORYTANIA_PLANE_ZERO_SHA256
            )
            assertTrue(
                distinctMorytaniaEndpoint.cameraLatitude != morytania.cameraLatitude ||
                    distinctMorytaniaEndpoint.cameraLongitude != morytania.cameraLongitude
            )
        }
    }

    @Test
    fun activityRecreationPreservesEndpointRealmFloorCameraAndVerifiedAsset() {
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            val surface = selectSurface(scenario)
            assumeTrue("Exact Candidate 006 assets are required", surface.candidate == "006")
            scenario.onActivity { activity ->
                assertTrue(activity.selectAuthoritativeLinkForTesting("intermap-0357"))
            }
            val before = awaitDiagnostics(scenario) { diagnostics ->
                diagnostics.activeRealmId == OSRS_MORYTANIA_REALM_ID &&
                    diagnostics.switchCompletedAtNanos != null &&
                    diagnostics.renderMarker.startsWith("map-idle@") &&
                    diagnostics.stagedAssetSha256 == OSRS_MORYTANIA_PLANE_ZERO_SHA256
            }

            scenario.recreate()

            val after = awaitDiagnostics(scenario) { diagnostics ->
                diagnostics.activeRealmId == OSRS_MORYTANIA_REALM_ID &&
                    diagnostics.activePlane == before.activePlane &&
                    diagnostics.switchCompletedAtNanos != null &&
                    diagnostics.renderMarker.startsWith("map-idle@") &&
                    diagnostics.stagedAssetSha256 == before.stagedAssetSha256
            }
            assertEquals(before.activeRealmId, after.activeRealmId)
            assertEquals(before.activePlane, after.activePlane)
            assertEquals(before.cameraLatitude!!, after.cameraLatitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(before.cameraLongitude!!, after.cameraLongitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(before.cameraZoom!!, after.cameraZoom!!, OSRS_CAMERA_EPSILON)
            assertEquals(before.cameraBearing!!, after.cameraBearing!!, OSRS_CAMERA_EPSILON)
            assertEquals(before.stagedAssetSha256, after.stagedAssetSha256)
            assertNotNull(after.sourceId)
            assertTrue(after.manifestAssetNonblank == true)
            assertEquals(after.manifestRealmCount, after.selectorRealmCount)
        }
    }

    private fun selectSurface(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>
    ): osrsMapDiagnostics {
        awaitDiagnostics(scenario) { it.activeRealmId != null && it.sourceId != null }
        scenario.onActivity { activity ->
            assertTrue(activity.selectRealmForTesting(OSRS_SURFACE_REALM_ID))
            assertTrue(activity.selectPlaneForTesting(0))
        }
        return awaitDiagnostics(scenario) { diagnostics ->
            diagnostics.activeRealmId == OSRS_SURFACE_REALM_ID &&
                diagnostics.activePlane == 0 &&
                diagnostics.sourceId != null &&
                diagnostics.switchCompletedAtNanos != null &&
                diagnostics.stagedAssetPath == OSRS_SURFACE_PLANE_ZERO_PATH &&
                diagnostics.renderMarker.startsWith("map-idle@")
        }
    }

    private fun assertEndpoint(
        diagnostics: osrsMapDiagnostics,
        realmId: String,
        gameX: Int,
        gameY: Int,
        latitude: Double,
        longitude: Double,
        zoom: Double,
        assetSha256: String? = null
    ) {
        assertEquals(realmId, diagnostics.activeRealmId)
        assertEquals(0, diagnostics.activePlane)
        assertEquals(gameX, diagnostics.linkTargetGameX)
        assertEquals(gameY, diagnostics.linkTargetGameY)
        assertEquals(latitude, diagnostics.linkMappedLatitude!!, OSRS_CAMERA_EPSILON)
        assertEquals(longitude, diagnostics.linkMappedLongitude!!, OSRS_CAMERA_EPSILON)
        assertEquals(zoom, diagnostics.linkMappedZoom!!, OSRS_CAMERA_EPSILON)
        assertEquals(latitude, diagnostics.cameraLatitude!!, OSRS_CAMERA_EPSILON)
        assertEquals(longitude, diagnostics.cameraLongitude!!, OSRS_CAMERA_EPSILON)
        assertEquals(zoom, diagnostics.cameraZoom!!, OSRS_CAMERA_EPSILON)
        assertTrue((diagnostics.linkMatchingLayoutCount ?: 0) >= 1)
        assetSha256?.let { assertEquals(it, diagnostics.stagedAssetSha256) }
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
        throw AssertionError("Timed out waiting for Candidate 004 diagnostics; latest=$latest")
    }

    private companion object {
        const val OSRS_SURFACE_REALM_ID = "surface-gielinor"
        const val OSRS_MORYTANIA_REALM_ID = "cache-world-map:morytania-underground"
        // Bound to the retained locked Candidate 004 manifest and staged MBTiles bytes.
        const val OSRS_MORYTANIA_PLANE_ZERO_SHA256 =
            "969fecc404f2a5e400e469e9e67252537ae46217b7b869c863a04cee62ee2305"
        const val OSRS_SURFACE_PLANE_ZERO_PATH = "assets/surface-gielinor/plane-0.mbtiles"
        const val OSRS_TEST_TIMEOUT_NANOS = 30_000_000_000L
        const val OSRS_TEST_POLL_MILLIS = 100L
        const val OSRS_CAMERA_EPSILON = 1e-7
    }
}
