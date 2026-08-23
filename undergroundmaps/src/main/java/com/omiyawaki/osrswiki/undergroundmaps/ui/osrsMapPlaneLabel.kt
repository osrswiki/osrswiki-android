package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.Locale

/** Appearance `floor_numbering` key; must match [com.omiyawaki.osrswiki.settings.AppearancePreferenceKeys.FLOOR_NUMBERING]. */
const val OSRS_MAP_FLOOR_NUMBERING_PREFERENCE_KEY = "floor_numbering"

fun osrsMapFloorNumberingPreferences(context: Context): SharedPreferences =
    PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

fun osrsMapFloorUsEntranceIsFirstFloor(context: Context): Boolean {
    val host = context.applicationContext as? osrsMapFloorNumberingHost
    return host?.osrsMapFloorUsEntranceIsFirstFloor() == true
}

/** Map plane indices are game coordinates; display digits follow Appearance floor numbering. */
fun osrsMapPlaneDisplayDigit(
    plane: Int,
    usEntranceIsFirstFloor: Boolean
): Int = if (usEntranceIsFirstFloor) plane + 1 else plane

fun osrsMapPlaneLabel(
    plane: Int,
    usEntranceIsFirstFloor: Boolean = false
): String = osrsMapPlaneDisplayDigit(plane, usEntranceIsFirstFloor).toString(radix = 10)

fun osrsMapPlaneCurrentDescription(
    plane: Int,
    realmName: String,
    usEntranceIsFirstFloor: Boolean = false,
    template: String = "Current floor %1\$s of %2\$s"
): String {
    return String.format(
        Locale.ROOT,
        template,
        osrsMapPlaneLabel(plane, usEntranceIsFirstFloor),
        realmName
    )
}
