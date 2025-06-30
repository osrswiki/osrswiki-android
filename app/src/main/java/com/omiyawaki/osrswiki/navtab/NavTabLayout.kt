package com.omiyawaki.osrswiki.navtab

import android.content.Context
import android.util.AttributeSet
import android.view.Menu
import com.google.android.material.bottomnavigation.BottomNavigationView
// The R class from com.omiyawaki.osrswiki.R will be available due to package structure
// and Kotlin's import system.

/**
 * Custom BottomNavigationView that populates its menu items programmatically
 * based on the NavTab enum, mirroring Wikipedia's NavTabLayout approach.
 * This allows for centralized definition of navigation tabs in Kotlin
 * rather than relying on a static menu XML file.
 */
class NavTabLayout(context: Context, attrs: AttributeSet) : BottomNavigationView(context, attrs) {

    init {
        // Clear any menu items that might have been inflated from an XML attribute
        // or a previous state, ensuring a clean slate for programmatic item addition.
        menu.clear()

        // Add navigation items based on the NavTab enum.
        // Items are sorted to ensure that tabs corresponding to ViewPager2 pages
        // appear first and in their correct order, followed by any other tabs (e.g., "More").
        // The 'order' parameter in menu.add is derived from the iteration index after sorting.
        NavTab.entries
            .sortedWith(compareBy(nullsLast()) { it.viewPagerIndex }) // Sorts by viewPagerIndex; tabs without it (null) go last.
            .forEachIndexed { order, tab ->
                // Use tab.id as the itemId for the menu item. This ID should correspond
                // to an existing ID in R.id (e.g., R.id.nav_saved_bottom), which is used
                // by the OnItemSelectedListener in MainFragment.
                // The 'order' variable from forEachIndexed ensures items are added in the sorted sequence.
                menu.add(Menu.NONE, tab.id, order, context.getString(tab.textResId))
                    .setIcon(tab.iconResId)
            }
    }
}
