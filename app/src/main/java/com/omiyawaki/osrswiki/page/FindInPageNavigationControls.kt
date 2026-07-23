package com.omiyawaki.osrswiki.page

import android.view.View

object FindInPageNavigationControls {
    fun apply(
        previousButton: View?,
        nextButton: View?,
        hasMatches: Boolean
    ) {
        listOfNotNull(previousButton, nextButton).forEach { button ->
            button.isEnabled = hasMatches
            button.isClickable = hasMatches
            button.isFocusable = hasMatches
            button.alpha = if (hasMatches) ENABLED_ALPHA else DISABLED_ALPHA
        }
    }

    private const val ENABLED_ALPHA = 1f
    private const val DISABLED_ALPHA = 0.38f
}
