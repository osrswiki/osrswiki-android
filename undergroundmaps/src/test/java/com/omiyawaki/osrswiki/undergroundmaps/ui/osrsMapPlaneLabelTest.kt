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
    fun `plane labels stay Western digits when the default locale is ar-SA`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-SA"))
        try {
            for (plane in 0..3) {
                val label = osrsMapPlaneLabel(plane)
                assertEquals(plane.toString(radix = 10), label)
                assertTrue(label.all { it in '0'..'9' })
                assertFalse(
                    "Plane $plane leaked Eastern Arabic-Indic digits: $label",
                    label.any { it in '\u0660'..'\u0669' }
                )
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `current-floor description interpolates Western digits under ar-SA`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-SA"))
        try {
            val description = osrsMapPlaneCurrentDescription(
                plane = 2,
                realmName = "Gielinor Surface"
            )
            assertEquals("Current floor 2 of Gielinor Surface", description)
            assertFalse(description.any { it in '\u0660'..'\u0669' })
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `locale-sensitive percent-d is why plane labels must not use Resources percent-d`() {
        val indic = Formatter(Locale.forLanguageTag("ar-SA")).format("%d", 0).toString()
        assertEquals("٠", indic)
        assertEquals("0", osrsMapPlaneLabel(0))
    }

    @Test
    fun activityPaintsPlaneChromeThroughAsciiHelperNotPercentD() {
        val activity = File("src/main/java/com/omiyawaki/osrswiki/undergroundmaps/osrsUndergroundMapsActivity.kt").readText()
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(activity.contains("osrsMapPlaneLabel(current.activePlane)"))
        assertTrue(activity.contains("osrsMapPlaneCurrentDescription("))
        assertFalse(activity.contains("R.string.floor_number"))
        assertTrue(strings.contains("<string name=\"floor_current_description\">Current floor %1\$s of %2\$s</string>"))
        assertTrue(strings.contains("<string name=\"floor_number\">%1\$s</string>"))
        assertFalse(strings.contains("floor_current_description\">Current floor %1\$d"))
        assertFalse(strings.contains("floor_number\">%1\$d"))
    }
}
