package com.omiyawaki.osrswiki.undergroundmaps.ui

import java.io.File
import java.util.Formatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsMapPlaneLabelTest {
    @Test
    fun `GB labels stay Western digits 0-3 when the default locale is ar-SA`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-SA"))
        try {
            for (plane in 0..3) {
                val label = osrsMapPlaneLabel(plane, usEntranceIsFirstFloor = false)
                assertEquals(plane.toString(radix = 10), label)
                assertWesternDigits(label)
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `US labels stay Western digits 1-4 when the default locale is ar-SA`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-SA"))
        try {
            for (plane in 0..3) {
                val label = osrsMapPlaneLabel(plane, usEntranceIsFirstFloor = true)
                assertEquals((plane + 1).toString(radix = 10), label)
                assertWesternDigits(label)
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `current-floor description interpolates Western GB digits under ar-SA`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-SA"))
        try {
            val description = osrsMapPlaneCurrentDescription(
                plane = 2,
                realmName = "Gielinor Surface",
                usEntranceIsFirstFloor = false
            )
            assertEquals("Current floor 2 of Gielinor Surface", description)
            assertFalse(description.any { it in '\u0660'..'\u0669' })
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `current-floor description interpolates Western US digits under ar-SA`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-SA"))
        try {
            val description = osrsMapPlaneCurrentDescription(
                plane = 0,
                realmName = "Gielinor Surface",
                usEntranceIsFirstFloor = true
            )
            assertEquals("Current floor 1 of Gielinor Surface", description)
            assertFalse(description.any { it in '\u0660'..'\u0669' })
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `locale-sensitive percent-d is why plane labels must not use Resources percent-d`() {
        val indic = Formatter(Locale.forLanguageTag("ar-SA")).format("%d", 0).toString()
        assertEquals("٠", indic)
        assertEquals("0", osrsMapPlaneLabel(0, usEntranceIsFirstFloor = false))
        assertEquals("1", osrsMapPlaneLabel(0, usEntranceIsFirstFloor = true))
    }

    @Test
    fun activityPaintsPlaneChromeThroughAsciiHelperNotPercentD() {
        val activity = File("src/main/java/com/omiyawaki/osrswiki/undergroundmaps/osrsUndergroundMapsActivity.kt").readText()
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(activity.contains("osrsMapPlaneLabel("))
        assertTrue(activity.contains("usEntranceIsFirstFloor = usEntranceIsFirstFloor"))
        assertTrue(activity.contains("osrsMapFloorUsEntranceIsFirstFloor(requireContext())"))
        assertTrue(activity.contains("osrsMapPlaneCurrentDescription("))
        assertTrue(activity.contains("registerOnSharedPreferenceChangeListener(floorNumberingPreferenceListener)"))
        assertTrue(activity.contains("override fun onHiddenChanged(hidden: Boolean)"))
        assertFalse(activity.contains("R.string.floor_number"))
        assertTrue(strings.contains("<string name=\"floor_current_description\">Current floor %1\$s of %2\$s</string>"))
        assertTrue(strings.contains("<string name=\"floor_number\">%1\$s</string>"))
        assertFalse(strings.contains("floor_current_description\">Current floor %1\$d"))
        assertFalse(strings.contains("floor_number\">%1\$d"))
    }

    @Test
    fun mapFloorPreferenceKeyMatchesAppearanceSetting() {
        assertEquals("floor_numbering", OSRS_MAP_FLOOR_NUMBERING_PREFERENCE_KEY)
        val appearance = File("../app/src/main/java/com/omiyawaki/osrswiki/settings/AppearancePreferences.kt").readText()
        assertTrue(appearance.contains("const val FLOOR_NUMBERING = \"floor_numbering\""))
        val app = File("../app/src/main/java/com/omiyawaki/osrswiki/OSRSWikiApp.kt").readText()
        assertTrue(app.contains("osrsMapFloorNumberingHost"))
        assertTrue(app.contains("osrsMapFloorUsEntranceIsFirstFloor"))
        assertTrue(app.contains("osrsArticleFloorConvention.resolved().usEntranceIsFirstFloor"))
    }

    private fun assertWesternDigits(label: String) {
        assertTrue(label.all { it in '0'..'9' })
        assertFalse(
            "Leaked Eastern Arabic-Indic digits: $label",
            label.any { it in '\u0660'..'\u0669' }
        )
    }
}
