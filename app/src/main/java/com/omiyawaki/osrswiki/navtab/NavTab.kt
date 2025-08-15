package com.omiyawaki.osrswiki.navtab

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.omiyawaki.osrswiki.R // Ensure R is imported from the application package

/**
 * Represents the tabs in the bottom navigation.
 *
 * @property id The unique resource ID for this tab item (e.g., R.id.nav_search_bottom).
 * @property textResId The string resource ID for the tab's title (e.g., R.string.bottom_nav_title_search).
 * @property iconResId The drawable resource ID for the tab's icon (e.g., R.drawable.selector_nav_search).
 * @property viewPagerIndex The index of the ViewPager2 page this tab corresponds to, if any.
 * Null if this tab does not directly control a ViewPager2 page (e.g., the "More" tab).
 */
enum class NavTab(
    @IdRes val id: Int,
    @StringRes val textResId: Int,
    @DrawableRes val iconResId: Int,
    val viewPagerIndex: Int? // For tabs that correspond to a ViewPager page
) {
    SAVED(R.id.nav_saved_bottom, R.string.bottom_nav_title_saved, R.drawable.selector_nav_saved, 0),
    SEARCH(R.id.nav_search_bottom, R.string.bottom_nav_title_search, R.drawable.selector_nav_search, 1),
    MORE(R.id.nav_more_bottom, R.string.bottom_nav_title_more, R.drawable.ic_menu_white_24dp, null);

    companion object {
        /**
         * Finds an NavTab by its menu item resource ID.
         * @param id The resource ID of the menu item.
         * @return The corresponding NavTab, or null if not found.
         */
        fun fromId(@IdRes id: Int): NavTab? = entries.find { it.id == id }

        /**
         * Finds an NavTab by its ViewPager2 index.
         * @param index The index of the page in the ViewPager2.
         * @return The corresponding NavTab, or null if no tab is associated with that index.
         */
        fun fromViewPagerIndex(index: Int): NavTab? = entries.find { it.viewPagerIndex == index }
    }
}
