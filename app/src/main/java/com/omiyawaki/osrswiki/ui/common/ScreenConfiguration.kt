package com.omiyawaki.osrswiki.ui.common

import androidx.annotation.StringRes

/**
 * Interface for Fragments to declare their desired Toolbar configuration
 * to the hosting Activity (e.g., MainActivity).
 */
interface ScreenConfiguration {
    /**
     * Gets the desired title for the Toolbar.
     * Can return a direct String or a resource ID.
     * For simplicity, returning String here. Consider returning Int (@StringRes)
     * if localization is a primary concern from the start.
     */
    fun getToolbarTitle(getString: (id: Int) -> String): String

    /**
     * Gets the type of navigation icon to be displayed (e.g., Back, Menu, None).
     */
    fun getNavigationIconType(): NavigationIconType

    /**
     * Indicates if this screen contributes custom items to the options menu.
     * If true, MainActivity should invalidate the options menu.
     */
    fun hasCustomOptionsMenu(): Boolean
}
