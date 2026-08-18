package com.omiyawaki.osrswiki.undergroundmaps

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsLongitudesEquivalent
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinksDialogDebugState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileOutputStream
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class osrsCandidate009PresentationInstrumentedTest {
    @Test
    fun offCenterZoomMomentumKeepsItsFocalAnchorInsteadOfFlingingTheMap() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            dismissLegacyTargetWarningIfPresent(instrumentation, device)
            val ready = awaitDiagnostics(scenario) {
                it.sourceId != null &&
                    it.installedCameraRequestId == it.activeSwitchRequestId &&
                    it.cameraZoom != null
            }
            scenario.onActivity { activity ->
                assertTrue(
                    activity.startZoomMomentumForTesting(
                        velocityLevelsPerSecond = 2.4,
                        focalXPx = ready.mapDrawableCenterXPx.toInt() / 2,
                        focalYPx = ready.mapDrawableCenterYPx.toInt() / 2
                    )
                )
            }
            val settled = awaitDiagnostics(scenario) {
                !it.cameraEdgePhysicsActive && it.lastCameraZoomMomentumFrameCount >= 2
            }
            assertTrue(
                "Off-center zoom momentum must preserve the release focal anchor: $settled",
                requireNotNull(settled.cameraZoomMomentumFocalDriftPx) <= 3.0
            )

            scenario.onActivity { activity ->
                assertTrue(
                    activity.startZoomMomentumForTesting(
                        velocityLevelsPerSecond = -2.4,
                        focalXPx = ready.mapDrawableCenterXPx.toInt() / 2,
                        focalYPx = ready.mapDrawableCenterYPx.toInt() / 2
                    )
                )
            }
            val zoomOutSettled = awaitDiagnostics(scenario) {
                !it.cameraEdgePhysicsActive &&
                    it.lastCameraZoomMomentumFrameCount >= 2 &&
                    requireNotNull(it.cameraZoom) < requireNotNull(settled.cameraZoom) - 0.01
            }
            assertTrue(
                "Off-center zoom-out momentum must preserve the release focal anchor: " +
                    zoomOutSettled,
                requireNotNull(zoomOutSettled.cameraZoomMomentumFocalDriftPx) <= 3.0
            )
        }
    }

    @Test
    fun pinchReleaseContinuesWithBoundedZoomMomentum() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            dismissLegacyTargetWarningIfPresent(instrumentation, device)
            awaitDiagnostics(scenario) {
                it.sourceId != null && it.installedCameraRequestId == it.activeSwitchRequestId
            }
            val mapObject = requireNotNull(
                device.wait(
                    Until.findObject(By.res(instrumentation.targetContext.packageName, "osrs_underground_map")),
                    5_000
                )
            )
            val before = awaitDiagnostics(scenario) { it.cameraZoom != null }
            val beforeZoom = requireNotNull(before.cameraZoom)
            val beforeLatitude = requireNotNull(before.cameraLatitude)
            val beforeLongitude = requireNotNull(before.cameraLongitude)
            val envelope = requireNotNull(before.cameraCenterEnvelope)
            mapObject.pinchOpen(0.55f, 24)
            val settled = awaitDiagnostics(scenario) {
                !it.cameraEdgePhysicsActive && it.lastCameraZoomMomentumFrameCount >= 2
            }
            assertTrue(requireNotNull(settled.cameraZoom) > beforeZoom + 0.01)
            assertTrue(
                "Pinch release must continue through app-owned zoom momentum: $settled",
                settled.cameraZoomMomentumPeakContinuation > 0.01
            )
            assertTrue(requireNotNull(settled.lastCameraZoomMomentumDurationNanos) > 30_000_000L)
            assertTrue(requireNotNull(settled.lastCameraZoomMomentumDurationNanos) < 2_500_000_000L)
            assertTrue(
                "A centered pinch must not turn zoom momentum into a latitude fling: $settled",
                abs(requireNotNull(settled.cameraLatitude) - beforeLatitude) <=
                    (envelope[3] - envelope[1]) * 0.02
            )
            assertTrue(
                "A centered pinch must not turn zoom momentum into a longitude fling: $settled",
                abs(requireNotNull(settled.cameraLongitude) - beforeLongitude) <=
                    (envelope[2] - envelope[0]) * 0.02
            )
        }
    }

    @Test
    fun edgeReleaseUsesMultiFrameResistedOverscrollAndSpringBounce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            dismissLegacyTargetWarningIfPresent(instrumentation, device)
            awaitDiagnostics(scenario) {
                it.sourceId != null &&
                    it.installedCameraRequestId == it.activeSwitchRequestId
            }
            scenario.onActivity { activity ->
                assertTrue(activity.selectRealmForTesting(OSRS_SURFACE_REALM_ID))
            }
            val surfaceInstalled = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_SURFACE_REALM_ID &&
                    it.cameraCenterEnvelope != null &&
                    it.cameraCopySafeMinZoom != null &&
                    it.installedCameraRequestId == it.activeSwitchRequestId
            }
            val envelope = requireNotNull(surfaceInstalled.cameraCenterEnvelope)
            val west = envelope[0]
            val latitude = (envelope[1] + envelope[3]) / 2.0
            val zoom = requireNotNull(surfaceInstalled.cameraCopySafeMinZoom)
            val longitudeSpan = envelope[2] - envelope[0]
            scenario.onActivity { activity ->
                assertTrue(activity.moveCameraTargetForTesting(latitude, west, zoom))
            }
            awaitDiagnostics(scenario) { cameraMatches(it, latitude, west) }

            val y = device.displayHeight / 2
            assertTrue(device.swipe(device.displayWidth / 2, y, device.displayWidth - 8, y, 4))
            val settled = awaitDiagnostics(scenario) {
                !it.cameraEdgePhysicsActive &&
                    it.lastCameraEdgeBounceFrameCount >= OSRS_MINIMUM_BOUNCE_FRAME_COUNT &&
                    cameraMatches(it, latitude, west)
            }
            val durationMillis = requireNotNull(settled.lastCameraEdgeBounceDurationNanos) / 1_000_000.0
            assertTrue("Expected visible multi-frame bounce; duration=$durationMillis", durationMillis >= 80.0)
            assertTrue("Bounce should settle promptly; duration=$durationMillis", durationMillis <= 2_500.0)
            assertTrue(settled.cameraEdgePeakLongitudeOvershoot > longitudeSpan * 0.0001)
            assertTrue(
                settled.cameraEdgePeakLongitudeOvershoot <
                    longitudeSpan * (OSRS_MAXIMUM_OVERSHOOT_FRACTION_FOR_TEST + 0.001)
            )
            assertFalse(settled.horizontalWrapEnabled)
        }
    }

    @Test
    fun surfaceKeepsHalfViewportOverboundWithoutRenderingWorldCopies() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            dismissLegacyTargetWarningIfPresent(instrumentation, device)
            awaitDiagnostics(scenario) {
                it.sourceId != null && it.installedCameraRequestId == it.activeSwitchRequestId
            }
            scenario.onActivity { activity ->
                assertTrue(activity.selectRealmForTesting(OSRS_SURFACE_REALM_ID))
            }
            val installed = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_SURFACE_REALM_ID &&
                    it.cameraCenterEnvelope != null &&
                    it.cameraCopySafeMinZoom != null &&
                    it.installedCameraRequestId == it.activeSwitchRequestId
            }
            val envelope = requireNotNull(installed.cameraCenterEnvelope)
            val west = envelope[0]
            val east = envelope[2]
            val latitude = (envelope[1] + envelope[3]) / 2.0
            val minimumZoom = requireNotNull(installed.cameraCopySafeMinZoom)
            assertTrue("Surface must use a positive copy-safe zoom floor", minimumZoom > 0.0)
            assertTrue(installed.centerEdgeOverflowEnabled)
            assertFalse(installed.horizontalWrapEnabled)

            scenario.onActivity { activity ->
                assertTrue(activity.moveCameraTargetForTesting(
                    latitude = latitude,
                    longitude = west - 360.0,
                    zoom = minimumZoom,
                    bearing = 0.0,
                    tilt = 0.0
                ))
            }
            val westEdge = awaitDiagnostics(scenario) {
                cameraMatches(it, latitude, west) &&
                    it.cameraZoom?.let { zoom -> abs(zoom - minimumZoom) <= 0.05 } == true
            }
            assertTrue(westEdge.cameraClampState.startsWith("clamped:"))
            instrumentation.waitForIdleSync()
            SystemClock.sleep(OSRS_EVIDENCE_SETTLE_MILLIS)
            val westScreenshot = instrumentation.uiAutomation.takeScreenshot()
            val westDarkRatio = darkPixelRatio(westScreenshot, 0.03, 0.24, 0.34, 0.46)
            retainSurfaceBoundaryScreenshot(instrumentation, westScreenshot, "west")
            assertTrue(
                "The west overbound half must be blank rather than the eastern world copy; " +
                    "darkRatio=$westDarkRatio cameraLongitude=${westEdge.cameraLongitude}",
                westDarkRatio > 0.92
            )
            westScreenshot.recycle()

            var westClampCount = westEdge.cameraClampCount
            repeat(3) {
                scenario.onActivity { activity ->
                    assertTrue(activity.moveCameraTargetForTesting(
                        latitude = latitude,
                        longitude = west - 360.0,
                        zoom = minimumZoom
                    ))
                }
                westClampCount = awaitDiagnostics(scenario) {
                    cameraMatches(it, latitude, west) && it.cameraClampCount > westClampCount
                }.cameraClampCount
            }

            scenario.onActivity { activity ->
                assertTrue(activity.moveCameraTargetForTesting(
                    latitude = latitude,
                    longitude = east + 360.0,
                    zoom = minimumZoom,
                    bearing = 0.0,
                    tilt = 0.0
                ))
            }
            val eastEdge = awaitDiagnostics(scenario) {
                cameraMatches(it, latitude, east) &&
                    it.cameraZoom?.let { zoom -> abs(zoom - minimumZoom) <= 0.05 } == true
            }
            assertTrue(eastEdge.cameraClampState.startsWith("clamped:"))
            instrumentation.waitForIdleSync()
            SystemClock.sleep(OSRS_EVIDENCE_SETTLE_MILLIS)
            val eastScreenshot = instrumentation.uiAutomation.takeScreenshot()
            val eastDarkRatio = darkPixelRatio(eastScreenshot, 0.63, 0.24, 0.34, 0.46)
            retainSurfaceBoundaryScreenshot(instrumentation, eastScreenshot, "east")
            assertTrue(
                "The east overbound half must be blank rather than the western world copy; " +
                    "darkRatio=$eastDarkRatio cameraLongitude=${eastEdge.cameraLongitude}",
                eastDarkRatio > 0.92
            )
            eastScreenshot.recycle()
        }
    }

    @Test
    fun r12GodWarsPlanesStopAtFiniteHorizontalEdgesAndControlsMatch() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            dismissLegacyTargetWarningIfPresent(
                InstrumentationRegistry.getInstrumentation(),
                device
            )
            awaitDiagnostics(scenario) {
                it.sourceId != null && it.installedCameraRequestId == it.activeSwitchRequestId
            }
            scenario.onActivity { activity ->
                assertTrue(activity.selectRealmForTesting(OSRS_GOD_WARS_REALM_ID))
            }

            OSRS_GOD_WARS_PLANES.forEach { plane ->
                scenario.onActivity { activity ->
                    assertTrue(activity.selectPlaneForTesting(plane))
                }
                val installed = awaitDiagnostics(scenario) {
                    it.activeRealmId == OSRS_GOD_WARS_REALM_ID &&
                        it.activePlane == plane &&
                        it.installedCameraPlane == plane &&
                        it.installedCameraRequestId == it.activeSwitchRequestId &&
                        it.cameraCenterEnvelope != null &&
                        it.switchCompletedAtNanos != null
                }
                val bounds = requireNotNull(installed.cameraCenterEnvelope)
                val west = bounds[0]
                val east = bounds[2]
                val middleLatitude = (bounds[1] + bounds[3]) / 2.0
                assertEquals(4096, installed.cameraVisibleCompositionCanvasSize)
                assertTrue(requireNotNull(installed.cameraVisibleCompositionHorizontalPaddingPx) >= 512)
                assertTrue(requireNotNull(installed.cameraVisibleCompositionVerticalPaddingPx) >= 512)
                assertTrue(requireNotNull(installed.cameraVisibleCompositionLongitudeSpanDegrees) < 360.0)
                assertFalse(installed.horizontalWrapEnabled)
                assertTrue(installed.centerEdgeOverflowEnabled)
                val minimumZoom = requireNotNull(installed.cameraCopySafeMinZoom)
                assertTrue(minimumZoom >= 0.0)

                scenario.onActivity { activity ->
                    assertTrue(
                        activity.moveCameraTargetForTesting(
                            latitude = bounds[3] + 5.0,
                            longitude = (west + east) / 2.0,
                            zoom = minimumZoom,
                            bearing = 0.0,
                            tilt = 0.0
                        )
                    )
                }
                val northEdge = awaitDiagnostics(scenario) {
                    it.activeRealmId == OSRS_GOD_WARS_REALM_ID &&
                        it.activePlane == plane &&
                        cameraMatches(it, bounds[3], (west + east) / 2.0) &&
                        it.cameraZoom?.let { zoom -> abs(zoom - minimumZoom) <= 0.05 } == true
                }
                assertTrue(northEdge.cameraClampState.startsWith("clamped:"))

                if (plane == 0) {
                    assertTrue(installed.floorControlVisible)
                    assertTrue(installed.topAndFloorControlsSeparated == true)
                }

                listOf(west - 360.0 to west, east + 360.0 to east).forEach { (request, edge) ->
                    var priorClampCount = installed.cameraClampCount
                    repeat(3) { attempt ->
                        scenario.onActivity { activity ->
                            assertTrue(
                                activity.moveCameraTargetForTesting(
                                    latitude = middleLatitude,
                                    longitude = request,
                                    bearing = 0.0,
                                    tilt = 0.0
                                )
                            )
                        }
                        val clamped = awaitDiagnostics(scenario) {
                            it.activeRealmId == OSRS_GOD_WARS_REALM_ID &&
                                it.activePlane == plane &&
                                cameraMatches(it, middleLatitude, edge) &&
                                it.cameraRequestedLongitude == request &&
                                it.cameraFinalLongitude == edge &&
                                it.cameraClampCount > priorClampCount
                        }
                        assertTrue("Repeated overpan $attempt must remain clamped", clamped.cameraClampState.startsWith("clamped:"))
                        assertFalse(clamped.horizontalWrapEnabled)
                        priorClampCount = clamped.cameraClampCount
                    }
                }
                captureR12BoundaryEvidence(scenario, device, plane)
            }
        }
    }

    @Test
    fun planeZeroIsReusedBelowEveryPublishedUpperFloor() {
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            dismissLegacyTargetWarningIfPresent()
            awaitDiagnostics(scenario) {
                it.sourceId != null && it.visiblePlanesBottomToTop.isNotEmpty()
            }
            var realmId: String? = null
            scenario.onActivity { activity ->
                realmId = activity.firstFourPlaneRealmIdForTesting()
                assertTrue(activity.selectRealmForTesting(requireNotNull(realmId)))
            }
            awaitDiagnostics(scenario) {
                it.activeRealmId == realmId && it.switchCompletedAtNanos != null
            }

            var planeZeroSource: String? = null
            var planeZeroLayer: String? = null
            (0..3).forEach { plane ->
                scenario.onActivity { activity ->
                    assertTrue(activity.selectPlaneForTesting(plane))
                }
                val diagnostics = awaitDiagnostics(scenario) {
                    it.activeRealmId == realmId &&
                        it.activePlane == plane &&
                        it.installedCameraPlane == plane &&
                        it.switchCompletedAtNanos != null &&
                        it.visiblePlanesBottomToTop.lastOrNull() == plane
                }
                if (plane == 0) {
                    assertEquals(listOf(0), diagnostics.visiblePlanesBottomToTop)
                    assertEquals(listOf(1.0f), diagnostics.visibleRasterOpacitiesBottomToTop)
                    planeZeroSource = diagnostics.visibleSourceIdsBottomToTop.single()
                    planeZeroLayer = diagnostics.visibleLayerIdsBottomToTop.single()
                } else {
                    assertEquals(listOf(0, plane), diagnostics.visiblePlanesBottomToTop)
                    assertEquals(
                        listOf(0.5f, 1.0f),
                        diagnostics.visibleRasterOpacitiesBottomToTop
                    )
                    assertEquals(planeZeroSource, diagnostics.visibleSourceIdsBottomToTop.first())
                    assertEquals(planeZeroLayer, diagnostics.visibleLayerIdsBottomToTop.first())
                    assertTrue(diagnostics.planeZeroResourceReused == true)
                }
                assertTrue(diagnostics.visibleLayerOrderMatchesStyle)
                assertTrue(diagnostics.replacementPreparedBeforeRemoval == true)
                assertEquals(
                    diagnostics.visiblePlanesBottomToTop.toSet(),
                    diagnostics.stagedAssetSha256ByPlane.keys
                )
            }

            scenario.onActivity { activity ->
                assertTrue(activity.selectRealmForTesting(requireNotNull(activity.surfaceRealmIdForTesting())))
            }
            val surface = awaitDiagnostics(scenario) {
                it.activeGroup == "surface" &&
                    it.switchCompletedAtNanos != null
            }
            assertEquals(listOf(0), surface.visiblePlanesBottomToTop)
            assertEquals(listOf(1.0f), surface.visibleRasterOpacitiesBottomToTop)
        }
    }

    @Test
    fun activeAssetEdgesReachMapCenterAndRejectFurtherOverpan() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            dismissLegacyTargetWarningIfPresent(
                InstrumentationRegistry.getInstrumentation(),
                device
            )
            awaitDiagnostics(scenario) {
                it.sourceId != null && it.installedCameraRequestId == it.activeSwitchRequestId
            }
            val realmIds = mutableListOf<String>()
            scenario.onActivity { activity ->
                realmIds += requireNotNull(activity.surfaceRealmIdForTesting())
                realmIds += requireNotNull(activity.compactPlaneZeroRealmIdForTesting())
            }

            realmIds.forEach { realmId ->
                scenario.onActivity { activity ->
                    assertTrue(activity.selectRealmForTesting(realmId))
                    activity.lowestPlaneForRealmForTesting(realmId)?.let {
                        assertTrue(activity.selectPlaneForTesting(it))
                    }
                }
                val installed = awaitDiagnostics(scenario) {
                    it.activeRealmId == realmId &&
                        it.installedCameraRealmId == realmId &&
                        it.cameraCenterEnvelope != null &&
                        it.switchCompletedAtNanos != null
                }
                val bounds = requireNotNull(installed.cameraCenterEnvelope)
                val west = bounds[0]
                val south = bounds[1]
                val east = bounds[2]
                val north = bounds[3]
                val midLatitude = (south + north) / 2.0
                val midLongitude = (west + east) / 2.0
                val installedZoom = requireNotNull(installed.cameraZoom)
                val minimumZoom = requireNotNull(installed.cameraCopySafeMinZoom)
                val changedZoom = if (installedZoom - 0.25 >= minimumZoom) {
                    installedZoom - 0.25
                } else {
                    installedZoom + 0.25
                }

                scenario.onActivity { activity ->
                    assertTrue(
                        activity.moveCameraTargetForTesting(
                            latitude = north + 5.0,
                            longitude = midLongitude,
                            zoom = changedZoom,
                            bearing = 23.0,
                            tilt = 0.0
                        )
                    )
                }
                val preservedCamera = awaitDiagnostics(scenario) {
                    it.activeRealmId == realmId &&
                        cameraMatches(it, north, midLongitude) &&
                        it.cameraClampState.startsWith("clamped:")
                }
                assertEquals(changedZoom, preservedCamera.cameraZoom!!, OSRS_CAMERA_EPSILON)
                assertEquals(23.0, preservedCamera.cameraBearing!!, OSRS_CAMERA_EPSILON)
                assertEquals(0.0, preservedCamera.cameraTilt!!, OSRS_CAMERA_EPSILON)

                val cases = listOf(
                    osrsEdgeCase(
                        "west",
                        midLatitude,
                        west - 0.25,
                        midLatitude,
                        west,
                        300,
                        0
                    ),
                    osrsEdgeCase(
                        "east",
                        midLatitude,
                        east + 0.25,
                        midLatitude,
                        east,
                        -300,
                        0
                    ),
                    osrsEdgeCase(
                        "south",
                        south - 5.0,
                        midLongitude,
                        south,
                        midLongitude,
                        0,
                        -300
                    ),
                    osrsEdgeCase(
                        "north",
                        north + 5.0,
                        midLongitude,
                        north,
                        midLongitude,
                        0,
                        300
                    )
                )
                cases.forEach { edge ->
                    scenario.onActivity { activity ->
                        assertTrue(
                            activity.moveCameraTargetForTesting(
                                latitude = edge.requestedLatitude,
                                longitude = edge.requestedLongitude,
                                zoom = changedZoom,
                                bearing = 0.0,
                                tilt = 0.0
                            )
                        )
                    }
                    val clamped = awaitDiagnostics(scenario) {
                        it.activeRealmId == realmId &&
                            cameraMatches(it, edge.expectedLatitude, edge.expectedLongitude) &&
                            it.cameraClampState.startsWith("clamped:")
                    }
                    assertEquals(edge.requestedLatitude, clamped.cameraRequestedLatitude!!, 0.0)
                    assertEquals(edge.requestedLongitude, clamped.cameraRequestedLongitude!!, 0.0)
                    assertEquals(edge.expectedLatitude, clamped.cameraFinalLatitude!!, 0.0)
                    assertEquals(edge.expectedLongitude, clamped.cameraFinalLongitude!!, 0.0)
                    assertEquals(changedZoom, clamped.cameraZoom!!, OSRS_CAMERA_EPSILON)
                    assertEquals(0.0, clamped.cameraBearing!!, OSRS_CAMERA_EPSILON)
                    assertEquals(0.0, clamped.cameraTilt!!, OSRS_CAMERA_EPSILON)
                    assertTrue(
                        abs(requireNotNull(clamped.cameraTargetScreenXPx) -
                            clamped.mapDrawableCenterXPx) <= OSRS_CENTER_TOLERANCE_PX
                    )
                    assertTrue(
                        abs(requireNotNull(clamped.cameraTargetScreenYPx) -
                            clamped.mapDrawableCenterYPx) <= OSRS_CENTER_TOLERANCE_PX
                    )

                    val clampedTarget = clamped.cameraLatitude to clamped.cameraLongitude
                    var priorClampCount = clamped.cameraClampCount
                    repeat(2) { dragIndex ->
                        assertTrue(
                            "Failed to inject ${edge.name} overpan drag $dragIndex",
                            device.swipe(
                                clamped.mapDrawableCenterXPx.toInt(),
                                clamped.mapDrawableCenterYPx.toInt(),
                                clamped.mapDrawableCenterXPx.toInt() + edge.swipeDeltaX,
                                clamped.mapDrawableCenterYPx.toInt() + edge.swipeDeltaY,
                                OSRS_SWIPE_STEPS
                            )
                        )
                        device.waitForIdle()
                        SystemClock.sleep(OSRS_GESTURE_SETTLE_MILLIS)
                        val afterExtraDrag = awaitDiagnostics(scenario) {
                            it.activeRealmId == realmId &&
                                cameraMatches(it, edge.expectedLatitude, edge.expectedLongitude)
                        }
                        assertEquals(
                            requireNotNull(clampedTarget.first),
                            requireNotNull(afterExtraDrag.cameraLatitude),
                            OSRS_CAMERA_EPSILON
                        )
                        assertTrue(
                            osrsLongitudesEquivalent(
                                requireNotNull(clampedTarget.second),
                                requireNotNull(afterExtraDrag.cameraLongitude)
                            )
                        )
                        assertEquals(changedZoom, afterExtraDrag.cameraZoom!!, OSRS_CAMERA_EPSILON)
                        assertEquals(0.0, afterExtraDrag.cameraBearing!!, OSRS_CAMERA_EPSILON)
                        assertTrue(afterExtraDrag.cameraClampCount >= priorClampCount)
                        assertTrue(
                            requireNotNull(afterExtraDrag.cameraClampP95Nanos) <
                                OSRS_SIMPLE_CONTROL_BUDGET_NANOS
                        )
                        priorClampCount = afterExtraDrag.cameraClampCount
                    }
                }

                val beforeRecreate = awaitDiagnostics(scenario) {
                    it.activeRealmId == realmId && it.cameraPersistenceMarker.startsWith("persisted:")
                }
                scenario.recreate()
                val recreated = awaitDiagnostics(scenario) {
                    it.activeRealmId == realmId &&
                        it.installedCameraRequestId == it.activeSwitchRequestId &&
                        it.cameraLatitude != null
                }
                assertEquals(beforeRecreate.cameraLatitude!!, recreated.cameraLatitude!!, OSRS_CAMERA_EPSILON)
                assertEquals(beforeRecreate.cameraLongitude!!, recreated.cameraLongitude!!, OSRS_CAMERA_EPSILON)
                assertEquals(beforeRecreate.cameraZoom!!, recreated.cameraZoom!!, OSRS_CAMERA_EPSILON)
                assertEquals(beforeRecreate.cameraBearing!!, recreated.cameraBearing!!, OSRS_CAMERA_EPSILON)
            }
        }
    }

    @Test
    fun mapLinksActionIsHiddenWhileInternalCatalogAndNavigationRemainVerified() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            dismissLegacyTargetWarningIfPresent(
                InstrumentationRegistry.getInstrumentation(),
                device
            )
            awaitDiagnostics(scenario) {
                it.sourceId != null && it.activeRealmId != null
            }
            var density = 0f
            scenario.onActivity { activity ->
                density = activity.resources.displayMetrics.density
                assertTrue(activity.selectRealmForTesting(requireNotNull(activity.surfaceRealmIdForTesting())))
            }
            awaitDiagnostics(scenario) {
                it.activeGroup == "surface" &&
                    it.availableLinkCount == 335 &&
                    it.unavailableLinkCount == 47
            }
            scenario.onActivity { activity ->
                assertNull(activity.linksActionAccessibilityTextForTesting())
                assertTrue(activity.openRealmLinksForTesting())
            }
            val initial = awaitLinksDialog(scenario) {
                it.isShowing &&
                    it.decorAttached &&
                    it.decorLaidOut &&
                    it.decorShown &&
                    it.visibleBoundRowCount > 0
            }
            assertTrue(initial.explicitOsrsPalette)
            assertFalse(initial.searchFocused)
            assertTrue(initial.searchWidthPx >= (48 * density).toInt())
            assertTrue(initial.searchHeightPx >= (48 * density).toInt())
            captureLinksPresentationEvidence(scenario, device, "links-idle", initial)

            val search = requireNotNull(
                device.wait(
                    Until.findObject(By.desc(OSRS_LINKS_SEARCH_DESCRIPTION)),
                    OSRS_UI_TIMEOUT_MILLIS
                )
            )
            search.click()
            search.text = "ancient"
            val landscape =
                resourcesOrientation() == Configuration.ORIENTATION_LANDSCAPE
            val focused = awaitLinksDialog(scenario) {
                it.searchFocused &&
                    it.query == "ancient" &&
                    it.displayedRowCount > 0 &&
                    it.visibleBoundRowCount > 0 &&
                    (!landscape || it.compactLandscapeImeChrome)
            }
            assertTrue(focused.searchHeightPx >= (48 * density).toInt())
            if (landscape) {
                assertTrue(focused.compactLandscapeImeChrome)
            }
            captureLinksPresentationEvidence(scenario, device, "links-search-focused", focused)
            val actionableRow = requireNotNull(
                device.wait(
                    Until.findObject(By.descContains("Open authoritative link intermap-0076")),
                    OSRS_UI_TIMEOUT_MILLIS
                )
            )
            actionableRow.click()
            val linked = awaitDiagnostics(scenario) {
                it.selectedLinkId == "intermap-0076" &&
                    it.linkAppliedMarker?.startsWith("camera-applied-") == true &&
                    it.sourceId != null
            }
            assertEquals("intermap-0076", linked.selectedLinkId)
        }
    }

    private fun captureLinksPresentationEvidence(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        device: UiDevice,
        state: String,
        dialog: osrsRealmLinksDialogDebugState
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        device.waitForIdle()
        SystemClock.sleep(OSRS_EVIDENCE_SETTLE_MILLIS)
        var diagnostics: osrsMapDiagnostics? = null
        scenario.onActivity { activity -> diagnostics = activity.debugStateForTesting() }
        val captured = requireNotNull(diagnostics)
        val orientation = if (captured.screenWidthDp > captured.screenHeightDp) {
            "landscape"
        } else {
            "portrait"
        }
        val uiMode = instrumentation.targetContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        val theme = if (uiMode == Configuration.UI_MODE_NIGHT_YES) "night" else "day"
        val prefix = "$orientation-$theme-$state"
        val directory = requireNotNull(
            instrumentation.targetContext.getExternalFilesDir(null)
        ).resolve(OSRS_EVIDENCE_DIRECTORY).apply {
            check(mkdirs() || isDirectory)
        }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(directory.resolve("$prefix.png")).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        device.dumpWindowHierarchy(directory.resolve("$prefix.xml"))
        directory.resolve("$prefix.json").writeText(
            JSONObject().apply {
                put("orientation", orientation)
                put("theme", theme)
                put("state", state)
                put("font_scale", captured.fontScale.toDouble())
                put("active_realm_id", captured.activeRealmId)
                put("query", dialog.query)
                put("search_focused", dialog.searchFocused)
                put("compact_landscape_ime_chrome", dialog.compactLandscapeImeChrome)
                put("displayed_row_count", dialog.displayedRowCount)
                put("visible_bound_row_count", dialog.visibleBoundRowCount)
                put("explicit_osrs_palette", dialog.explicitOsrsPalette)
                put("title_text_color_argb", argbHex(dialog.titleTextColor))
                put("summary_text_color_argb", argbHex(dialog.summaryTextColor))
                put("search_text_color_argb", argbHex(dialog.searchTextColor))
                put("search_hint_color_argb", argbHex(dialog.searchHintColor))
                put("search_width_px", dialog.searchWidthPx)
                put("search_height_px", dialog.searchHeightPx)
                put(
                    "production_map_links_action_visible",
                    captured.realmLinksActionVisible
                )
            }.toString(2)
        )
    }

    private fun captureR12BoundaryEvidence(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        device: UiDevice,
        plane: Int
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        device.waitForIdle()
        SystemClock.sleep(OSRS_EVIDENCE_SETTLE_MILLIS)
        var diagnostics: osrsMapDiagnostics? = null
        scenario.onActivity { activity -> diagnostics = activity.debugStateForTesting() }
        val captured = requireNotNull(diagnostics)
        val directory = requireNotNull(
            instrumentation.targetContext.getExternalFilesDir(null)
        ).resolve(OSRS_R12_EVIDENCE_DIRECTORY).apply {
            check(mkdirs() || isDirectory)
        }
        val prefix = "god-wars-plane-$plane-east-clamped"
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(directory.resolve("$prefix.png")).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        device.dumpWindowHierarchy(directory.resolve("$prefix.xml"))
        directory.resolve("$prefix.json").writeText(
            JSONObject().apply {
                put("realm_id", captured.activeRealmId)
                put("plane", captured.activePlane)
                put("camera_center_envelope", captured.cameraCenterEnvelope)
                put("camera_latitude", captured.cameraLatitude)
                put("camera_longitude", captured.cameraLongitude)
                put("camera_requested_longitude", captured.cameraRequestedLongitude)
                put("camera_final_longitude", captured.cameraFinalLongitude)
                put("camera_clamp_state", captured.cameraClampState)
                put("camera_clamp_count", captured.cameraClampCount)
                put("horizontal_wrap_enabled", captured.horizontalWrapEnabled)
                put("shared_canvas_size", captured.cameraVisibleCompositionCanvasSize)
                put("horizontal_padding_px", captured.cameraVisibleCompositionHorizontalPaddingPx)
                put("vertical_padding_px", captured.cameraVisibleCompositionVerticalPaddingPx)
                put("visible_longitude_span_degrees", captured.cameraVisibleCompositionLongitudeSpanDegrees)
                put("copy_safe_min_zoom", captured.cameraCopySafeMinZoom)
                put("floor_width_px", captured.floorControlRightPx - captured.floorControlLeftPx)
                put("floor_corner_radius_px", captured.floorControlCornerRadiusPx.toDouble())
                put("map_links_ui_enabled", captured.realmLinksUiEnabled)
                put("map_links_action_visible", captured.realmLinksActionVisible)
            }.toString(2)
        )
    }

    private fun retainSurfaceBoundaryScreenshot(
        instrumentation: android.app.Instrumentation,
        bitmap: Bitmap,
        edge: String
    ) {
        val directory = requireNotNull(
            instrumentation.targetContext.getExternalFilesDir(null)
        ).resolve(OSRS_SURFACE_BOUNDARY_EVIDENCE_DIRECTORY).apply {
            check(mkdirs() || isDirectory)
        }
        val retained = directory.resolve("surface-$edge-copy-safe.png")
        FileOutputStream(retained).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun dismissLegacyTargetWarningIfPresent(
        instrumentation: android.app.Instrumentation = InstrumentationRegistry.getInstrumentation(),
        device: UiDevice = UiDevice.getInstance(instrumentation)
    ) {
        if (!device.wait(Until.hasObject(By.text("OK")), 2_000)) return
        device.findObject(By.text("OK"))?.click()
        device.wait(Until.gone(By.text("OK")), 5_000)
        device.waitForIdle()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
    }

    private fun argbHex(color: Int): String = "#%08X".format(color)

    private fun darkPixelRatio(
        bitmap: Bitmap,
        normalizedX: Double,
        normalizedY: Double,
        normalizedWidth: Double,
        normalizedHeight: Double
    ): Double {
        val left = (bitmap.width * normalizedX).toInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * normalizedY).toInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * (normalizedX + normalizedWidth)).toInt()
            .coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * (normalizedY + normalizedHeight)).toInt()
            .coerceIn(top + 1, bitmap.height)
        var dark = 0L
        var total = 0L
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) <= 20 && Color.green(pixel) <= 20 && Color.blue(pixel) <= 20) {
                    dark += 1
                }
                total += 1
            }
        }
        return dark.toDouble() / total.toDouble()
    }

    private fun resourcesOrientation(): Int =
        InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.configuration.orientation

    private fun awaitDiagnostics(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        predicate: (osrsMapDiagnostics) -> Boolean
    ): osrsMapDiagnostics {
        val deadline = SystemClock.elapsedRealtime() + OSRS_STATE_TIMEOUT_MILLIS
        var latest: osrsMapDiagnostics? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> latest = activity.debugStateForTesting() }
            latest?.let { if (predicate(it)) return it }
            Thread.sleep(OSRS_TEST_POLL_MILLIS)
        }
        throw AssertionError("Timed out awaiting Candidate 010 diagnostics; latest=$latest")
    }

    private fun awaitLinksDialog(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        predicate: (osrsRealmLinksDialogDebugState) -> Boolean
    ): osrsRealmLinksDialogDebugState {
        val deadline = SystemClock.elapsedRealtime() + OSRS_UI_TIMEOUT_MILLIS
        var latest: osrsRealmLinksDialogDebugState? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> latest = activity.realmLinksDialogStateForTesting() }
            latest?.let { if (predicate(it)) return it }
            Thread.sleep(OSRS_TEST_POLL_MILLIS)
        }
        throw AssertionError("Timed out awaiting Candidate 010 links dialog; latest=$latest")
    }

    private fun cameraMatches(
        diagnostics: osrsMapDiagnostics,
        latitude: Double,
        longitude: Double
    ): Boolean =
            diagnostics.cameraLatitude?.let { abs(it - latitude) <= OSRS_CAMERA_EPSILON } == true &&
            diagnostics.cameraLongitude?.let {
                osrsLongitudesEquivalent(it, longitude)
            } == true

    private data class osrsEdgeCase(
        val name: String,
        val requestedLatitude: Double,
        val requestedLongitude: Double,
        val expectedLatitude: Double,
        val expectedLongitude: Double,
        val swipeDeltaX: Int,
        val swipeDeltaY: Int
    )

    private companion object {
        const val OSRS_MINIMUM_BOUNCE_FRAME_COUNT = 5
        const val OSRS_MAXIMUM_OVERSHOOT_FRACTION_FOR_TEST = 0.12
        const val OSRS_SURFACE_REALM_ID = "surface-gielinor"
        const val OSRS_GOD_WARS_REALM_ID = "cache-world-map:godwars"
        val OSRS_GOD_WARS_PLANES = listOf(0, 1, 2)
        const val OSRS_R12_EVIDENCE_DIRECTORY = "candidate010-r12-boundary-evidence"
        const val OSRS_SURFACE_BOUNDARY_EVIDENCE_DIRECTORY = "surface-copy-safe-evidence"
        const val OSRS_PACKAGE_ID = "com.omiyawaki.osrswiki.undergroundmaps"
        const val OSRS_LINKS_SEARCH_DESCRIPTION =
            "Search links by map, endpoint, or link ID"
        const val OSRS_UI_TIMEOUT_MILLIS = 20_000L
        const val OSRS_STATE_TIMEOUT_MILLIS = 45_000L
        const val OSRS_TEST_POLL_MILLIS = 25L
        const val OSRS_CAMERA_EPSILON = 1e-6
        const val OSRS_CENTER_TOLERANCE_PX = 2.0f
        const val OSRS_SIMPLE_CONTROL_BUDGET_NANOS = 50_000_000L
        const val OSRS_SWIPE_STEPS = 20
        const val OSRS_GESTURE_SETTLE_MILLIS = 350L
        const val OSRS_EVIDENCE_SETTLE_MILLIS = 250L
        const val OSRS_EVIDENCE_DIRECTORY = "candidate010-presentation-evidence"
    }
}
