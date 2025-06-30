package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * Custom Preference class that allows its title to span multiple lines.
 * Based on Wikipedia's PreferenceMultiLine.
 */
@Suppress("unused") // Suppress warnings for constructors if not all are directly called from app code
class PreferenceMultiLine : Preference {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val titleView = holder.itemView.findViewById<TextView>(android.R.id.title)
        titleView?.isSingleLine = false
        // Additional styling (e.g., typeface, padding) can be added here if needed,
        // similar to Wikipedia's version, or managed via themes/styles.
        // For now, only multi-line is explicitly handled.

        // The Wikipedia version also has logic to intercept clicks for preferences with intents
        // and to log breadcrumbs. For this OSRSWiki version, we'll keep it simple
        // and focused on the multi-line title aspect. Specific click listeners will be
        // set by the SettingsPreferenceLoader or SettingsFragment.
    }

    // Optional: If analytics/breadcrumb logging similar to Wikipedia's is desired later,
    // the performClick() override can be added here.
    // override fun performClick() {
    //     // Add logging logic if needed
    //     super.performClick()
    // }
}
