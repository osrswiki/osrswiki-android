package com.omiyawaki.osrswiki.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class osrsReleaseManifestContractTest {

    @Test
    fun standardNavigationTestActivityIsAbsentFromMainAndReleaseManifests() {
        val mainManifest = File("src/main/AndroidManifest.xml").readText()
        val releaseManifest = File("src/release/AndroidManifest.xml")

        assertFalse(
            "StandardNavigationTestActivity must not ship in the main/release manifest",
            mainManifest.contains("StandardNavigationTestActivity")
        )
        if (releaseManifest.isFile) {
            assertFalse(
                releaseManifest.readText().contains("StandardNavigationTestActivity")
            )
        }
        assertFalse(
            File("src/main/java/com/omiyawaki/osrswiki/ui/map/StandardNavigationTestActivity.kt").exists()
        )
        assertFalse(
            File("src/main/res/layout/activity_standard_navigation_test.xml").exists()
        )
        assertFalse(
            File("src/main/res/menu/standard_navigation_test.xml").exists()
        )
    }

    @Test
    fun standardNavigationTestActivityRemainsAvailableToDebugBuilds() {
        val debugManifest = File("src/debug/AndroidManifest.xml").readText()
        assertTrue(debugManifest.contains("StandardNavigationTestActivity"))
        assertTrue(
            File("src/debug/java/com/omiyawaki/osrswiki/ui/map/StandardNavigationTestActivity.kt").isFile
        )
        assertTrue(
            File("src/debug/res/layout/activity_standard_navigation_test.xml").isFile
        )
        assertTrue(
            File("src/debug/res/menu/standard_navigation_test.xml").isFile
        )
    }
}
