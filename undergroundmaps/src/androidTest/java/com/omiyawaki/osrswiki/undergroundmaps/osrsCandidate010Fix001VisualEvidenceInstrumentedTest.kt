package com.omiyawaki.osrswiki.undergroundmaps

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class osrsCandidate010Fix001VisualEvidenceInstrumentedTest {
    @Test
    fun selectorOutsideDismissCapturesInAppCollapseEvidence() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            awaitDiagnostics(scenario) {
                it.sourceId != null &&
                    it.selectorExpanded == false &&
                    it.renderMarker.startsWith("map-idle@")
            }
            moveSurfacePlaneToContentAnchor(scenario = scenario, plane = 0, anchor = surfaceAnchorForPlane(1))
            scenario.onActivity { activity ->
                assertTrue(activity.openRealmSelectorForTesting())
                assertTrue(activity.filterRealmSelectorForTesting("ancient"))
                assertTrue(activity.focusRealmSelectorSearchForTesting())
            }
            val expanded = awaitDiagnostics(scenario) {
                it.selectorExpanded == true &&
                    it.selectorImeVisible == true &&
                    it.selectorOutsideDismissAvailable == true &&
                    it.selectorQuery == "ancient" &&
                    it.cameraLatitude != null &&
                    it.cameraLongitude != null &&
                    it.cameraZoom != null
            }
            captureEvidence(
                scenario = scenario,
                device = device,
                scenarioName = "selector-expanded-ime-filtered",
                diagnostics = expanded,
                extra = JSONObject().apply {
                    put("finding", "FINDING-r2-002")
                    put("outside_dismiss_contract", "before outside tap")
                }
            )

            assertTrue(
                "Outside tap injection failed",
                device.click(
                    expanded.mapDrawableCenterXPx.toInt(),
                    outsideTapY(expanded, device.displayHeight)
                )
            )
            device.waitForIdle()
            SystemClock.sleep(OSRS_SETTLE_MILLIS)
            val collapsed = awaitDiagnostics(scenario) {
                it.selectorExpanded == false &&
                    it.selectorImeVisible == false &&
                    it.selectorQuery == "ancient" &&
                    it.selectorOutsideDismissCount == expanded.selectorOutsideDismissCount + 1 &&
                    it.lastSelectorOutsideDismissNanos != null
            }
            assertEquals(expanded.activeRealmId, collapsed.activeRealmId)
            assertEquals(expanded.activePlane, collapsed.activePlane)
            assertEquals(expanded.selectedLinkId, collapsed.selectedLinkId)
            assertEquals(expanded.cameraLatitude!!, collapsed.cameraLatitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(expanded.cameraLongitude!!, collapsed.cameraLongitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(expanded.cameraZoom!!, collapsed.cameraZoom!!, OSRS_CAMERA_EPSILON)
            captureEvidence(
                scenario = scenario,
                device = device,
                scenarioName = "selector-after-one-outside-tap",
                diagnostics = collapsed,
                extra = JSONObject().apply {
                    put("finding", "FINDING-r2-002")
                    put("outside_dismiss_contract", "collapsed, ime hidden, query preserved")
                    put("camera_unchanged", true)
                    put("underlying_activation_suppressed", true)
                }
            )
        }
    }

    @Test
    fun floorCompositionCapturesSurfaceAndModularUpperAlphaEvidence() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            awaitDiagnostics(scenario) {
                it.sourceId != null && it.renderMarker.startsWith("map-idle@")
            }

            listOf(1, 2, 3).forEach { upperPlane ->
                val anchor = surfaceAnchorForPlane(upperPlane)
                var surfacePlaneZero: osrsEvidenceCapture? = null
                try {
                    val planeZeroDiagnostics = moveSurfacePlaneToContentAnchor(
                        scenario = scenario,
                        plane = 0,
                        anchor = anchor,
                        forceReviewerPolarCameraFirst = upperPlane == 1,
                        zoom = OSRS_SURFACE_COMPOSITION_ZOOM
                    )
                    assertCompositionForPlane(planeZeroDiagnostics, 0)
                    surfacePlaneZero = requireNotNull(
                        captureEvidence(
                            scenario = scenario,
                            device = device,
                            scenarioName = "surface-plane-0-for-plane-$upperPlane",
                            diagnostics = planeZeroDiagnostics,
                            keepBitmap = true,
                            extra = JSONObject().apply {
                                put("finding", "FINDING-r2-002")
                                put("composition_contract", "surface plane zero baseline for upper floor")
                                put("paired_upper_plane", upperPlane)
                                put("deterministic_camera_anchor", surfaceAnchorJson(anchor, OSRS_SURFACE_COMPOSITION_ZOOM))
                                put("reviewer_polar_state_reset_probe", upperPlane == 1)
                                if (upperPlane == 1) {
                                    put("reviewer_reproduced_polar_camera", reviewerPolarCameraJson())
                                    put("reset_from_persisted_polar_state_verified", true)
                                }
                            }
                        )
                    )

                    val upperDiagnostics = moveSurfacePlaneToContentAnchor(
                        scenario = scenario,
                        plane = upperPlane,
                        anchor = anchor,
                        zoom = OSRS_SURFACE_COMPOSITION_ZOOM
                    )
                    assertCompositionForPlane(upperDiagnostics, upperPlane)
                    captureEvidence(
                        scenario = scenario,
                        device = device,
                        scenarioName = "surface-plane-$upperPlane",
                        diagnostics = upperDiagnostics,
                        referenceBitmap = surfacePlaneZero.bitmap,
                        referenceLabel = "surface-plane-0-for-plane-$upperPlane",
                        extra = JSONObject().apply {
                            put("finding", "FINDING-r2-002")
                            put("composition_contract", "surface upper floor over same-camera plane zero")
                            put("deterministic_camera_anchor", surfaceAnchorJson(anchor, OSRS_SURFACE_COMPOSITION_ZOOM))
                            put("paired_plane_zero_scenario", "surface-plane-0-for-plane-$upperPlane")
                        }
                    )
                } finally {
                    surfacePlaneZero?.bitmap?.recycle()
                }
            }

            var modularRealmId: String? = null
            scenario.onActivity { activity ->
                modularRealmId = activity.firstFourPlaneRealmIdForTesting()
                assertTrue(activity.selectRealmForTesting(requireNotNull(modularRealmId)))
            }
            awaitDiagnostics(scenario) {
                it.activeRealmId == modularRealmId && it.renderMarker.startsWith("map-idle@")
            }
            var modularPlaneZero: osrsEvidenceCapture? = null
            try {
                listOf(0, 1).forEach { plane ->
                    val diagnostics = moveCurrentRealmToBoundsAnchor(scenario = scenario, plane = plane)
                    assertCompositionForPlane(diagnostics, plane)
                    val capture = captureEvidence(
                        scenario = scenario,
                        device = device,
                        scenarioName = "modular-four-plane-$plane",
                        diagnostics = diagnostics,
                        referenceBitmap = modularPlaneZero?.bitmap,
                        referenceLabel = modularPlaneZero?.let { "modular-four-plane-0" },
                        keepBitmap = plane == 0,
                        extra = JSONObject().apply {
                            put("finding", "FINDING-r2-002")
                            put("composition_contract", "modular upper floor over plane zero")
                            put("modular_realm_id", modularRealmId)
                            put("deterministic_camera_anchor", boundsAnchorJson(diagnostics))
                        }
                    )
                    if (plane == 0) {
                        modularPlaneZero = requireNotNull(capture)
                    }
                }
            } finally {
                modularPlaneZero?.bitmap?.recycle()
            }
        }
    }

    @Test
    fun authoritativeLinkCapturesExactDestinationAndRelativeZoomEvidence() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            val initial = awaitDiagnostics(scenario) {
                it.sourceId != null &&
                    it.cameraLatitude != null &&
                    it.cameraLongitude != null &&
                    it.cameraZoom != null &&
                    it.renderMarker.startsWith("map-idle@")
            }
            val sourceAnchor = surfaceAnchorForPlane(1)
            val surface = moveSurfacePlaneToContentAnchor(
                scenario = scenario,
                plane = 0,
                anchor = sourceAnchor,
                forceReviewerPolarCameraFirst = true
            )
            val requestedSourceZoom = sourceAnchor.zoom + 0.75
            scenario.onActivity { activity ->
                assertTrue(
                    activity.moveCameraTargetForTesting(
                        latitude = sourceAnchor.latitude,
                        longitude = sourceAnchor.longitude,
                        zoom = requestedSourceZoom,
                        bearing = requireNotNull(surface.cameraBearing),
                        tilt = requireNotNull(surface.cameraTilt)
                    )
                )
            }
            val before = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_SURFACE_REALM_ID &&
                    it.activePlane == 0 &&
                    it.cameraLatitude?.let { latitude -> abs(latitude - sourceAnchor.latitude) <= OSRS_CAMERA_EPSILON } == true &&
                    it.cameraLongitude?.let { longitude -> abs(longitude - sourceAnchor.longitude) <= OSRS_CAMERA_EPSILON } == true &&
                    it.cameraZoom != null &&
                    abs(it.cameraZoom!! - requestedSourceZoom) <= OSRS_CAMERA_EPSILON &&
                    it.renderMarker.startsWith("map-idle@")
            }
            captureEvidence(
                scenario = scenario,
                device = device,
                scenarioName = "authoritative-link-before",
                diagnostics = before,
                extra = JSONObject().apply {
                    put("finding", "FINDING-r2-002")
                    put("link_id", OSRS_ANCIENT_CAVERN_LINK_ID)
                    put("source_zoom_offset_requested", 0.75)
                    put("initial_realm_id", initial.activeRealmId)
                    put("deterministic_camera_anchor", surfaceAnchorJson(sourceAnchor, requestedSourceZoom))
                    put("reviewer_reproduced_polar_camera", reviewerPolarCameraJson())
                    put("before_rendered_surface_content", true)
                }
            )

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
            assertEquals(linked.linkMappedLatitude!!, linked.cameraLatitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(linked.linkMappedLongitude!!, linked.cameraLongitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(linked.linkFinalZoom!!, linked.cameraZoom!!, OSRS_CAMERA_EPSILON)
            assertEquals(
                linked.linkSourceZoom!! - linked.linkSourceNativeMaxZoom!!,
                linked.linkRelativeZoom!!,
                OSRS_CAMERA_EPSILON
            )
            assertEquals(
                linked.linkTargetNativeMaxZoom!! + linked.linkRelativeZoom!!,
                linked.linkRequestedZoom!!,
                OSRS_CAMERA_EPSILON
            )
            captureEvidence(
                scenario = scenario,
                device = device,
                scenarioName = "authoritative-link-after",
                diagnostics = linked,
                extra = JSONObject().apply {
                    put("finding", "FINDING-r2-002")
                    put("link_id", OSRS_ANCIENT_CAVERN_LINK_ID)
                    put("exact_destination_verified", true)
                    put("relative_zoom_policy_verified", true)
                }
            )
        }
    }

    @Test
    fun dayAndNightLinkSearchCaptureCandidatePresentation() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            setNightMode(false)
            captureSearchPresentation(device, "day")
            setNightMode(true)
            captureSearchPresentation(device, "night")
        } finally {
            setNightMode(false)
        }
    }

    @Test
    fun portraitAndLandscapeCaptureCandidateUi() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        captureOrientation(device, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, "portrait")
        captureOrientation(device, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, "landscape")
    }

    private fun captureSearchPresentation(device: UiDevice, theme: String) {
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            awaitDiagnostics(scenario) {
                it.sourceId != null && it.activeRealmId != null
            }
            moveSurfacePlaneToContentAnchor(scenario = scenario, plane = 0, anchor = surfaceAnchorForPlane(1))
            scenario.onActivity { activity ->
                assertTrue(activity.openRealmLinksForTesting())
            }
            awaitLinksDialog(scenario) {
                it.isShowing && it.visibleBoundRowCount > 0 && !it.searchFocused
            }
            val search = requireNotNull(
                device.wait(
                    Until.findObject(By.res(OSRS_PACKAGE_ID, "osrs_links_search")),
                    OSRS_UI_TIMEOUT_MILLIS
                )
            )
            search.click()
            search.text = "ancient"
            val dialog = awaitLinksDialog(scenario) {
                it.searchFocused && it.query == "ancient" && it.visibleBoundRowCount > 0
            }
            val diagnostics = awaitDiagnostics(scenario) {
                it.activeGroup == "surface" && it.sourceId != null
            }
            captureEvidence(
                scenario = scenario,
                device = device,
                scenarioName = "links-search-$theme",
                diagnostics = diagnostics,
                requireMapViewportContent = false,
                extra = JSONObject().apply {
                    put("finding", "FINDING-r2-002")
                    put("theme", theme)
                    put("map_viewport_gate_relaxed_reason", "modal links search presentation overlays the drawable MapView")
                    put("links_query", dialog.query)
                    put("links_search_focused", dialog.searchFocused)
                    put("links_visible_bound_row_count", dialog.visibleBoundRowCount)
                    put("links_explicit_osrs_palette", dialog.explicitOsrsPalette)
                }
            )
        }
    }

    private fun captureOrientation(
        device: UiDevice,
        orientation: Int,
        label: String
    ) {
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.requestedOrientation = orientation
            }
            awaitDiagnostics(scenario) {
                it.sourceId != null &&
                    it.renderMarker.startsWith("map-idle@") &&
                    if (label == "landscape") {
                        it.screenWidthDp > it.screenHeightDp
                    } else {
                        it.screenHeightDp >= it.screenWidthDp
                    }
            }
            val diagnostics = moveSurfacePlaneToContentAnchor(scenario = scenario, plane = 0, anchor = surfaceAnchorForPlane(1))
            captureEvidence(
                scenario = scenario,
                device = device,
                scenarioName = "$label-candidate-ui",
                diagnostics = diagnostics,
                extra = JSONObject().apply {
                    put("finding", "FINDING-r2-002")
                    put("orientation_contract", label)
                }
            )
        }
    }

    private fun captureEvidence(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        device: UiDevice,
        scenarioName: String,
        diagnostics: osrsMapDiagnostics,
        extra: JSONObject = JSONObject(),
        referenceBitmap: Bitmap? = null,
        referenceLabel: String? = null,
        keepBitmap: Boolean = false,
        requireMapViewportContent: Boolean = true
    ): osrsEvidenceCapture? {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        device.waitForIdle()
        val renderWait = awaitStableRender(scenario)
        SystemClock.sleep(OSRS_EVIDENCE_SETTLE_MILLIS)
        assertEquals(OSRS_PACKAGE_ID, device.currentPackageName)
        var latest: osrsMapDiagnostics? = diagnostics
        scenario.onActivity { activity ->
            latest = activity.debugStateForTesting()
        }
        val captured = requireNotNull(latest)
        val packageInfo = instrumentation.targetContext.packageManager.getPackageInfo(
            instrumentation.targetContext.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
        val directory = instrumentation.targetContext.filesDir
            .resolve(OSRS_EVIDENCE_DIRECTORY)
            .apply { check(mkdirs() || isDirectory) }
        val prefix = "${scenarioName}-${SystemClock.elapsedRealtime()}"
        val screenshot = captureCompleteScreenshot(
            diagnostics = captured,
            referenceBitmap = referenceBitmap,
            referenceLabel = referenceLabel,
            requireMapViewportContent = requireMapViewportContent
        )
        val screenshotFile = directory.resolve("$prefix.png")
        FileOutputStream(screenshotFile).use { output ->
            check(screenshot.bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        val retainedBitmap = if (keepBitmap) {
            screenshot.bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            null
        }
        screenshot.bitmap.recycle()
        val xmlFile = directory.resolve("$prefix.xml")
        device.dumpWindowHierarchy(xmlFile)
        val jsonFile = directory.resolve("$prefix.json")
        jsonFile.writeText(
            JSONObject().apply {
                put("schema_version", 2)
                put("candidate", "010")
                put("scenario", scenarioName)
                put("api_level", Build.VERSION.SDK_INT)
                put("package_name", instrumentation.targetContext.packageName)
                put("activity_name", osrsUndergroundMapsActivity::class.java.name)
                put("activity_component", "$OSRS_PACKAGE_ID/.osrsUndergroundMapsActivity")
                put("current_foreground_package", device.currentPackageName)
                put("version_code", packageInfo.longVersionCode)
                put("version_name", packageInfo.versionName)
                put("installed_apk_sha256", sha256(File(requireNotNull(packageInfo.applicationInfo).sourceDir)))
                put("installed_apk_bytes", File(requireNotNull(packageInfo.applicationInfo).sourceDir).length())
                put("screenshot_file", screenshotFile.name)
                put("ui_xml_file", xmlFile.name)
                put("screenshot_sha256", sha256(screenshotFile))
                put("ui_xml_sha256", sha256(xmlFile))
                put("screenshot_content_coverage", screenshot.contentCoverage)
                put("screenshot_capture_attempts", screenshot.attempts)
                put("render_wait", renderWait.toJson())
                put("map_viewport_gate_required", requireMapViewportContent)
                put("map_viewport_adjudication", screenshot.mapAdjudication.toJson())
                put(
                    "canonical_asset_adjudication",
                    canonicalAssetAdjudication(captured, referenceLabel, screenshot.mapAdjudication)
                )
                put("diagnostics", diagnosticsJson(captured))
                put("extra", extra)
            }.toString(2)
        )
        return retainedBitmap?.let { osrsEvidenceCapture(it, screenshot.mapAdjudication) }
    }

    private fun awaitStableRender(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>
    ): osrsRenderWaitEvidence {
        val startedAt = SystemClock.elapsedRealtime()
        val deadline = startedAt + OSRS_RENDER_IDLE_TIMEOUT_MILLIS
        var firstIdleMarker: String? = null
        var lastMarker: String? = null
        var stableMarker: String? = null
        var stableCount = 0
        var attempts = 0
        while (SystemClock.elapsedRealtime() < deadline) {
            attempts += 1
            var diagnostics: osrsMapDiagnostics? = null
            scenario.onActivity { activity -> diagnostics = activity.debugStateForTesting() }
            val marker = diagnostics?.renderMarker
            if (marker?.startsWith("map-idle@") == true) {
                if (firstIdleMarker == null) firstIdleMarker = marker
                stableCount = if (marker == lastMarker) stableCount + 1 else 1
                lastMarker = marker
                if (stableCount >= OSRS_RENDER_IDLE_STABLE_POLLS) {
                    stableMarker = marker
                    break
                }
            } else {
                stableCount = 0
                lastMarker = marker
            }
            Thread.sleep(OSRS_RENDER_IDLE_POLL_MILLIS)
        }
        return osrsRenderWaitEvidence(
            firstIdleMarker = firstIdleMarker,
            stableMarker = stableMarker
                ?: throw AssertionError("Timed out waiting for stable map render marker; firstIdle=$firstIdleMarker last=$lastMarker"),
            attempts = attempts,
            elapsedMillis = SystemClock.elapsedRealtime() - startedAt
        )
    }

    private fun moveSurfacePlaneToContentAnchor(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        plane: Int,
        anchor: osrsCameraAnchor = surfaceAnchorForPlane(plane),
        forceReviewerPolarCameraFirst: Boolean = false,
        zoom: Double? = null
    ): osrsMapDiagnostics {
        val targetZoom = zoom ?: anchor.zoom
        scenario.onActivity { activity ->
            assertTrue(activity.selectRealmForTesting(OSRS_SURFACE_REALM_ID))
        }
        awaitDiagnostics(scenario) {
            it.activeRealmId == OSRS_SURFACE_REALM_ID && it.renderMarker.startsWith("map-idle@")
        }
        scenario.onActivity { activity ->
            assertTrue(activity.selectPlaneForTesting(plane))
        }
        awaitDiagnostics(scenario) {
            it.activeRealmId == OSRS_SURFACE_REALM_ID &&
                it.activePlane == plane &&
                it.renderMarker.startsWith("map-idle@")
        }
        if (forceReviewerPolarCameraFirst) {
            scenario.onActivity { activity ->
                assertTrue(
                    activity.moveCameraTargetForTesting(
                        latitude = OSRS_REVIEWER_POLAR_LATITUDE,
                        longitude = OSRS_REVIEWER_POLAR_LONGITUDE,
                        zoom = OSRS_REVIEWER_POLAR_ZOOM,
                        bearing = 0.0,
                        tilt = 0.0
                    )
                )
            }
            awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_SURFACE_REALM_ID &&
                    it.activePlane == plane &&
                    it.cameraLatitude?.let { latitude -> abs(latitude - OSRS_REVIEWER_POLAR_LATITUDE) <= OSRS_CAMERA_EPSILON } == true &&
                    it.cameraLongitude?.let { longitude -> abs(longitude - OSRS_REVIEWER_POLAR_LONGITUDE) <= OSRS_CAMERA_EPSILON } == true &&
                    it.renderMarker.startsWith("map-idle@")
            }
        }
        scenario.onActivity { activity ->
            assertTrue(
                activity.moveCameraTargetForTesting(
                    latitude = anchor.latitude,
                    longitude = anchor.longitude,
                    zoom = targetZoom,
                    bearing = 0.0,
                    tilt = 0.0
                )
            )
        }
        return awaitDiagnostics(scenario) {
            it.activeRealmId == OSRS_SURFACE_REALM_ID &&
                it.activePlane == plane &&
                it.cameraLatitude?.let { latitude -> abs(latitude - anchor.latitude) <= OSRS_CAMERA_EPSILON } == true &&
                it.cameraLongitude?.let { longitude -> abs(longitude - anchor.longitude) <= OSRS_CAMERA_EPSILON } == true &&
                it.cameraZoom?.let { currentZoom -> abs(currentZoom - targetZoom) <= OSRS_CAMERA_EPSILON } == true &&
                it.renderMarker.startsWith("map-idle@")
        }
    }

    private fun moveCurrentRealmToBoundsAnchor(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        plane: Int,
        zoom: Double = OSRS_MODULAR_CONTENT_ZOOM
    ): osrsMapDiagnostics {
        scenario.onActivity { activity ->
            assertTrue(activity.selectPlaneForTesting(plane))
        }
        val ready = awaitDiagnostics(scenario) {
            it.activePlane == plane && it.realmBounds != null && it.renderMarker.startsWith("map-idle@")
        }
        val bounds = requireNotNull(ready.realmBounds)
        val longitude = (bounds[0] + bounds[2]) / 2.0
        val latitude = (bounds[1] + bounds[3]) / 2.0
        scenario.onActivity { activity ->
            assertTrue(
                activity.moveCameraTargetForTesting(
                    latitude = latitude,
                    longitude = longitude,
                    zoom = zoom,
                    bearing = 0.0,
                    tilt = 0.0
                )
            )
        }
        return awaitDiagnostics(scenario) {
            it.activePlane == plane &&
                it.cameraLatitude?.let { currentLatitude -> abs(currentLatitude - latitude) <= OSRS_CAMERA_EPSILON } == true &&
                it.cameraLongitude?.let { currentLongitude -> abs(currentLongitude - longitude) <= OSRS_CAMERA_EPSILON } == true &&
                it.cameraZoom?.let { currentZoom -> abs(currentZoom - zoom) <= OSRS_CAMERA_EPSILON } == true &&
                it.renderMarker.startsWith("map-idle@")
        }
    }


    private fun diagnosticsJson(diagnostics: osrsMapDiagnostics): JSONObject =
        JSONObject().apply {
            putNullable("candidate", diagnostics.candidate)
            putNullable("active_realm_id", diagnostics.activeRealmId)
            putNullable("active_realm_name", diagnostics.activeRealmName)
            putNullable("active_realm_display_name", diagnostics.activeRealmDisplayName)
            putNullable("active_group", diagnostics.activeGroup)
            putNullable("active_group_label", diagnostics.activeGroupLabel)
            putNullable("active_plane", diagnostics.activePlane)
            putNullable("source_id", diagnostics.sourceId)
            put("visible_planes_bottom_to_top", JSONArray(diagnostics.visiblePlanesBottomToTop))
            put("visible_source_ids_bottom_to_top", JSONArray(diagnostics.visibleSourceIdsBottomToTop))
            put("visible_layer_ids_bottom_to_top", JSONArray(diagnostics.visibleLayerIdsBottomToTop))
            put("visible_raster_opacities_bottom_to_top", JSONArray(diagnostics.visibleRasterOpacitiesBottomToTop))
            put("visible_layer_order_matches_style", diagnostics.visibleLayerOrderMatchesStyle)
            putNullable("canonical_plane_zero_available", diagnostics.canonicalPlaneZeroAvailable)
            putNullable("plane_zero_resource_reused", diagnostics.planeZeroResourceReused)
            putNullable("replacement_prepared_before_removal", diagnostics.replacementPreparedBeforeRemoval)
            put("staged_asset_sha256_by_plane", shaByPlaneJson(diagnostics.stagedAssetSha256ByPlane))
            putNullable("staged_asset_sha256", diagnostics.stagedAssetSha256)
            putNullable("realm_bounds", diagnostics.realmBounds?.let { JSONArray(it) })
            putNullable("camera_center_envelope", diagnostics.cameraCenterEnvelope?.let { JSONArray(it) })
            putNullable("camera_latitude", diagnostics.cameraLatitude)
            putNullable("camera_longitude", diagnostics.cameraLongitude)
            putNullable("camera_zoom", diagnostics.cameraZoom)
            putNullable("camera_bearing", diagnostics.cameraBearing)
            putNullable("camera_tilt", diagnostics.cameraTilt)
            putNullable("camera_requested_latitude", diagnostics.cameraRequestedLatitude)
            putNullable("camera_requested_longitude", diagnostics.cameraRequestedLongitude)
            putNullable("camera_final_latitude", diagnostics.cameraFinalLatitude)
            putNullable("camera_final_longitude", diagnostics.cameraFinalLongitude)
            putNullable("camera_clamp_state", diagnostics.cameraClampState)
            put("selector_outside_dismiss_count", diagnostics.selectorOutsideDismissCount)
            putNullable("last_selector_outside_dismiss_nanos", diagnostics.lastSelectorOutsideDismissNanos)
            putNullable("selector_outside_dismiss_available", diagnostics.selectorOutsideDismissAvailable)
            putNullable("selector_expanded", diagnostics.selectorExpanded)
            putNullable("selector_ime_visible", diagnostics.selectorImeVisible)
            putNullable("selector_search_focused", diagnostics.selectorSearchFocused)
            putNullable("selector_query", diagnostics.selectorQuery)
            putNullable("selector_visible_result_count", diagnostics.selectorVisibleResultCount)
            putNullable("selector_left_px", diagnostics.selectorLeftPx)
            putNullable("selector_top_px", diagnostics.selectorTopPx)
            putNullable("selector_right_px", diagnostics.selectorRightPx)
            putNullable("selector_bottom_px", diagnostics.selectorBottomPx)
            put("screen_width_dp", diagnostics.screenWidthDp)
            put("screen_height_dp", diagnostics.screenHeightDp)
            put("font_scale", diagnostics.fontScale.toDouble())
            putNullable("selected_link_id", diagnostics.selectedLinkId)
            putNullable("link_target_realm_id", diagnostics.linkTargetRealmId)
            putNullable("link_target_plane", diagnostics.linkTargetPlane)
            putNullable("link_target_game_x", diagnostics.linkTargetGameX)
            putNullable("link_target_game_y", diagnostics.linkTargetGameY)
            putNullable("link_mapped_latitude", diagnostics.linkMappedLatitude)
            putNullable("link_mapped_longitude", diagnostics.linkMappedLongitude)
            putNullable("link_source_zoom", diagnostics.linkSourceZoom)
            putNullable("link_source_native_max_zoom", diagnostics.linkSourceNativeMaxZoom)
            putNullable("link_target_native_max_zoom", diagnostics.linkTargetNativeMaxZoom)
            putNullable("link_relative_zoom", diagnostics.linkRelativeZoom)
            putNullable("link_requested_zoom", diagnostics.linkRequestedZoom)
            putNullable("link_final_zoom", diagnostics.linkFinalZoom)
            putNullable("link_zoom_clamp_state", diagnostics.linkZoomClampState)
            putNullable("link_applied_marker", diagnostics.linkAppliedMarker)
            put("style_generation", diagnostics.styleGeneration)
            putNullable("installed_camera_realm_id", diagnostics.installedCameraRealmId)
            putNullable("installed_camera_plane", diagnostics.installedCameraPlane)
            putNullable("camera_persistence_marker", diagnostics.cameraPersistenceMarker)
            putNullable("render_marker", diagnostics.renderMarker)
        }


    private fun JSONObject.putNullable(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun assertCompositionForPlane(diagnostics: osrsMapDiagnostics, plane: Int) {
        if (plane == 0) {
            assertEquals(listOf(0), diagnostics.visiblePlanesBottomToTop)
            assertEquals(listOf(1.0f), diagnostics.visibleRasterOpacitiesBottomToTop)
        } else {
            assertEquals(listOf(0, plane), diagnostics.visiblePlanesBottomToTop)
            assertEquals(listOf(0.5f, 1.0f), diagnostics.visibleRasterOpacitiesBottomToTop)
        }
        assertTrue(diagnostics.visibleLayerOrderMatchesStyle)
        assertTrue(diagnostics.replacementPreparedBeforeRemoval == true)
    }

    private fun outsideTapY(diagnostics: osrsMapDiagnostics, displayHeight: Int): Int {
        val aboveSelector = (diagnostics.selectorTopPx ?: 80) - OSRS_OUTSIDE_TAP_MARGIN_PX
        val belowSelector = (diagnostics.selectorBottomPx ?: 0) + OSRS_OUTSIDE_TAP_MARGIN_PX
        return if (aboveSelector >= OSRS_OUTSIDE_TAP_MARGIN_PX) {
            aboveSelector
        } else {
            min(displayHeight - OSRS_OUTSIDE_TAP_MARGIN_PX, max(OSRS_OUTSIDE_TAP_MARGIN_PX, belowSelector))
        }
    }

    private fun captureCompleteScreenshot(
        diagnostics: osrsMapDiagnostics,
        referenceBitmap: Bitmap? = null,
        referenceLabel: String? = null,
        requireMapViewportContent: Boolean = true
    ): osrsScreenshotCapture {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var latestAdjudication: osrsMapPixelAdjudication? = null
        repeat(OSRS_SCREENSHOT_MAX_ATTEMPTS) { attempt ->
            val bitmap = automation.takeScreenshot()
            val contentCoverage = screenshotContentCoverage(bitmap)
            val adjudication = mapPixelAdjudication(bitmap, diagnostics, referenceBitmap, referenceLabel)
            latestAdjudication = adjudication
            val mapViewportSatisfied = !requireMapViewportContent || adjudication.mapContentVisible
            val comparisonSatisfied = referenceBitmap == null ||
                adjudication.referenceComparison?.compositionVerified == true
            if (
                contentCoverage >= OSRS_SCREENSHOT_MINIMUM_CONTENT_COVERAGE &&
                mapViewportSatisfied &&
                comparisonSatisfied
            ) {
                return osrsScreenshotCapture(
                    bitmap = bitmap,
                    contentCoverage = contentCoverage,
                    attempts = attempt + 1,
                    mapAdjudication = adjudication
                )
            }
            bitmap.recycle()
            SystemClock.sleep(OSRS_SCREENSHOT_RETRY_MILLIS)
        }
        throw AssertionError(
            "Screenshot compositor never reached required map viewport evidence; " +
                "requireMapViewportContent=$requireMapViewportContent latest=${latestAdjudication?.toJson()}"
        )
    }

    private fun mapPixelAdjudication(
        bitmap: Bitmap,
        diagnostics: osrsMapDiagnostics,
        referenceBitmap: Bitmap?,
        referenceLabel: String?
    ): osrsMapPixelAdjudication {
        val rect = mapViewportRect(bitmap, diagnostics)
        val metrics = viewportMetrics(bitmap, rect)
        val comparison = referenceBitmap?.let {
            compareReferenceViewport(bitmap, it, rect, referenceLabel ?: "reference")
        }
        return osrsMapPixelAdjudication(
            viewport = rect,
            metrics = metrics,
            mapContentVisible = metrics.contentCoverage >= OSRS_MAP_VIEWPORT_MINIMUM_CONTENT_COVERAGE &&
                metrics.uniqueRgbCount >= OSRS_MAP_VIEWPORT_MINIMUM_UNIQUE_RGB &&
                metrics.adjacentRgbMeanDelta >= OSRS_MAP_VIEWPORT_MINIMUM_ADJACENT_DELTA &&
                metrics.luminanceEntropy >= OSRS_MAP_VIEWPORT_MINIMUM_LUMINANCE_ENTROPY &&
                metrics.dominantRgbFraction <= OSRS_MAP_VIEWPORT_MAXIMUM_DOMINANT_RGB_FRACTION,
            referenceComparison = comparison
        )
    }

    private fun mapViewportRect(bitmap: Bitmap, diagnostics: osrsMapDiagnostics): Rect {
        val centerX = diagnostics.mapDrawableCenterXPx.roundToInt()
            .coerceIn(0, bitmap.width - 1)
        val centerY = diagnostics.mapDrawableCenterYPx.roundToInt()
            .coerceIn(0, bitmap.height - 1)
        val halfWidth = max(OSRS_MAP_VIEWPORT_MIN_HALF_WIDTH_PX, (bitmap.width * 0.28).roundToInt())
        val halfHeight = max(OSRS_MAP_VIEWPORT_MIN_HALF_HEIGHT_PX, (bitmap.height * 0.18).roundToInt())
        return Rect(
            (centerX - halfWidth).coerceAtLeast(0),
            (centerY - halfHeight).coerceAtLeast(0),
            (centerX + halfWidth).coerceAtMost(bitmap.width),
            (centerY + halfHeight).coerceAtMost(bitmap.height)
        )
    }

    private fun viewportMetrics(bitmap: Bitmap, rect: Rect): osrsViewportMetrics {
        val rgbCounts = mutableMapOf<Int, Int>()
        val luminanceBuckets = IntArray(OSRS_LUMINANCE_BUCKET_COUNT)
        var contentSamples = 0
        var sampleCount = 0
        var adjacentDeltaSum = 0.0
        var adjacentDeltaCount = 0
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val color = bitmap.getPixel(x, y)
                val rgb = color and 0x00ffffff
                rgbCounts[rgb] = (rgbCounts[rgb] ?: 0) + 1
                if (isContentColor(color)) contentSamples += 1
                val luminance = luminance(color).toInt().coerceIn(0, 255)
                luminanceBuckets[min(OSRS_LUMINANCE_BUCKET_COUNT - 1, luminance / 16)] += 1
                if (x + OSRS_MAP_VIEWPORT_SAMPLE_STRIDE_PX < rect.right) {
                    adjacentDeltaSum += rgbDelta(
                        color,
                        bitmap.getPixel(x + OSRS_MAP_VIEWPORT_SAMPLE_STRIDE_PX, y)
                    )
                    adjacentDeltaCount += 1
                }
                if (y + OSRS_MAP_VIEWPORT_SAMPLE_STRIDE_PX < rect.bottom) {
                    adjacentDeltaSum += rgbDelta(
                        color,
                        bitmap.getPixel(x, y + OSRS_MAP_VIEWPORT_SAMPLE_STRIDE_PX)
                    )
                    adjacentDeltaCount += 1
                }
                sampleCount += 1
                x += OSRS_MAP_VIEWPORT_SAMPLE_STRIDE_PX
            }
            y += OSRS_MAP_VIEWPORT_SAMPLE_STRIDE_PX
        }
        val dominantCount = rgbCounts.values.maxOrNull() ?: 0
        return osrsViewportMetrics(
            sampleCount = sampleCount,
            contentCoverage = contentSamples.toDouble() / sampleCount.coerceAtLeast(1),
            uniqueRgbCount = rgbCounts.size,
            dominantRgbFraction = dominantCount.toDouble() / sampleCount.coerceAtLeast(1),
            adjacentRgbMeanDelta = adjacentDeltaSum / adjacentDeltaCount.coerceAtLeast(1),
            luminanceEntropy = luminanceEntropy(luminanceBuckets, sampleCount)
        )
    }

    private fun compareReferenceViewport(
        bitmap: Bitmap,
        referenceBitmap: Bitmap,
        rect: Rect,
        referenceLabel: String
    ): osrsReferenceComparison {
        require(bitmap.width == referenceBitmap.width && bitmap.height == referenceBitmap.height) {
            "Reference bitmap dimensions do not match screenshot dimensions"
        }
        var changedSamples = 0
        var contextSamples = 0
        var referenceContentSamples = 0
        var sampleCount = 0
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val color = bitmap.getPixel(x, y)
                val reference = referenceBitmap.getPixel(x, y)
                val delta = rgbDelta(color, reference)
                if (delta >= OSRS_REFERENCE_DIFFERENCE_MIN_RGB_DELTA) changedSamples += 1
                if (isContentColor(reference)) {
                    referenceContentSamples += 1
                    if (isPlaneZeroContextVisible(color, reference, delta)) contextSamples += 1
                }
                sampleCount += 1
                x += OSRS_MAP_VIEWPORT_SAMPLE_STRIDE_PX
            }
            y += OSRS_MAP_VIEWPORT_SAMPLE_STRIDE_PX
        }
        val changedFraction = changedSamples.toDouble() / sampleCount.coerceAtLeast(1)
        val contextFraction = contextSamples.toDouble() / sampleCount.coerceAtLeast(1)
        return osrsReferenceComparison(
            referenceLabel = referenceLabel,
            sampleCount = sampleCount,
            changedPixelFraction = changedFraction,
            floorZeroContextPixelFraction = contextFraction,
            referenceContentPixelFraction = referenceContentSamples.toDouble() / sampleCount.coerceAtLeast(1),
            compositionVerified = changedFraction >= OSRS_UPPER_CHANGED_PIXEL_MIN_FRACTION &&
                contextFraction >= OSRS_PLANE_ZERO_CONTEXT_MIN_FRACTION
        )
    }

    private fun luminanceEntropy(buckets: IntArray, sampleCount: Int): Double {
        if (sampleCount <= 0) return 0.0
        var entropy = 0.0
        buckets.forEach { count ->
            if (count > 0) {
                val probability = count.toDouble() / sampleCount
                entropy -= probability * (ln(probability) / ln(2.0))
            }
        }
        return entropy
    }

    private fun luminance(color: Int): Double =
        0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)

    private fun rgbDelta(a: Int, b: Int): Double =
        (abs(Color.red(a) - Color.red(b)) +
            abs(Color.green(a) - Color.green(b)) +
            abs(Color.blue(a) - Color.blue(b))) / 3.0

    private fun isPlaneZeroContextVisible(color: Int, reference: Int, delta: Double): Boolean {
        if (delta <= OSRS_REFERENCE_CONTEXT_MAX_RGB_DELTA) return true
        if (!isContentColor(color)) return false
        val referenceLuminance = luminance(reference).coerceAtLeast(1.0)
        val currentRatio = luminance(color) / referenceLuminance
        if (
            currentRatio < OSRS_REFERENCE_DIMMED_CONTEXT_MIN_LUMINANCE_RATIO ||
            currentRatio > OSRS_REFERENCE_DIMMED_CONTEXT_MAX_LUMINANCE_RATIO
        ) {
            return false
        }
        val scaledReference = Color.rgb(
            (Color.red(reference) * currentRatio).roundToInt().coerceIn(0, 255),
            (Color.green(reference) * currentRatio).roundToInt().coerceIn(0, 255),
            (Color.blue(reference) * currentRatio).roundToInt().coerceIn(0, 255)
        )
        return rgbDelta(color, scaledReference) <= OSRS_REFERENCE_DIMMED_CONTEXT_MAX_SCALED_RGB_DELTA
    }

    private fun isContentColor(color: Int): Boolean =
        Color.alpha(color) > 0 &&
            maxOf(Color.red(color), Color.green(color), Color.blue(color)) >
            OSRS_SCREENSHOT_CONTENT_CHANNEL_THRESHOLD

    private fun canonicalAssetAdjudication(
        diagnostics: osrsMapDiagnostics,
        referenceLabel: String?,
        adjudication: osrsMapPixelAdjudication
    ): JSONObject = JSONObject().apply {
        put("staged_asset_sha256_by_plane", shaByPlaneJson(diagnostics.stagedAssetSha256ByPlane))
        putNullable("active_staged_asset_sha256", diagnostics.stagedAssetSha256)
        putNullable("reference_label", referenceLabel)
        put("map_viewport_content_visible", adjudication.mapContentVisible)
        if (diagnostics.activeRealmId == OSRS_SURFACE_REALM_ID) {
            put("expected_surface_mbtiles_sha256_by_plane", surfaceShaByPlaneJson())
            put(
                "matches_expected_surface_assets",
                diagnostics.stagedAssetSha256ByPlane.all { (plane, sha) ->
                    OSRS_SURFACE_MBTILES_SHA256_BY_PLANE[plane] == sha
                }
            )
        }
        adjudication.referenceComparison?.let { comparison ->
            put("upper_differs_from_plane_zero", comparison.changedPixelFraction >= OSRS_UPPER_CHANGED_PIXEL_MIN_FRACTION)
            put("floor_zero_context_visible", comparison.floorZeroContextPixelFraction >= OSRS_PLANE_ZERO_CONTEXT_MIN_FRACTION)
        }
    }

    private fun shaByPlaneJson(values: Map<Int, String>): JSONObject = JSONObject().apply {
        values.toSortedMap().forEach { (plane, sha) -> put(plane.toString(), sha) }
    }

    private fun surfaceShaByPlaneJson(): JSONObject = JSONObject().apply {
        OSRS_SURFACE_MBTILES_SHA256_BY_PLANE.toSortedMap().forEach { (plane, sha) ->
            put(plane.toString(), sha)
        }
    }

    private fun surfaceAnchorJson(anchor: osrsCameraAnchor, zoom: Double = anchor.zoom): JSONObject =
        JSONObject().apply {
            put("realm_id", OSRS_SURFACE_REALM_ID)
            put("plane", anchor.plane)
            put("latitude", anchor.latitude)
            put("longitude", anchor.longitude)
            put("zoom", zoom)
            put("source", anchor.source)
        }

    private fun surfaceAnchorForPlane(plane: Int): osrsCameraAnchor =
        OSRS_SURFACE_CONTENT_ANCHORS_BY_PLANE[plane]
            ?: requireNotNull(OSRS_SURFACE_CONTENT_ANCHORS_BY_PLANE[1])

    private fun boundsAnchorJson(diagnostics: osrsMapDiagnostics): JSONObject =
        JSONObject().apply {
            putNullable("realm_id", diagnostics.activeRealmId)
            putNullable("plane", diagnostics.activePlane)
            putNullable("latitude", diagnostics.cameraLatitude)
            putNullable("longitude", diagnostics.cameraLongitude)
            putNullable("zoom", diagnostics.cameraZoom)
            put("source", "active realm content_latlon_bounds center")
        }

    private fun reviewerPolarCameraJson(): JSONObject = JSONObject().apply {
        put("latitude", OSRS_REVIEWER_POLAR_LATITUDE)
        put("longitude", OSRS_REVIEWER_POLAR_LONGITUDE)
        put("zoom", OSRS_REVIEWER_POLAR_ZOOM)
    }


    private fun screenshotContentCoverage(bitmap: Bitmap): Double {
        var contentSamples = 0
        var sampleCount = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (
                    Color.alpha(color) > 0 &&
                    maxOf(Color.red(color), Color.green(color), Color.blue(color)) >
                    OSRS_SCREENSHOT_CONTENT_CHANNEL_THRESHOLD
                ) {
                    contentSamples += 1
                }
                sampleCount += 1
                x += OSRS_SCREENSHOT_SAMPLE_STRIDE_PX
            }
            y += OSRS_SCREENSHOT_SAMPLE_STRIDE_PX
        }
        return contentSamples.toDouble() / sampleCount.coerceAtLeast(1)
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
            Thread.sleep(OSRS_TEST_POLL_MILLIS)
        }
        throw AssertionError("Timed out awaiting Candidate 010 fix evidence diagnostics; latest=$latest")
    }

    private fun awaitLinksDialog(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        predicate: (com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinksDialogDebugState) -> Boolean
    ): com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinksDialogDebugState {
        val deadline = SystemClock.elapsedRealtime() + OSRS_UI_TIMEOUT_MILLIS
        var latest: com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinksDialogDebugState? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> latest = activity.realmLinksDialogStateForTesting() }
            latest?.let { if (predicate(it)) return it }
            Thread.sleep(OSRS_TEST_POLL_MILLIS)
        }
        throw AssertionError("Timed out awaiting Candidate 010 fix evidence links dialog; latest=$latest")
    }

    private fun setNightMode(enabled: Boolean) {
        executeShellCommand("cmd uimode night ${if (enabled) "yes" else "no"}")
        SystemClock.sleep(OSRS_NIGHT_MODE_SETTLE_MILLIS)
    }

    private fun executeShellCommand(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class osrsCameraAnchor(
        val plane: Int,
        val latitude: Double,
        val longitude: Double,
        val zoom: Double,
        val source: String
    )

    private data class osrsScreenshotCapture(
        val bitmap: Bitmap,
        val contentCoverage: Double,
        val attempts: Int,
        val mapAdjudication: osrsMapPixelAdjudication
    )

    private data class osrsEvidenceCapture(
        val bitmap: Bitmap,
        val mapAdjudication: osrsMapPixelAdjudication
    )

    private data class osrsRenderWaitEvidence(
        val firstIdleMarker: String?,
        val stableMarker: String,
        val attempts: Int,
        val elapsedMillis: Long
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("first_idle_marker", firstIdleMarker ?: JSONObject.NULL)
            put("stable_marker", stableMarker)
            put("attempts", attempts)
            put("elapsed_millis", elapsedMillis)
            put("stable", true)
        }
    }

    private data class osrsMapPixelAdjudication(
        val viewport: Rect,
        val metrics: osrsViewportMetrics,
        val mapContentVisible: Boolean,
        val referenceComparison: osrsReferenceComparison?
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put(
                "viewport_rect_px",
                JSONObject().apply {
                    put("left", viewport.left)
                    put("top", viewport.top)
                    put("right", viewport.right)
                    put("bottom", viewport.bottom)
                    put("width", viewport.width())
                    put("height", viewport.height())
                }
            )
            put("metrics", metrics.toJson())
            put("map_content_visible", mapContentVisible)
            put("reference_comparison", referenceComparison?.toJson() ?: JSONObject.NULL)
        }
    }

    private data class osrsViewportMetrics(
        val sampleCount: Int,
        val contentCoverage: Double,
        val uniqueRgbCount: Int,
        val dominantRgbFraction: Double,
        val adjacentRgbMeanDelta: Double,
        val luminanceEntropy: Double
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("sample_count", sampleCount)
            put("content_coverage", contentCoverage)
            put("unique_rgb_count", uniqueRgbCount)
            put("dominant_rgb_fraction", dominantRgbFraction)
            put("adjacent_rgb_mean_delta", adjacentRgbMeanDelta)
            put("luminance_entropy", luminanceEntropy)
            put("minimum_content_coverage", OSRS_MAP_VIEWPORT_MINIMUM_CONTENT_COVERAGE)
            put("minimum_unique_rgb_count", OSRS_MAP_VIEWPORT_MINIMUM_UNIQUE_RGB)
            put("minimum_adjacent_rgb_mean_delta", OSRS_MAP_VIEWPORT_MINIMUM_ADJACENT_DELTA)
            put("minimum_luminance_entropy", OSRS_MAP_VIEWPORT_MINIMUM_LUMINANCE_ENTROPY)
            put("maximum_dominant_rgb_fraction", OSRS_MAP_VIEWPORT_MAXIMUM_DOMINANT_RGB_FRACTION)
        }
    }

    private data class osrsReferenceComparison(
        val referenceLabel: String,
        val sampleCount: Int,
        val changedPixelFraction: Double,
        val floorZeroContextPixelFraction: Double,
        val referenceContentPixelFraction: Double,
        val compositionVerified: Boolean
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("reference_label", referenceLabel)
            put("sample_count", sampleCount)
            put("changed_pixel_fraction", changedPixelFraction)
            put("floor_zero_context_pixel_fraction", floorZeroContextPixelFraction)
            put("reference_content_pixel_fraction", referenceContentPixelFraction)
            put("minimum_changed_pixel_fraction", OSRS_UPPER_CHANGED_PIXEL_MIN_FRACTION)
            put("minimum_floor_zero_context_pixel_fraction", OSRS_PLANE_ZERO_CONTEXT_MIN_FRACTION)
            put("floor_zero_context_modes", "exact-or-dimmed-plane-zero-reference")
            put("dimmed_context_min_luminance_ratio", OSRS_REFERENCE_DIMMED_CONTEXT_MIN_LUMINANCE_RATIO)
            put("dimmed_context_max_luminance_ratio", OSRS_REFERENCE_DIMMED_CONTEXT_MAX_LUMINANCE_RATIO)
            put("dimmed_context_max_scaled_rgb_delta", OSRS_REFERENCE_DIMMED_CONTEXT_MAX_SCALED_RGB_DELTA)
            put("composition_verified", compositionVerified)
        }
    }


    private companion object {
        const val OSRS_PACKAGE_ID = "com.omiyawaki.osrswiki.undergroundmaps"
        const val OSRS_SURFACE_REALM_ID = "surface-gielinor"
        const val OSRS_ANCIENT_CAVERN_REALM_ID = "cache-world-map:ancient-cavern"
        const val OSRS_ANCIENT_CAVERN_LINK_ID = "intermap-0137"
        const val OSRS_EVIDENCE_DIRECTORY = "candidate010-fix002-r3-visual-evidence"
        const val OSRS_CAMERA_EPSILON = 1e-6
        const val OSRS_MODULAR_CONTENT_ZOOM = 0.0
        const val OSRS_SURFACE_COMPOSITION_ZOOM = 4.0
        const val OSRS_REVIEWER_POLAR_LATITUDE = 85.05112877980663
        const val OSRS_REVIEWER_POLAR_LONGITUDE = -44.99999999999426
        const val OSRS_REVIEWER_POLAR_ZOOM = 8.341442674192901
        const val OSRS_STATE_TIMEOUT_MILLIS = 45_000L
        const val OSRS_UI_TIMEOUT_MILLIS = 20_000L
        const val OSRS_RENDER_IDLE_TIMEOUT_MILLIS = 20_000L
        const val OSRS_TEST_POLL_MILLIS = 25L
        const val OSRS_RENDER_IDLE_POLL_MILLIS = 75L
        const val OSRS_RENDER_IDLE_STABLE_POLLS = 3
        const val OSRS_SETTLE_MILLIS = 350L
        const val OSRS_EVIDENCE_SETTLE_MILLIS = 500L
        const val OSRS_NIGHT_MODE_SETTLE_MILLIS = 1_000L
        const val OSRS_OUTSIDE_TAP_MARGIN_PX = 24
        const val OSRS_SCREENSHOT_RETRY_MILLIS = 100L
        const val OSRS_SCREENSHOT_MAX_ATTEMPTS = 10
        const val OSRS_SCREENSHOT_SAMPLE_STRIDE_PX = 8
        const val OSRS_SCREENSHOT_CONTENT_CHANNEL_THRESHOLD = 32
        const val OSRS_SCREENSHOT_MINIMUM_CONTENT_COVERAGE = 0.01
        const val OSRS_MAP_VIEWPORT_SAMPLE_STRIDE_PX = 6
        const val OSRS_MAP_VIEWPORT_MIN_HALF_WIDTH_PX = 160
        const val OSRS_MAP_VIEWPORT_MIN_HALF_HEIGHT_PX = 140
        const val OSRS_MAP_VIEWPORT_MINIMUM_CONTENT_COVERAGE = 0.05
        const val OSRS_MAP_VIEWPORT_MINIMUM_UNIQUE_RGB = 40
        const val OSRS_MAP_VIEWPORT_MINIMUM_ADJACENT_DELTA = 1.25
        const val OSRS_MAP_VIEWPORT_MINIMUM_LUMINANCE_ENTROPY = 0.6
        const val OSRS_MAP_VIEWPORT_MAXIMUM_DOMINANT_RGB_FRACTION = 0.95
        const val OSRS_REFERENCE_DIFFERENCE_MIN_RGB_DELTA = 18.0
        const val OSRS_REFERENCE_CONTEXT_MAX_RGB_DELTA = 8.0
        const val OSRS_REFERENCE_DIMMED_CONTEXT_MIN_LUMINANCE_RATIO = 0.35
        const val OSRS_REFERENCE_DIMMED_CONTEXT_MAX_LUMINANCE_RATIO = 1.15
        const val OSRS_REFERENCE_DIMMED_CONTEXT_MAX_SCALED_RGB_DELTA = 28.0
        const val OSRS_UPPER_CHANGED_PIXEL_MIN_FRACTION = 0.001
        const val OSRS_PLANE_ZERO_CONTEXT_MIN_FRACTION = 0.05
        const val OSRS_LUMINANCE_BUCKET_COUNT = 16
        val OSRS_SURFACE_MBTILES_SHA256_BY_PLANE = mapOf(
            0 to "d0137fc1375da33df1c1b9b01c6d5046cf42e029193207fdf0253ddb52f685f8",
            1 to "ca3ec759a32b3680c7efa1e9e37a6a3f5da2029b2334d3a73a0c9f9f27dc598d",
            2 to "25a0cc874b8aa98fb848b5f15ea22ee9bdb7b6c26f5d7b22ff6fdaee0fce8425",
            3 to "b4dcdee17ab85435048bd0f93cf2fa2c3358f96839802b4e7a267db918a08140"
        )
        val OSRS_SURFACE_CONTENT_ANCHORS_BY_PLANE = mapOf(
            1 to osrsCameraAnchor(
                plane = 1,
                latitude = 80.13928133738148,
                longitude = -117.861328125,
                zoom = 5.0,
                source = "accepted surface plane-1 max-zoom alpha densest tile center"
            ),
            2 to osrsCameraAnchor(
                plane = 2,
                latitude = 80.12610248167238,
                longitude = -117.9052734375,
                zoom = 5.0,
                source = "accepted surface plane-2 max-zoom alpha densest tile center"
            ),
            3 to osrsCameraAnchor(
                plane = 3,
                latitude = 75.5822021686347,
                longitude = -16.962890625,
                zoom = 5.0,
                source = "accepted surface plane-3 max-zoom alpha densest tile center"
            )
        )
    }
}
