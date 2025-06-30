package com.omiyawaki.osrswiki.util

import android.text.TextUtils
import android.view.View
import java.util.Locale

object L10nUtil {
    val isDeviceRTL: Boolean
        get() = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL

    fun isLangRTL(lang: String): Boolean {
        return TextUtils.getLayoutDirectionFromLocale(Locale(lang)) == View.LAYOUT_DIRECTION_RTL
    }
}
