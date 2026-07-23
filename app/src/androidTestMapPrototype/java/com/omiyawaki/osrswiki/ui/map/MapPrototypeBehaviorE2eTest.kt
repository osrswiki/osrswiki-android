package com.omiyawaki.osrswiki.ui.map

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.test.MapPrototypeStateStore
import com.omiyawaki.osrswiki.test.SemanticPrototypeStandaloneActivity
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapPrototypeBehaviorE2eTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val targetContext = instrumentation.targetContext
    private val evidenceRoot = File(
        targetContext.getExternalFilesDir(null),
        "map-prototype-candidate-008-e2e"
    ).apply { mkdirs() }

    @Test
    fun candidate008BehaviorMatrix() {
        val evidenceDir = File(evidenceRoot, "behavior-matrix").resetDirectory()
        val assertions = JSONObject()
        val startedAt = SystemClock.elapsedRealtime()
        ActivityScenario.launch(SemanticPrototypeStandaloneActivity::class.java).use { scenario ->
            val first = waitForDiagnostics(scenario, evidenceDir, "first-launch") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.LABEL.value].orEmpty().isNotEmpty() &&
                    it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().size >= 3 &&
                    it.renderedFeatureIdsByKind[osrsMapFeatureKind.MAP_LINK.value].orEmpty().isNotEmpty()
            }
            assertions.put("first_launch_ready_ms", SystemClock.elapsedRealtime() - startedAt)
            assertions.put("first_launch", first.toJson())
            assertTrue(first.isNear(osrsMapPrototypeOverlay.initialCenter(), 0.02))
            assertEquals(100, first.referenceStopPercent)
            assertEquals("gielinor-surface", first.activeSurfaceId)
            assertTrue(first.overviewVisible)
            assertTrue(first.featureActionMetadata.values.any { it.startsWith("recenter:") })
            assertTrue(first.featureActionMetadata.values.any { it.startsWith("unknown_pending_evidence:") })
            assertNoLabelPoiOverlap(first)
            waitForTerrainPreviewDismissed(scenario)
            val base = stableScreenshot(evidenceDir, "01-first-launch")
            dumpHierarchy(evidenceDir, "01-first-launch")
            val terrainCrop = terrainStabilityCrop(base)
            assertCropExcludesSemanticFeatures(scenario, first, terrainCrop)

            val labelIds = first.renderedFeatureIdsByKind[osrsMapFeatureKind.LABEL.value].orEmpty()
            val labelCrop = featureCrop(scenario, first, labelIds, base, 40)
            clickRes("prototype_toggle_labels")
            val labelsOffDiagnostics = waitForDiagnostics(scenario, evidenceDir, "labels-off") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.LABEL.value].orEmpty().isEmpty()
            }
            val labelsOff = stableScreenshot(evidenceDir, "02-labels-off")
            val labelChanged = changedPixels(base, labelsOff, labelCrop)
            val labelTerrainChanged = changedPixels(base, labelsOff, terrainCrop)
            assertions.put("labels_off_changed_pixels", labelChanged)
            assertions.put("labels_off_terrain_changed_pixels", labelTerrainChanged)
            assertTrue("Labels must change their rendered region: $labelChanged", labelChanged > 180)
            assertTrue("Labels must preserve remote terrain: $labelTerrainChanged", labelTerrainChanged < 40)
            assertControlBudget(labelsOffDiagnostics)
            clickRes("prototype_toggle_labels")
            waitForDiagnostics(scenario, evidenceDir, "labels-restored") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.LABEL.value].orEmpty().isNotEmpty()
            }
            val labelsRestored = stableScreenshot(evidenceDir, "03-labels-restored")
            val labelRestoreDelta = changedPixels(base, labelsRestored, labelCrop)
            assertions.put("labels_restore_delta", labelRestoreDelta)
            assertTrue(labelRestoreDelta < labelChanged / 3 + 20)
            labelsOff.recycle()

            val poisBefore = latestDiagnostics(scenario)
            val poiIds = poisBefore.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty()
            val poiCrop = featureCrop(scenario, poisBefore, poiIds, labelsRestored, 48)
            clickRes("prototype_toggle_pois")
            val poisOffDiagnostics = waitForDiagnostics(scenario, evidenceDir, "pois-off") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().isEmpty()
            }
            val poisOff = stableScreenshot(evidenceDir, "04-pois-off")
            val poiChanged = changedPixels(labelsRestored, poisOff, poiCrop)
            val poiTerrainChanged = changedPixels(labelsRestored, poisOff, terrainCrop)
            assertions.put("pois_off_changed_pixels", poiChanged)
            assertions.put("pois_off_terrain_changed_pixels", poiTerrainChanged)
            assertTrue("POIs must change their rendered region: $poiChanged", poiChanged > 160)
            assertTrue("POIs must preserve remote terrain: $poiTerrainChanged", poiTerrainChanged < 40)
            assertControlBudget(poisOffDiagnostics)
            clickRes("prototype_toggle_pois")
            waitForDiagnostics(scenario, evidenceDir, "pois-restored") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().size >= 3
            }
            val poisRestored = stableScreenshot(evidenceDir, "05-pois-restored")
            poisOff.recycle()

            val linksBefore = latestDiagnostics(scenario)
            val linkIds = linksBefore.renderedFeatureIdsByKind[osrsMapFeatureKind.MAP_LINK.value].orEmpty()
            val linkCrop = featureCrop(scenario, linksBefore, linkIds, poisRestored, 48)
            clickRes("prototype_toggle_links")
            val linksOffDiagnostics = waitForDiagnostics(scenario, evidenceDir, "links-off") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.MAP_LINK.value].orEmpty().isEmpty()
            }
            val linksOff = stableScreenshot(evidenceDir, "06-links-off")
            val linkChanged = changedPixels(poisRestored, linksOff, linkCrop)
            assertions.put("links_off_changed_pixels", linkChanged)
            assertTrue("Links must change their rendered region: $linkChanged", linkChanged > 140)
            assertControlBudget(linksOffDiagnostics)
            clickRes("prototype_toggle_links")
            waitForDiagnostics(scenario, evidenceDir, "links-restored") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.MAP_LINK.value].orEmpty().isNotEmpty()
            }
            val linksRestored = stableScreenshot(evidenceDir, "07-links-restored")
            linksOff.recycle()

            val keyBefore = latestDiagnostics(scenario)
            val visibleBankIds = keyBefore.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty()
                .filter { id -> osrsMapPrototypeOverlay.features.firstOrNull { it.id == id }?.category == osrsMapCategory.BANK }
            assertTrue("At least one visible bank is required for Key proof", visibleBankIds.isNotEmpty())
            val keyCrop = featureCrop(scenario, keyBefore, visibleBankIds, linksRestored, 72)
            clickRes("prototype_key_banks")
            val keyOnDiagnostics = waitForDiagnostics(scenario, evidenceDir, "key-banks-on") {
                osrsMapCategory.BANK.value in it.highlightedCategories
            }
            val keyOn = stableScreenshot(evidenceDir, "08-key-banks-on")
            val keyChanged = changedPixels(linksRestored, keyOn, keyCrop, threshold = 28)
            val keyTerrainChanged = changedPixels(linksRestored, keyOn, terrainCrop)
            assertions.put("bank_key_on_changed_pixels", keyChanged)
            assertions.put("bank_key_on_terrain_changed_pixels", keyTerrainChanged)
            assertTrue("Key selection must draw visible category rings: $keyChanged", keyChanged > 120)
            assertTrue("Key selection must preserve remote terrain: $keyTerrainChanged", keyTerrainChanged < 40)
            assertControlBudget(keyOnDiagnostics)

            clickRes("prototype_toggle_pois")
            waitForDiagnostics(scenario, evidenceDir, "pois-off-key-selected") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().isEmpty() &&
                    osrsMapCategory.BANK.value in it.highlightedCategories
            }
            val poisOffKeySelected = stableScreenshot(evidenceDir, "09-pois-off-key-selected")
            clickRes("prototype_toggle_pois")
            waitForDiagnostics(scenario, evidenceDir, "pois-on-key-selected") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().isNotEmpty()
            }
            val poisOnKeySelected = stableScreenshot(evidenceDir, "10-pois-on-key-selected")
            val keyInteractionPixels = changedPixels(poisOffKeySelected, poisOnKeySelected, keyCrop, threshold = 28)
            assertions.put("pois_plus_key_interaction_pixels", keyInteractionPixels)
            assertTrue(keyInteractionPixels > 120)
            poisOffKeySelected.recycle()
            clickRes("prototype_key_banks")
            waitForDiagnostics(scenario, evidenceDir, "key-banks-off") {
                osrsMapCategory.BANK.value !in it.highlightedCategories
            }

            testVisibleCoordinateHits(scenario, evidenceDir, assertions)
            testSearchSurfaceAndOverview(scenario, evidenceDir, assertions)
            testContinuousZoomAndReferenceStops(scenario, evidenceDir, assertions)
            testPanStyleAndForegroundPersistence(scenario, evidenceDir, assertions)
            testSettledFrameMetrics(scenario, assertions, evidenceDir)

            base.recycle()
            labelsRestored.recycle()
            poisRestored.recycle()
            linksRestored.recycle()
            keyOn.recycle()
            poisOnKeySelected.recycle()
        }
        File(evidenceDir, "assertions.json").writeText(assertions.toString(2))
    }

    @Test
    fun candidate008NavigationHistoryAndStatusRestoration() {
        val evidenceDir = File(evidenceRoot, "navigation-status-restoration").resetDirectory()
        val assertions = JSONObject()
        ActivityScenario.launch(SemanticPrototypeStandaloneActivity::class.java).use { scenario ->
            waitForDiagnostics(scenario, evidenceDir, "ready") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().isNotEmpty()
            }

            runOnFragment(scenario) { fragment.performPrototypeSearchForTesting("Falador") }
            val falador = waitForNavigationState(
                scenario,
                evidenceDir,
                "falador",
                "Falador",
                "Centered on Falador",
                2965.0,
                3378.0
            )
            val faladorCamera = falador.cameraTriple()
            val faladorHistoryDepth = falador.historyDepth

            val missResult = AtomicReference<Boolean>()
            scenario.onActivity { activity ->
                missResult.set(
                    activity.mapFragmentForTesting()
                        ?.performPrototypeSearchForTesting("no-such-place-8844") == true
                )
            }
            assertFalse(missResult.get())
            val miss = waitForDiagnostics(scenario, evidenceDir, "miss-preserves-valid-history") {
                it.searchQuery == "no-such-place-8844" &&
                    it.statusText == "No surface result for no-such-place-8844"
            }
            assertCameraEqual(faladorCamera, miss.cameraTriple(), 0.00001)
            assertEquals(faladorHistoryDepth, miss.historyDepth)

            runOnFragment(scenario) { fragment.performPrototypeSearchForTesting("Karamja") }
            waitForNavigationState(
                scenario,
                evidenceDir,
                "karamja",
                "Karamja",
                "Centered on Karamja",
                2918.0,
                3175.0
            )
            clickRes("prototype_history_back")
            val backToFalador = waitForNavigationState(
                scenario,
                evidenceDir,
                "karamja-back-to-falador",
                "Falador",
                "Centered on Falador",
                2965.0,
                3378.0
            )
            assertCameraEqual(faladorCamera, backToFalador.cameraTriple(), 0.00001)
            stableScreenshot(evidenceDir, "01-history-coherent-falador").recycle()
            dumpHierarchy(evidenceDir, "01-history-coherent-falador")

            runOnFragment(scenario) { fragment.performPrototypeSearchForTesting("Draynor") }
            waitForNavigationState(
                scenario,
                evidenceDir,
                "draynor",
                "Draynor",
                "Centered on Draynor",
                3093.0,
                3244.0
            )
            runOnFragment(scenario) { fragment.performPrototypeSearchForTesting("Al Kharid") }
            waitForNavigationState(
                scenario,
                evidenceDir,
                "al-kharid",
                "Al Kharid",
                "Centered on Al Kharid",
                3293.0,
                3183.0
            )
            clickRes("prototype_history_back")
            waitForNavigationState(
                scenario,
                evidenceDir,
                "first-of-two-back-steps",
                "Draynor",
                "Centered on Draynor",
                3093.0,
                3244.0
            )
            clickRes("prototype_history_back")
            waitForNavigationState(
                scenario,
                evidenceDir,
                "second-of-two-back-steps",
                "Falador",
                "Centered on Falador",
                2965.0,
                3378.0
            )

            runOnFragment(scenario) { fragment.performPrototypeSearchForTesting("Karamja") }
            val newNavigationAfterBack = waitForNavigationState(
                scenario,
                evidenceDir,
                "new-navigation-after-back",
                "Karamja",
                "Centered on Karamja",
                2918.0,
                3175.0
            )
            runOnFragment(scenario) {
                fragment.performPrototypeFeatureActionForTesting("link-draynor-recenter")
            }
            val linkResult = waitForDiagnostics(scenario, evidenceDir, "functional-link-result") {
                it.currentNavigationResultId == "link-draynor-recenter" &&
                    it.statusText.orEmpty().contains("recenter -> Draynor semantic test cluster") &&
                    it.isNear(osrsMapPrototypeOverlay.gameToLatLng(3093.0, 3244.0), 0.02)
            }
            val linkStatus = requireNotNull(linkResult.statusText)
            assertEquals(linkStatus, linkResult.statusContentDescription)
            clickRes("prototype_history_back")
            val linkBack = waitForNavigationState(
                scenario,
                evidenceDir,
                "functional-link-back",
                "Karamja",
                "Centered on Karamja",
                2918.0,
                3175.0
            )
            assertCameraEqual(newNavigationAfterBack.cameraTriple(), linkBack.cameraTriple(), 0.00001)

            runOnFragment(scenario) { fragment.setPrototypeOverviewCenterForTesting(3060.0, 3300.0) }
            val overviewResult = waitForDiagnostics(scenario, evidenceDir, "overview-result") {
                it.currentNavigationResultId == "overview-recenter" &&
                    it.searchQuery.isEmpty() &&
                    it.statusText == "Recentered from overview map"
            }
            assertEquals(overviewResult.statusText, overviewResult.statusContentDescription)
            clickRes("prototype_history_back")
            val overviewBack = waitForNavigationState(
                scenario,
                evidenceDir,
                "overview-back",
                "Karamja",
                "Centered on Karamja",
                2918.0,
                3175.0
            )
            assertCameraEqual(newNavigationAfterBack.cameraTriple(), overviewBack.cameraTriple(), 0.00001)

            recreateAndAssertState(
                scenario,
                evidenceDir,
                "search-success-recreated",
                overviewBack.cameraTriple(),
                "Karamja",
                "Centered on Karamja"
            )

            scenario.onActivity { activity ->
                activity.mapFragmentForTesting()?.performPrototypeSearchForTesting("another-missing-place")
            }
            val missingBeforeRecreate = waitForDiagnostics(scenario, evidenceDir, "missing-before-recreate") {
                it.searchQuery == "another-missing-place" &&
                    it.statusText == "No surface result for another-missing-place"
            }
            recreateAndAssertState(
                scenario,
                evidenceDir,
                "search-miss-recreated",
                missingBeforeRecreate.cameraTriple(),
                "another-missing-place",
                "No surface result for another-missing-place"
            )

            runOnFragment(scenario) {
                fragment.performPrototypeFeatureActionForTesting("link-draynor-recenter")
            }
            val recenterBeforeRecreate = waitForDiagnostics(scenario, evidenceDir, "recenter-before-recreate") {
                it.currentNavigationResultId == "link-draynor-recenter" &&
                    it.statusText.orEmpty().contains("recenter -> Draynor semantic test cluster")
            }
            recreateAndAssertState(
                scenario,
                evidenceDir,
                "recenter-recreated",
                recenterBeforeRecreate.cameraTriple(),
                "",
                requireNotNull(recenterBeforeRecreate.statusText)
            )

            val unavailableResult = AtomicReference<Boolean>()
            scenario.onActivity { activity ->
                unavailableResult.set(
                    activity.mapFragmentForTesting()
                        ?.selectPrototypeSurfaceForTesting("kharidian-desert-underground") == true
                )
            }
            assertFalse(unavailableResult.get())
            val unavailableBeforeRecreate = waitForDiagnostics(scenario, evidenceDir, "unavailable-before-recreate") {
                it.statusText.orEmpty().contains("Kharidian Desert Underground is unavailable") &&
                    it.activeSurfaceId == "gielinor-surface"
            }
            val unavailableRestored = recreateAndAssertState(
                scenario,
                evidenceDir,
                "unavailable-recreated",
                unavailableBeforeRecreate.cameraTriple(),
                "",
                requireNotNull(unavailableBeforeRecreate.statusText)
            )
            assertEquals("gielinor-surface", unavailableRestored.activeSurfaceId)
            stableScreenshot(evidenceDir, "02-restored-unavailable-state").recycle()
            dumpHierarchy(evidenceDir, "02-restored-unavailable-state")

            assertions.put("falador_miss_karamja_back", backToFalador.toJson())
            assertions.put("new_navigation_after_back", newNavigationAfterBack.toJson())
            assertions.put("functional_link_result", linkResult.toJson())
            assertions.put("functional_link_back", linkBack.toJson())
            assertions.put("overview_result", overviewResult.toJson())
            assertions.put("overview_back", overviewBack.toJson())
            assertions.put("unavailable_restored", unavailableRestored.toJson())
        }
        File(evidenceDir, "assertions.json").writeText(assertions.toString(2))
    }

    @Test
    fun trueConfigurationRotationPreservesMapAndSemanticState() {
        val evidenceDir = File(evidenceRoot, "rotation-recreation").resetDirectory()
        val assertions = JSONObject()
        device.setOrientationNatural()
        ActivityScenario.launch(SemanticPrototypeStandaloneActivity::class.java).use { scenario ->
            waitForDiagnostics(scenario, evidenceDir, "rotation-start") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().size >= 3
            }
            runOnFragment(scenario) {
                fragment.togglePrototypeCategoryForTesting(osrsMapCategory.BANK.value)
                fragment.setPrototypeLayerVisibilityForTesting(links = false)
                fragment.performPrototypeSearchForTesting("Falador")
                fragment.setPrototypeZoomForTesting(7.05)
            }
            val before = waitForDiagnostics(scenario, evidenceDir, "before-rotation") {
                osrsMapCategory.BANK.value in it.highlightedCategories &&
                    it.renderedFeatureIdsByKind[osrsMapFeatureKind.MAP_LINK.value].orEmpty().isEmpty() &&
                    abs(it.cameraZoom - 7.05) < 0.01 &&
                    it.searchQuery == "Falador" &&
                    it.statusText == "Centered on Falador" &&
                    it.statusContentDescription == "Centered on Falador"
            }
            val portraitIdentity = activityIdentity(scenario)
            stableScreenshot(evidenceDir, "01-before-rotation-portrait").recycle()
            dumpHierarchy(evidenceDir, "01-before-rotation-portrait")

            try {
                device.setOrientationLeft()
                val landscapeIdentity = waitForNewActivity(scenario, portraitIdentity, true)
                val landscape = waitForDiagnostics(scenario, evidenceDir, "after-landscape-recreation") {
                        it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().isNotEmpty() &&
                        osrsMapCategory.BANK.value in it.highlightedCategories &&
                        it.renderedFeatureIdsByKind[osrsMapFeatureKind.MAP_LINK.value].orEmpty().isEmpty() &&
                        it.searchQuery == "Falador" &&
                        it.statusText == "Centered on Falador" &&
                        it.statusContentDescription == "Centered on Falador"
                }
                assertPersistedCamera(before, landscape)
                assertTrue(landscapeIdentity != portraitIdentity)
                assertLandscapePanelsDoNotOverlap(scenario)
                stableScreenshot(evidenceDir, "02-after-landscape-recreation").recycle()
                dumpHierarchy(evidenceDir, "02-after-landscape-recreation")

                device.setOrientationNatural()
                val restoredPortraitIdentity = waitForNewActivity(scenario, landscapeIdentity, false)
                val restoredPortrait = waitForDiagnostics(scenario, evidenceDir, "after-portrait-recreation") {
                        it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().isNotEmpty() &&
                        osrsMapCategory.BANK.value in it.highlightedCategories &&
                        it.renderedFeatureIdsByKind[osrsMapFeatureKind.MAP_LINK.value].orEmpty().isEmpty() &&
                        it.searchQuery == "Falador" &&
                        it.statusText == "Centered on Falador" &&
                        it.statusContentDescription == "Centered on Falador"
                }
                assertPersistedCamera(before, restoredPortrait)
                assertTrue(restoredPortraitIdentity != landscapeIdentity)
                assertEquals(View.VISIBLE, viewVisibility(scenario, R.id.prototype_key_panel))
                stableScreenshot(evidenceDir, "03-after-portrait-recreation").recycle()
                dumpHierarchy(evidenceDir, "03-after-portrait-recreation")
                assertions.put("portrait_activity_identity", portraitIdentity)
                assertions.put("landscape_activity_identity", landscapeIdentity)
                assertions.put("restored_portrait_activity_identity", restoredPortraitIdentity)
                assertions.put("before", before.toJson())
                assertions.put("landscape", landscape.toJson())
                assertions.put("restored_portrait", restoredPortrait.toJson())
            } finally {
                device.setOrientationNatural()
                device.unfreezeRotation()
            }
        }
        File(evidenceDir, "assertions.json").writeText(assertions.toString(2))
    }

    @Test
    fun candidate008TransactionalPoseAndOwnerGenerationSurviveRapidRecreation() {
        val evidenceDir = File(evidenceRoot, "transactional-pose-owner-generation").resetDirectory()
        val assertions = JSONObject()
        MapPrototypeStateStore.clearForTesting(targetContext)
        ActivityScenario.launch(SemanticPrototypeStandaloneActivity::class.java).use { scenario ->
            waitForDiagnostics(scenario, evidenceDir, "ready") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().isNotEmpty()
            }
            runOnFragment(scenario) {
                fragment.setPrototypeCameraPoseForTesting(
                    gameX = 2965.0,
                    gameY = 3378.0,
                    zoom = 7.15,
                    bearing = 27.5,
                    tilt = 8.0
                )
            }
            val firstPose = waitForDiagnostics(scenario, evidenceDir, "first-pose") {
                abs(it.cameraZoom - 7.15) < 0.01 &&
                    abs(it.cameraBearing - 27.5) < 0.01 &&
                    abs(it.cameraTilt - 8.0) < 0.01
            }

            // Start an old-pose snapshot and immediately publish a newer stopped-state generation.
            scenario.onActivity { activity ->
                val fragment = requireNotNull(activity.mapFragmentForTesting())
                fragment.capturePrototypeTerrainPreviewForPersistence()
                fragment.setPrototypeCameraPoseForTesting(
                    gameX = 2816.0,
                    gameY = 3182.0,
                    zoom = 6.65,
                    bearing = 81.0,
                    tilt = 4.0
                )
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            val persisted = requireNotNull(MapPrototypeStateStore.loadSession(targetContext))
            persisted.terrainPreview?.recycle()
            assertEquals(81.0, persisted.descriptor.cameraBearing, 0.01)
            assertEquals(4.0, persisted.descriptor.cameraTilt, 0.01)
            assertEquals(6.65, persisted.descriptor.cameraZoom, 0.01)
            assertEquals("state-only-generation", persisted.previewStatus)
            assertions.put("stopped_generation", persisted.generation)
            assertions.put("stopped_preview_status", persisted.previewStatus)

            scenario.moveToState(Lifecycle.State.RESUMED)
            val stoppedPose = waitForDiagnostics(scenario, evidenceDir, "stopped-pose-restored") {
                abs(it.cameraZoom - 6.65) < 0.01 &&
                    abs(it.cameraBearing - 81.0) < 0.01 &&
                    abs(it.cameraTilt - 4.0) < 0.01
            }
            val identities = JSONArray()
            repeat(6) { index ->
                val priorIdentity = activityIdentity(scenario)
                scenario.recreate()
                val nextIdentity = waitForNewActivity(
                    scenario,
                    priorIdentity,
                    targetContext.resources.configuration.orientation ==
                        android.content.res.Configuration.ORIENTATION_LANDSCAPE
                )
                identities.put(nextIdentity)
                val restored = waitForDiagnostics(scenario, evidenceDir, "owner-recreate-$index") {
                    it.renderedFeatureIdsByKind.values.any(List<String>::isNotEmpty) &&
                        it.semanticLayersPresent.values.all { present -> present } &&
                        it.mapContentBounds?.let { bounds ->
                            bounds.right > bounds.left && bounds.bottom > bounds.top
                        } == true &&
                        abs(it.cameraZoom - 6.65) < 0.01 &&
                        abs(it.cameraBearing - 81.0) < 0.01 &&
                        abs(it.cameraTilt - 4.0) < 0.01
                }
                assertEquals(
                    requireNotNull(stoppedPose.cameraLatitude),
                    requireNotNull(restored.cameraLatitude),
                    0.00001
                )
                assertEquals(
                    requireNotNull(stoppedPose.cameraLongitude),
                    requireNotNull(restored.cameraLongitude),
                    0.00001
                )
                waitForTerrainPreviewDismissed(scenario)
            }
            assertions.put("first_pose", firstPose.toJson())
            assertions.put("restored_pose", stoppedPose.toJson())
            assertions.put("activity_identities", identities)
            stableScreenshot(evidenceDir, "final-owner-generation").recycle()
            dumpHierarchy(evidenceDir, "final-owner-generation")
        }
        File(evidenceDir, "assertions.json").writeText(assertions.toString(2))
    }

    @Test
    fun candidate008OverviewAccessibilityActionsMoveCameraAndHistory() {
        val evidenceDir = File(evidenceRoot, "overview-accessibility-actions").resetDirectory()
        val assertions = JSONObject()
        ActivityScenario.launch(SemanticPrototypeStandaloneActivity::class.java).use { scenario ->
            val initial = waitForDiagnostics(scenario, evidenceDir, "ready") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().isNotEmpty()
            }
            val actionIds = AtomicReference<Set<Int>>()
            val overviewBounds = AtomicReference<Rect>()
            scenario.onActivity { activity ->
                val overview = activity.findViewById<View>(R.id.prototype_overview)
                val node = overview.createAccessibilityNodeInfo()
                actionIds.set(node.actionList.map { it.id }.toSet())
                overviewBounds.set(viewBoundsOnScreen(overview))
                node.recycle()
            }
            val requiredActions = setOf(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
            )
            assertTrue(actionIds.get().containsAll(requiredActions))
            val density = targetContext.resources.displayMetrics.density
            assertTrue(overviewBounds.get().width() >= (48f * density).roundToInt())
            assertTrue(overviewBounds.get().height() >= (48f * density).roundToInt())

            val rightResult = AtomicReference<Boolean>()
            scenario.onActivity { activity ->
                rightResult.set(
                    activity.findViewById<View>(R.id.prototype_overview)
                        .performAccessibilityAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id,
                            null
                        )
                )
            }
            assertTrue(rightResult.get())
            val movedRight = waitForDiagnostics(scenario, evidenceDir, "moved-right") {
                it.historyDepth > initial.historyDepth &&
                    it.statusText == targetContext.getString(R.string.map_semantic_overview_recentered) &&
                    cameraDistance(initial.cameraTriple(), it.cameraTriple()) > 0.0001
            }

            val clickResult = AtomicReference<Boolean>()
            scenario.onActivity { activity ->
                clickResult.set(
                    activity.findViewById<View>(R.id.prototype_overview)
                        .performAccessibilityAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id,
                            null
                        )
                )
            }
            assertTrue(clickResult.get())
            val recentered = waitForDiagnostics(scenario, evidenceDir, "recentered") {
                it.historyDepth > movedRight.historyDepth &&
                    it.isNear(osrsMapPrototypeOverlay.initialCenter(), 0.02)
            }
            assertions.put("required_action_ids", JSONArray(requiredActions.toList()))
            assertions.put("overview_bounds", JSONObject()
                .put("left", overviewBounds.get().left)
                .put("top", overviewBounds.get().top)
                .put("right", overviewBounds.get().right)
                .put("bottom", overviewBounds.get().bottom))
            assertions.put("initial", initial.toJson())
            assertions.put("moved_right", movedRight.toJson())
            assertions.put("recentered", recentered.toJson())
            stableScreenshot(evidenceDir, "overview-accessibility-final").recycle()
            dumpHierarchy(evidenceDir, "overview-accessibility-final")
        }
        File(evidenceDir, "assertions.json").writeText(assertions.toString(2))
    }

    @Test
    fun currentFontScaleFitsControlsAndDecluttersSemantics() {
        val scale = targetContext.resources.configuration.fontScale
        val scaleLabel = scale.toString().replace('.', '_')
        val evidenceDir = File(evidenceRoot, "font-scale-$scaleLabel").resetDirectory()
        val assertions = JSONObject().put("font_scale", scale.toDouble())
        device.setOrientationNatural()
        try {
            ActivityScenario.launch(SemanticPrototypeStandaloneActivity::class.java).use { scenario ->
                val diagnostics = waitForDiagnostics(scenario, evidenceDir, "font-scale-$scaleLabel") {
                    it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().isNotEmpty()
                }
                assertNoLabelPoiOverlap(diagnostics)
                val unavailableResult = AtomicReference<Boolean>()
                scenario.onActivity { activity ->
                    unavailableResult.set(
                        activity.mapFragmentForTesting()
                            ?.selectPrototypeSurfaceForTesting("kharidian-desert-underground") == true
                    )
                }
                assertFalse(unavailableResult.get())
                val unavailable = waitForDiagnostics(scenario, evidenceDir, "font-scale-unavailable") {
                    it.statusText.orEmpty().contains("Kharidian Desert Underground is unavailable")
                }
                val restoredUnavailable = recreateAndAssertState(
                    scenario,
                    evidenceDir,
                    "font-scale-unavailable-recreated",
                    unavailable.cameraTriple(),
                    unavailable.searchQuery,
                    requireNotNull(unavailable.statusText)
                )
                assertions.put(
                    "unavailable_orientation_visibility",
                    assertStatusAcrossOrientations(
                        scenario,
                        evidenceDir,
                        "unavailable",
                        requireNotNull(restoredUnavailable.statusText)
                    )
                )

                scenario.onActivity { activity ->
                    activity.mapFragmentForTesting()?.performPrototypeSearchForTesting("Falador")
                }
                val searchSuccess = waitForDiagnostics(scenario, evidenceDir, "font-search-success") {
                    it.statusText == "Centered on Falador"
                }
                assertions.put(
                    "search_success_orientation_visibility",
                    assertStatusAcrossOrientations(
                        scenario,
                        evidenceDir,
                        "search-success",
                        requireNotNull(searchSuccess.statusText)
                    )
                )

                scenario.onActivity { activity ->
                    activity.mapFragmentForTesting()?.performPrototypeSearchForTesting("missing-font-scale-place")
                }
                val searchMiss = waitForDiagnostics(scenario, evidenceDir, "font-search-miss") {
                    it.statusText.orEmpty().contains("No surface result")
                }
                assertions.put(
                    "search_miss_orientation_visibility",
                    assertStatusAcrossOrientations(
                        scenario,
                        evidenceDir,
                        "search-miss",
                        requireNotNull(searchMiss.statusText)
                    )
                )

                scenario.onActivity { activity ->
                    activity.mapFragmentForTesting()
                        ?.performPrototypeFeatureActionForTesting("link-draynor-recenter")
                }
                val recenter = waitForDiagnostics(scenario, evidenceDir, "font-recenter") {
                    it.statusText.orEmpty().contains("recenter -> Draynor semantic test cluster")
                }
                assertions.put(
                    "recenter_orientation_visibility",
                    assertStatusAcrossOrientations(
                        scenario,
                        evidenceDir,
                        "recenter",
                        requireNotNull(recenter.statusText)
                    )
                )

                val selectorAccessibility = AtomicReference<String>()
                val clipped = AtomicReference<List<String>>(emptyList())
                val touchTargets = AtomicReference<JSONArray>()
                scenario.onActivity { activity ->
                    selectorAccessibility.set(
                        activity.findViewById<View>(R.id.prototype_surface_selector)
                            .contentDescription?.toString().orEmpty()
                    )
                    val textViewIds = listOf(
                        R.id.prototype_search_input,
                        R.id.prototype_surface_selector,
                        R.id.prototype_key_toggle,
                        R.id.prototype_overview_toggle,
                        R.id.prototype_status,
                        R.id.prototype_key_banks,
                        R.id.prototype_key_transport,
                        R.id.prototype_key_dungeons,
                        R.id.prototype_key_places,
                        R.id.prototype_reference_stop,
                        R.id.prototype_toggle_labels,
                        R.id.prototype_toggle_pois,
                        R.id.prototype_toggle_links
                    )
                    clipped.set(textViewIds.mapNotNull { id ->
                        val view = activity.findViewById<TextView>(id)
                        if (view == null || view.visibility != View.VISIBLE || textFits(view)) null
                        else activity.resources.getResourceEntryName(id)
                    })
                    touchTargets.set(assertMinimumTouchTargets(activity))
                    assertViewsInsideRoot(activity)
                }
                assertTrue(selectorAccessibility.get().contains("Kharidian Desert Underground"))
                assertTrue(selectorAccessibility.get().contains("transform evidence pending"))
                assertEquals(recenter.statusText, recenter.statusContentDescription)
                assertTrue("Text clipped at font scale $scale: ${clipped.get()}", clipped.get().isEmpty())
                assertions.put("clipped_text_views", JSONArray(clipped.get()))
                assertions.put("touch_targets", touchTargets.get())
                assertions.put("portrait_geometry", assertPortraitPanelsDoNotOverlap(scenario))
                assertions.put("diagnostics", diagnostics.toJson())
                assertions.put("restored_unavailable", restoredUnavailable.toJson())
                assertions.put("surface_selector_accessibility", selectorAccessibility.get())
                stableScreenshot(evidenceDir, "font-scale-$scaleLabel-final").recycle()
                dumpHierarchy(evidenceDir, "font-scale-$scaleLabel-final")
            }
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
        File(evidenceDir, "assertions.json").writeText(assertions.toString(2))
    }

    @Test
    fun semanticAccessibilityMatchesRenderedFeaturesAndLabelsOffState() {
        val evidenceDir = File(evidenceRoot, "semantic-accessibility").resetDirectory()
        val assertions = JSONObject()
        ActivityScenario.launch(SemanticPrototypeStandaloneActivity::class.java).use { scenario ->
            val initial = waitForDiagnostics(scenario, evidenceDir, "accessibility-initial") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.LABEL.value].orEmpty().isNotEmpty() &&
                    it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().isNotEmpty() &&
                    it.accessibilityHostDescription.contains("Known searchable places")
            }
            val renderedIds = initial.renderedFeatureIdsByKind.values.flatten().sorted()
            assertEquals(renderedIds, initial.accessibilityVisibleFeatureIds.sorted())
            assertTrue(initial.accessibilityHostDescription.contains("Visible selectable features"))
            assertTrue(initial.accessibilityHostDescription.contains("Labels layer visible"))
            assertTrue(initial.accessibilityHostDescription.contains("Lumbridge"))

            val representativePoi = initial.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value]
                .orEmpty()
                .first()
            val representativePoiName = requireNotNull(
                osrsMapPrototypeOverlay.features.firstOrNull { it.id == representativePoi }
            ).name.removePrefix("SEM ")
            val providerAssertions = AtomicReference<JSONObject>()
            scenario.onActivity { activity ->
                val overlay = requireNotNull(activity.findViewById<View>(R.id.map_semantic_overlay))
                assertTrue(overlay.isClickable)
                assertTrue(overlay.isFocusable)
            }
            instrumentation.waitForIdleSync()
            val visibleNodes = accessibilityNodes()
            val poiNode = requireNotNull(visibleNodes.firstOrNull { node ->
                val description = node.contentDescription?.toString().orEmpty()
                description.contains(representativePoiName) && description.contains("Visible point of interest")
            }) { "Rendered POI was not exposed as a virtual accessibility child" }
            assertTrue(poiNode.isEnabled)
            assertTrue(poiNode.isClickable)
            val hostNode = requireNotNull(visibleNodes.firstOrNull { node ->
                node.contentDescription?.toString().orEmpty().startsWith("Semantic map. Visible selectable features")
            }) { "Semantic map host node was not exposed" }
            assertTrue(hostNode.isClickable)
            assertTrue(hostNode.contentDescription.toString().contains("Known searchable places"))
            assertTrue(poiNode.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            providerAssertions.set(
                JSONObject()
                    .put("poi_feature_id", representativePoi)
                    .put("poi_description", poiNode.contentDescription.toString())
                    .put("host_description", hostNode.contentDescription.toString())
                    .put("virtual_child_count", visibleNodes.count {
                        it.contentDescription?.toString().orEmpty().contains("Visible ")
                    })
            )
            val afterAccessibilityClick = waitForDiagnostics(scenario, evidenceDir, "accessibility-poi-click") {
                it.lastHitFeatureId == representativePoi
            }
            assertEquals(representativePoi, afterAccessibilityClick.lastHitFeatureId)
            stableScreenshot(evidenceDir, "01-accessibility-visible-features").recycle()
            dumpHierarchy(evidenceDir, "01-accessibility-visible-features")

            scenario.onActivity { activity ->
                activity.mapFragmentForTesting()?.setPrototypeLayerVisibilityForTesting(labels = false)
            }
            val labelsOff = waitForDiagnostics(scenario, evidenceDir, "accessibility-labels-off") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.LABEL.value].orEmpty().isEmpty() &&
                    it.accessibilityHostDescription.contains("Labels layer hidden")
            }
            val labelIds = osrsMapPrototypeOverlay.features
                .filter { it.kind == osrsMapFeatureKind.LABEL }
                .map { it.id }
            assertTrue(labelsOff.accessibilityVisibleFeatureIds.none(labelIds::contains))
            assertTrue(labelsOff.accessibilityHostDescription.contains("Known searchable places"))
            assertTrue(labelsOff.accessibilityHostDescription.contains("Lumbridge"))
            val hiddenLabel = labelIds.first()
            val hiddenLabelName = requireNotNull(
                osrsMapPrototypeOverlay.features.firstOrNull { it.id == hiddenLabel }
            ).name.removePrefix("SEM ")
            val labelsOffNodes = accessibilityNodes()
            assertTrue(labelsOffNodes.none { node ->
                val description = node.contentDescription?.toString().orEmpty()
                description.contains(hiddenLabelName) && description.contains("Visible label")
            })
            assertTrue(labelsOffNodes.any { node ->
                val description = node.contentDescription?.toString().orEmpty()
                description.startsWith("Semantic map.") && description.contains(hiddenLabelName)
            })
            stableScreenshot(evidenceDir, "02-accessibility-labels-off").recycle()
            dumpHierarchy(evidenceDir, "02-accessibility-labels-off")

            assertions.put("initial", initial.toJson())
            assertions.put("provider", providerAssertions.get())
            assertions.put("after_accessibility_click", afterAccessibilityClick.toJson())
            assertions.put("labels_off", labelsOff.toJson())
            assertions.put("hidden_label_not_selectable", hiddenLabel)
        }
        File(evidenceDir, "assertions.json").writeText(assertions.toString(2))
    }

    @Test
    fun candidate008VirtualTargetsAreTruthfulSizedAndUnambiguous() {
        val scale = targetContext.resources.configuration.fontScale
        val scaleLabel = scale.toString().replace('.', '_')
        val evidenceDir = File(evidenceRoot, "virtual-targets-font-$scaleLabel").resetDirectory()
        val assertions = JSONObject().put("font_scale", scale.toDouble())
        ActivityScenario.launch(SemanticPrototypeStandaloneActivity::class.java).use { scenario ->
            waitForDiagnostics(scenario, evidenceDir, "initial-ready") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.MAP_LINK.value].orEmpty().isNotEmpty()
            }
            runOnFragment(scenario) {
                fragment.setPrototypeCameraForTesting(
                    2965.0,
                    3378.0,
                    osrsMapPrototypeOverlay.initialZoom
                )
            }
            val falador = waitForDiagnostics(scenario, evidenceDir, "falador-offscreen-links") {
                val content = it.mapContentBounds
                content != null && listOf(
                    "link-kharidian-underground-pending",
                    "link-draynor-recenter",
                    "link-al-kharid-recenter"
                ).all { id ->
                    val point = it.featureScreenPoints.getValue(id)
                    !content.contains(point.x, point.y)
                }
            }
            val content = requireNotNull(falador.mapContentBounds)
            val offscreenLinks = listOf(
                "link-kharidian-underground-pending",
                "link-draynor-recenter",
                "link-al-kharid-recenter"
            )
            offscreenLinks.forEach { id ->
                assertTrue(id !in falador.renderedFeatureBounds)
                assertTrue(id !in falador.virtualTargetBounds)
                assertTrue(id !in falador.accessibilityVisibleFeatureIds)
            }
            val offscreenNames = offscreenLinks.map { id ->
                requireNotNull(osrsMapPrototypeOverlay.features.firstOrNull { it.id == id })
                    .name.removePrefix("SEM ")
            }
            val visibleMapLinkDescriptions = accessibilityNodes()
                .map { it.contentDescription?.toString().orEmpty() }
                .filter { it.contains("Visible map link") }
            assertTrue(visibleMapLinkDescriptions.none { description ->
                offscreenNames.any(description::contains)
            })
            stableScreenshot(evidenceDir, "01-falador-offscreen-links-not-advertised").recycle()
            dumpHierarchy(evidenceDir, "01-falador-offscreen-links-not-advertised")

            val density = targetContext.resources.displayMetrics.density
            val minimumPx = 48f * density
            val targetRows = JSONArray()
            val overlapRows = JSONArray()
            falador.virtualTargetBounds.forEach { (id, target) ->
                val visual = requireNotNull(falador.renderedFeatureBounds[id])
                assertTrue("$id target width ${target.width()} < $minimumPx", target.width() + 0.01f >= minimumPx)
                assertTrue("$id target height ${target.height()} < $minimumPx", target.height() + 0.01f >= minimumPx)
                assertTrue(content.contains(target))
                assertTrue(target.contains(visual))
                assertEquals(id, hitFeatureId(scenario, target.centerX(), target.centerY()))
                targetRows.put(
                    JSONObject()
                        .put("id", id)
                        .put("visual", visual.toJson())
                        .put("target", target.toJson())
                        .put("width_dp", target.width() / density)
                        .put("height_dp", target.height() / density)
                        .put("center_hit", id)
                )
            }
            val targets = falador.virtualTargetBounds.entries.toList()
            targets.indices.forEach { leftIndex ->
                for (rightIndex in leftIndex + 1 until targets.size) {
                    val (leftId, left) = targets[leftIndex]
                    val (rightId, right) = targets[rightIndex]
                    val overlapWidth = (minOf(left.right, right.right) - maxOf(left.left, right.left)).coerceAtLeast(0f)
                    val overlapHeight = (minOf(left.bottom, right.bottom) - maxOf(left.top, right.top)).coerceAtLeast(0f)
                    if (overlapWidth > 0f && overlapHeight > 0f) {
                        overlapRows.put(
                            JSONObject()
                                .put("left_id", leftId)
                                .put("right_id", rightId)
                                .put("overlap_area_px", overlapWidth * overlapHeight)
                                .put("left_center_hit", hitFeatureId(scenario, left.centerX(), left.centerY()))
                                .put("right_center_hit", hitFeatureId(scenario, right.centerX(), right.centerY()))
                        )
                    }
                }
            }

            runOnFragment(scenario) {
                fragment.setPrototypeCameraForTesting(
                    3198.0,
                    3224.0,
                    osrsMapPrototypeOverlay.initialZoom
                )
            }
            val visibleLinks = waitForDiagnostics(scenario, evidenceDir, "visible-link-targets") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.MAP_LINK.value].orEmpty().isNotEmpty()
            }
            val linkBoundaryRows = JSONArray()
            visibleLinks.renderedFeatureIdsByKind[osrsMapFeatureKind.MAP_LINK.value].orEmpty().forEach { id ->
                val target = requireNotNull(visibleLinks.virtualTargetBounds[id])
                val points = listOf(
                    target.centerX() to target.centerY(),
                    (target.left + 1f) to target.centerY(),
                    (target.right - 1f) to target.centerY(),
                    target.centerX() to (target.top + 1f),
                    target.centerX() to (target.bottom - 1f)
                )
                val hits = points.map { (x, y) -> hitFeatureId(scenario, x, y) }
                assertTrue("$id boundary routing was $hits", hits.all { it == id })
                linkBoundaryRows.put(
                    JSONObject()
                        .put("id", id)
                        .put("target", target.toJson())
                        .put("hits", JSONArray(hits))
                )
            }
            stableScreenshot(evidenceDir, "02-visible-link-target-boundaries").recycle()
            dumpHierarchy(evidenceDir, "02-visible-link-target-boundaries")
            assertions.put("falador", falador.toJson())
            assertions.put("offscreen_link_ids", JSONArray(offscreenLinks))
            assertions.put("virtual_targets", targetRows)
            assertions.put("overlap_matrix", overlapRows)
            assertions.put("visible_link_boundaries", linkBoundaryRows)
        }
        File(evidenceDir, "assertions.json").writeText(assertions.toString(2))
    }

    @Test
    fun settledControlPerformanceOnly() {
        val evidenceDir = File(evidenceRoot, "settled-performance").resetDirectory()
        val assertions = JSONObject()
        ActivityScenario.launch(SemanticPrototypeStandaloneActivity::class.java).use { scenario ->
            waitForDiagnostics(scenario, evidenceDir, "performance-ready") {
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().size >= 3
            }
            stableScreenshot(evidenceDir, "performance-ready").recycle()
            try {
                testSettledFrameMetrics(scenario, assertions, evidenceDir)
            } finally {
                File(evidenceDir, "assertions.json").writeText(assertions.toString(2))
            }
        }
    }

    private fun testVisibleCoordinateHits(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        evidenceDir: File,
        assertions: JSONObject
    ) {
        runOnFragment(scenario) {
            fragment.setPrototypeCameraForTesting(3198.0, 3224.0, osrsMapPrototypeOverlay.initialZoom)
            fragment.setPrototypeLayerVisibilityForTesting(labels = true, pois = true, links = true)
        }
        val before = waitForDiagnostics(scenario, evidenceDir, "before-visible-hits") {
            it.renderedFeatureIdsByKind.values.sumOf(List<String>::size) >= 5
        }
        assertNoLabelPoiOverlap(before)

        val labelId = before.renderedFeatureIdsByKind[osrsMapFeatureKind.LABEL.value].orEmpty().first()
        tapFeature(scenario, before, labelId)
        val afterLabel = waitForDiagnostics(scenario, evidenceDir, "after-label-hit") {
            it.lastHitFeatureId == labelId
        }
        assertEquals(labelId, afterLabel.lastHitFeatureId)

        val poiId = before.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().first()
        tapFeature(scenario, latestDiagnostics(scenario), poiId)
        val afterPoi = waitForDiagnostics(scenario, evidenceDir, "after-poi-hit") {
            it.lastHitFeatureId == poiId
        }
        assertEquals(poiId, afterPoi.lastHitFeatureId)

        val unknownLinkId = "link-kharidian-underground-pending"
        val unknownBefore = latestDiagnostics(scenario)
        if (unknownLinkId in unknownBefore.renderedFeatureIdsByKind[osrsMapFeatureKind.MAP_LINK.value].orEmpty()) {
            val cameraBefore = unknownBefore.cameraTriple()
            tapFeature(scenario, unknownBefore, unknownLinkId)
            val afterUnknown = waitForDiagnostics(scenario, evidenceDir, "after-unknown-link-hit") {
                it.lastHitFeatureId == unknownLinkId
            }
            assertCameraEqual(cameraBefore, afterUnknown.cameraTriple(), 0.00001)
            assertTrue(afterUnknown.lastActionDescription.orEmpty().contains("unknown_pending_evidence"))
        }

        runOnFragment(scenario) {
            fragment.setPrototypeCameraForTesting(3198.0, 3224.0, osrsMapPrototypeOverlay.initialZoom)
        }
        val recenterBefore = waitForDiagnostics(scenario, evidenceDir, "before-recenter-link-hit") {
            "link-draynor-recenter" in it.renderedFeatureIdsByKind[osrsMapFeatureKind.MAP_LINK.value].orEmpty()
        }
        tapFeature(scenario, recenterBefore, "link-draynor-recenter")
        val afterRecenter = waitForDiagnostics(scenario, evidenceDir, "after-recenter-link-hit") {
            it.lastHitFeatureId == "link-draynor-recenter" &&
                it.isNear(osrsMapPrototypeOverlay.gameToLatLng(3093.0, 3244.0), 0.02)
        }

        runOnFragment(scenario) {
            fragment.setPrototypeCameraForTesting(2965.0, 3378.0, osrsMapPrototypeOverlay.initialZoom)
        }
        val overlap = waitForDiagnostics(scenario, evidenceDir, "falador-visible-overlap") {
            "label-falador" in it.renderedFeatureIdsByKind[osrsMapFeatureKind.LABEL.value].orEmpty() &&
                "poi-falador-square-overlap" in
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty()
        }
        val labelBounds = requireNotNull(overlap.renderedFeatureBounds["label-falador"])
        val poiBounds = requireNotNull(overlap.renderedFeatureBounds["poi-falador-square-overlap"])
        val overlapLeft = maxOf(labelBounds.left, poiBounds.left)
        val overlapTop = maxOf(labelBounds.top, poiBounds.top)
        val overlapRight = minOf(labelBounds.right, poiBounds.right)
        val overlapBottom = minOf(labelBounds.bottom, poiBounds.bottom)
        assertTrue(
            "Falador fixture must render simultaneous label/POI overlap",
            overlapRight > overlapLeft && overlapBottom > overlapTop
        )
        val mapBounds = viewScreenBounds(scenario, R.id.map_view)
        assertTrue(
            device.click(
                mapBounds.left + ((overlapLeft + overlapRight) / 2f).roundToInt(),
                mapBounds.top + ((overlapTop + overlapBottom) / 2f).roundToInt()
            )
        )
        val overlapHit = waitForDiagnostics(scenario, evidenceDir, "falador-overlap-poi-precedence") {
            it.lastHitFeatureId == "poi-falador-square-overlap"
        }
        stableScreenshot(evidenceDir, "11-visible-falador-overlap-poi-precedence").recycle()
        assertions.put("visible_coordinate_label_hit", labelId)
        assertions.put("visible_coordinate_poi_hit_with_labels_on", poiId)
        assertions.put("visible_coordinate_recenter_link_hit", afterRecenter.lastActionDescription)
        assertions.put(
            "visible_overlap_bounds",
            JSONObject()
                .put("label", labelBounds.toJson())
                .put("poi", poiBounds.toJson())
                .put("resolved_hit", overlapHit.lastHitFeatureId)
        )
    }

    private fun testSearchSurfaceAndOverview(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        evidenceDir: File,
        assertions: JSONObject
    ) {
        runOnFragment(scenario) { fragment.performPrototypeSearchForTesting("Falador") }
        val falador = waitForDiagnostics(scenario, evidenceDir, "search-falador") {
            it.isNear(osrsMapPrototypeOverlay.gameToLatLng(2965.0, 3378.0), 0.02)
        }
        stableScreenshot(evidenceDir, "12-search-falador").recycle()
        val beforeMiss = falador.cameraTriple()
        val missResult = AtomicReference<Boolean>()
        scenario.onActivity { activity ->
            missResult.set(activity.mapFragmentForTesting()?.performPrototypeSearchForTesting("no-such-place-8844") == true)
        }
        assertFalse(missResult.get())
        val afterMiss = latestDiagnostics(scenario)
        assertCameraEqual(beforeMiss, afterMiss.cameraTriple(), 0.00001)
        runOnFragment(scenario) { fragment.restorePrototypeCameraForTesting() }

        val disabledSurface = AtomicReference<Boolean>()
        scenario.onActivity { activity ->
            disabledSurface.set(
                activity.mapFragmentForTesting()
                    ?.selectPrototypeSurfaceForTesting("kharidian-desert-underground") == true
            )
        }
        assertFalse(disabledSurface.get())
        assertEquals("gielinor-surface", latestDiagnostics(scenario).activeSurfaceId)
        clickRes("prototype_surface_selector")
        assertNotNull(device.findObject(By.textContains("Kharidian Desert Underground")))
        stableScreenshot(evidenceDir, "13-surface-selector-unavailable-honest").recycle()
        device.pressBack()

        val beforeOverview = latestDiagnostics(scenario).cameraTriple()
        val overviewBounds = viewScreenBounds(scenario, R.id.prototype_overview)
        device.swipe(
            overviewBounds.centerX(),
            overviewBounds.centerY(),
            overviewBounds.right - 18,
            overviewBounds.top + 18,
            20
        )
        val afterOverview = waitForDiagnostics(scenario, evidenceDir, "after-overview-drag") {
            cameraDistance(beforeOverview, it.cameraTriple()) > 0.01
        }
        assertions.put("search_falador_camera", falador.cameraJson())
        assertions.put("search_miss_camera_preserved", true)
        assertions.put("disabled_surface_preserved_gielinor", true)
        assertions.put("overview_drag_camera", afterOverview.cameraJson())
        stableScreenshot(evidenceDir, "14-after-overview-drag").recycle()
        runOnFragment(scenario) { fragment.restorePrototypeCameraForTesting() }
    }

    private fun testContinuousZoomAndReferenceStops(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        evidenceDir: File,
        assertions: JSONObject
    ) {
        runOnFragment(scenario) {
            fragment.setPrototypeCameraForTesting(3198.0, 3224.0, osrsMapPrototypeOverlay.initialZoom)
        }
        val zoomValues = listOf(6.18, 6.46, 6.79, 7.08, 7.42, 7.77, 8.11, 8.49, 8.73)
        val zoomResults = JSONArray()
        val poiSizes = mutableListOf<Double>()
        val labelSizes = mutableListOf<Double>()
        val labelSets = mutableListOf<Set<String>>()
        for ((index, zoom) in zoomValues.withIndex()) {
            runOnFragment(scenario) { fragment.setPrototypeZoomForTesting(zoom) }
            val diagnostics = waitForDiagnostics(scenario, evidenceDir, "zoom-$index") {
                abs(it.cameraZoom - zoom) < 0.01 && it.semanticMetricsPx["poi_radius"] != null
            }
            val shot = stableScreenshot(evidenceDir, "15-zoom-${index + 1}-${zoom.toString().replace('.', '_')}")
            val poiId = diagnostics.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().firstOrNull()
            val crispEdges = if (poiId != null) {
                edgeTransitions(shot, featureCrop(scenario, diagnostics, listOf(poiId), shot, 24))
            } else {
                0
            }
            assertTrue("Semantic icon must remain crisp at zoom $zoom: edges=$crispEdges", crispEdges > 8)
            poiSizes += diagnostics.semanticMetricsPx.getValue("poi_radius")
            labelSizes += diagnostics.semanticMetricsPx.getValue("label_text")
            labelSets += diagnostics.renderedFeatureIdsByKind[osrsMapFeatureKind.LABEL.value].orEmpty().toSet()
            zoomResults.put(
                diagnostics.toJson()
                    .put("requested_zoom", zoom)
                    .put("semantic_edge_transitions", crispEdges)
            )
            shot.recycle()
        }
        assertTrue(poiSizes.zipWithNext().all { (a, b) -> b > a })
        assertTrue(labelSizes.zipWithNext().all { (a, b) -> b > a })
        assertTrue((poiSizes.last() - poiSizes.first()) > (labelSizes.last() - labelSizes.first()) * 1.5)
        assertTrue("Label hierarchy must transition across continuous zoom", labelSets.first() != labelSets.last())
        assertions.put("continuous_intermediate_zoom", zoomResults)
        assertions.put("independent_poi_scale_delta_px", poiSizes.last() - poiSizes.first())
        assertions.put("independent_label_scale_delta_px", labelSizes.last() - labelSizes.first())

        val fullRangeResults = JSONArray()
        val fullRangeZooms = listOf(0.0, 3.0, 5.9, 9.0, 12.0)
        val effectiveFullRangeZooms = mutableListOf<Double>()
        for ((index, zoom) in fullRangeZooms.withIndex()) {
            runOnFragment(scenario) { fragment.setPrototypeCameraForTesting(3198.0, 3224.0, zoom) }
            val diagnostics = waitForDiagnostics(scenario, evidenceDir, "full-range-zoom-$index") {
                val reachedRequestedRange = if (zoom == 0.0) {
                    // MapLibre clamps the requested zero to the effective viewport minimum.
                    it.cameraZoom in 0.0..2.25
                } else {
                    abs(it.cameraZoom - zoom) < 0.01
                }
                reachedRequestedRange && it.semanticLayersPresent.values.all { present -> present }
            }
            effectiveFullRangeZooms += diagnostics.cameraZoom
            val shot = stableScreenshot(evidenceDir, "15-range-${index + 1}-${zoom.toString().replace('.', '_')}")
            val mapEdges = edgeTransitions(shot, mapCenterCrop(shot))
            assertTrue("Base map must retain rendered detail at supported zoom $zoom: edges=$mapEdges", mapEdges > 20)
            assertTrue(diagnostics.semanticMetricsPx.values.all { it > 0.0 })
            fullRangeResults.put(
                diagnostics.toJson()
                    .put("requested_zoom", zoom)
                    .put("map_edge_transitions", mapEdges)
            )
            shot.recycle()
        }
        assertTrue(effectiveFullRangeZooms.zipWithNext().all { (left, right) -> right > left })
        assertions.put("full_supported_zoom_range", fullRangeResults)

        runOnFragment(scenario) {
            fragment.setPrototypeCameraForTesting(3198.0, 3224.0, osrsMapPrototypeOverlay.initialZoom)
        }
        val stopStartDiagnostics = waitForDiagnostics(scenario, evidenceDir, "stop-100-start") {
            it.referenceStopPercent == 100
        }
        val stopStart = stableScreenshot(evidenceDir, "16-stop-100-start")
        val expectedStops = listOf(200, 37, 50, 75, 100)
        val observedStops = JSONArray()
        for ((index, expected) in expectedStops.withIndex()) {
            clickRes("prototype_reference_stop")
            val stopped = waitForDiagnostics(scenario, evidenceDir, "stop-cycle-$index") {
                it.referenceStopPercent == expected
            }
            observedStops.put(stopped.cameraJson().put("percent", expected))
        }
        val stopReturnDiagnostics = latestDiagnostics(scenario)
        val stopReturn = stableScreenshot(evidenceDir, "17-stop-100-return")
        assertCameraEqual(stopStartDiagnostics.cameraTriple(), stopReturnDiagnostics.cameraTriple(), 0.00001)
        val resetCrop = mapCenterCrop(stopStart)
        val resetPixelDelta = changedPixels(stopStart, stopReturn, resetCrop, threshold = 36)
        val sampledPixels = ((resetCrop.width() + 1) / 2) * ((resetCrop.height() + 1) / 2)
        val resetFraction = resetPixelDelta.toDouble() / sampledPixels.toDouble()
        assertions.put("reference_stop_cycle", observedStops)
        assertions.put("stop_100_return_changed_fraction", resetFraction)
        assertTrue("Returning to 100% must restore a stable view: fraction=$resetFraction", resetFraction < 0.08)
        stopStart.recycle()
        stopReturn.recycle()

        testGeographyReferenceLadder(
            scenario = scenario,
            evidenceDir = evidenceDir,
            assertions = assertions,
            key = "falador",
            gameX = 2965.0,
            gameY = 3378.0,
            expectedLowZoomLabelId = "label-asgarnia"
        )
        testGeographyReferenceLadder(
            scenario = scenario,
            evidenceDir = evidenceDir,
            assertions = assertions,
            key = "ardent-ocean",
            gameX = 2520.0,
            gameY = 2550.0,
            expectedLowZoomLabelId = "label-ardent-ocean"
        )
    }

    private fun testGeographyReferenceLadder(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        evidenceDir: File,
        assertions: JSONObject,
        key: String,
        gameX: Double,
        gameY: Double,
        expectedLowZoomLabelId: String
    ) {
        val anchorResults = JSONArray()
        val anchorStops = osrsMapPrototypeOverlay.referenceStops
        for (stop in anchorStops) {
            runOnFragment(scenario) {
                fragment.setPrototypeCameraForTesting(gameX, gameY, stop.mapLibreZoom)
            }
            val diagnostics = waitForDiagnostics(scenario, evidenceDir, "$key-anchor-${stop.percent}") {
                val visibleLabels = it.renderedFeatureIdsByKind[osrsMapFeatureKind.LABEL.value].orEmpty()
                val visiblePois = it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty()
                abs(it.cameraZoom - stop.mapLibreZoom) < 0.01 &&
                    it.semanticMetricsPx["poi_radius"] != null &&
                    (stop.percent !in setOf(37, 50) || expectedLowZoomLabelId in visibleLabels) &&
                    (key != "ardent-ocean" || stop.percent < 75 || expectedLowZoomLabelId !in visibleLabels) &&
                    (key != "ardent-ocean" || "poi-ardent-anchorage" in visiblePois)
            }
            val screenshot = stableScreenshot(evidenceDir, "18-$key-anchor-${stop.percent}")
            val terrainEdges = edgeTransitions(screenshot, mapCenterCrop(screenshot))
            assertTrue("$key terrain must stay detailed at ${stop.percent}%: $terrainEdges", terrainEdges > 20)
            val visibleLabels = diagnostics
                .renderedFeatureIdsByKind[osrsMapFeatureKind.LABEL.value]
                .orEmpty()
            if (stop.percent == 37 || stop.percent == 50) {
                assertTrue(
                    "$expectedLowZoomLabelId must orient $key at ${stop.percent}%",
                    expectedLowZoomLabelId in visibleLabels
                )
            }
            if (key == "ardent-ocean" && stop.percent >= 75) {
                assertFalse(
                    "Ardent Ocean sea label must declutter above 50%",
                    expectedLowZoomLabelId in visibleLabels
                )
            }
            if (key == "ardent-ocean") {
                val visiblePois = diagnostics
                    .renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value]
                    .orEmpty()
                assertTrue(
                    "Sparse anchorage must remain usable at ${stop.percent}%",
                    "poi-ardent-anchorage" in visiblePois
                )
                if (stop.percent == 200) {
                    assertEquals("Ardent Ocean must be minimally dense at 200%", 1, visiblePois.size)
                }
            }
            anchorResults.put(
                diagnostics.toJson()
                    .put("percent", stop.percent)
                    .put("terrain_edge_transitions", terrainEdges)
            )
            screenshot.recycle()
        }

        val continuousResults = JSONArray()
        val slowZooms = buildList {
            anchorStops.zipWithNext().forEachIndexed { segment, (start, end) ->
                if (segment == 0) add(start.mapLibreZoom)
                for (step in 1..4) {
                    add(start.mapLibreZoom + (end.mapLibreZoom - start.mapLibreZoom) * step / 4.0)
                }
            }
        }
        for ((index, zoom) in slowZooms.withIndex()) {
            val started = SystemClock.elapsedRealtimeNanos()
            runOnFragment(scenario) { fragment.setPrototypeCameraForTesting(gameX, gameY, zoom) }
            val diagnostics = waitForDiagnostics(scenario, evidenceDir, "$key-slow-$index") {
                abs(it.cameraZoom - zoom) < 0.01 &&
                    it.semanticMetricsPx["poi_radius"] != null
            }
            val settledMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
            continuousResults.put(
                diagnostics.toJson()
                    .put("requested_zoom", zoom)
                    .put("settled_ms", settledMs)
            )
        }
        val poiSizes = (0 until continuousResults.length()).map {
            continuousResults.getJSONObject(it)
                .getJSONObject("semantic_metrics_px")
                .getDouble("poi_radius")
        }
        val labelSizes = (0 until continuousResults.length()).map {
            continuousResults.getJSONObject(it)
                .getJSONObject("semantic_metrics_px")
                .getDouble("label_text")
        }
        assertTrue("$key POI scaling must be strictly continuous", poiSizes.zipWithNext().all { (a, b) -> b > a })
        assertTrue("$key label scaling must be strictly continuous", labelSizes.zipWithNext().all { (a, b) -> b > a })
        assertions.put(
            "${key.replace('-', '_')}_reference_ladder",
            JSONObject()
                .put("anchors", anchorResults)
                .put("slow_continuous_zoom", continuousResults)
                .put("poi_scale_delta_px", poiSizes.last() - poiSizes.first())
                .put("label_scale_delta_px", labelSizes.last() - labelSizes.first())
        )
    }

    private fun testPanStyleAndForegroundPersistence(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        evidenceDir: File,
        assertions: JSONObject
    ) {
        runOnFragment(scenario) {
            fragment.setPrototypeCameraForTesting(3198.0, 3224.0, osrsMapPrototypeOverlay.initialZoom)
        }
        val before = waitForDiagnostics(scenario, evidenceDir, "pan-before") {
            it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().isNotEmpty()
        }
        val trackedId = before.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().first()
        val beforePoint = before.featureScreenPoints.getValue(trackedId)
        runOnFragment(scenario) { fragment.setPrototypeCameraForTesting(3250.0, 3280.0, 7.58) }
        val away = waitForDiagnostics(scenario, evidenceDir, "pan-away") {
            val point = it.featureScreenPoints[trackedId]
            point != null && (abs(point.x - beforePoint.x) > 20 || abs(point.y - beforePoint.y) > 20)
        }
        stableScreenshot(evidenceDir, "18-pan-away").recycle()
        runOnFragment(scenario) {
            fragment.setPrototypeCameraForTesting(3198.0, 3224.0, osrsMapPrototypeOverlay.initialZoom)
        }
        val back = waitForDiagnostics(scenario, evidenceDir, "pan-back") {
            val point = it.featureScreenPoints[trackedId]
            point != null && abs(point.x - beforePoint.x) < 2 && abs(point.y - beforePoint.y) < 2
        }
        assertions.put("pan_tracked_feature", trackedId)
        assertions.put("pan_away_point", away.featureScreenPoints.getValue(trackedId).toJson())
        assertions.put("pan_back_point", back.featureScreenPoints.getValue(trackedId).toJson())

        runOnFragment(scenario) { fragment.recreatePrototypeStyleForTesting() }
        val styleReload = waitForDiagnostics(scenario, evidenceDir, "style-reload") {
            it.semanticLayersPresent.values.all { present -> present } &&
                it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().isNotEmpty()
        }
        stableScreenshot(evidenceDir, "19-after-style-reload").recycle()

        val lifecycleBefore = styleReload.cameraTriple()
        scenario.moveToState(Lifecycle.State.STARTED)
        SystemClock.sleep(180)
        scenario.moveToState(Lifecycle.State.RESUMED)
        val foreground = waitForDiagnostics(scenario, evidenceDir, "background-foreground") {
            it.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty().isNotEmpty()
        }
        assertCameraEqual(lifecycleBefore, foreground.cameraTriple(), 0.00001)
        assertions.put("style_reload", styleReload.toJson())
        assertions.put("background_foreground", foreground.toJson())
    }

    private fun testSettledFrameMetrics(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        assertions: JSONObject,
        evidenceDir: File
    ) {
        val frameSamples = Collections.synchronizedList(mutableListOf<SettledFrameMetricSample>())
        val thread = HandlerThread("prototype-frame-metrics").apply { start() }
        val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            fun metricMs(metric: Int): Double {
                return metrics.getMetric(metric).coerceAtLeast(0L) / 1_000_000.0
            }
            val sample = SettledFrameMetricSample(
                totalMs = metricMs(FrameMetrics.TOTAL_DURATION),
                deadlineMs = metricMs(FrameMetrics.DEADLINE),
                inputHandlingMs = metricMs(FrameMetrics.INPUT_HANDLING_DURATION),
                animationMs = metricMs(FrameMetrics.ANIMATION_DURATION),
                layoutMeasureMs = metricMs(FrameMetrics.LAYOUT_MEASURE_DURATION),
                drawMs = metricMs(FrameMetrics.DRAW_DURATION),
                syncMs = metricMs(FrameMetrics.SYNC_DURATION),
                commandIssueMs = metricMs(FrameMetrics.COMMAND_ISSUE_DURATION),
                swapBuffersMs = metricMs(FrameMetrics.SWAP_BUFFERS_DURATION)
            )
            if (sample.totalMs > 0.0 && sample.deadlineMs > 0.0) {
                frameSamples += sample
            }
        }
        scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
        repeat(3) {
            clickRes("prototype_toggle_labels")
            clickRes("prototype_toggle_pois")
            clickRes("prototype_toggle_links")
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(350)
        scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
        thread.quitSafely()
        val samples = frameSamples.toList()
        val rawTotals = samples.map { it.totalMs }.sorted()
        val uiThreadWork = samples.map { it.uiThreadWorkMs }.sorted()
        val rendererSubmission = samples.map { it.rendererSubmissionMs }.sorted()
        val measuredPhaseComposite = samples.map { it.measuredPhaseCompositeMs }.sorted()
        assertTrue("FrameMetrics did not capture settled toggle frames", rawTotals.size >= 6)
        val uiThreadWorkP50 = percentile(uiThreadWork, 0.50)
        val uiThreadWorkP90 = percentile(uiThreadWork, 0.90)
        val uiThreadWorkP95 = percentile(uiThreadWork, 0.95)
        val rendererSubmissionP95 = percentile(rendererSubmission, 0.95)
        val measuredPhaseCompositeP95 = percentile(measuredPhaseComposite, 0.95)
        val rawP95 = percentile(rawTotals, 0.95)
        val uiThreadWorkDeadlineMissRatio = samples.count { it.uiThreadWorkMs >= it.deadlineMs }
            .toDouble() / samples.size.toDouble()
        val measuredPhaseCompositeDeadlineMissRatio = samples.count { it.measuredPhaseCompositeMs >= it.deadlineMs }
            .toDouble() / samples.size.toDouble()
        val rawJankRatio = samples.count { it.totalMs >= it.deadlineMs }
            .toDouble() / samples.size.toDouble()
        val fixedBudgetMissRatio = rawTotals.count { it > 16.67 }.toDouble() / rawTotals.size.toDouble()
        val metricsJson = JSONObject()
            .put("frames", rawTotals.size)
            .put("ui_thread_work_durations_ms", JSONArray(uiThreadWork))
            .put("renderer_submission_durations_ms", JSONArray(rendererSubmission))
            .put("measured_phase_composite_durations_ms", JSONArray(measuredPhaseComposite))
            .put("raw_total_durations_ms", JSONArray(rawTotals))
            .put("deadlines_ms", JSONArray(samples.map { it.deadlineMs }.sorted()))
            .put("ui_thread_work_p50_ms", uiThreadWorkP50)
            .put("ui_thread_work_p90_ms", uiThreadWorkP90)
            .put("ui_thread_work_p95_ms", uiThreadWorkP95)
            .put("renderer_submission_p95_ms", rendererSubmissionP95)
            .put("measured_phase_composite_p95_ms", measuredPhaseCompositeP95)
            .put("raw_total_p95_ms", rawP95)
            .put("raw_total_p95_within_100ms_diagnostic", rawP95 < 100.0)
            .put("ui_thread_work_deadline_miss_ratio", uiThreadWorkDeadlineMissRatio)
            .put("measured_phase_composite_deadline_miss_ratio", measuredPhaseCompositeDeadlineMissRatio)
            .put("raw_total_deadline_miss_ratio", rawJankRatio)
            .put("raw_fixed_16_67ms_miss_ratio_diagnostic", fixedBudgetMissRatio)
        assertions.put("settled_toggle_frame_metrics", metricsJson)
        File(evidenceDir, "settled-toggle-frame-metrics.json").writeText(metricsJson.toString(2))
        assertTrue(
            "Settled UI-thread app-owned control p95 must remain under 50ms: $uiThreadWorkP95",
            uiThreadWorkP95 < 50.0
        )
        assertTrue(
            "Settled UI-thread app-owned deadline-miss ratio must remain under 35%: " +
                uiThreadWorkDeadlineMissRatio,
            uiThreadWorkDeadlineMissRatio < 0.35
        )
    }

    private fun waitForNavigationState(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        evidenceDir: File,
        label: String,
        query: String,
        status: String,
        gameX: Double,
        gameY: Double
    ): osrsMapPrototypeDiagnostics {
        val target = osrsMapPrototypeOverlay.gameToLatLng(gameX, gameY)
        val diagnostics = waitForDiagnostics(scenario, evidenceDir, label) {
            it.searchQuery == query &&
                it.statusText == status &&
                it.statusContentDescription == status &&
                it.isNear(target, 0.02)
        }
        assertStatusView(scenario, status)
        return diagnostics
    }

    private fun recreateAndAssertState(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        evidenceDir: File,
        label: String,
        camera: Triple<Double, Double, Double>,
        query: String,
        status: String
    ): osrsMapPrototypeDiagnostics {
        val previousIdentity = activityIdentity(scenario)
        scenario.recreate()
        val diagnostics = waitForDiagnostics(scenario, evidenceDir, label) {
            activityIdentity(scenario) != previousIdentity &&
                it.searchQuery == query &&
                it.statusText == status &&
                it.statusContentDescription == status &&
                it.renderedFeatureIdsByKind.values.sumOf(List<String>::size) > 0
        }
        assertCameraEqual(camera, diagnostics.cameraTriple(), 0.00001)
        assertStatusView(scenario, status)
        return diagnostics
    }

    private fun assertStatusView(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        expected: String
    ) {
        val actualText = AtomicReference<String>()
        val actualAccessibility = AtomicReference<String>()
        val visibility = AtomicInteger()
        scenario.onActivity { activity ->
            val status = requireNotNull(activity.findViewById<TextView>(R.id.prototype_status))
            actualText.set(status.text.toString())
            actualAccessibility.set(status.contentDescription?.toString().orEmpty())
            visibility.set(status.visibility)
        }
        assertEquals(View.VISIBLE, visibility.get())
        assertEquals(expected, actualText.get())
        assertEquals(expected, actualAccessibility.get())
    }

    private fun runOnFragment(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        block: FragmentHandle.() -> Unit
    ) {
        val failure = AtomicReference<Throwable?>()
        scenario.onActivity { activity ->
            try {
                val fragment = requireNotNull(activity.mapFragmentForTesting()) { "Prototype fragment not attached" }
                FragmentHandle(activity, fragment).block()
            } catch (throwable: Throwable) {
                failure.set(throwable)
            }
        }
        failure.get()?.let { throw AssertionError("Activity operation failed", it) }
        instrumentation.waitForIdleSync()
    }

    private fun hitFeatureId(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        x: Float,
        y: Float
    ): String? {
        val result = AtomicReference<String?>()
        scenario.onActivity { activity ->
            result.set(activity.mapFragmentForTesting()?.hitPrototypeFeatureIdForTesting(x, y))
        }
        return result.get()
    }

    private fun waitForDiagnostics(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        evidenceDir: File,
        label: String,
        predicate: (osrsMapPrototypeDiagnostics) -> Boolean
    ): osrsMapPrototypeDiagnostics {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        var latest: osrsMapPrototypeDiagnostics? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                latest = activity.mapFragmentForTesting()?.prototypeDiagnosticsForTesting()
            }
            val diagnostics = latest
            if (diagnostics != null && predicate(diagnostics)) {
                File(evidenceDir, "diagnostics-$label.json").writeText(diagnostics.toJson().toString(2))
                return diagnostics
            }
            SystemClock.sleep(120)
        }
        throw AssertionError("Timed out waiting for prototype diagnostics: $label; latest=${latest?.toJson()}")
    }

    private fun waitForTerrainPreviewDismissed(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>
    ) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var dismissed = false
            scenario.onActivity { activity ->
                dismissed = activity.findViewById<View?>(R.id.map_prototype_loading_preview) == null
            }
            if (dismissed) {
                instrumentation.waitForIdleSync()
                return
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for packaged terrain preview to hand off to MapLibre")
    }

    private fun latestDiagnostics(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>
    ): osrsMapPrototypeDiagnostics {
        val value = AtomicReference<osrsMapPrototypeDiagnostics?>()
        scenario.onActivity { activity ->
            value.set(activity.mapFragmentForTesting()?.prototypeDiagnosticsForTesting())
        }
        return requireNotNull(value.get())
    }

    private fun stableScreenshot(evidenceDir: File, name: String): Bitmap {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(260)
        val candidates = mutableListOf<Bitmap>()
        repeat(5) {
            candidates += takeScreenshot()
            SystemClock.sleep(180)
        }
        val completenessScores = candidates.map(::renderCompletenessRatio)
        val bestIndex = completenessScores.indices.maxBy { completenessScores[it] }
        val best = candidates[bestIndex]
        candidates.forEachIndexed { index, bitmap -> if (index != bestIndex) bitmap.recycle() }
        File(evidenceDir, "$name-capture.json").writeText(
            JSONObject()
                .put("sample_non_black_ratios", JSONArray(completenessScores))
                .put("selected_sample", bestIndex)
                .put("selected_non_black_ratio", completenessScores[bestIndex])
                .toString(2)
        )
        saveBitmap(best, File(evidenceDir, "$name.png"))
        return best
    }

    private fun renderCompletenessRatio(bitmap: Bitmap): Double {
        var rendered = 0L
        var sampled = 0L
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val red = color shr 16 and 0xff
                val green = color shr 8 and 0xff
                val blue = color and 0xff
                if (red > 24 || green > 24 || blue > 24) rendered++
                sampled++
                x += 4
            }
            y += 4
        }
        return rendered.toDouble() / sampled.toDouble()
    }

    private fun takeScreenshot(): Bitmap {
        repeat(5) { attempt ->
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.takeScreenshot()?.let { return it }
            if (attempt < 4) SystemClock.sleep(180)
        }
        throw AssertionError("UiAutomation screenshot returned null after 5 attempts")
    }

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun dumpHierarchy(evidenceDir: File, name: String) {
        device.dumpWindowHierarchy(File(evidenceDir, "$name-ui.xml"))
    }

    private fun clickRes(resourceName: String) {
        val node = device.findObject(By.res(targetContext.packageName, resourceName))
        assertNotNull("Missing UI control: $resourceName", node)
        node.click()
        device.waitForIdle()
        SystemClock.sleep(130)
    }

    private fun tapFeature(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        diagnostics: osrsMapPrototypeDiagnostics,
        featureId: String
    ) {
        val bounds = requireNotNull(diagnostics.renderedFeatureBounds[featureId]) {
            "Feature $featureId has no rendered bounds"
        }
        val mapBounds = viewScreenBounds(scenario, R.id.map_view)
        val x = mapBounds.left + ((bounds.left + bounds.right) / 2f).roundToInt()
        val y = mapBounds.top + ((bounds.top + bounds.bottom) / 2f).roundToInt()
        assertTrue("Feature tap failed for $featureId at $x,$y", device.click(x, y))
        device.waitForIdle()
        SystemClock.sleep(180)
    }

    private fun featureCrop(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        diagnostics: osrsMapPrototypeDiagnostics,
        featureIds: List<String>,
        bitmap: Bitmap,
        padding: Int
    ): Rect {
        val mapBounds = viewScreenBounds(scenario, R.id.map_view)
        val featureBounds = featureIds.mapNotNull(diagnostics.renderedFeatureBounds::get)
        require(featureBounds.isNotEmpty()) { "No rendered bounds for $featureIds" }
        return Rect(
            (mapBounds.left + featureBounds.minOf { it.left }.roundToInt() - padding).coerceIn(0, bitmap.width - 1),
            (mapBounds.top + featureBounds.minOf { it.top }.roundToInt() - padding).coerceIn(0, bitmap.height - 1),
            (mapBounds.left + featureBounds.maxOf { it.right }.roundToInt() + padding).coerceIn(1, bitmap.width),
            (mapBounds.top + featureBounds.maxOf { it.bottom }.roundToInt() + padding).coerceIn(1, bitmap.height)
        )
    }

    private fun viewScreenBounds(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        viewId: Int
    ): Rect {
        val result = AtomicReference<Rect>()
        scenario.onActivity { activity ->
            val view = requireNotNull(activity.findViewById<View>(viewId))
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            result.set(Rect(location[0], location[1], location[0] + view.width, location[1] + view.height))
        }
        return requireNotNull(result.get())
    }

    private fun changedPixels(left: Bitmap, right: Bitmap, crop: Rect, threshold: Int = 48): Int {
        var changed = 0
        var y = crop.top
        while (y < crop.bottom) {
            var x = crop.left
            while (x < crop.right) {
                val a = left.getPixel(x, y)
                val b = right.getPixel(x, y)
                val delta = abs((a shr 16 and 0xff) - (b shr 16 and 0xff)) +
                    abs((a shr 8 and 0xff) - (b shr 8 and 0xff)) +
                    abs((a and 0xff) - (b and 0xff))
                if (delta > threshold) changed++
                x += 2
            }
            y += 2
        }
        return changed
    }

    private fun edgeTransitions(bitmap: Bitmap, crop: Rect): Int {
        var transitions = 0
        var y = crop.top
        while (y < crop.bottom - 2) {
            var x = crop.left
            while (x < crop.right - 2) {
                val here = bitmap.getPixel(x, y)
                val right = bitmap.getPixel(x + 2, y)
                val down = bitmap.getPixel(x, y + 2)
                if (colorDelta(here, right) > 110) transitions++
                if (colorDelta(here, down) > 110) transitions++
                x += 2
            }
            y += 2
        }
        return transitions
    }

    private fun colorDelta(left: Int, right: Int): Int {
        return abs((left shr 16 and 0xff) - (right shr 16 and 0xff)) +
            abs((left shr 8 and 0xff) - (right shr 8 and 0xff)) +
            abs((left and 0xff) - (right and 0xff))
    }

    private fun terrainStabilityCrop(bitmap: Bitmap): Rect {
        return Rect(
            (bitmap.width * 0.03f).roundToInt(),
            (bitmap.height * 0.72f).roundToInt(),
            (bitmap.width * 0.17f).roundToInt(),
            (bitmap.height * 0.82f).roundToInt()
        )
    }

    private fun assertCropExcludesSemanticFeatures(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        diagnostics: osrsMapPrototypeDiagnostics,
        crop: Rect
    ) {
        val mapBounds = viewScreenBounds(scenario, R.id.map_view)
        diagnostics.renderedFeatureBounds.forEach { (featureId, bounds) ->
            val screenBounds = bounds.toRect().apply { offset(mapBounds.left, mapBounds.top) }
            assertFalse(
                "Terrain control crop intersects semantic feature $featureId: $screenBounds",
                Rect.intersects(crop, screenBounds)
            )
        }
    }

    private fun mapCenterCrop(bitmap: Bitmap): Rect {
        return Rect(
            (bitmap.width * 0.20f).roundToInt(),
            (bitmap.height * 0.26f).roundToInt(),
            (bitmap.width * 0.78f).roundToInt(),
            (bitmap.height * 0.78f).roundToInt()
        )
    }

    private fun assertNoLabelPoiOverlap(diagnostics: osrsMapPrototypeDiagnostics) {
        val labelIds = diagnostics.renderedFeatureIdsByKind[osrsMapFeatureKind.LABEL.value].orEmpty()
        val poiIds = diagnostics.renderedFeatureIdsByKind[osrsMapFeatureKind.POI.value].orEmpty()
        for (labelId in labelIds) {
            val label = diagnostics.renderedFeatureBounds[labelId] ?: continue
            for (poiId in poiIds) {
                val poi = diagnostics.renderedFeatureBounds[poiId] ?: continue
                val labelFeature = osrsMapPrototypeOverlay.features.firstOrNull { it.id == labelId }
                val poiFeature = osrsMapPrototypeOverlay.features.firstOrNull { it.id == poiId }
                if (labelFeature?.hitOverlapFixture == true && poiFeature?.hitOverlapFixture == true) {
                    continue
                }
                assertFalse(
                    "Rendered label $labelId obscures tappable POI $poiId",
                    Rect.intersects(label.toRect(), poi.toRect())
                )
            }
        }
    }

    private fun assertControlBudget(diagnostics: osrsMapPrototypeDiagnostics) {
        val duration = requireNotNull(diagnostics.lastControlDurationMs)
        assertTrue("App-owned control work exceeded 50ms: $duration", duration < 50.0)
    }

    private fun activityIdentity(scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>): Int {
        val identity = AtomicInteger()
        scenario.onActivity { identity.set(System.identityHashCode(it)) }
        return identity.get()
    }

    private fun waitForNewActivity(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        oldIdentity: Int,
        landscape: Boolean
    ): Int {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var identity = oldIdentity
            var isExpectedOrientation = false
            scenario.onActivity { activity ->
                identity = System.identityHashCode(activity)
                val orientation = activity.resources.configuration.orientation
                isExpectedOrientation = orientation == if (landscape) {
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                } else {
                    android.content.res.Configuration.ORIENTATION_PORTRAIT
                }
            }
            if (identity != oldIdentity && isExpectedOrientation) return identity
            SystemClock.sleep(120)
        }
        throw AssertionError("Activity did not recreate into expected orientation")
    }

    private fun assertPersistedCamera(
        expected: osrsMapPrototypeDiagnostics,
        actual: osrsMapPrototypeDiagnostics
    ) {
        assertCameraEqual(expected.cameraTriple(), actual.cameraTriple(), 0.00001)
        assertEquals(expected.highlightedCategories, actual.highlightedCategories)
        assertEquals(expected.activeSurfaceId, actual.activeSurfaceId)
        assertEquals(expected.overviewVisible, actual.overviewVisible)
    }

    private fun assertLandscapePanelsDoNotOverlap(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>
    ): JSONObject {
        assertEquals(View.GONE, viewVisibility(scenario, R.id.prototype_key_panel))
        return assertPanelsDoNotOverlap(
            scenario,
            linkedMapOf(
                "product" to R.id.prototype_product_controls,
                "overview" to R.id.prototype_overview,
                "floor" to R.id.floor_controls,
                "debug" to R.id.prototype_debug_controls
            )
        )
    }

    private fun assertPortraitPanelsDoNotOverlap(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>
    ): JSONObject {
        return assertPanelsDoNotOverlap(
            scenario,
            linkedMapOf(
                "product" to R.id.prototype_product_controls,
                "overview" to R.id.prototype_overview,
                "floor" to R.id.floor_controls,
                "key" to R.id.prototype_key_panel,
                "debug" to R.id.prototype_debug_controls
            )
        )
    }

    private fun assertPanelsDoNotOverlap(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        panelIds: LinkedHashMap<String, Int>
    ): JSONObject {
        val bounds = linkedMapOf<String, Rect>()
        scenario.onActivity { activity ->
            panelIds.forEach { (name, id) ->
                activity.findViewById<View>(id)?.takeIf { it.isShown }?.let { view ->
                    bounds[name] = viewBoundsOnScreen(view)
                }
            }
        }
        bounds.entries.toList().forEachIndexed { index, left ->
            bounds.entries.drop(index + 1).forEach { right ->
                assertFalse(
                    "Panels ${left.key} and ${right.key} overlap: ${left.value} / ${right.value}",
                    Rect.intersects(left.value, right.value)
                )
            }
        }
        val touchTargets = AtomicReference<JSONArray>()
        scenario.onActivity { activity -> touchTargets.set(assertMinimumTouchTargets(activity)) }
        return JSONObject()
            .put("bounds", JSONObject().apply {
                bounds.forEach { (name, rect) -> put(name, rect.toString()) }
            })
            .put("touch_targets", touchTargets.get())
    }

    private fun viewVisibility(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        viewId: Int
    ): Int {
        val value = AtomicInteger()
        scenario.onActivity { value.set(requireNotNull(it.findViewById<View>(viewId)).visibility) }
        return value.get()
    }

    private fun assertStatusAcrossOrientations(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        evidenceDir: File,
        label: String,
        expectedStatus: String
    ): JSONObject {
        val portraitBefore = waitForDiagnostics(scenario, evidenceDir, "$label-portrait-before") {
            it.statusText == expectedStatus && it.statusContentDescription == expectedStatus
        }
        val portraitIdentity = activityIdentity(scenario)
        val portraitPixels = captureStatusGlyphEvidence(
            scenario,
            evidenceDir,
            "$label-portrait",
            expectedStatus
        )

        device.setOrientationLeft()
        val landscapeIdentity = waitForNewActivity(scenario, portraitIdentity, landscape = true)
        val landscape = waitForDiagnostics(scenario, evidenceDir, "$label-landscape") {
            it.statusText == expectedStatus && it.statusContentDescription == expectedStatus
        }
        assertPersistedCamera(portraitBefore, landscape)
        val landscapePixels = captureStatusGlyphEvidence(
            scenario,
            evidenceDir,
            "$label-landscape",
            expectedStatus
        )
        val landscapeGeometry = assertLandscapePanelsDoNotOverlap(scenario)

        device.setOrientationNatural()
        val restoredPortraitIdentity = waitForNewActivity(scenario, landscapeIdentity, landscape = false)
        val restoredPortrait = waitForDiagnostics(scenario, evidenceDir, "$label-portrait-restored") {
            it.statusText == expectedStatus && it.statusContentDescription == expectedStatus
        }
        assertPersistedCamera(portraitBefore, restoredPortrait)
        val restoredPortraitGeometry = assertPortraitPanelsDoNotOverlap(scenario)

        return JSONObject()
            .put("status", expectedStatus)
            .put("portrait_activity_identity", portraitIdentity)
            .put("landscape_activity_identity", landscapeIdentity)
            .put("restored_portrait_activity_identity", restoredPortraitIdentity)
            .put("portrait_glyph_evidence", portraitPixels)
            .put("landscape_glyph_evidence", landscapePixels)
            .put("landscape_geometry", landscapeGeometry)
            .put("restored_portrait_geometry", restoredPortraitGeometry)
            .put("camera_before", portraitBefore.cameraJson())
            .put("camera_landscape", landscape.cameraJson())
            .put("camera_restored_portrait", restoredPortrait.cameraJson())
    }

    private fun captureStatusGlyphEvidence(
        scenario: ActivityScenario<SemanticPrototypeStandaloneActivity>,
        evidenceDir: File,
        name: String,
        expectedStatus: String
    ): JSONObject {
        val screenshot = stableScreenshot(evidenceDir, name)
        val glyphBounds = AtomicReference<Rect>()
        val statusBounds = AtomicReference<Rect>()
        val consumedCharacters = AtomicInteger()
        scenario.onActivity { activity ->
            val status = requireNotNull(activity.findViewById<TextView>(R.id.prototype_status))
            assertEquals(View.VISIBLE, status.visibility)
            assertEquals(expectedStatus, status.text.toString())
            assertEquals(expectedStatus, status.contentDescription?.toString())
            val layout = requireNotNull(status.layout)
            assertTrue("Status has no laid out lines", layout.lineCount > 0)
            val finalLine = layout.lineCount - 1
            consumedCharacters.set(layout.getLineEnd(finalLine))
            assertTrue(
                "Status layout truncated ${expectedStatus.length - consumedCharacters.get()} characters: $expectedStatus",
                consumedCharacters.get() >= expectedStatus.length
            )
            assertTrue((0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 })

            val wordStart = expectedStatus.lastIndexOf(' ').let { if (it < 0) 0 else it + 1 }
            val wordEnd = expectedStatus.length
            val wordLine = layout.getLineForOffset(wordStart)
            assertEquals("Final word wrapped or clipped unexpectedly", wordLine, layout.getLineForOffset(wordEnd - 1))
            val location = IntArray(2).also(status::getLocationOnScreen)
            val startX = layout.getPrimaryHorizontal(wordStart)
            val endX = layout.getPrimaryHorizontal(wordEnd)
            glyphBounds.set(
                Rect(
                    (location[0] + status.totalPaddingLeft + minOf(startX, endX)).roundToInt() - 2,
                    location[1] + status.totalPaddingTop + layout.getLineTop(wordLine) - 2,
                    (location[0] + status.totalPaddingLeft + maxOf(startX, endX)).roundToInt() + 2,
                    location[1] + status.totalPaddingTop + layout.getLineBottom(wordLine) + 2
                )
            )
            statusBounds.set(viewBoundsOnScreen(status))
            listOf(
                R.id.floor_controls,
                R.id.prototype_overview,
                R.id.prototype_key_panel,
                R.id.prototype_debug_controls
            )
                .mapNotNull(activity::findViewById)
                .filter { it.visibility == View.VISIBLE }
                .forEach { sibling ->
                    assertFalse(
                        "Status overlaps ${activity.resources.getResourceEntryName(sibling.id)}",
                        Rect.intersects(statusBounds.get(), viewBoundsOnScreen(sibling))
                    )
                }
        }
        val boundedGlyphs = Rect(glyphBounds.get()).apply {
            left = left.coerceIn(0, screenshot.width - 1)
            top = top.coerceIn(0, screenshot.height - 1)
            right = right.coerceIn(left + 1, screenshot.width)
            bottom = bottom.coerceIn(top + 1, screenshot.height)
        }
        var minimumLuminance = 255
        var maximumLuminance = 0
        val luminances = ArrayList<Int>(boundedGlyphs.width() * boundedGlyphs.height())
        for (y in boundedGlyphs.top until boundedGlyphs.bottom) {
            for (x in boundedGlyphs.left until boundedGlyphs.right) {
                val color = screenshot.getPixel(x, y)
                val luminance = ((color shr 16 and 0xff) * 299 +
                    (color shr 8 and 0xff) * 587 +
                    (color and 0xff) * 114) / 1000
                minimumLuminance = minOf(minimumLuminance, luminance)
                maximumLuminance = maxOf(maximumLuminance, luminance)
                luminances += luminance
            }
        }
        val threshold = minimumLuminance + (maximumLuminance - minimumLuminance) * 2 / 3
        val glyphPixels = luminances.count { it >= threshold }
        assertTrue("Final status word has insufficient pixel contrast", maximumLuminance - minimumLuminance >= 70)
        assertTrue("Final status word has no visible glyph pixels", glyphPixels >= 12)
        screenshot.recycle()
        return JSONObject()
            .put("layout_consumed_characters", consumedCharacters.get())
            .put("expected_characters", expectedStatus.length)
            .put("final_word", expectedStatus.substringAfterLast(' '))
            .put("final_word_bounds", boundedGlyphs.toString())
            .put("final_word_contrast", maximumLuminance - minimumLuminance)
            .put("final_word_glyph_pixels", glyphPixels)
            .put("status_bounds", statusBounds.get().toString())
    }

    private fun assertMinimumTouchTargets(activity: Activity): JSONArray {
        val minimumPx = (48f * activity.resources.displayMetrics.density).roundToInt()
        val targets = listOf(
            R.id.floor_control_up,
            R.id.floor_control_down,
            R.id.prototype_search_input,
            R.id.prototype_search_button,
            R.id.prototype_history_back,
            R.id.prototype_surface_selector,
            R.id.prototype_key_toggle,
            R.id.prototype_overview_toggle,
            R.id.prototype_key_banks,
            R.id.prototype_key_transport,
            R.id.prototype_key_dungeons,
            R.id.prototype_key_places,
            R.id.prototype_reference_stop,
            R.id.prototype_toggle_labels,
            R.id.prototype_toggle_pois,
            R.id.prototype_toggle_links
        )
        return JSONArray().apply {
            targets.mapNotNull(activity::findViewById).filter { it.isShown }.forEach { view ->
                val name = activity.resources.getResourceEntryName(view.id)
                assertTrue("$name is narrower than 48dp: ${view.width}px < ${minimumPx}px", view.width >= minimumPx - 1)
                assertTrue("$name is shorter than 48dp: ${view.height}px < ${minimumPx}px", view.height >= minimumPx - 1)
                put(
                    JSONObject()
                        .put("id", name)
                        .put("width_px", view.width)
                        .put("height_px", view.height)
                        .put("minimum_px", minimumPx)
                )
            }
        }
    }

    private fun viewBoundsOnScreen(view: View): Rect {
        val location = IntArray(2).also(view::getLocationOnScreen)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun accessibilityNodes(): List<AccessibilityNodeInfo> {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val root = requireNotNull(automation.rootInActiveWindow) { "No active accessibility window" }
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            nodes += node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(::visit)
            }
        }
        visit(root)
        return nodes
    }

    private fun textFits(view: TextView): Boolean {
        val layout = view.layout ?: return false
        if (view.text.isNotEmpty() && (layout.lineCount == 0 || layout.getLineEnd(layout.lineCount - 1) < view.text.length)) {
            return false
        }
        if ((0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }) return false
        val availableHeight = view.height - view.compoundPaddingTop - view.compoundPaddingBottom
        return layout.height <= availableHeight + 2
    }

    private fun assertViewsInsideRoot(activity: Activity) {
        val root = activity.findViewById<View>(android.R.id.content)
        val rootLocation = IntArray(2).also(root::getLocationOnScreen)
        val rootRect = Rect(rootLocation[0], rootLocation[1], rootLocation[0] + root.width, rootLocation[1] + root.height)
        val ids = listOf(
            R.id.prototype_product_controls,
            R.id.prototype_key_panel,
            R.id.prototype_overview,
            R.id.prototype_debug_controls
        )
        for (id in ids) {
            val view = activity.findViewById<View>(id) ?: continue
            if (view.visibility != View.VISIBLE) continue
            val location = IntArray(2).also(view::getLocationOnScreen)
            val rect = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
            assertTrue("View ${activity.resources.getResourceEntryName(id)} exceeds root: $rect vs $rootRect", rootRect.contains(rect))
        }
    }

    private fun percentile(values: List<Double>, percentile: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val index = ((values.size - 1) * percentile).roundToInt().coerceIn(0, values.lastIndex)
        return values[index]
    }

    private fun assertCameraEqual(left: Triple<Double, Double, Double>, right: Triple<Double, Double, Double>, tolerance: Double) {
        assertTrue(abs(left.first - right.first) <= tolerance)
        assertTrue(abs(left.second - right.second) <= tolerance)
        assertTrue(abs(left.third - right.third) <= tolerance)
    }

    private fun cameraDistance(left: Triple<Double, Double, Double>, right: Triple<Double, Double, Double>): Double {
        return abs(left.first - right.first) + abs(left.second - right.second) + abs(left.third - right.third)
    }

    private fun osrsMapPrototypeDiagnostics.isNear(
        target: org.maplibre.android.geometry.LatLng,
        tolerance: Double
    ): Boolean {
        val lat = cameraLatitude ?: return false
        val lon = cameraLongitude ?: return false
        return abs(lat - target.latitude) <= tolerance && abs(lon - target.longitude) <= tolerance
    }

    private fun osrsMapPrototypeDiagnostics.cameraTriple(): Triple<Double, Double, Double> {
        return Triple(requireNotNull(cameraLatitude), requireNotNull(cameraLongitude), cameraZoom)
    }

    private fun osrsMapPrototypeDiagnostics.cameraJson(): JSONObject {
        return JSONObject()
            .put("zoom", cameraZoom)
            .put("latitude", cameraLatitude)
            .put("longitude", cameraLongitude)
            .put("bearing", cameraBearing)
            .put("tilt", cameraTilt)
    }

    private fun osrsMapPrototypeDiagnostics.toJson(): JSONObject {
        fun mapToJson(map: Map<String, *>): JSONObject = JSONObject().apply {
            map.forEach { (key, value) ->
                put(key, if (value is List<*>) JSONArray(value) else value)
            }
        }
        return JSONObject()
            .put("camera", cameraJson())
            .put("rendered", mapToJson(renderedFeatureIdsByKind))
            .put("source", mapToJson(sourceFeatureIdsByKind))
            .put(
                "feature_screen_points",
                JSONObject().apply {
                    featureScreenPoints.forEach { (id, point) -> put(id, point.toJson()) }
                }
            )
            .put(
                "rendered_feature_bounds",
                JSONObject().apply {
                    renderedFeatureBounds.forEach { (id, bounds) -> put(id, bounds.toJson()) }
                }
            )
            .put(
                "virtual_target_bounds",
                JSONObject().apply {
                    virtualTargetBounds.forEach { (id, bounds) -> put(id, bounds.toJson()) }
                }
            )
            .put("map_content_bounds", mapContentBounds?.toJson())
            .put("viewport_padding_top_px", viewportPaddingTopPx)
            .put("viewport_padding_bottom_px", viewportPaddingBottomPx)
            .put("semantic_layers_present", mapToJson(semanticLayersPresent))
            .put("semantic_metrics_px", mapToJson(semanticMetricsPx))
            .put("highlighted_categories", JSONArray(highlightedCategories))
            .put("active_surface_id", activeSurfaceId)
            .put("overview_visible", overviewVisible)
            .put("reference_stop_percent", referenceStopPercent)
            .put("feature_actions", mapToJson(featureActionMetadata))
            .put("last_control_duration_ms", lastControlDurationMs)
            .put("last_hit_feature_id", lastHitFeatureId)
            .put("last_action_description", lastActionDescription)
            .put("search_query", searchQuery)
            .put("status_text", statusText)
            .put("status_content_description", statusContentDescription)
            .put("accessibility_host_description", accessibilityHostDescription)
            .put("accessibility_visible_feature_ids", JSONArray(accessibilityVisibleFeatureIds))
            .put("history_depth", historyDepth)
            .put("current_navigation_result_id", currentNavigationResultId)
            .put("elapsed_realtime_ms", elapsedRealtimeMs)
    }

    private fun osrsMapPrototypeScreenPoint.toJson(): JSONObject = JSONObject().put("x", x).put("y", y)

    private fun osrsMapPrototypeScreenBounds.toJson(): JSONObject {
        return JSONObject()
            .put("left", left)
            .put("top", top)
            .put("right", right)
            .put("bottom", bottom)
    }

    private fun osrsMapPrototypeScreenBounds.toRect(): Rect {
        return Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
    }

    private fun osrsMapPrototypeScreenBounds.width(): Float = right - left

    private fun osrsMapPrototypeScreenBounds.height(): Float = bottom - top

    private fun osrsMapPrototypeScreenBounds.centerX(): Float = (left + right) / 2f

    private fun osrsMapPrototypeScreenBounds.centerY(): Float = (top + bottom) / 2f

    private fun osrsMapPrototypeScreenBounds.contains(x: Float, y: Float): Boolean {
        return x in left..right && y in top..bottom
    }

    private fun osrsMapPrototypeScreenBounds.contains(other: osrsMapPrototypeScreenBounds): Boolean {
        return left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
    }

    private fun File.resetDirectory(): File {
        mkdirs()
        listFiles()?.forEach { file ->
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
        return this
    }

    private data class SettledFrameMetricSample(
        val totalMs: Double,
        val deadlineMs: Double,
        val inputHandlingMs: Double,
        val animationMs: Double,
        val layoutMeasureMs: Double,
        val drawMs: Double,
        val syncMs: Double,
        val commandIssueMs: Double,
        val swapBuffersMs: Double
    ) {
        val uiThreadWorkMs: Double
            get() = inputHandlingMs + animationMs + layoutMeasureMs + drawMs + syncMs

        val rendererSubmissionMs: Double
            get() = commandIssueMs + swapBuffersMs

        val measuredPhaseCompositeMs: Double
            get() = uiThreadWorkMs + rendererSubmissionMs
    }

    private data class FragmentHandle(
        val activity: Activity,
        val fragment: StandardNavigationMapFragment
    )
}
