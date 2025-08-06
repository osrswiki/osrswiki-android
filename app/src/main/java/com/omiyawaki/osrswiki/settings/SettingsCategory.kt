package com.omiyawaki.osrswiki.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class SettingsCategory(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val action: SettingsCategoryAction
)

enum class SettingsCategoryAction {
    APPEARANCE,
    OFFLINE_STORAGE
}