package com.omiyawaki.osrswiki.undergroundmaps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class osrsStandalonePackagingContractTest {
    @Test
    fun `module is a separate application with exact identity and one launcher`() {
        val project = osrsModuleDirectory()
        val gradle = File(project, "build.gradle.kts").readText()
        val manifest = File(project, "src/main/AndroidManifest.xml").readText()
        val strings = File(project, "src/main/res/values/strings.xml").readText()

        assertTrue(gradle.contains("applicationId = \"com.omiyawaki.osrswiki.undergroundmaps\""))
        assertTrue(gradle.contains("versionCode = 6"))
        assertTrue(gradle.contains("versionName = \"0.6.0-candidate-006\""))
        assertTrue(strings.contains(">OSRS Underground Maps</string>"))
        assertEquals(1, Regex("android.intent.category.LAUNCHER").findAll(manifest).count())
        assertEquals(1, Regex("<activity(?:\\s|>)").findAll(manifest).count())
        assertTrue(manifest.contains(".osrsUndergroundMapsActivity"))
        assertTrue(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue(manifest.contains("android.permission.INTERNET\"\n        tools:node=\"remove\""))
        assertFalse(gradle.contains("project(\":app\")"))
        assertFalse(manifest.contains("com.omiyawaki.osrswiki.MainActivity"))
    }

    @Test
    fun `release assembly scans apk release and retained evidence through one gate`() {
        val gradle = File(osrsModuleDirectory(), "build.gradle.kts").readText()

        assertTrue(gradle.contains("sanitize_osrs_maplibre_aar.py"))
        assertTrue(gradle.contains("7b86efb12b6581d1e73128d55036a4a4c8f4b756c7272b7cde774cbdb906c2f7"))
        assertTrue(gradle.contains("expectedReplacementCount", ignoreCase = true))
        assertTrue(gradle.contains("osrsValidateReleaseApkPathHygiene"))
        assertTrue(gradle.contains("osrs_public_path_hygiene.py"))
        assertTrue(gradle.contains("--archive"))
        assertTrue(gradle.contains("--public-tree"))
        assertTrue(gradle.contains("--artifact-root"))
        assertTrue(gradle.contains("osrsUndergroundEvidenceDir"))
        assertTrue(gradle.contains("osrsRequireUndergroundPublicationClosure"))
        assertTrue(gradle.contains("tasks.matching { it.name == \"assembleRelease\" }.configureEach"))
        assertTrue(gradle.contains("finalizedBy(osrsValidateReleaseApkPathHygiene)"))
    }

    @Test
    fun `generated asset hook is manifest driven and has no hardcoded realm names`() {
        val project = osrsModuleDirectory()
        val gradle = File(project, "build.gradle.kts").readText()
        val productionSources = File(project, "src/main/java").walkTopDown()
            .filter(File::isFile)
            .joinToString("\n") { it.readText() }

        assertTrue(gradle.contains("osrsUndergroundAssetsDir"))
        assertTrue(gradle.contains("OSRS_UNDERGROUND_ASSETS_DIR"))
        assertFalse(productionSources.contains("Last Man Standing Desert Island"))
        assertFalse(productionSources.contains("Braindeath Island"))
        assertFalse(productionSources.contains("contains(\"underground\""))
        assertTrue(productionSources.contains("mapLibreMap.moveCamera(CameraUpdateFactory.newLatLngBounds"))
        assertFalse(productionSources.contains("mapLibreMap.easeCamera(CameraUpdateFactory.newLatLngBounds"))
    }

    @Test
    fun `floor controls keep explicit contrast and lifecycle cancellation is not an app error`() {
        val activity = File(
            osrsModuleDirectory(),
            "src/main/java/com/omiyawaki/osrswiki/undergroundmaps/osrsUndergroundMapsActivity.kt"
        ).readText()

        assertTrue(activity.contains("setTextColor(floorTextColors)"))
        assertTrue(activity.contains("backgroundTintList = floorBackgroundColors"))
        assertTrue(activity.contains("strokeColor = floorStrokeColor"))
        assertTrue(activity.contains("catch (cancellation: CancellationException)"))
        assertFalse(activity.contains("runCatching { repository.loadCatalog() }"))
    }

    @Test
    fun `camera target bounds isolate surface without clamping modular realm endpoints`() {
        val activity = File(
            osrsModuleDirectory(),
            "src/main/java/com/omiyawaki/osrswiki/undergroundmaps/osrsUndergroundMapsActivity.kt"
        ).readText()

        assertTrue(activity.contains("val cameraTargetBounds = bounds.takeIf { realm.isSurface }"))
        assertTrue(activity.contains("setLatLngBoundsForCameraTarget(cameraTargetBounds)"))
        assertTrue(activity.contains("moveCamera(CameraUpdateFactory.newLatLngBounds(bounds"))
    }

    @Test
    fun `camera persistence and display zoom use the shared installed-source contract`() {
        val project = osrsModuleDirectory()
        val activity = File(
            project,
            "src/main/java/com/omiyawaki/osrswiki/undergroundmaps/osrsUndergroundMapsActivity.kt"
        ).readText()
        val endpointMapper = File(
            project,
            "src/main/java/com/omiyawaki/osrswiki/undergroundmaps/model/osrsRealmEndpointMapper.kt"
        ).readText()
        val realmState = File(
            project,
            "src/main/java/com/omiyawaki/osrswiki/undergroundmaps/state/osrsRealmState.kt"
        ).readText()

        assertTrue(activity.contains("cameraPersistenceOwnership.authorization"))
        assertTrue(activity.contains("osrsRealmAction.InstalledCameraChanged"))
        assertTrue(activity.contains("installedRequestId == identity.requestId"))
        assertTrue(activity.contains("installedStyleGeneration == identity.styleGeneration"))
        assertTrue(activity.contains("windowManager.maximumWindowMetrics.bounds"))
        assertTrue(activity.contains("windowManager.defaultDisplay.getRealMetrics(metrics)"))
        assertTrue(activity.contains("osrsMaximumDisplayExtentDp("))
        assertFalse(activity.contains("coerceAtLeast(1.0)"))
        assertTrue(activity.contains("osrsRealmCameraEnvelope.minZoom(asset)"))
        assertTrue(activity.contains("osrsRealmCameraEnvelope.maxZoom(asset)"))
        assertTrue(endpointMapper.contains("OSRS_MAX_OVERZOOM_LEVELS = 8.0"))
        assertTrue(realmState.contains("osrsRealmCameraEnvelope.contains(asset, zoom)"))
    }

    private fun osrsModuleDirectory(): File {
        val workingDirectory = File(System.getProperty("user.dir") ?: error("Missing user.dir"))
        return if (workingDirectory.name == "undergroundmaps") {
            workingDirectory
        } else {
            File(workingDirectory, "undergroundmaps")
        }
    }
}
