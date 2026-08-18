package com.omiyawaki.osrswiki.undergroundmaps

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.ceil

@RunWith(AndroidJUnit4::class)
class osrsDirection3SelectorInstrumentedTest {
    @Test
    fun exactCandidateRuntimeIsPinned() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assertEquals(
            "Exact Direction 3 release gate argument is mandatory",
            "true",
            arguments.getString(OSRS_EXACT_GATE_ARGUMENT)
        )
        assertEquals(34, Build.VERSION.SDK_INT)
        val expectedApkSha256 = requireNotNull(
            arguments.getString(OSRS_EXACT_APK_SHA_ARGUMENT)
        ) { "Exact Direction 3 APK SHA-256 argument is mandatory" }
        val expectedApkBytes = requireNotNull(
            arguments.getString(OSRS_EXACT_APK_BYTES_ARGUMENT)?.toLongOrNull()
        ) { "Exact Direction 3 APK byte-size argument is mandatory" }
        val expectedSignerSha256 = requireNotNull(
            arguments.getString(OSRS_EXACT_APK_SIGNER_SHA_ARGUMENT)
        ) { "Exact Direction 3 signer SHA-256 argument is mandatory" }
        assertTrue(expectedApkSha256.matches(Regex("^[0-9a-f]{64}$")))
        assertTrue(expectedSignerSha256.matches(Regex("^[0-9a-f]{64}$")))

        val targetContext = instrumentation.targetContext
        val packageInfo = targetContext.packageManager.getPackageInfo(
            targetContext.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
        assertEquals(OSRS_PACKAGE_ID, targetContext.packageName)
        assertEquals(10L, packageInfo.longVersionCode)
        assertEquals("0.10.0-candidate-010", packageInfo.versionName)
        val installedBase = File(requireNotNull(packageInfo.applicationInfo).sourceDir)
        assertEquals(expectedApkBytes, installedBase.length())
        assertEquals(expectedApkSha256, sha256(installedBase))
        val signerDigests = requireNotNull(packageInfo.signingInfo)
            .apkContentsSigners
            .map { certificate -> sha256(certificate.toByteArray()) }
            .sorted()
        assertEquals(listOf(expectedSignerSha256), signerDigests)
    }

    @Test
    fun pickerUsesOneGeometryAndOnlyShowsImeAfterExplicitSearchFocus() {
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            val collapsed = awaitDiagnostics(scenario) {
                it.sourceId != null &&
                    it.selectorExpanded == false &&
                    it.selectorImeVisible == false &&
                    it.selectorLeftPx != null
            }
            assertEquals(collapsed.selectorBottomPx, collapsed.selectorBaseRowBottomPx)

            clickSelectorBaseRow(scenario)
            val expanded = awaitDiagnostics(scenario) {
                it.selectorExpanded == true &&
                    it.selectorImeVisible == false &&
                    it.selectorSearchFocused == false
            }
            assertSameHorizontalBounds(collapsed, expanded)
            assertEquals(collapsed.selectorBottomPx, expanded.selectorBottomPx)
            assertEquals(expanded.selectorBottomPx, expanded.selectorBaseRowBottomPx)
            assertEquals(collapsed.selectorActiveViewportBottomGapPx, expanded.selectorActiveViewportBottomGapPx)
            assertEquals(expanded.manifestRealmCount, expanded.selectorVisibleResultCount)
            assertTrue(requireNotNull(expanded.selectorTopPx) < requireNotNull(collapsed.selectorTopPx))
            assertTrue((expanded.lastSelectorToggleNanos ?: Long.MAX_VALUE) < OSRS_SIMPLE_CONTROL_BUDGET_NANOS)
            assertSearchTouchTarget(expanded)
            assertFalse(expanded.statusVisible)
            assertEquals(null, expanded.selectorAndStatusSeparated)

            scenario.onActivity { activity ->
                activity.showErrorForTesting()
            }
            val errorWithoutIme = awaitDiagnostics(scenario) {
                it.selectorExpanded == true &&
                    it.selectorImeVisible == false &&
                    it.statusVisible &&
                    it.selectorAndStatusSeparated == true
            }
            assertStatusSeparation(errorWithoutIme)
            captureRuntimeEvidence(scenario, "error-ime-hidden")

            scenario.onActivity { activity ->
                assertTrue(activity.focusRealmSelectorSearchForTesting())
            }
            val withIme = awaitDiagnostics(scenario) {
                it.selectorExpanded == true &&
                    it.selectorImeVisible == true &&
                    it.selectorSearchFocused == true
            }
            assertSameHorizontalBounds(collapsed, withIme)
            assertTrue(requireNotNull(withIme.selectorBottomPx) < requireNotNull(collapsed.selectorBottomPx))
            assertEquals(withIme.selectorBottomPx, withIme.selectorBaseRowBottomPx)
            assertEquals(collapsed.selectorActiveViewportBottomGapPx, withIme.selectorActiveViewportBottomGapPx)
            assertTrue(requireNotNull(withIme.selectorSearchTopPx) >= requireNotNull(withIme.selectorTopPx))
            assertTrue(requireNotNull(withIme.selectorSearchBottomPx) <= requireNotNull(withIme.selectorBottomPx))
            assertTrue(requireNotNull(withIme.selectorListBottomPx) <= requireNotNull(withIme.selectorBaseRowTopPx))
            assertTrue(withIme.topAndFloorControlsSeparated != false)
            assertTrue(withIme.selectorAndLinksSeparated != false)
            assertSearchTouchTarget(withIme)
            assertTrue(withIme.statusVisible)
            assertStatusSeparation(withIme)
            captureRuntimeEvidence(scenario, "error-ime-visible")

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
            val keyboardDismissed = awaitDiagnostics(scenario) {
                it.selectorExpanded == true &&
                    it.selectorImeVisible == false &&
                    it.selectorSearchFocused == false
            }
            assertSameHorizontalBounds(collapsed, keyboardDismissed)
            assertEquals(collapsed.selectorBottomPx, keyboardDismissed.selectorBottomPx)
            assertStatusSeparation(keyboardDismissed)

            scenario.onActivity { activity ->
                activity.clearErrorForTesting()
            }
            val errorCleared = awaitDiagnostics(scenario) {
                it.selectorExpanded == true &&
                    it.selectorImeVisible == false &&
                    !it.statusVisible &&
                    it.selectorAndStatusSeparated == null
            }
            assertEquals(null, errorCleared.selectorStatusSeparationPx)

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
            awaitDiagnostics(scenario) {
                it.selectorExpanded == false && it.selectorImeVisible == false
            }
        }
    }

    @Test
    fun expandedQueryAndSearchFocusSurviveActivityRecreation() {
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            awaitDiagnostics(scenario) { it.sourceId != null && it.selectorExpanded == false }
            scenario.onActivity { activity ->
                assertTrue(activity.openRealmSelectorForTesting())
                assertTrue(activity.filterRealmSelectorForTesting("trawler"))
            }
            val before = awaitDiagnostics(scenario) {
                it.selectorExpanded == true &&
                    it.selectorQuery == "trawler" &&
                    it.selectorSearchFocused == false &&
                    it.selectorVisibleResultCount == 3
            }

            scenario.recreate()
            val restoredUnfocused = awaitDiagnostics(scenario) {
                it.sourceId != null &&
                    it.selectorExpanded == true &&
                    it.selectorQuery == "trawler" &&
                    it.selectorSearchFocused == false
            }
            assertEquals(before.selectorLeftPx, restoredUnfocused.selectorLeftPx)
            assertEquals(before.selectorRightPx, restoredUnfocused.selectorRightPx)
            assertEquals(
                before.selectorActiveViewportBottomGapPx,
                restoredUnfocused.selectorActiveViewportBottomGapPx
            )

            scenario.onActivity { activity ->
                assertTrue(activity.focusRealmSelectorSearchForTesting())
            }
            awaitDiagnostics(scenario) {
                it.selectorSearchFocused == true && it.selectorImeVisible == true
            }
            scenario.recreate()
            awaitDiagnostics(scenario) {
                it.sourceId != null &&
                    it.selectorExpanded == true &&
                    it.selectorQuery == "trawler" &&
                    it.selectorSearchFocused == true &&
                    it.selectorImeVisible == true
            }
        }
    }

    @Test
    fun repeatedUserPathToggleAndVerticalFloorControlStayResponsive() {
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            awaitDiagnostics(scenario) { it.sourceId != null }
            var singlePlaneRealmId: String? = null
            scenario.onActivity { activity ->
                singlePlaneRealmId = activity.firstSinglePlaneRealmIdForTesting()
                assertTrue(
                    requireNotNull(singlePlaneRealmId).let(activity::selectRealmForTesting)
                )
            }
            val singlePlane = awaitDiagnostics(scenario) {
                it.sourceId != null &&
                    it.activeRealmId == singlePlaneRealmId &&
                    it.selectorExpanded == false &&
                    it.selectorImeVisible == false
            }
            assertFalse(
                "A canonical single-plane realm must not expose floor controls",
                singlePlane.floorControlVisible
            )
            val openSamples = mutableListOf<Long>()
            repeat(OSRS_REPEATED_OPEN_COUNT) {
                clickSelectorBaseRow(scenario)
                val opened = awaitDiagnostics(scenario) {
                    it.selectorExpanded == true && it.selectorImeVisible == false
                }
                openSamples += requireNotNull(opened.lastSelectorToggleNanos)
                clickSelectorBaseRow(scenario)
                awaitDiagnostics(scenario) { it.selectorExpanded == false }
            }
            assertTrue(
                "Repeated selector-open p95 exceeded 50 ms: ${nearestRankP95(openSamples)} ns",
                nearestRankP95(openSamples) < OSRS_SIMPLE_CONTROL_BUDGET_NANOS
            )
            println(
                "osrs_direction3_selector_toggle realms=" +
                    "${awaitDiagnostics(scenario) { it.manifestRealmCount != null }.manifestRealmCount} " +
                    "samples=${openSamples.size} p95Nanos=${nearestRankP95(openSamples)} " +
                    "maxNanos=${openSamples.max()}"
            )

            var multiPlaneRealmId: String? = null
            var lowestPlane: Int? = null
            scenario.onActivity { activity ->
                multiPlaneRealmId = activity.firstMultiPlaneRealmIdForTesting()
                lowestPlane = multiPlaneRealmId?.let(activity::lowestPlaneForRealmForTesting)
            }
            assertNotNull("Direction 3 fixture needs a multi-plane realm", multiPlaneRealmId)
            assertNotNull("Multi-plane realm needs a lowest floor", lowestPlane)
            val realmId = requireNotNull(multiPlaneRealmId)
            scenario.onActivity { activity ->
                assertTrue(activity.selectRealmForTesting(realmId))
                assertTrue(activity.selectPlaneForTesting(requireNotNull(lowestPlane)))
            }
            val floorZero = awaitDiagnostics(scenario) {
                it.activeRealmId == realmId &&
                    it.sourceId != null &&
                    it.floorControlVisible
            }
            assertTrue(floorZero.topAndFloorControlsSeparated != false)
            assertTrue(floorZero.floorControlBottomPx > floorZero.floorControlTopPx)
            assertFloorTouchTargets(floorZero)

            scenario.onActivity { activity ->
                val floorUp = requireNotNull(
                    activity.findViewById<View>(R.id.osrs_floor_up)
                )
                assertTrue(floorUp.isEnabled)
                assertTrue(floorUp.contentDescription.contains("next floor"))
                assertTrue(floorUp.performClick())
            }
            val nextFloor = awaitDiagnostics(scenario) {
                it.activeRealmId == realmId &&
                    it.activePlane != floorZero.activePlane &&
                    it.floorControlVisible
            }
            assertFloorTouchTargets(nextFloor)

            var longestRealmId: String? = null
            scenario.onActivity { activity ->
                longestRealmId = activity.longestRealmIdForTesting()
                assertTrue(
                    requireNotNull(longestRealmId).let(activity::selectRealmForTesting)
                )
            }
            val longestIdentity = awaitDiagnostics(scenario) {
                it.activeRealmId == longestRealmId &&
                    it.sourceId != null &&
                    it.selectorIdentityHonest == true
            }
            assertTrue(
                requireNotNull(longestIdentity.selectorIdentityAccessibilityText)
                    .contains(requireNotNull(longestIdentity.activeRealmDisplayName))
            )
        }
    }

    @Test
    fun landscapePickerKeepsRelationalGeometryAndControlSeparation() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            executeShellCommand("wm fixed-to-user-rotation enabled")
            executeShellCommand("wm user-rotation lock 1")
            device.waitForIdle()
            ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
                awaitDiagnostics(scenario) {
                    it.sourceId != null && it.screenWidthDp > it.screenHeightDp
                }
                var singlePlaneRealmId: String? = null
                scenario.onActivity { activity ->
                    singlePlaneRealmId = activity.firstSinglePlaneRealmIdForTesting()
                    assertTrue(
                        requireNotNull(singlePlaneRealmId).let(activity::selectRealmForTesting)
                    )
                }
                val collapsed = awaitDiagnostics(scenario) {
                    it.sourceId != null &&
                        it.activeRealmId == singlePlaneRealmId &&
                        it.screenWidthDp > it.screenHeightDp &&
                        it.selectorExpanded == false &&
                        it.selectorLeftPx != null
                }
                assertFalse(
                    "A canonical single-plane realm must not expose floor controls in landscape",
                    collapsed.floorControlVisible
                )
                clickSelectorBaseRow(scenario)
                val expanded = awaitDiagnostics(scenario) {
                    it.screenWidthDp > it.screenHeightDp &&
                        it.selectorExpanded == true &&
                        it.selectorImeVisible == false
                }
                assertSameHorizontalBounds(collapsed, expanded)
                assertEquals(collapsed.selectorBottomPx, expanded.selectorBottomPx)
                assertEquals(expanded.selectorBottomPx, expanded.selectorBaseRowBottomPx)
                assertSearchTouchTarget(expanded)
                assertFalse(expanded.statusVisible)

                var multiPlaneRealmId: String? = null
                scenario.onActivity { activity ->
                    multiPlaneRealmId = activity.firstMultiPlaneRealmIdForTesting()
                    assertTrue(
                        requireNotNull(multiPlaneRealmId).let(activity::selectRealmForTesting)
                    )
                }
                val landscapeControls = awaitDiagnostics(scenario) {
                    it.activeRealmId == multiPlaneRealmId &&
                        it.sourceId != null &&
                        it.selectorExpanded == true &&
                        it.selectorImeVisible == false &&
                        it.floorControlVisible
                }
                assertFloorTouchTargets(landscapeControls)
                assertTrue(landscapeControls.topAndFloorControlsSeparated == true)
                scenario.onActivity { activity ->
                    activity.showErrorForTesting()
                }
                val landscapeControlsAndError = awaitDiagnostics(scenario) {
                    it.activeRealmId == multiPlaneRealmId &&
                        it.selectorExpanded == true &&
                        it.floorControlVisible &&
                        it.statusVisible &&
                        it.selectorAndStatusSeparated == true
                }
                assertFloorTouchTargets(landscapeControlsAndError)
                assertStatusSeparation(landscapeControlsAndError)
                assertTrue(landscapeControlsAndError.topAndFloorControlsSeparated == true)
                captureRuntimeEvidence(scenario, "error-ime-hidden")

                scenario.onActivity { activity ->
                    assertEquals(
                        Configuration.ORIENTATION_LANDSCAPE,
                        activity.resources.configuration.orientation
                    )
                    assertTrue(activity.focusRealmSelectorSearchForTesting())
                }
                val withIme = awaitDiagnostics(scenario) {
                    it.selectorExpanded == true &&
                        it.selectorImeVisible == true &&
                        it.selectorSearchFocused == true
                }
                assertSameHorizontalBounds(collapsed, withIme)
                assertEquals(withIme.selectorBottomPx, withIme.selectorBaseRowBottomPx)
                assertEquals(
                    collapsed.selectorActiveViewportBottomGapPx,
                    withIme.selectorActiveViewportBottomGapPx
                )
                assertTrue(requireNotNull(withIme.selectorSearchTopPx) >= requireNotNull(withIme.selectorTopPx))
                assertTrue(requireNotNull(withIme.selectorListBottomPx) <= requireNotNull(withIme.selectorBaseRowTopPx))
                assertTrue(withIme.topAndFloorControlsSeparated != false)
                assertTrue(withIme.selectorAndLinksSeparated != false)
                assertTrue(withIme.selectorIdentityHonest == true)
                assertSearchTouchTarget(withIme)
                assertFloorTouchTargets(withIme)
                assertActionableVisibleResult(withIme)
                assertTrue(withIme.compactLandscapeImeChrome)
                assertTrue(
                    "Floor controls must remain above the landscape IME",
                    withIme.floorControlBottomPx <=
                        requireNotNull(withIme.selectorBottomPx) +
                        requireNotNull(withIme.selectorActiveViewportBottomGapPx)
                )
                assertStatusSeparation(withIme)

                scenario.onActivity { activity ->
                    activity.onBackPressedDispatcher.onBackPressed()
                }
                val imeDismissed = awaitDiagnostics(scenario) {
                    it.selectorExpanded == true &&
                        it.selectorImeVisible == false &&
                        it.floorControlVisible &&
                        it.statusVisible &&
                        it.selectorAndStatusSeparated == true
                }
                assertSearchTouchTarget(imeDismissed)
                assertFloorTouchTargets(imeDismissed)
                assertStatusSeparation(imeDismissed)

                scenario.onActivity { activity ->
                    assertTrue(activity.focusRealmSelectorSearchForTesting())
                }
                awaitDiagnostics(scenario) {
                    it.selectorExpanded == true &&
                        it.selectorImeVisible == true &&
                        it.selectorSearchFocused == true &&
                        it.floorControlVisible &&
                        it.statusVisible
                }
                val searchObject = device.findObject(
                    UiSelector().resourceId("$OSRS_PACKAGE_ID:id/osrs_selector_search")
                )
                assertTrue("Landscape search field must exist", searchObject.waitForExists(5_000L))
                assertTrue(searchObject.setText(OSRS_LANDSCAPE_RESULT_QUERY))
                val queried = awaitDiagnostics(scenario) {
                    it.selectorExpanded == true &&
                        it.selectorImeVisible == true &&
                        it.selectorSearchFocused == true &&
                        it.selectorQuery == OSRS_LANDSCAPE_RESULT_QUERY &&
                        requireNotNull(it.selectorVisibleResultCount) > 0 &&
                        it.selectorFirstResultText != null
                }
                assertSameHorizontalBounds(collapsed, queried)
                assertEquals(queried.selectorBottomPx, queried.selectorBaseRowBottomPx)
                assertEquals(
                    collapsed.selectorActiveViewportBottomGapPx,
                    queried.selectorActiveViewportBottomGapPx
                )
                assertSearchTouchTarget(queried)
                assertFloorTouchTargets(queried)
                assertStatusSeparation(queried)
                assertActionableVisibleResult(queried)
                captureRuntimeEvidence(scenario, "error-ime-visible")

                val priorRealmId = requireNotNull(queried.activeRealmId)
                val expectedRealmName = requireNotNull(queried.selectorFirstResultText)
                val resultObject = device.findObject(
                    UiSelector()
                        .resourceId("$OSRS_PACKAGE_ID:id/osrs_selector_result")
                        .instance(0)
                )
                assertTrue(
                    "A virtualized landscape realm result must exist",
                    resultObject.waitForExists(5_000L)
                )
                val resultBounds = resultObject.bounds
                val minimumTargetPx = minimumTouchTargetPx()
                assertTrue(resultBounds.width() >= minimumTargetPx)
                assertTrue(resultBounds.height() >= minimumTargetPx)
                assertEquals(expectedRealmName, resultObject.text)
                assertTrue("Visible realm result must activate", resultObject.click())
                val selected = awaitDiagnostics(scenario) {
                    it.activeRealmId != null &&
                        it.activeRealmId != priorRealmId &&
                        it.activeRealmDisplayName == expectedRealmName &&
                        it.sourceId != null &&
                        it.selectorExpanded == false
                }
                assertEquals(expectedRealmName, selected.activeRealmDisplayName)
                println(
                    "osrs_direction3_landscape_result_selection " +
                        "fontScale=${selected.fontScale} from=$priorRealmId " +
                        "to=${selected.activeRealmId} resultBounds=$resultBounds"
                )
                scenario.onActivity { activity -> activity.clearErrorForTesting() }
            }
        } finally {
            executeShellCommand("wm user-rotation lock 0")
            executeShellCommand("wm fixed-to-user-rotation default")
            executeShellCommand("wm user-rotation free")
            device.waitForIdle()
        }
    }

    private fun clickSelectorBaseRow(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>
    ) {
        scenario.onActivity { activity ->
            val selector = requireNotNull(
                activity.findViewById<View>(R.id.osrs_realm_selector)
            )
            assertTrue(selector.performClick())
        }
    }

    private fun assertSameHorizontalBounds(
        expected: osrsMapDiagnostics,
        actual: osrsMapDiagnostics
    ) {
        assertEquals(expected.selectorLeftPx, actual.selectorLeftPx)
        assertEquals(expected.selectorRightPx, actual.selectorRightPx)
    }

    private fun assertSearchTouchTarget(diagnostics: osrsMapDiagnostics) {
        val minimumPx = minimumTouchTargetPx()
        val width = requireNotNull(diagnostics.selectorSearchRightPx) -
            requireNotNull(diagnostics.selectorSearchLeftPx)
        val height = requireNotNull(diagnostics.selectorSearchBottomPx) -
            requireNotNull(diagnostics.selectorSearchTopPx)
        assertTrue("Search target width $width px is below $minimumPx px", width >= minimumPx)
        assertTrue("Search target height $height px is below $minimumPx px", height >= minimumPx)
        assertEquals(true, diagnostics.selectorSearchClickable)
        assertEquals(true, diagnostics.selectorSearchFocusable)
    }

    private fun assertActionableVisibleResult(diagnostics: osrsMapDiagnostics) {
        val minimumPx = minimumTouchTargetPx()
        val listWidth = requireNotNull(diagnostics.selectorListRightPx) -
            requireNotNull(diagnostics.selectorListLeftPx)
        val listHeight = requireNotNull(diagnostics.selectorListBottomPx) -
            requireNotNull(diagnostics.selectorListTopPx)
        val resultWidth = requireNotNull(diagnostics.selectorFirstResultRightPx) -
            requireNotNull(diagnostics.selectorFirstResultLeftPx)
        val resultHeight = requireNotNull(diagnostics.selectorFirstResultBottomPx) -
            requireNotNull(diagnostics.selectorFirstResultTopPx)
        assertTrue("Result viewport width $listWidth px is below $minimumPx px", listWidth >= minimumPx)
        assertTrue(
            "Result viewport height $listHeight px is below $minimumPx px",
            listHeight >= minimumPx
        )
        assertTrue("Result target width $resultWidth px is below $minimumPx px", resultWidth >= minimumPx)
        assertTrue(
            "Result target height $resultHeight px is below $minimumPx px",
            resultHeight >= minimumPx
        )
        assertTrue(
            requireNotNull(diagnostics.selectorSearchRightPx) <=
                requireNotNull(diagnostics.selectorListLeftPx)
        )
        assertTrue(
            requireNotNull(diagnostics.selectorListBottomPx) <=
                requireNotNull(diagnostics.selectorBaseRowTopPx)
        )
        assertEquals(true, diagnostics.selectorFirstResultClickable)
        assertEquals(true, diagnostics.selectorFirstResultFocusable)
        val visibleText = requireNotNull(diagnostics.selectorFirstResultText)
        assertTrue(visibleText.isNotBlank())
        val accessibilityIdentity = visibleText.replace(" — ", ", ")
        val accessibilityText = requireNotNull(
            diagnostics.selectorFirstResultAccessibilityText
        )
        assertTrue(
            "Realm result accessibility label did not preserve its full structured identity",
            accessibilityText == "Select map $accessibilityIdentity" ||
                accessibilityText == "Selected map, $accessibilityIdentity"
        )
    }

    private fun assertFloorTouchTargets(diagnostics: osrsMapDiagnostics) {
        val minimumPx = minimumTouchTargetPx()
        assertTrue(diagnostics.floorControlVisible)
        assertTrue(diagnostics.floorUpWidthPx >= minimumPx)
        assertTrue(diagnostics.floorUpHeightPx >= minimumPx)
        assertTrue(diagnostics.floorDownWidthPx >= minimumPx)
        assertTrue(diagnostics.floorDownHeightPx >= minimumPx)
        assertTrue(diagnostics.floorButtonsSeparated)
    }

    private fun assertStatusSeparation(diagnostics: osrsMapDiagnostics) {
        val minimumSeparationPx = (
            OSRS_CONTROL_SEPARATION_DP *
                InstrumentationRegistry.getInstrumentation()
                    .targetContext
                    .resources
                    .displayMetrics
                    .density
            ).toInt()
        assertTrue(diagnostics.statusVisible)
        assertTrue(diagnostics.statusBottomPx > diagnostics.statusTopPx)
        assertTrue(diagnostics.selectorAndStatusSeparated == true)
        assertTrue(
            requireNotNull(diagnostics.selectorTopObstructionPx) >=
                diagnostics.statusBottomPx + minimumSeparationPx
        )
        assertTrue(
            requireNotNull(diagnostics.selectorStatusSeparationPx) >= minimumSeparationPx
        )
        assertTrue(
            "Visible status must be complete or explicitly ellipsized",
            diagnostics.statusLastVisibleEnd == diagnostics.statusTextLength ||
                requireNotNull(diagnostics.statusEllipsisCount) > 0
        )
        assertTrue(
            requireNotNull(diagnostics.statusAccessibilityText).length >=
                diagnostics.statusTextLength
        )
    }

    private fun captureRuntimeEvidence(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        state: String
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        instrumentation.waitForIdleSync()
        device.waitForIdle()
        Thread.sleep(OSRS_RENDER_SETTLE_MILLIS)
        instrumentation.waitForIdleSync()
        var diagnostics: osrsMapDiagnostics? = null
        scenario.onActivity { activity ->
            diagnostics = activity.debugStateForTesting()
        }
        val captured = requireNotNull(diagnostics)
        val orientation = if (captured.screenWidthDp > captured.screenHeightDp) {
            "landscape"
        } else {
            "portrait"
        }
        val prefix = "$orientation-font-${captured.fontScale}-$state"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir(null))
            .resolve(OSRS_EVIDENCE_DIRECTORY)
            .apply { check(mkdirs() || isDirectory) }
        val screenshot = captureCompleteScreenshot(
            minimumCoverage = if (captured.selectorImeVisible == true) {
                OSRS_IME_SCREENSHOT_MINIMUM_CONTENT_COVERAGE
            } else {
                OSRS_SCREENSHOT_MINIMUM_CONTENT_COVERAGE
            }
        )
        FileOutputStream(directory.resolve("$prefix.png")).use { output ->
            check(screenshot.bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        screenshot.bitmap.recycle()
        device.dumpWindowHierarchy(directory.resolve("$prefix.xml"))
        directory.resolve("$prefix.json").writeText(
            JSONObject().apply {
                put("orientation", orientation)
                put("font_scale", captured.fontScale.toDouble())
                put("ime_visible", captured.selectorImeVisible)
                put("selector_query", captured.selectorQuery)
                put("active_realm_id", captured.activeRealmId)
                put("active_realm_display_name", captured.activeRealmDisplayName)
                put("status_visible", captured.statusVisible)
                put("status_bottom_px", captured.statusBottomPx)
                put("selector_top_px", captured.selectorTopPx)
                put("selector_bottom_px", captured.selectorBottomPx)
                put("selector_top_obstruction_px", captured.selectorTopObstructionPx)
                put(
                    "selector_active_viewport_bottom_gap_px",
                    captured.selectorActiveViewportBottomGapPx
                )
                put("selector_status_separation_px", captured.selectorStatusSeparationPx)
                put("selector_base_row_top_px", captured.selectorBaseRowTopPx)
                put("selector_base_row_bottom_px", captured.selectorBaseRowBottomPx)
                put("search_left_px", captured.selectorSearchLeftPx)
                put("search_top_px", captured.selectorSearchTopPx)
                put("search_right_px", captured.selectorSearchRightPx)
                put("search_bottom_px", captured.selectorSearchBottomPx)
                put("list_left_px", captured.selectorListLeftPx)
                put("list_top_px", captured.selectorListTopPx)
                put("list_right_px", captured.selectorListRightPx)
                put("list_bottom_px", captured.selectorListBottomPx)
                put("first_result_left_px", captured.selectorFirstResultLeftPx)
                put("first_result_top_px", captured.selectorFirstResultTopPx)
                put("first_result_right_px", captured.selectorFirstResultRightPx)
                put("first_result_bottom_px", captured.selectorFirstResultBottomPx)
                put("first_result_clickable", captured.selectorFirstResultClickable)
                put("first_result_focusable", captured.selectorFirstResultFocusable)
                put("first_result_text", captured.selectorFirstResultText)
                put(
                    "first_result_accessibility_text",
                    captured.selectorFirstResultAccessibilityText
                )
                put("screenshot_capture_attempts", screenshot.attempts)
                put("screenshot_content_coverage", screenshot.contentCoverage)
                put("compact_landscape_ime_chrome", captured.compactLandscapeImeChrome)
                put("status_text_length", captured.statusTextLength)
                put("status_last_visible_end", captured.statusLastVisibleEnd)
                put("status_ellipsis_count", captured.statusEllipsisCount)
                put("status_accessibility_text", captured.statusAccessibilityText)
                put("floor_controls_visible", captured.floorControlVisible)
                put("floor_control_top_px", captured.floorControlTopPx)
                put("floor_control_bottom_px", captured.floorControlBottomPx)
                put("floor_up_width_px", captured.floorUpWidthPx)
                put("floor_up_height_px", captured.floorUpHeightPx)
                put("floor_down_width_px", captured.floorDownWidthPx)
                put("floor_down_height_px", captured.floorDownHeightPx)
                put("floor_buttons_separated", captured.floorButtonsSeparated)
            }.toString(2)
        )
    }

    private fun captureCompleteScreenshot(minimumCoverage: Double): osrsScreenshotCapture {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        repeat(OSRS_SCREENSHOT_MAX_ATTEMPTS) { attempt ->
            val bitmap = automation.takeScreenshot()
            val contentCoverage = screenshotContentCoverage(bitmap)
            if (contentCoverage >= minimumCoverage) {
                return osrsScreenshotCapture(
                    bitmap = bitmap,
                    contentCoverage = contentCoverage,
                    attempts = attempt + 1
                )
            }
            bitmap.recycle()
            Thread.sleep(OSRS_SCREENSHOT_RETRY_MILLIS)
        }
        throw AssertionError(
            "Screenshot compositor never reached minimum content coverage $minimumCoverage"
        )
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

    private fun minimumTouchTargetPx(): Int = ceil(
        OSRS_MINIMUM_TOUCH_TARGET_DP *
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .resources
                .displayMetrics
                .density
    ).toInt()

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
        throw AssertionError("Timed out awaiting Direction 3 diagnostics; latest=$latest")
    }

    private fun nearestRankP95(values: List<Long>): Long {
        val sorted = values.sorted()
        return sorted[(ceil(sorted.size * 0.95).toInt() - 1).coerceIn(sorted.indices)]
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

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private data class osrsScreenshotCapture(
        val bitmap: Bitmap,
        val contentCoverage: Double,
        val attempts: Int
    )

    private companion object {
        const val OSRS_EXACT_GATE_ARGUMENT = "osrsDirection3ExactRelease"
        const val OSRS_EXACT_APK_SHA_ARGUMENT = "osrsDirection3ExactReleaseSha256"
        const val OSRS_EXACT_APK_BYTES_ARGUMENT = "osrsDirection3ExactReleaseBytes"
        const val OSRS_EXACT_APK_SIGNER_SHA_ARGUMENT =
            "osrsDirection3ExactReleaseSignerSha256"
        const val OSRS_PACKAGE_ID = "com.omiyawaki.osrswiki.undergroundmaps"
        const val OSRS_REPEATED_OPEN_COUNT = 20
        const val OSRS_SIMPLE_CONTROL_BUDGET_NANOS = 50_000_000L
        const val OSRS_MINIMUM_TOUCH_TARGET_DP = 48
        const val OSRS_CONTROL_SEPARATION_DP = 12
        const val OSRS_LANDSCAPE_RESULT_QUERY = "trawler"
        const val OSRS_EVIDENCE_DIRECTORY = "direction3-rereview-evidence"
        const val OSRS_STATE_TIMEOUT_MILLIS = 30_000L
        const val OSRS_TEST_POLL_MILLIS = 25L
        const val OSRS_RENDER_SETTLE_MILLIS = 250L
        const val OSRS_SCREENSHOT_RETRY_MILLIS = 100L
        const val OSRS_SCREENSHOT_MAX_ATTEMPTS = 10
        const val OSRS_SCREENSHOT_SAMPLE_STRIDE_PX = 8
        const val OSRS_SCREENSHOT_CONTENT_CHANNEL_THRESHOLD = 32
        const val OSRS_SCREENSHOT_MINIMUM_CONTENT_COVERAGE = 0.01
        const val OSRS_IME_SCREENSHOT_MINIMUM_CONTENT_COVERAGE = 0.20
    }
}
