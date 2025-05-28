package com.omiyawaki.osrswiki.navigation

import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.omiyawaki.osrswiki.page.PageFragment // For PageFragment.newInstance
import com.omiyawaki.osrswiki.search.SearchFragment

class AppRouterImpl(
    private val fragmentManager: FragmentManager,
    @IdRes private val containerId: Int
) : Router {

    override fun navigateToSearchScreen() {
        // Clear the back stack and navigate to SearchFragment.
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        fragmentManager.beginTransaction()
            .replace(containerId, SearchFragment.newInstance()) // Assuming SearchFragment has newInstance()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
    }

    override fun navigateToPage(pageId: String?, pageTitle: String?) {
        // Use PageFragment.newInstance to create the fragment and pass arguments
        val fragment = PageFragment.newInstance(pageId = pageId, pageTitle = pageTitle)
        fragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .addToBackStack(PageFragment::class.java.name) // Optional: add to back stack
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
    }

    override fun goBack(): Boolean {
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
            return true
        }
        return false
    }
}
