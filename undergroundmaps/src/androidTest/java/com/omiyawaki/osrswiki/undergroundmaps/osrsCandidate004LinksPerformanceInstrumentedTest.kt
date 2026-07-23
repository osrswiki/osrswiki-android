package com.omiyawaki.osrswiki.undergroundmaps

import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ceil

/** Opt-in, fail-closed performance gate for the exact Candidate 006 release. */
@RunWith(AndroidJUnit4::class)
class osrsCandidate004LinksPerformanceInstrumentedTest {
    @Test
    fun oneColdAndTwentyRepeatedSurfaceLinkOpensMeetTheStrictBudget() {
        val device = requireExactCandidateRuntime()

        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            awaitDiagnostics(scenario) { it.sourceId != null && it.switchCompletedAtNanos != null }
            scenario.onActivity { activity ->
                assertTrue(activity.selectRealmForTesting(OSRS_SURFACE_REALM_ID))
                assertTrue(activity.selectPlaneForTesting(0))
            }
            val initial = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_SURFACE_REALM_ID &&
                    it.activePlane == 0 &&
                    it.sourceId != null &&
                    it.switchCompletedAtNanos != null
            }
            assertEquals("006", initial.candidate)
            assertEquals(OSRS_SURFACE_REALM_ID, initial.activeRealmId)
            assertEquals(0, initial.activePlane)
            assertEquals(OSRS_EXPECTED_REALM_COUNT, initial.manifestRealmCount)
            assertEquals(OSRS_EXPECTED_REALM_COUNT, initial.selectorRealmCount)
            assertEquals(OSRS_AVAILABLE_LINK_COUNT, initial.availableLinkCount)
            assertEquals(OSRS_UNAVAILABLE_LINK_COUNT, initial.unavailableLinkCount)

            val cold = openAndAssert(scenario, device, expectedOrdinal = 1)
            assertTrue(cold.phase.cold)
            assertFalse(cold.phase.catalogCacheHit)
            assertFalse(cold.phase.dialogReused)
            assertEquals("direct_before_attach", cold.phase.initialUpdateStrategy)
            dismissAndAssert(scenario, device)

            val repeated = (2..21).map { ordinal ->
                openAndAssert(scenario, device, expectedOrdinal = ordinal).also {
                    assertFalse(it.phase.cold)
                    assertTrue(it.phase.catalogCacheHit)
                    assertTrue(it.phase.dialogReused)
                    assertEquals("reused", it.phase.initialUpdateStrategy)
                    assertEquals(0L, it.phase.viewConstructionNanos)
                    assertEquals(0L, it.phase.initialFilterNanos)
                    assertEquals(0L, it.phase.initialRowConversionNanos)
                    assertEquals(0L, it.phase.initialAdapterSubmissionNanos)
                    dismissAndAssert(scenario, device)
                }
            }
            val repeatedP95 = nearestRankP95(repeated.map { it.phase.appNanos })
            val allP95 = nearestRankP95(listOf(cold.phase.appNanos) + repeated.map { it.phase.appNanos })
            val diagnostics = awaitDiagnostics(scenario) {
                it.repeatedLinkDialogP95Nanos != null && it.simpleControlP95Nanos != null
            }
            assertEquals(cold.phase.appNanos, diagnostics.coldLinkDialogOpenNanos)
            assertEquals(repeatedP95, diagnostics.repeatedLinkDialogP95Nanos)
            assertTrue(
                "Repeated simple-control p95 exceeded 50 ms: ${diagnostics.simpleControlP95Nanos}",
                requireNotNull(diagnostics.simpleControlP95Nanos) < OSRS_SIMPLE_CONTROL_BUDGET_NANOS
            )
            Log.i(
                OSRS_GATE_LOG_TAG,
                "candidate=005 coldAppNanos=${cold.phase.appNanos} " +
                    "coldClickReturnNanos=${cold.clickReturnNanos} " +
                    "coldPresentedNanos=${cold.presentedNanos} " +
                    "repeatedCount=${repeated.size} " +
                    "repeatedNanos=${repeated.joinToString(",") { it.phase.appNanos.toString() }} " +
                    "repeatedClickReturnNanos=${repeated.joinToString(",") { it.clickReturnNanos.toString() }} " +
                    "repeatedPresentedNanos=${repeated.joinToString(",") { it.presentedNanos.toString() }} " +
                    "repeatedP95Nanos=$repeatedP95 allIncludingColdP95Nanos=$allP95 " +
                    "productionSimpleControlP95Nanos=${diagnostics.simpleControlP95Nanos}"
            )
            assertTrue(
                "Repeated Surface Links dialog p95 must be strictly below 50 ms; was $repeatedP95 ns",
                repeatedP95 < OSRS_SIMPLE_CONTROL_BUDGET_NANOS
            )
        }
    }

    @Test
    fun reusedDialogPreservesRenderedSearchScrollUnavailableAndNavigationContracts() {
        val device = requireExactCandidateRuntime()
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            awaitDiagnostics(scenario) { it.sourceId != null }
            scenario.onActivity { activity ->
                assertTrue(activity.selectRealmForTesting(OSRS_SURFACE_REALM_ID))
                assertTrue(activity.selectPlaneForTesting(0))
            }
            awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_SURFACE_REALM_ID &&
                    it.activePlane == 0 &&
                    it.switchCompletedAtNanos != null
            }

            openAndAssert(scenario, device, expectedOrdinal = 1)
            val list = requireNotNull(
                device.wait(
                    Until.findObject(By.res(OSRS_PACKAGE_ID, "osrs_links_list")),
                    OSRS_UI_TIMEOUT_MILLIS
                )
            )
            assertTrue(device.hasObject(By.descContains("intermap-0076")))
            assertTrue(list.scroll(Direction.DOWN, 0.8f))
            assertTrue(
                device.wait(Until.gone(By.descContains("intermap-0076")), OSRS_UI_TIMEOUT_MILLIS)
            )

            val unavailableSearch = requireNotNull(
                device.findObject(By.res(OSRS_PACKAGE_ID, "osrs_links_search"))
            )
            unavailableSearch.text = "intermap-0001"
            val unavailable = requireNotNull(
                device.wait(
                    Until.findObject(By.descContains("Unavailable link intermap-0001")),
                    OSRS_UI_TIMEOUT_MILLIS
                )
            )
            assertFalse(unavailable.isClickable)
            assertTrue(unavailable.contentDescription.contains("Not selectable"))
            dismissAndAssert(scenario, device)

            openAndAssert(scenario, device, expectedOrdinal = 2)
            val actionableSearch = requireNotNull(
                device.findObject(By.res(OSRS_PACKAGE_ID, "osrs_links_search"))
            )
            actionableSearch.text = OSRS_ACTIONABLE_LINK_ID
            val actionable = requireNotNull(
                device.wait(
                    Until.findObject(By.descContains("Open authoritative link $OSRS_ACTIONABLE_LINK_ID")),
                    OSRS_UI_TIMEOUT_MILLIS
                )
            )
            assertTrue(actionable.isClickable)
            actionable.click()
            val morytania = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_MORYTANIA_REALM_ID &&
                    it.selectedLinkId == OSRS_ACTIONABLE_LINK_ID &&
                    it.switchCompletedAtNanos != null &&
                    it.stagedAssetSha256 == OSRS_MORYTANIA_PLANE_ZERO_SHA256 &&
                    it.linkAppliedMarker?.startsWith("camera-applied-") == true &&
                    it.renderMarker.startsWith("map-idle@") &&
                    cameraMatches(it, 84.23194746223983, -170.5078125, 6.0)
            }
            assertEquals(3405, morytania.linkTargetGameX)
            assertEquals(9907, morytania.linkTargetGameY)
            assertEquals(84.23194746223983, morytania.cameraLatitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(-170.5078125, morytania.cameraLongitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(6.0, morytania.cameraZoom!!, OSRS_CAMERA_EPSILON)

            openVisibleDialog(device)
            val returnSearch = requireNotNull(
                device.findObject(By.res(OSRS_PACKAGE_ID, "osrs_links_search"))
            )
            returnSearch.text = OSRS_ACTIONABLE_LINK_ID
            val returnLink = requireNotNull(
                device.wait(
                    Until.findObject(By.descContains("Open authoritative link $OSRS_ACTIONABLE_LINK_ID")),
                    OSRS_UI_TIMEOUT_MILLIS
                )
            )
            assertTrue(returnLink.isClickable)
            returnLink.click()
            val returnedSurface = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_SURFACE_REALM_ID &&
                    it.selectedLinkId == OSRS_ACTIONABLE_LINK_ID &&
                    it.switchCompletedAtNanos != null &&
                    it.stagedAssetSha256 == OSRS_SURFACE_PLANE_ZERO_SHA256 &&
                    it.linkAppliedMarker?.startsWith("camera-applied-") == true &&
                    it.renderMarker.startsWith("map-idle@") &&
                    cameraMatches(it, 75.19702129578613, 34.9365234375, 7.0)
            }
            assertEquals(3405, returnedSurface.linkTargetGameX)
            assertEquals(3506, returnedSurface.linkTargetGameY)
            assertEquals(75.19702129578613, returnedSurface.cameraLatitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(34.9365234375, returnedSurface.cameraLongitude!!, OSRS_CAMERA_EPSILON)
            assertEquals(7.0, returnedSurface.cameraZoom!!, OSRS_CAMERA_EPSILON)
        }
    }

    private fun openAndAssert(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        device: UiDevice,
        expectedOrdinal: Int
    ): osrsMeasuredDialogOpen {
        val button = requireNotNull(
            device.wait(
                Until.findObject(By.res(OSRS_PACKAGE_ID, "osrs_realm_links")),
                OSRS_UI_TIMEOUT_MILLIS
            )
        ) { "Visible Surface Links button is unavailable" }
        val clickStarted = SystemClock.elapsedRealtimeNanos()
        button.click()
        val clickReturnNanos = SystemClock.elapsedRealtimeNanos() - clickStarted
        assertTrue(
            device.wait(Until.hasObject(By.text(OSRS_DIALOG_TITLE)), OSRS_UI_TIMEOUT_MILLIS)
        )
        val presentedNanos = awaitVisiblePresentation(scenario, device, clickStarted)
        var phase: osrsLinkDialogOpenPhase? = null
        scenario.onActivity { activity ->
            phase = activity.lastLinkDialogPhaseForTesting()
            val dialogState = activity.realmLinksDialogStateForTesting()
            assertNotNull(dialogState)
            assertTrue(dialogState!!.isShowing)
            assertEquals(OSRS_SURFACE_REALM_ID, dialogState!!.realmId)
            assertEquals(OSRS_TOTAL_LINK_COUNT, dialogState!!.displayedRowCount)
            assertEquals("", dialogState!!.query)
        }
        val measured = requireNotNull(phase)
        assertEquals(expectedOrdinal, measured.ordinal)
        assertEquals(OSRS_SURFACE_REALM_ID, measured.realmId)
        assertEquals(OSRS_AVAILABLE_LINK_COUNT, measured.availableCount)
        assertEquals(OSRS_UNAVAILABLE_LINK_COUNT, measured.unavailableCount)
        assertEquals(0L, measured.initialFilterObserverNanos)
        assertTrue(measured.showingAfterReturn)
        assertTrue(measured.unclassifiedNanos >= 0L)
        assertEquals(measured.appNanos, measured.reconciledNanos)
        assertTrue(presentedNanos >= measured.appNanos)
        return osrsMeasuredDialogOpen(measured, clickReturnNanos, presentedNanos)
    }

    private fun openVisibleDialog(device: UiDevice) {
        val button = requireNotNull(
            device.wait(
                Until.findObject(By.res(OSRS_PACKAGE_ID, "osrs_realm_links")),
                OSRS_UI_TIMEOUT_MILLIS
            )
        )
        button.click()
        assertTrue(
            device.wait(Until.hasObject(By.text(OSRS_DIALOG_TITLE)), OSRS_UI_TIMEOUT_MILLIS)
        )
        assertTrue(
            device.wait(
                Until.hasObject(By.res(OSRS_PACKAGE_ID, "osrs_links_list")),
                OSRS_UI_TIMEOUT_MILLIS
            )
        )
    }

    private fun requireExactCandidateRuntime(): UiDevice {
        val arguments = InstrumentationRegistry.getArguments()
        assertEquals(
            "Exact Candidate 006 gate argument is mandatory; skipping is forbidden",
            "true",
            arguments.getString(OSRS_EXACT_GATE_ARGUMENT)
        )
        assertEquals(34, Build.VERSION.SDK_INT)
        val expectedApkSha256 = requireNotNull(arguments.getString(OSRS_EXACT_APK_SHA_ARGUMENT)) {
            "Exact Candidate 006 APK SHA-256 argument is mandatory"
        }
        val expectedApkBytes = requireNotNull(
            arguments.getString(OSRS_EXACT_APK_BYTES_ARGUMENT)?.toLongOrNull()
        ) { "Exact Candidate 006 APK byte-size argument is mandatory" }
        val expectedSignerSha256 = requireNotNull(
            arguments.getString(OSRS_EXACT_APK_SIGNER_SHA_ARGUMENT)
        ) { "Exact Candidate 006 signing-certificate SHA-256 argument is mandatory" }
        assertTrue(expectedApkSha256.matches(Regex("^[0-9a-f]{64}$")))
        assertTrue(expectedSignerSha256.matches(Regex("^[0-9a-f]{64}$")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val packageInfo = targetContext.packageManager.getPackageInfo(
            targetContext.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
        assertEquals(OSRS_PACKAGE_ID, targetContext.packageName)
        assertEquals(6L, packageInfo.longVersionCode)
        assertEquals("0.6.0-candidate-006", packageInfo.versionName)
        val installedBase = File(requireNotNull(packageInfo.applicationInfo).sourceDir)
        assertEquals(expectedApkBytes, installedBase.length())
        assertEquals(expectedApkSha256, sha256(installedBase))
        val signerDigests = requireNotNull(packageInfo.signingInfo)
            .apkContentsSigners
            .map { certificate -> sha256(certificate.toByteArray()) }
            .sorted()
        assertEquals(listOf(expectedSignerSha256), signerDigests)
        return UiDevice.getInstance(instrumentation)
    }

    private fun awaitVisiblePresentation(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        device: UiDevice,
        clickStarted: Long
    ): Long {
        val deadline = clickStarted + OSRS_PRESENTATION_TIMEOUT_NANOS
        var latest: com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinksDialogDebugState? = null
        while (SystemClock.elapsedRealtimeNanos() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            device.waitForIdle()
            scenario.onActivity { activity -> latest = activity.realmLinksDialogStateForTesting() }
            latest?.let { state ->
                if (
                    state.isShowing &&
                    state.decorAttached &&
                    state.decorLaidOut &&
                    state.decorShown &&
                    state.visibleBoundRowCount > 0 &&
                    device.hasObject(By.res(OSRS_PACKAGE_ID, "osrs_links_search")) &&
                    device.hasObject(By.res(OSRS_PACKAGE_ID, "osrs_links_list")) &&
                    device.hasObject(By.textContains("Ancient Cavern"))
                ) {
                    assertEquals(OSRS_LINK_SUMMARY, state.summaryText)
                    assertEquals(OSRS_RESULTS_DESCRIPTION, state.resultsContentDescription)
                    return SystemClock.elapsedRealtimeNanos() - clickStarted
                }
            }
            Thread.sleep(OSRS_PRESENTATION_POLL_MILLIS)
        }
        throw AssertionError("Dialog never produced an attached, laid-out visible row; latest=$latest")
    }

    private fun dismissAndAssert(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        device: UiDevice
    ) {
        device.pressBack()
        if (!device.wait(Until.gone(By.text(OSRS_DIALOG_TITLE)), OSRS_DIALOG_DISMISS_GRACE_MILLIS)) {
            device.pressBack()
        }
        assertTrue(
            device.wait(Until.gone(By.text(OSRS_DIALOG_TITLE)), OSRS_UI_TIMEOUT_MILLIS)
        )
        assertTrue(
            device.wait(
                Until.hasObject(By.res(OSRS_PACKAGE_ID, "osrs_realm_links")),
                OSRS_UI_TIMEOUT_MILLIS
            )
        )
        scenario.onActivity { activity ->
            assertFalse(activity.realmLinksDialogStateForTesting()?.isShowing ?: true)
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
        throw AssertionError("Timed out waiting for exact Candidate 004 diagnostics; latest=$latest")
    }

    private fun nearestRankP95(values: List<Long>): Long {
        val sorted = values.sorted()
        return sorted[(ceil(sorted.size * 0.95).toInt() - 1).coerceIn(sorted.indices)]
    }

    private fun cameraMatches(
        diagnostics: osrsMapDiagnostics,
        latitude: Double,
        longitude: Double,
        zoom: Double
    ): Boolean =
        diagnostics.cameraLatitude?.let { abs(it - latitude) <= OSRS_CAMERA_EPSILON } == true &&
            diagnostics.cameraLongitude?.let { abs(it - longitude) <= OSRS_CAMERA_EPSILON } == true &&
            diagnostics.cameraZoom?.let { abs(it - zoom) <= OSRS_CAMERA_EPSILON } == true

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

    private data class osrsMeasuredDialogOpen(
        val phase: osrsLinkDialogOpenPhase,
        val clickReturnNanos: Long,
        val presentedNanos: Long
    )

    private companion object {
        const val OSRS_EXACT_GATE_ARGUMENT = "osrsExactCandidate004"
        const val OSRS_EXACT_APK_SHA_ARGUMENT = "osrsExactCandidate004Sha256"
        const val OSRS_EXACT_APK_BYTES_ARGUMENT = "osrsExactCandidate004Bytes"
        const val OSRS_EXACT_APK_SIGNER_SHA_ARGUMENT = "osrsExactCandidate004SignerSha256"
        const val OSRS_GATE_LOG_TAG = "osrsCandidate004Gate"
        const val OSRS_PACKAGE_ID = "com.omiyawaki.osrswiki.undergroundmaps"
        const val OSRS_DIALOG_TITLE = "Map links"
        const val OSRS_SURFACE_REALM_ID = "surface-gielinor"
        const val OSRS_MORYTANIA_REALM_ID = "cache-world-map:morytania-underground"
        const val OSRS_ACTIONABLE_LINK_ID = "intermap-0357"
        const val OSRS_MORYTANIA_PLANE_ZERO_SHA256 =
            "969fecc404f2a5e400e469e9e67252537ae46217b7b869c863a04cee62ee2305"
        const val OSRS_SURFACE_PLANE_ZERO_SHA256 =
            "216589de5843c912361b4d6adf0999c445cd12d8194fd64a6baf59e68574aa69"
        const val OSRS_EXPECTED_REALM_COUNT = 1097
        const val OSRS_AVAILABLE_LINK_COUNT = 335
        const val OSRS_UNAVAILABLE_LINK_COUNT = 47
        const val OSRS_TOTAL_LINK_COUNT = 382
        const val OSRS_LINK_SUMMARY =
            "335 authoritative links available. 47 unresolved links remain unavailable."
        const val OSRS_RESULTS_DESCRIPTION = "382 map link records"
        const val OSRS_SIMPLE_CONTROL_BUDGET_NANOS = 50_000_000L
        const val OSRS_TEST_TIMEOUT_NANOS = 30_000_000_000L
        const val OSRS_TEST_POLL_MILLIS = 100L
        const val OSRS_PRESENTATION_TIMEOUT_NANOS = 5_000_000_000L
        const val OSRS_PRESENTATION_POLL_MILLIS = 10L
        const val OSRS_UI_TIMEOUT_MILLIS = 5_000L
        const val OSRS_DIALOG_DISMISS_GRACE_MILLIS = 1_500L
        const val OSRS_CAMERA_EPSILON = 1e-7
    }
}
