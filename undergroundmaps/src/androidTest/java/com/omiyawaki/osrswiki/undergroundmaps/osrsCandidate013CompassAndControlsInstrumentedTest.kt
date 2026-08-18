package com.omiyawaki.osrswiki.undergroundmaps

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class osrsCandidate013CompassAndControlsInstrumentedTest {
    @Test
    fun freshRealmSelectionUsesOneSourcePixelScale() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.targetContext
            .getSharedPreferences("osrs_underground_realm_state", 0)
            .edit()
            .clear()
            .commit()

        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            dismissCompatibilityDialogIfPresent()
            val surface = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_SURFACE_REALM_ID &&
                    it.installedCameraRequestId == it.activeSwitchRequestId &&
                    it.cameraZoom != null
            }
            val relativeDefaultZoom = requireNotNull(surface.cameraZoom) - 6.0
            val observed = linkedMapOf<String, Double>()
            listOf(
                Triple("cache-world-map:ancient-cavern", 3.0, "Ancient Cavern"),
                Triple("cache-world-map:ardent-ocean-underground", 3.0, "Ardent Ocean Underground"),
                Triple("cache-world-map:ardougne-underground", 3.0, "Ardougne Underground")
            ).forEach { (realmId, nativeMaximumZoom, name) ->
                scenario.onActivity { activity ->
                    assertTrue("Missing $name", activity.selectRealmForTesting(realmId))
                }
                val selected = awaitDiagnostics(scenario) {
                    it.activeRealmId == realmId &&
                        it.installedCameraRequestId == it.activeSwitchRequestId &&
                        it.cameraZoom != null
                }
                observed[name] = requireNotNull(selected.cameraZoom) - nativeMaximumZoom
            }
            val inconsistent = observed.filterValues { abs(it - relativeDefaultZoom) > 0.01 }
            assertTrue(
                "Fresh realms should retain relative zoom $relativeDefaultZoom; observed=$observed",
                inconsistent.isEmpty()
            )
        }
    }

    @Test
    fun freshSurfaceUsesLegacyVisualScaleAndPressedFloorFillMeetsCardEdge() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val density = instrumentation.targetContext.resources.displayMetrics.density
        instrumentation.targetContext
            .getSharedPreferences("osrs_underground_realm_state", 0)
            .edit()
            .clear()
            .commit()

        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            dismissCompatibilityDialogIfPresent()
            val diagnostics = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_SURFACE_REALM_ID &&
                    it.sourceId != null &&
                    it.installedCameraRequestId == it.activeSwitchRequestId &&
                    it.cameraZoom != null
            }
            assertEquals(OSRS_SCALE_COMPATIBLE_SURFACE_ZOOM, diagnostics.cameraZoom!!, 1e-9)

            val cardLocation = IntArray(2)
            val upLocation = IntArray(2)
            val downLocation = IntArray(2)
            scenario.onActivity { activity ->
                val card = activity.findViewById<View>(R.id.osrs_floor_controls)
                val up = activity.findViewById<View>(R.id.osrs_floor_up)
                val down = activity.findViewById<View>(R.id.osrs_floor_down)
                card.getLocationOnScreen(cardLocation)
                up.getLocationOnScreen(upLocation)
                down.getLocationOnScreen(downLocation)
                assertEquals(cardLocation[1], upLocation[1])
                assertEquals(cardLocation[1] + card.height, downLocation[1] + down.height)
            }

            instrumentation.waitForIdleSync()
            val unpressed = instrumentation.uiAutomation.takeScreenshot()
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.osrs_floor_up).isPressed = true
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(OSRS_RENDER_SETTLE_MILLIS)
            val pressed = instrumentation.uiAutomation.takeScreenshot()

            val sampleX = upLocation[0] + (48 * density).toInt() / 2
            val sampleY = cardLocation[1] + (3 * density).toInt().coerceAtLeast(2)
            assertTrue(
                "Pressed fill did not reach the rounded card edge",
                colorDistance(
                    unpressed.getPixel(sampleX, sampleY),
                    pressed.getPixel(sampleX, sampleY)
                ) >= OSRS_PRESSED_EDGE_MINIMUM_COLOR_DISTANCE
            )

            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.osrs_floor_up).isPressed = false
            }
            unpressed.recycle()
            pressed.recycle()
        }
    }

    @Test
    fun appOwnedCompassStaysInsideFixedBoundsAndResetsNorth() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val density = instrumentation.targetContext.resources.displayMetrics.density
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            dismissCompatibilityDialogIfPresent()
            val initial = awaitDiagnostics(scenario) {
                it.sourceId != null &&
                    it.installedCameraRequestId == it.activeSwitchRequestId &&
                    it.cameraLatitude != null &&
                    it.cameraLongitude != null
            }
            val latitude = requireNotNull(initial.cameraLatitude)
            val longitude = requireNotNull(initial.cameraLongitude)
            val zoom = requireNotNull(initial.cameraZoom)
            val tilt = requireNotNull(initial.cameraTilt)
            assertTrue(initial.compassFacingNorth)
            assertEquals(0.0, requireNotNull(initial.cameraBearing), OSRS_NORTH_EPSILON)

            scenario.onActivity { activity ->
                assertTrue(
                    activity.moveCameraTargetForTesting(
                        latitude = latitude,
                        longitude = longitude,
                        zoom = zoom,
                        bearing = 1.0,
                        tilt = tilt
                    )
                )
            }
            val toleranceEdge = awaitDiagnostics(scenario) {
                angularDifference(it.cameraBearing ?: 0.0, 1.0) <= OSRS_EPSILON &&
                    it.compassFacingNorth
            }
            assertEquals(0f, toleranceEdge.compassWholeViewRotationDegrees, 0f)

            val expectedSize = (48 * density).toInt()
            OSRS_TEST_BEARINGS.forEach { bearing ->
                scenario.onActivity { activity ->
                    assertTrue(
                        activity.moveCameraTargetForTesting(
                            latitude = latitude,
                            longitude = longitude,
                            zoom = zoom,
                            bearing = bearing,
                            tilt = tilt
                        )
                    )
                }
                val rotated = awaitDiagnostics(scenario) {
                    it.compassVisible &&
                        angularDifference(it.cameraBearing ?: 0.0, bearing) <= OSRS_EPSILON &&
                        abs(it.compassNeedleRotationDegrees + bearing.toFloat()) <= OSRS_EPSILON
                }

                assertEquals(expectedSize, rotated.compassRightPx - rotated.compassLeftPx)
                assertEquals(expectedSize, rotated.compassBottomPx - rotated.compassTopPx)
                assertEquals(0f, rotated.compassWholeViewRotationDegrees, 0f)
                assertFalse(rotated.compassFacingNorth)

                // Compass updates are deliberately deferred until MapLibre exits its camera
                // callback. Diagnostics can therefore observe the new logical needle state one
                // frame before the hardware-rendered screenshot contains it.
                instrumentation.waitForIdleSync()
                SystemClock.sleep(OSRS_RENDER_SETTLE_MILLIS)
                var screenshot = instrumentation.uiAutomation.takeScreenshot()
                var screenshotAttempts = 1
                while (
                    !hasCompleteCardinalRing(screenshot, rotated, density) &&
                    screenshotAttempts < OSRS_SCREENSHOT_MAX_ATTEMPTS
                ) {
                    screenshot.recycle()
                    SystemClock.sleep(OSRS_RENDER_SETTLE_MILLIS)
                    instrumentation.waitForIdleSync()
                    screenshot = instrumentation.uiAutomation.takeScreenshot()
                    screenshotAttempts += 1
                }
                writeCompassEvidence(screenshot, rotated, bearing)
                assertTrue(
                    "Compass ring did not render completely after $screenshotAttempts attempts",
                    hasCompleteCardinalRing(screenshot, rotated, density)
                )
                screenshot.recycle()
            }

            scenario.onActivity { activity ->
                assertTrue(
                    activity.findViewById<View>(R.id.osrs_map_compass).performClick()
                )
            }
            val north = awaitDiagnostics(scenario) {
                it.compassFacingNorth &&
                    abs(it.cameraBearing ?: Double.MAX_VALUE) <= OSRS_NORTH_EPSILON
            }
            assertEquals(latitude, requireNotNull(north.cameraLatitude), OSRS_EPSILON)
            assertEquals(longitude, requireNotNull(north.cameraLongitude), OSRS_EPSILON)
            assertEquals(zoom, requireNotNull(north.cameraZoom), OSRS_EPSILON)
            assertEquals(tilt, requireNotNull(north.cameraTilt), OSRS_EPSILON)
            assertEquals(0f, north.compassWholeViewRotationDegrees, 0f)

            val faded = awaitDiagnostics(scenario, timeoutMillis = 3_000L) {
                it.compassFacingNorth && !it.compassVisible
            }
            assertFalse(faded.compassVisible)
        }
    }

    @Test
    fun compassAndFloorUseSymmetricVisibleInsetsWhileMapLinksActionIsHidden() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            dismissCompatibilityDialogIfPresent()
            awaitDiagnostics(scenario) {
                it.sourceId != null && it.installedCameraRequestId == it.activeSwitchRequestId
            }
            scenario.onActivity { activity ->
                assertTrue(activity.selectRealmForTesting(OSRS_GOD_WARS_REALM_ID))
            }
            val diagnostics = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_GOD_WARS_REALM_ID &&
                    it.floorControlVisible &&
                    it.compassRightPx > it.compassLeftPx
            }

            val expectedWidth = (48 * density).toInt()
            val expectedRadius = (22 * density).toInt()
            val floorWidth = diagnostics.floorControlRightPx - diagnostics.floorControlLeftPx
            assertEquals(expectedWidth, floorWidth)
            assertEquals(
                expectedRadius.toFloat(),
                diagnostics.floorControlCornerRadiusPx,
                0.5f
            )
            assertEquals(expectedWidth, diagnostics.floorUpWidthPx)
            assertEquals(expectedWidth, diagnostics.floorUpHeightPx)
            assertEquals(expectedWidth, diagnostics.floorDownWidthPx)
            assertEquals(expectedWidth, diagnostics.floorDownHeightPx)
            assertEquals(diagnostics.floorControlTopPx, diagnostics.compassTopPx)
            val selectorSideMargin = requireNotNull(diagnostics.selectorLeftPx)
            assertTrue(
                "Floor/selector left margins differ: " +
                    "floor=${diagnostics.floorLeftVisualMarginPx}, " +
                    "selector=$selectorSideMargin",
                abs(diagnostics.floorLeftVisualMarginPx - selectorSideMargin) <= 1
            )
            assertTrue(
                "Compass/selector right margins differ: " +
                    "compass=${diagnostics.compassRightVisualMarginPx}, " +
                    "selector=$selectorSideMargin",
                abs(diagnostics.compassRightVisualMarginPx - selectorSideMargin) <= 1
            )
            assertTrue(
                "Visible left/right control margins differ: " +
                    "floor=${diagnostics.floorLeftVisualMarginPx}, " +
                    "compass=${diagnostics.compassRightVisualMarginPx}",
                abs(
                    diagnostics.floorLeftVisualMarginPx -
                        diagnostics.compassRightVisualMarginPx
                ) <= 1
            )
            assertFalse(diagnostics.realmLinksUiEnabled)
            assertFalse(diagnostics.realmLinksActionVisible)
            assertFalse(device.hasObject(By.res(OSRS_PACKAGE_ID, "osrs_realm_links")))
            scenario.onActivity { activity ->
                assertNull(activity.linksActionAccessibilityTextForTesting())
            }
        }
    }

    private fun hasCompleteCardinalRing(
        bitmap: Bitmap,
        diagnostics: osrsMapDiagnostics,
        density: Float
    ): Boolean {
        val centerX = (diagnostics.compassLeftPx + diagnostics.compassRightPx) / 2
        val centerY = (diagnostics.compassTopPx + diagnostics.compassBottomPx) / 2
        val expectedRadius =
            (diagnostics.compassRightPx - diagnostics.compassLeftPx) / 2f - 2f * density
        val radialTolerance = ceil(2f * density).toInt()
        val tangentTolerance = ceil(density).toInt()
        return listOf(
            -1 to 0,
            1 to 0,
            0 to -1,
            0 to 1
        ).all { (directionX, directionY) ->
            (
                expectedRadius.toInt() - radialTolerance..
                    expectedRadius.toInt() + radialTolerance
                ).any { radialOffset ->
                    (-tangentTolerance..tangentTolerance).any { tangentOffset ->
                        val x = centerX + directionX * radialOffset - directionY * tangentOffset
                        val y = centerY + directionY * radialOffset + directionX * tangentOffset
                        x in 0 until bitmap.width &&
                            y in 0 until bitmap.height &&
                            isWhite(bitmap.getPixel(x, y))
                    }
                }
        }
    }

    private fun isWhite(color: Int): Boolean =
        Color.red(color) >= 200 && Color.green(color) >= 200 && Color.blue(color) >= 200

    private fun dismissCompatibilityDialogIfPresent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        device.findObject(By.res("android", "button1"))?.click()
        instrumentation.waitForIdleSync()
    }

    private fun colorDistance(left: Int, right: Int): Int =
        abs(Color.red(left) - Color.red(right)) +
            abs(Color.green(left) - Color.green(right)) +
            abs(Color.blue(left) - Color.blue(right))

    private fun writeCompassEvidence(
        bitmap: Bitmap,
        diagnostics: osrsMapDiagnostics,
        requestedBearing: Double
    ) {
        val directory = requireNotNull(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        ).resolve(OSRS_EVIDENCE_DIRECTORY).apply {
            check(mkdirs() || isDirectory)
        }
        val label = requestedBearing.toInt().toString()
        FileOutputStream(directory.resolve("bearing-$label.png")).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        directory.resolve("bearing-$label.json").writeText(
            JSONObject().apply {
                put("requested_bearing", requestedBearing)
                put("camera_bearing", diagnostics.cameraBearing)
                put("compass_left_px", diagnostics.compassLeftPx)
                put("compass_top_px", diagnostics.compassTopPx)
                put("compass_right_px", diagnostics.compassRightPx)
                put("compass_bottom_px", diagnostics.compassBottomPx)
                put("needle_rotation_degrees", diagnostics.compassNeedleRotationDegrees.toDouble())
                put("whole_view_rotation_degrees", diagnostics.compassWholeViewRotationDegrees.toDouble())
                put("visible", diagnostics.compassVisible)
                put("facing_north", diagnostics.compassFacingNorth)
            }.toString(2)
        )
    }

    private fun angularDifference(first: Double, second: Double): Double {
        val raw = abs((first - second) % 360.0)
        return minOf(raw, 360.0 - raw)
    }

    private fun awaitDiagnostics(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        timeoutMillis: Long = OSRS_TIMEOUT_MILLIS,
        predicate: (osrsMapDiagnostics) -> Boolean
    ): osrsMapDiagnostics {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var latest: osrsMapDiagnostics? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> latest = activity.debugStateForTesting() }
            latest?.let { if (predicate(it)) return it }
            Thread.sleep(50L)
        }
        throw AssertionError("Timed out awaiting r13 diagnostics; latest=$latest")
    }

    private companion object {
        const val OSRS_SURFACE_REALM_ID = "surface-gielinor"
        const val OSRS_SCALE_COMPATIBLE_SURFACE_ZOOM = 6.3414426741929
        const val OSRS_PRESSED_EDGE_MINIMUM_COLOR_DISTANCE = 80
        const val OSRS_GOD_WARS_REALM_ID = "cache-world-map:godwars"
        val OSRS_TEST_BEARINGS = listOf(23.0, 45.0, 90.0, 137.0, 225.0, 315.0)
        const val OSRS_EPSILON = 0.01
        const val OSRS_NORTH_EPSILON = 1.0
        const val OSRS_TIMEOUT_MILLIS = 60_000L
        const val OSRS_RENDER_SETTLE_MILLIS = 100L
        const val OSRS_SCREENSHOT_MAX_ATTEMPTS = 20
        const val OSRS_EVIDENCE_DIRECTORY = "candidate010-r13-compass"
        const val OSRS_PACKAGE_ID = "com.omiyawaki.osrswiki.undergroundmaps"
    }
}
