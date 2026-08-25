package com.omiyawaki.osrswiki.undergroundmaps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class osrsStandalonePackagingContractTest {
    @Test
    fun `module is an integrated library with one private activity and no launcher`() {
        val project = osrsModuleDirectory()
        val gradle = File(project, "build.gradle.kts").readText()
        val manifest = File(project, "src/main/AndroidManifest.xml").readText()
        val strings = File(project, "src/main/res/values/strings.xml").readText()

        assertTrue(gradle.contains("alias(libs.plugins.android.library)"))
        assertFalse(gradle.contains("applicationId ="))
        assertTrue(strings.contains("name=\"osrs_underground_app_name\""))
        assertEquals(0, Regex("android.intent.category.LAUNCHER").findAll(manifest).count())
        assertEquals(1, Regex("<activity(?:\\s|>)").findAll(manifest).count())
        assertTrue(manifest.contains(".osrsUndergroundMapsActivity"))
        assertTrue(manifest.contains("android:exported=\"false\""))
        assertTrue(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue(manifest.contains("android.permission.INTERNET\"\n        tools:node=\"remove\""))
        assertFalse(gradle.contains("project(\":app\")"))
        assertFalse(manifest.contains("com.omiyawaki.osrswiki.MainActivity"))
    }

    @Test
    fun `library stages exact reviewed assets and exposes sanitized MapLibre to its host`() {
        val gradle = File(osrsModuleDirectory(), "build.gradle.kts").readText()

        assertTrue(gradle.contains("sanitize_osrs_maplibre_aar.py"))
        assertTrue(gradle.contains("7b86efb12b6581d1e73128d55036a4a4c8f4b756c7272b7cde774cbdb906c2f7"))
        assertTrue(gradle.contains("expectedReplacementCount", ignoreCase = true))
        assertTrue(gradle.contains("expectedConstrainPatchCount", ignoreCase = true))
        assertTrue(gradle.contains("--expected-constrain-patches"))
        assertTrue(gradle.contains("api(osrsSanitizedMapLibreFiles)"))
        assertTrue(gradle.contains("prepareUndergroundRealmAssets"))
        assertTrue(gradle.contains("osrsExpectedUndergroundManifestSha256"))
        assertTrue(gradle.contains("OSRS_EXPECTED_UNDERGROUND_MANIFEST_SHA256"))
        assertTrue(gradle.contains("Canonical realm manifest SHA-256 mismatch"))
        assertTrue(gradle.contains("android.sourceSets.getByName(\"main\").assets.srcDir"))
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
        assertTrue(gradle.contains("src/fixtureAssets"))
        assertTrue(gradle.contains("rootProject.file(resolvedRealmAssets)"))
        assertFalse(gradle.contains("from(file(resolvedRealmAssets))"))
        assertFalse(gradle.contains("binary-assets/underground-realms"))
        assertFalse(gradle.contains("osrswiki-local-artifacts/cache"))
        assertFalse(productionSources.contains("Last Man Standing Desert Island"))
        assertFalse(productionSources.contains("Braindeath Island"))
        assertFalse(productionSources.contains("contains(\"underground\""))
        assertTrue(productionSources.contains("osrsDefaultZoomForAsset(asset)"))
        assertFalse(productionSources.contains("CameraUpdateFactory.newLatLngBounds"))
        assertFalse(
            Regex(
                """mapLibreMap\.easeCamera\(\s*CameraUpdateFactory\.newLatLngBounds"""
            ).containsMatchIn(productionSources)
        )
    }

    @Test
    fun `generated asset sync does not inherit immutable source permissions`() {
        val gradle = File(osrsModuleDirectory(), "build.gradle.kts").readText()

        assertTrue(gradle.contains("dirPermissions"))
        assertTrue(gradle.contains("unix(\"0755\")"))
        assertTrue(gradle.contains("filePermissions"))
        assertTrue(gradle.contains("unix(\"0644\")"))
    }

    @Test
    fun `vertical floor controls keep explicit contrast and lifecycle cancellation is not an app error`() {
        val project = osrsModuleDirectory()
        val activity = File(
            project,
            "src/main/java/com/omiyawaki/osrswiki/undergroundmaps/osrsUndergroundMapsActivity.kt"
        ).readText()
        val upIcon = File(project, "src/main/res/drawable/osrs_ic_arrow_up.xml").readText()
        val downIcon = File(project, "src/main/res/drawable/osrs_ic_arrow_down.xml").readText()

        assertTrue(activity.contains("orientation = LinearLayout.VERTICAL"))
        assertTrue(activity.contains("ContextCompat.getColor(context, R.color.osrs_map_control_ink)"))
        assertTrue(
            activity.contains(
                "setCardBackgroundColor(ContextCompat.getColor(context, R.color.osrs_map_control_surface))"
            )
        )
        assertTrue(
            activity.contains(
                "strokeColor = ContextCompat.getColor(context, R.color.osrs_underground_parchment_dark)"
            )
        )
        assertTrue(upIcon.contains("android:fillColor=\"@color/osrs_map_control_ink\""))
        assertTrue(downIcon.contains("android:fillColor=\"@color/osrs_map_control_ink\""))
        assertTrue(activity.contains("catch (cancellation: CancellationException)"))
        assertFalse(activity.contains("runCatching { repository.loadCatalog() }"))
    }

    @Test
    fun `realm selector globe is an outline coordinate icon`() {
        val icon = File(
            osrsModuleDirectory(),
            "src/main/res/drawable/osrs_ic_globe.xml"
        ).readText()

        assertTrue(icon.contains("android:strokeColor=\"@color/osrs_map_control_ink\""))
        assertTrue(icon.contains("android:fillColor=\"@android:color/transparent\""))
        assertTrue(icon.contains("C8.7,5.1"))
        assertTrue(icon.contains("M3.2,8.3"))
        assertFalse(icon.contains("android:fillColor=\"@color/osrs_map_control_ink\""))
    }

    @Test
    fun `camera target combines finite non surface bounds with the shared center envelope`() {
        val activity = File(
            osrsModuleDirectory(),
            "src/main/java/com/omiyawaki/osrswiki/undergroundmaps/osrsUndergroundMapsActivity.kt"
        ).readText()

        assertTrue(
            activity.contains(
                "setLatLngBoundsForCameraTarget(null)"
            )
        )
        assertTrue(activity.contains("osrsCameraCenterEnvelope.fromVisibleAssets(visibleAssets)"))
        assertTrue(activity.contains("osrsFiniteRealmMinimumZoom("))
        assertFalse(activity.contains("if (realm.isSurface) return baseMinimum"))
        assertTrue(activity.contains("horizontalWrapEnabled = false"))
        assertTrue(activity.contains("clampCameraForActiveEnvelope"))
        assertFalse(activity.contains("bounds.takeIf { realm.isSurface }"))
        assertTrue(activity.contains("zoom = osrsDefaultZoomForAsset(asset).coerceIn("))
        assertFalse(activity.contains("CameraUpdateFactory.newLatLngBounds"))
    }

    @Test
    fun `dormant map links ui retains its internal presentation resources for future reuse`() {
        val project = osrsModuleDirectory()
        val activity = File(
            project,
            "src/main/java/com/omiyawaki/osrswiki/undergroundmaps/osrsUndergroundMapsActivity.kt"
        ).readText()
        val dialog = File(
            project,
            "src/main/java/com/omiyawaki/osrswiki/undergroundmaps/ui/osrsRealmLinksDialog.kt"
        ).readText()
        val strings = File(project, "src/main/res/values/strings.xml").readText()

        assertTrue(activity.contains("OSRS_REALM_LINKS_UI_ENABLED = false"))
        assertTrue(activity.contains("R.drawable.osrs_ic_search"))
        assertFalse(activity.contains("R.drawable.osrs_ic_links"))
        assertTrue(strings.contains("Search map links for %1\$s."))
        listOf(
            "osrs_map_control_surface",
            "osrs_map_control_surface_pressed",
            "osrs_parchment",
            "osrs_underground_parchment_dark",
            "osrs_map_control_divider"
        ).forEach { paletteName ->
            val resources = File(project, "src/main/res").walkTopDown()
                .filter(File::isFile)
                .joinToString("\n") { it.readText() }
            assertTrue("Missing explicit palette resource $paletteName", resources.contains(paletteName))
        }
        assertTrue(dialog.contains("R.drawable.osrs_links_sheet_background"))
        assertTrue(dialog.contains("R.drawable.osrs_links_search_background"))
        assertTrue(dialog.contains("R.drawable.osrs_link_row_background"))
        assertTrue(dialog.contains("R.drawable.osrs_link_divider"))
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
        assertTrue(activity.contains("osrsRelativeLinkZoomForAssets("))
        assertTrue(activity.contains("sourceCamera.bearing"))
        assertTrue(activity.contains("sourceCamera.tilt"))
        assertFalse(activity.contains("coerceAtLeast(1.0)"))
        assertTrue(activity.contains("osrsRealmCameraEnvelope.minZoom(asset)"))
        assertTrue(activity.contains("osrsRealmCameraEnvelope.maxZoom(asset)"))
        assertTrue(endpointMapper.contains("OSRS_MAX_OVERZOOM_LEVELS = 8.0"))
        assertTrue(realmState.contains("osrsRealmCameraEnvelope.contains(asset, zoom)"))
        assertTrue(realmState.contains("cameraGeometryFingerprint()"))
    }

    @Test
    fun `standalone surface camera is stamped from the shared Lumbridge default`() {
        val project = osrsModuleDirectory()
        val repository = requireNotNull(project.parentFile?.parentFile?.parentFile)
        val sharedDefault = File(repository, "shared/map-default-view.json").readText()
        val standaloneDefault = File(
            project,
            "src/main/java/com/omiyawaki/osrswiki/undergroundmaps/model/" +
                "osrsUndergroundMapDefaultView.kt"
        ).readText()

        val expectedValues = listOf(
            "\"x\": 3222.0" to "GAME_X = 3222.0",
            "\"y\": 3218.0" to "GAME_Y = 3218.0",
            "\"plane\": 0" to "PLANE = 0",
            "\"latitude\": \"-25.44327461230575\"" to
                "LATITUDE = -25.44327461230575",
            "\"longitude\": \"-130.2978515625\"" to
                "LONGITUDE = -130.2978515625",
            "\"zoom\": \"7.3414426741929\"" to "ZOOM = 7.3414426741929",
            "\"canvasSize\": 65536.0" to "CANVAS_SIZE = 65536.0"
        )

        expectedValues.forEach { (sharedNeedle, standaloneNeedle) ->
            assertTrue(sharedDefault.contains(sharedNeedle))
            assertTrue(standaloneDefault.contains(standaloneNeedle))
        }
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
