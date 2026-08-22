package com.omiyawaki.osrswiki.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class osrsSettingsTypographyContractTest {
    @Test
    fun preferenceRowsAreForcedToCompactSansAtBindTime() {
        val typography = File("src/main/java/com/omiyawaki/osrswiki/settings/osrsSettingsTypography.kt").readText()
        val fragment = File("src/main/java/com/omiyawaki/osrswiki/settings/osrsSettingsPreferenceFragment.kt").readText()
        val xml = File("src/main/res/values/typography.xml").readText()
        val titleStyle = xml.substringAfter("<style name=\"AppTextAppearance.PreferenceTitle\"")
            .substringBefore("</style>")
        val summaryStyle = xml.substringAfter("<style name=\"AppTextAppearance.PreferenceSummary\"")
            .substringBefore("</style>")

        assertTrue(typography.contains("TITLE_SIZE_SP = 14f"))
        assertTrue(typography.contains("SUMMARY_SIZE_SP = 12f"))
        assertTrue(typography.contains("Typeface.create(\"sans-serif\""))
        assertTrue(fragment.contains("osrsSettingsTypography.applyToRow"))
        assertTrue(fragment.contains("restyleSettingsType()"))
        assertTrue(fragment.contains("onCreateAdapter"))
        assertTrue(File("src/main/res/layout/osrs_preference_material.xml").readText().contains("@style/AppTextAppearance.PreferenceTitle"))
        assertTrue(File("src/main/res/values/themes.xml").readText().contains("dialogPreferenceStyle"))
        val toolbarStyle = xml.substringAfter("<style name=\"AppTextAppearance.SettingsToolbar\"")
            .substringBefore("</style>")
        assertTrue(titleStyle.contains("<item name=\"android:textSize\">14sp</item>"))
        assertTrue(toolbarStyle.contains("<item name=\"android:textSize\">20sp</item>"))
        assertTrue(titleStyle.contains("<item name=\"android:fontFamily\">sans-serif</item>"))
        assertTrue(titleStyle.contains("<item name=\"android:fontWeight\" tools:targetApi=\"o\">400</item>"))
        assertTrue(summaryStyle.contains("<item name=\"android:textSize\">12sp</item>"))
        assertFalse(titleStyle.contains("@font/alegreya"))
        assertFalse(summaryStyle.contains("@font/alegreya"))
    }

    @Test
    fun settingsHostsBindToolbarSansAfterTheTitleIsSet() {
        val appearance = File("src/main/java/com/omiyawaki/osrswiki/settings/AppearanceSettingsActivity.kt").readText()
        val downloads = File("src/main/java/com/omiyawaki/osrswiki/settings/OfflineSettingsActivity.kt").readText()
        assertTrue(appearance.contains("osrsSettingsTypography.bindToolbar"))
        assertTrue(downloads.contains("osrsSettingsTypography.bindToolbar"))
    }
}
