package com.omiyawaki.osrswiki.settings

/**
 * Sealed class representing all possible items in the settings list.
 * Follows expert guidance for modern settings UI architecture.
 */
sealed class SettingItem {
    
    /**
     * Category header for grouping related settings
     */
    data class CategoryHeader(val title: String) : SettingItem()

    /**
     * Switch/toggle setting with immediate state change
     */
    data class SwitchSetting(
        val key: String,
        val title: String,
        val summary: String,
        val isChecked: Boolean,
        val iconResId: Int? = null
    ) : SettingItem()

    /**
     * List setting that shows a dialog with options
     */
    data class ListSetting(
        val key: String,
        val title: String,
        val displayValue: String, // Human-readable current selection
        val iconResId: Int? = null
    ) : SettingItem()

    /**
     * Inline theme selection with visual previews
     */
    data class InlineThemeSelection(
        val key: String,
        val title: String,
        val themes: List<Pair<String, String>>, // (themeKey, displayName)
        val currentSelection: String
    ) : SettingItem()

    /**
     * Inline table preview selection with visual article previews
     * Shows horizontal comparison of collapsed vs expanded table states
     */
    data class InlineTablePreviewSelection(
        val key: String,
        val title: String,
        val options: List<Pair<Boolean, String>>, // (collapseTablesValue, displayName)
        val currentSelection: Boolean
    ) : SettingItem()

    // Future extensibility for more setting types
    // data class NavigationSetting(...)
    // data class SliderSetting(...)
}