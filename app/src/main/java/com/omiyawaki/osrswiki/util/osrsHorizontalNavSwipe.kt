package com.omiyawaki.osrswiki.util

import android.view.Gravity

fun osrsLogicalSwipeDelta(dx: Float, rtl: Boolean): Float = if (rtl) -dx else dx

/**
 * Maps a physical horizontal swipe onto article START (back) / END (contents).
 * RTL flips the mapping so swipe-right opens contents and swipe-left goes back.
 */
fun osrsArticleSwipeGravity(dx: Float, rtl: Boolean): Int {
    return if (osrsLogicalSwipeDelta(dx, rtl) > 0f) Gravity.START else Gravity.END
}

/** Back-swipe chrome follows the finger: LTR slides right, RTL slides left. */
fun osrsBackSwipeTranslationX(progress: Float, width: Float, rtl: Boolean): Float {
    val sign = if (rtl) -1f else 1f
    return progress.coerceIn(0f, 1f) * width.coerceAtLeast(1f) * sign
}
