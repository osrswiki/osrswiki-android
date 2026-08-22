package com.omiyawaki.osrswiki.undergroundmaps.ui

import java.util.Locale

/** Map plane indices are game coordinates, not locale numerals. */
fun osrsMapPlaneLabel(plane: Int): String = plane.toString(radix = 10)

fun osrsMapPlaneCurrentDescription(
    plane: Int,
    realmName: String,
    template: String = "Current floor %1\$s of %2\$s"
): String {
    return String.format(Locale.ROOT, template, osrsMapPlaneLabel(plane), realmName)
}
