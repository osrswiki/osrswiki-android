package com.omiyawaki.osrswiki.ui.main

object MainNavigationInsetPolicy {
    fun hostBottomMarginForNavigationInset(navigationBarBottom: Int): Int {
        return navigationBarBottom.coerceAtLeast(0)
    }
}
