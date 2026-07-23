package com.omiyawaki.osrswiki.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class MapPrototypeBoundaryContractTest {
    @Test
    fun mergedMapOnlyManifestContainsOnlyTheTwoOwnedActivities() {
        val manifest = mergedManifest()
        val root = manifest.documentElement
        val application = root.getElementsByTagName("application").item(0) as Element
        val activities = application.getElementsByTagName("activity")
        val names = (0 until activities.length).map { index ->
            (activities.item(index) as Element).androidAttribute("name")
        }.toSet()

        assertEquals("com.omiyawaki.osrswiki.mapprototype", root.getAttribute("package"))
        assertEquals(
            setOf(
                "com.omiyawaki.osrswiki.test.MapPrototypeLauncherActivity",
                "com.omiyawaki.osrswiki.test.SemanticPrototypeStandaloneActivity"
            ),
            names
        )
        val permissions = root.getElementsByTagName("uses-permission")
        val permissionNames = (0 until permissions.length).map { index ->
            (permissions.item(index) as Element).androidAttribute("name")
        }.toSet()
        assertEquals(setOf("android.permission.ACCESS_NETWORK_STATE"), permissionNames)
        assertEquals(0, root.getElementsByTagName("queries").length)
        assertEquals(0, application.getElementsByTagName("provider").length)
        assertEquals(0, application.getElementsByTagName("service").length)
        assertEquals(0, application.getElementsByTagName("receiver").length)
        assertEquals(
            "com.omiyawaki.osrswiki.test.MapPrototypeApplication",
            application.androidAttribute("name")
        )
    }

    @Test
    fun onlyLauncherIsExportedAndStandaloneIsInternal() {
        val application = mergedManifest().documentElement
            .getElementsByTagName("application").item(0) as Element
        val activities = application.getElementsByTagName("activity")
        val exported = (0 until activities.length).associate { index ->
            val activity = activities.item(index) as Element
            activity.androidAttribute("name") to activity.androidAttribute("exported")
        }

        assertEquals("true", exported["com.omiyawaki.osrswiki.test.MapPrototypeLauncherActivity"])
        assertEquals("false", exported["com.omiyawaki.osrswiki.test.SemanticPrototypeStandaloneActivity"])
    }

    @Test
    fun prototypeFixturesAndResourcesDoNotLeakIntoOrdinarySourceSets() {
        val mainMap = File("src/main/java/com/omiyawaki/osrswiki/ui/map")
        val prototypeMap = File("src/mapPrototype/java/com/omiyawaki/osrswiki/ui/map")
        val debugManifest = File("src/debug/AndroidManifest.xml").readText()
        val mainLayout = File("src/main/res/layout/fragment_map.xml").readText()
        val prototypeLayout = File("src/mapPrototype/res/layout/fragment_map.xml").readText()

        listOf(
            "osrsMapPrototypeController.kt",
            "osrsMapPrototypeOverlay.kt",
            "osrsMapPrototypeOverviewView.kt"
        ).forEach { fileName ->
            assertFalse(File(mainMap, fileName).exists())
            assertTrue(File(prototypeMap, fileName).isFile)
        }
        assertFalse(debugManifest.contains("SemanticPrototypeStandaloneActivity"))
        assertFalse(mainLayout.contains("prototype_product_controls"))
        assertTrue(prototypeLayout.contains("prototype_product_controls"))
    }

    @Test
    fun minimalPermissionPrototypeSourcesKeepLintExceptionGateScoped() {
        val prototypeSourceRoot = File("src/mapPrototype")
        val forbiddenPermissionApis = listOf(
            "ConnectivityManager",
            "Vibrator",
            "NotificationManager",
            "ACCESS_NETWORK_STATE",
            "POST_NOTIFICATIONS",
            "VIBRATE"
        )
        val prototypeSources = prototypeSourceRoot
            .walkTopDown()
            .filter { source -> source.isFile && source.extension in setOf("java", "kt") }
            .joinToString("\n") { source -> source.readText() }
        val buildScript = File("build.gradle.kts").readText()

        forbiddenPermissionApis.forEach { forbiddenApi ->
            assertFalse("Prototype source uses $forbiddenApi", prototypeSources.contains(forbiddenApi))
        }
        assertFalse(buildScript.contains("startParameter.taskNames"))
        assertFalse(buildScript.contains("\"MissingPermission\", \"NotificationPermission\""))
        assertFalse(buildScript.contains("disable += setOf"))
        assertTrue(
            File("src/main/java/com/omiyawaki/osrswiki/news/viewmodel/NewsViewModel.kt")
                .readText()
                .contains("@SuppressLint(\"MissingPermission\")\n    private fun performHapticFeedback")
        )
        assertTrue(
            File("src/main/java/com/omiyawaki/osrswiki/util/SpeechRecognitionManager.kt")
                .readText()
                .contains("@SuppressLint(\"MissingPermission\")\n    private fun startListening")
        )
        assertFalse(buildScript.contains("implementation(libs.picasso)"))
        assertFalse(File("src/main/lint.xml").exists())
        assertFalse(File("src/debug/lint.xml").exists())
    }

    private fun mergedManifest() = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(
            File(
                "build/intermediates/merged_manifests/mapPrototype/" +
                    "processMapPrototypeManifest/AndroidManifest.xml"
            )
        )

    private fun Element.androidAttribute(localName: String): String {
        return getAttributeNS("http://schemas.android.com/apk/res/android", localName)
            .ifBlank { getAttribute("android:$localName") }
    }
}
