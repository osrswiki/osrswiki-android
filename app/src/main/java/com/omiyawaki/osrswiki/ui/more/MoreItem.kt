package com.omiyawaki.osrswiki.ui.more

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class MoreItem(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val action: MoreAction
)

enum class MoreAction {
    APPEARANCE,
    DONATE,
    ABOUT,
    FEEDBACK
}