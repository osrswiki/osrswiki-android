package com.omiyawaki.osrswiki.views

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import com.google.android.material.R
import com.google.android.material.card.MaterialCardView

class OSRSWikiCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {

    init {
        // --- DEBUGGING ---
        // Let's check what color the theme resolves for colorSurfaceVariant.
        val typedValue = TypedValue()
        val theme = context.theme
        val wasResolved = theme.resolveAttribute(R.attr.colorSurfaceVariant, typedValue, true)

        if (wasResolved) {
            val colorInt = typedValue.data
            // Format the color to a hex string to make it human-readable in logs.
            val hexColor = String.format("#%06X", 0xFFFFFF and colorInt)
            Log.d(
                "OSRSWikiCardView",
                "Theme Check: ?attr/colorSurfaceVariant resolved to color: $hexColor"
            )
        } else {
            Log.e(
                "OSRSWikiCardView",
                "Theme Check: FAILED to resolve ?attr/colorSurfaceVariant."
            )
        }

        // Also, let's log the actual background color after initialization.
        val currentBgColor = String.format("#%06X", 0xFFFFFF and cardBackgroundColor.defaultColor)
        Log.d(
            "OSRSWikiCardView",
            "Initial cardBackgroundColor: $currentBgColor"
        )
        // --- END DEBUGGING ---
    }
}
