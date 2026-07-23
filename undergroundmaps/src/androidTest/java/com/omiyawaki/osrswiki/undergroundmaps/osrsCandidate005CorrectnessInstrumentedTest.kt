package com.omiyawaki.osrswiki.undergroundmaps

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLinkTraversalDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class osrsCandidate005CorrectnessInstrumentedTest {
    @Test
    fun allFourSameRealmLinksExposeAndNavigateBothDirectionalSides() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            awaitDiagnostics(scenario) { it.candidate == "006" && it.sourceId != null }
            OSRS_SAME_REALM_LINK_CASES.forEach { case ->
                scenario.onActivity { activity ->
                    assertTrue(activity.selectRealmForTesting(case.realmId))
                }
                awaitDiagnostics(scenario) {
                    it.activeRealmId == case.realmId &&
                        it.sourceId != null &&
                        it.switchCompletedAtNanos != null
                }

                val linksButton = requireNotNull(
                    device.wait(
                        Until.findObject(By.res(OSRS_PACKAGE_ID, "osrs_realm_links")),
                        OSRS_UI_TIMEOUT_MILLIS
                    )
                )
                linksButton.click()
                val search = requireNotNull(
                    device.wait(
                        Until.findObject(By.res(OSRS_PACKAGE_ID, "osrs_links_search")),
                        OSRS_UI_TIMEOUT_MILLIS
                    )
                )
                search.text = case.linkId
                val descriptions = awaitLinkDescriptions(device, case.linkId)
                assertEquals(2, descriptions.size)
                assertTrue(descriptions.any { it.contains("direction forward relative") })
                assertTrue(descriptions.any { it.contains("direction reverse relative") })
                device.pressBack()
                assertTrue(
                    device.wait(
                        Until.gone(By.res(OSRS_PACKAGE_ID, "osrs_links_search")),
                        OSRS_UI_TIMEOUT_MILLIS
                    )
                )

                listOf(
                    osrsRealmLinkTraversalDirection.FORWARD to case.forwardTarget,
                    osrsRealmLinkTraversalDirection.REVERSE to case.reverseTarget
                ).forEach { (direction, target) ->
                    scenario.onActivity { activity ->
                        assertTrue(
                            activity.selectAuthoritativeLinkForTesting(
                                case.linkId,
                                direction
                            )
                        )
                    }
                    val navigation = awaitDiagnostics(scenario) {
                        it.selectedLinkId == case.linkId &&
                            it.selectedLinkTraversalDirection == direction.name &&
                            it.linkTargetGameX == target.x &&
                            it.linkTargetGameY == target.y &&
                            it.linkTargetPlane == target.plane &&
                            it.activeRealmId == case.realmId &&
                            it.activePlane == target.plane &&
                            it.installedCameraRealmId == case.realmId &&
                            it.installedCameraPlane == target.plane &&
                            it.switchCompletedAtNanos != null &&
                            it.linkAppliedMarker?.startsWith("camera-applied-") == true
                    }
                    assertEquals("${case.linkId}:${direction.name.lowercase()}", navigation.selectedLinkSideKey)
                    assertEquals(
                        requireNotNull(navigation.linkMappedLatitude),
                        requireNotNull(navigation.cameraLatitude),
                        OSRS_CAMERA_EPSILON
                    )
                    assertEquals(
                        requireNotNull(navigation.linkMappedLongitude),
                        requireNotNull(navigation.cameraLongitude),
                        OSRS_CAMERA_EPSILON
                    )
                    assertEquals(
                        requireNotNull(navigation.linkMappedZoom),
                        requireNotNull(navigation.cameraZoom),
                        OSRS_CAMERA_EPSILON
                    )
                }
            }
        }
    }

    @Test
    fun delayedSelectionRacesAndLargeDisplayEndpointRemainOwnedAcrossRecreation() {
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            awaitDiagnostics(scenario) { it.candidate == "006" && it.sourceId != null }
            scenario.onActivity { activity ->
                assertTrue(activity.selectRealmForTesting(OSRS_SURFACE_REALM_ID))
            }
            awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_SURFACE_REALM_ID && it.sourceId != null
            }
            scenario.onActivity { activity ->
                assertTrue(
                    activity.selectAuthoritativeLinkForTesting(
                        OSRS_LARGE_DISPLAY_LINK_ID,
                        osrsRealmLinkTraversalDirection.FORWARD,
                        maximumViewportExtentDp = 1600.0
                    )
                )
            }
            val largeDisplay = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_OURANIA_REALM_ID &&
                    it.selectedLinkSideKey == "$OSRS_LARGE_DISPLAY_LINK_ID:forward" &&
                    it.installedCameraRealmId == OSRS_OURANIA_REALM_ID &&
                    it.installedCameraPlane == 0 &&
                    cameraMatches(it, 84.40594104126977, -150.46875, 7.0)
            }
            val expectedCamera = Triple(
                requireNotNull(largeDisplay.cameraLatitude),
                requireNotNull(largeDisplay.cameraLongitude),
                requireNotNull(largeDisplay.cameraZoom)
            )

            scenario.recreate()
            val recreatedLargeDisplay = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_OURANIA_REALM_ID &&
                    it.installedCameraRealmId == OSRS_OURANIA_REALM_ID &&
                    it.installedCameraPlane == 0 &&
                    it.installedCameraRequestId == it.activeSwitchRequestId &&
                    cameraMatches(it, expectedCamera.first, expectedCamera.second, expectedCamera.third)
            }
            assertEquals(7.0, recreatedLargeDisplay.cameraZoom!!, OSRS_CAMERA_EPSILON)

            var delayedWindow: osrsMapDiagnostics? = null
            scenario.onActivity { activity ->
                assertTrue(activity.selectRealmForTesting(OSRS_DELAYED_B_REALM_ID))
                delayedWindow = activity.debugStateForTesting()
            }
            val requestedB = requireNotNull(delayedWindow)
            assertEquals(OSRS_DELAYED_B_REALM_ID, requestedB.activeRealmId)
            assertEquals(OSRS_OURANIA_REALM_ID, requestedB.installedCameraRealmId)
            assertNotEquals(requestedB.installedCameraRequestId, requestedB.activeSwitchRequestId)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.RESUMED)
            val recreatedB = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_DELAYED_B_REALM_ID &&
                    it.installedCameraRealmId == OSRS_DELAYED_B_REALM_ID &&
                    it.installedCameraPlane == it.activePlane &&
                    it.installedCameraRequestId == it.activeSwitchRequestId &&
                    it.sourceId != null
            }
            assertCameraInsideRealm(recreatedB)
            assertTrue(
                abs(requireNotNull(recreatedB.cameraLatitude) - expectedCamera.first) > OSRS_CAMERA_EPSILON ||
                    abs(requireNotNull(recreatedB.cameraLongitude) - expectedCamera.second) > OSRS_CAMERA_EPSILON
            )

            var rapidWindow: osrsMapDiagnostics? = null
            scenario.onActivity { activity ->
                assertTrue(activity.selectRealmForTesting(OSRS_RAPID_B_REALM_ID))
                assertTrue(activity.selectRealmForTesting(OSRS_RAPID_C_REALM_ID))
                rapidWindow = activity.debugStateForTesting()
            }
            val requestedC = requireNotNull(rapidWindow)
            assertEquals(OSRS_RAPID_C_REALM_ID, requestedC.activeRealmId)
            assertNotEquals(OSRS_RAPID_C_REALM_ID, requestedC.installedCameraRealmId)
            scenario.recreate()
            val recreatedC = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_RAPID_C_REALM_ID &&
                    it.installedCameraRealmId == OSRS_RAPID_C_REALM_ID &&
                    it.installedCameraPlane == it.activePlane &&
                    it.installedCameraRequestId == it.activeSwitchRequestId &&
                    it.sourceId != null
            }
            assertCameraInsideRealm(recreatedC)
        }
    }

    private fun awaitLinkDescriptions(device: UiDevice, linkId: String): List<String> {
        val deadline = SystemClock.elapsedRealtime() + OSRS_UI_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val rows = device.findObjects(By.descContains("Open authoritative link $linkId"))
            if (rows.size == 2 && rows.all { it.isClickable }) {
                return rows.map { it.contentDescription }.sorted()
            }
            Thread.sleep(25)
        }
        throw AssertionError("Expected two visible actionable rows for $linkId")
    }

    private fun awaitDiagnostics(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        predicate: (osrsMapDiagnostics) -> Boolean
    ): osrsMapDiagnostics {
        val deadline = SystemClock.elapsedRealtime() + OSRS_STATE_TIMEOUT_MILLIS
        var latest: osrsMapDiagnostics? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> latest = activity.debugStateForTesting() }
            latest?.let { if (predicate(it)) return it }
            Thread.sleep(25)
        }
        throw AssertionError("Timed out awaiting Candidate 006 diagnostics; latest=$latest")
    }

    private fun cameraMatches(
        diagnostics: osrsMapDiagnostics,
        latitude: Double,
        longitude: Double,
        zoom: Double
    ): Boolean = diagnostics.cameraLatitude?.let { abs(it - latitude) <= OSRS_CAMERA_EPSILON } == true &&
        diagnostics.cameraLongitude?.let { abs(it - longitude) <= OSRS_CAMERA_EPSILON } == true &&
        diagnostics.cameraZoom?.let { abs(it - zoom) <= OSRS_CAMERA_EPSILON } == true

    private fun assertCameraInsideRealm(diagnostics: osrsMapDiagnostics) {
        val bounds = requireNotNull(diagnostics.realmBounds)
        val latitude = requireNotNull(diagnostics.cameraLatitude)
        val longitude = requireNotNull(diagnostics.cameraLongitude)
        assertTrue(latitude in bounds[1]..bounds[3])
        assertTrue(longitude in bounds[0]..bounds[2])
    }

    private data class osrsSameRealmLinkCase(
        val linkId: String,
        val realmId: String,
        val forwardTarget: osrsEndpoint,
        val reverseTarget: osrsEndpoint
    )

    private data class osrsEndpoint(val plane: Int, val x: Int, val y: Int)

    private companion object {
        const val OSRS_PACKAGE_ID = "com.omiyawaki.osrswiki.undergroundmaps"
        const val OSRS_SURFACE_REALM_ID = "surface-gielinor"
        const val OSRS_OURANIA_REALM_ID = "cache-world-map:ourania"
        const val OSRS_LARGE_DISPLAY_LINK_ID = "intermap-0125"
        const val OSRS_DELAYED_B_REALM_ID = "cache-world-map:ancient-cavern"
        const val OSRS_RAPID_B_REALM_ID = "cache-world-map:morytania-underground"
        const val OSRS_RAPID_C_REALM_ID = "other-map-10042"
        const val OSRS_UI_TIMEOUT_MILLIS = 15_000L
        const val OSRS_STATE_TIMEOUT_MILLIS = 30_000L
        const val OSRS_CAMERA_EPSILON = 1e-6

        val OSRS_SAME_REALM_LINK_CASES = listOf(
            osrsSameRealmLinkCase(
                "intermap-0036",
                "cache-world-map:cam-torum",
                osrsEndpoint(1, 1440, 9602),
                osrsEndpoint(1, 1440, 9549)
            ),
            osrsSameRealmLinkCase(
                "intermap-0338",
                "cache-world-map:wilderness-dungeons",
                osrsEndpoint(0, 3360, 10273),
                osrsEndpoint(1, 3295, 10189)
            ),
            osrsSameRealmLinkCase(
                "intermap-0350",
                "cache-world-map:wilderness-dungeons",
                osrsEndpoint(0, 3360, 10273),
                osrsEndpoint(0, 3359, 10313)
            ),
            osrsSameRealmLinkCase(
                "intermap-0362",
                "cache-world-map:wilderness-dungeons",
                osrsEndpoint(0, 3360, 10273),
                osrsEndpoint(2, 3423, 10182)
            )
        )
    }
}
