package com.omiyawaki.osrswiki.navigation

import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
// Import your actual Fragment classes here
import com.omiyawaki.osrswiki.ui.search.SearchFragment // e.g., com.omiyawaki.osrswiki.ui.search.SearchFragment
import com.omiyawaki.osrswiki.ui.article.PageFragment // e.g., com.omiyawaki.osrswiki.ui.article.ArticleFragment

class AppRouterImpl(
    private val fragmentManager: FragmentManager,
    @IdRes private val containerId: Int
) : Router {

    override fun navigateToSearchScreen() {
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        fragmentManager.beginTransaction()
            .replace(containerId, SearchFragment.newInstance()) // Assuming SearchFragment has newInstance()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
    }

    override fun navigateToArticle(articleId: String?, articleTitle: String?) { // Signature remains the same
        fragmentManager.beginTransaction()
            // Call updated to only pass articleTitle as PageFragment.newInstance expects
            .replace(containerId, PageFragment.newInstance(articleTitle = articleTitle))
            .addToBackStack(PageFragment::class.java.name)
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
