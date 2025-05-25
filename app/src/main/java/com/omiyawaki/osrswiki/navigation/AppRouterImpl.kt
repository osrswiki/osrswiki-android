package com.omiyawaki.osrswiki.navigation

import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
// Import your actual Fragment classes here
import com.omiyawaki.osrswiki.ui.search.SearchFragment // e.g., com.omiyawaki.osrswiki.ui.search.SearchFragment
import com.omiyawaki.osrswiki.ui.article.ArticleFragment // e.g., com.omiyawaki.osrswiki.ui.article.ArticleFragment

class AppRouterImpl(
    private val fragmentManager: FragmentManager,
    @IdRes private val containerId: Int
) : Router {

    override fun navigateToSearchScreen() {
        // Clear backstack for home/search screen for simplicity, adjust as needed
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        fragmentManager.beginTransaction()
            .replace(containerId, SearchFragment()) // Assuming SearchFragment has a no-arg constructor or a newInstance()
            // Do not typically add the root screen to the backstack
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
    }

    override fun navigateToArticle(articlePageId: String, articleTitle: String?) {
        fragmentManager.beginTransaction()
            // Ensure ArticleFragment.newInstance can handle these arguments
            .replace(containerId, ArticleFragment.newInstance(articlePageId = articlePageId, title = articleTitle))
            .addToBackStack(ArticleFragment::class.java.name) // Use a unique name for the transaction
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()
    }

    override fun goBack(): Boolean {
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
            return true
        }
        return false // No backstack to pop, Activity might handle it (e.g. finish())
    }
}
