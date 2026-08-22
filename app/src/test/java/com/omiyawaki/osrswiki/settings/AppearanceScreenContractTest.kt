package com.omiyawaki.osrswiki.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppearanceScreenContractTest {

    @Test
    fun appearanceUsesConventionalNativePreferencesForEveryRequestedControl() {
        val xml = resource("xml/preferences_appearance.xml")

        assertTrue(xml.contains("<com.omiyawaki.osrswiki.settings.osrsChoicePreference"))
        assertFalse(xml.contains("<DropDownPreference"))
        assertFalse(xml.contains("<ListPreference"))
        assertTrue(xml.contains("<SeekBarPreference"))
        assertTrue(xml.contains("app:min=\"85\""))
        assertTrue(xml.contains("android:max=\"140\""))
        assertTrue(xml.contains("app:seekBarIncrement=\"5\""))
        assertTrue(xml.contains("app:icon=\"@drawable/ic_format_size_24\""))
        assertTrue(xml.contains("app:key=\"${Prefs.KEY_APP_THEME_MODE}\""))
        assertTrue(xml.contains("app:key=\"${Prefs.KEY_COLLAPSE_TABLES}\""))
        assertTrue(xml.contains("app:key=\"${Prefs.KEY_WRAP_TABLE_CELLS}\""))
        assertTrue(xml.contains("app:key=\"${Prefs.KEY_READER_TEXT_SCALE_PERCENT}\""))
        assertTrue(xml.contains("app:key=\"${Prefs.KEY_SWIPE_RIGHT_BACK}\""))
        assertTrue(xml.contains("app:key=\"${Prefs.KEY_SWIPE_LEFT_CONTENTS}\""))
        assertTrue(xml.contains("app:key=\"${Prefs.KEY_FLOOR_NUMBERING}\""))
        assertTrue(xml.contains("app:entries=\"@array/floor_numbering_entries\""))
        assertFalse(xml.contains("theme_preview"))
        assertFalse(xml.contains("TablePreview"))
    }

    @Test
    fun appearanceFragmentPersistsEveryChangeThroughItsViewModel() {
        val source = source("settings/AppearanceSettingsFragment.kt")

        assertTrue(source.contains("SettingsRepository"))
        assertTrue(source.contains("viewModel.onThemeSelected"))
        assertTrue(source.contains("viewModel.onReaderTextScaleChanged"))
        assertTrue(source.contains("Prefs.KEY_COLLAPSE_TABLES"))
        assertTrue(source.contains("Prefs.KEY_WRAP_TABLE_CELLS"))
        assertTrue(source.contains("Prefs.KEY_SWIPE_RIGHT_BACK"))
        assertTrue(source.contains("Prefs.KEY_SWIPE_LEFT_CONTENTS"))
        assertTrue(source.contains("Prefs.KEY_FLOOR_NUMBERING"))
        assertTrue(source.contains("viewModel.onFloorNumberingSelected"))
        assertFalse(source.contains("CustomAppearanceSettingsFragment"))
        assertFalse(source.contains("ThemePreviewRenderer"))
        assertFalse(source.contains("TablePreviewRenderer"))
    }

    @Test
    fun preferenceSwitchesUseTheSharedHighContrastOsrsStateStyle() {
        val themes = resource("values/themes.xml")
        val thumb = resource("color/switch_thumb_tint.xml")
        val track = resource("color/switch_track_tint.xml")

        assertTrue(themes.contains("<item name=\"switchPreferenceCompatStyle\">@style/Preference.OSRS.SwitchPreferenceCompat</item>"))
        assertTrue(themes.contains("<style name=\"Widget.OSRSWiki.MaterialSwitch\""))
        assertTrue(themes.contains("preference_widget_osrs_material_switch"))
        assertTrue(track.contains("@color/osrs_gold_muted"))
        assertTrue(track.contains("android:alpha=\"0.36\""))
        assertTrue(thumb.contains("@color/osrs_brown_deep"))
    }

    @Test
    fun moreSettingsAndInformationalChromeUseScopedSansRoles() {
        val typography = resource("values/typography.xml")
        val moreRow = resource("layout/item_more.xml")
        val settingsCategory = resource("layout/item_settings_category.xml")
        val themes = resource("values/themes.xml")

        listOf(
            "AppTextAppearance.SettingsToolbar",
            "AppTextAppearance.SettingsRowTitle",
            "AppTextAppearance.SettingsPageHero",
            "AppTextAppearance.SettingsSection",
            "AppTextAppearance.SettingsSubheading",
            "AppTextAppearance.SettingsBody",
            "AppTextAppearance.PreferenceTitle",
            "AppTextAppearance.PreferenceSummary"
        ).forEach { role ->
            assertTrue(typography.contains("<style name=\"$role\""))
            val style = typography.substringAfter("<style name=\"$role\"").substringBefore("</style>")
            assertTrue("$role must explicitly remain system sans", style.contains("<item name=\"android:fontFamily\">sans-serif</item>"))
        }

        assertTrue(moreRow.contains("@style/AppTextAppearance.SettingsRowTitle"))
        assertTrue(settingsCategory.contains("@style/AppTextAppearance.SettingsRowTitle"))
        assertTrue(settingsCategory.contains("@style/AppTextAppearance.PreferenceSummary"))
        assertTrue(themes.contains("@style/AppTextAppearance.PreferenceTitle"))
        assertTrue(themes.contains("@style/AppTextAppearance.PreferenceSummary"))
        assertTrue(themes.contains("<item name=\"textAppearanceListItem\">@style/AppTextAppearance.PreferenceTitle</item>"))
        listOf(
            resource("layout/activity_report_issue.xml"),
            resource("layout/activity_request_feature.xml")
        ).forEach { subpage ->
            assertTrue(subpage.contains("@style/AppTextAppearance.SettingsToolbar"))
            assertTrue(subpage.contains("@style/AppTextAppearance.SettingsSubheading"))
            assertTrue(subpage.contains("@style/AppTextAppearance.SettingsBody"))
        }
    }

    @Test
    fun informationalSubpagesNoLongerOverrideScopedRolesWithAlegreyaAtRuntime() {
        val scopedSources = listOf(
            "about/AboutActivity.kt",
            "about/AboutFragment.kt",
            "about/PrivacyPolicyActivity.kt",
            "about/PrivacyPolicyFragment.kt",
            "donate/DonateActivity.kt",
            "donate/DonateFragment.kt",
            "feedback/FeedbackActivity.kt",
            "feedback/FeedbackFragmentSecure.kt",
            "settings/OfflineSettingsFragment.kt",
            "settings/SettingsCategoriesAdapter.kt"
        )

        scopedSources.forEach { path ->
            val source = source(path)
            assertFalse("$path must not mutate its XML typography", source.contains("applyAlegreya"))
            assertFalse("$path must not mutate its XML typography", source.contains("FontUtil.applyAlegreya"))
        }
    }

    @Test
    fun savedArticleRowEditorialContractRemainsUnchanged() {
        val layout = resource("layout/item_saved_page.xml")
        val holder = source("readinglist/adapter/SavedPageViewHolder.kt")

        assertTrue(layout.contains("style=\"@style/AppTextAppearance.ListTitleBold\""))
        assertTrue(layout.contains("android:maxLines=\"1\""))
        assertTrue(layout.contains("android:layout_width=\"60dp\""))
        assertTrue(layout.contains("android:layout_height=\"60dp\""))
        assertTrue(holder.contains("binding.itemSavedPageTitle.applyAlegreyaTitle()"))
        assertTrue(holder.contains("binding.itemSavedPageThumbnail.visibility = View.INVISIBLE"))
    }

    private fun source(relativePath: String): String =
        File("src/main/java/com/omiyawaki/osrswiki/$relativePath").readText()

    private fun resource(relativePath: String): String =
        File("src/main/res/$relativePath").readText()
}
