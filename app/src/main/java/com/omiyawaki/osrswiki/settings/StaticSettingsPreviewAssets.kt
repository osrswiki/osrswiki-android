package com.omiyawaki.osrswiki.settings

import androidx.annotation.DrawableRes
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.theme.Theme

object StaticSettingsPreviewAssets {
    const val PREVIEW_WIDTH_DP = 82
    const val PREVIEW_HEIGHT_DP = 120

    @DrawableRes
    fun themePreviewRes(themeKey: String): Int {
        return when (themeKey) {
            "dark" -> R.drawable.settings_preview_theme_dark
            "auto" -> R.drawable.settings_preview_theme_auto
            else -> R.drawable.settings_preview_theme_light
        }
    }

    @DrawableRes
    fun tablePreviewRes(collapseTablesEnabled: Boolean, theme: Theme): Int {
        return when (theme) {
            Theme.OSRS_DARK -> {
                if (collapseTablesEnabled) {
                    R.drawable.settings_preview_table_dark_collapsed
                } else {
                    R.drawable.settings_preview_table_dark_expanded
                }
            }
            Theme.OSRS_LIGHT -> {
                if (collapseTablesEnabled) {
                    R.drawable.settings_preview_table_light_collapsed
                } else {
                    R.drawable.settings_preview_table_light_expanded
                }
            }
        }
    }
}
