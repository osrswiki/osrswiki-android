package com.omiyawaki.osrswiki.navigation

/**
 * Defines the navigation actions available throughout the application.
 * Implemented by AppRouterImpl.
 */
interface Router {
    fun navigateToSearchScreen() // Assuming SearchFragment is the typical "home" or initial useful screen
    fun navigateToArticle(articleId: String?, articleTitle: String?) // Updated parameter name and nullability
    // Add other navigation destinations as needed, e.g.:
    // fun navigateToSettings()
    // fun navigateToBookmarks()

    /**
     * Handles back navigation.
     * @return true if navigation was handled (e.g., FragmentManager popped backstack),
     * false otherwise (e.g., at the root of the backstack).
     */
    fun goBack(): Boolean
}
