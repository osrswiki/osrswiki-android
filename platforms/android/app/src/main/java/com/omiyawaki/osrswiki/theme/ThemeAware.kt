package com.omiyawaki.osrswiki.theme

interface ThemeAware {
    /**
     * Called when the app's theme has changed and the fragment should refresh its theming.
     * This is typically called after the parent activity has been recreated due to a theme change.
     */
    fun onThemeChanged()
}