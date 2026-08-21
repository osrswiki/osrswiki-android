package com.omiyawaki.osrswiki.settings

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat

/** Shared PreferenceFragment chrome: dividerless list inside the inset settings card. */
abstract class osrsSettingsPreferenceFragment : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDivider(null)
        setDividerHeight(0)
        listView.apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, 0, 0, 0)
            setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
            itemAnimator?.changeDuration = 0
        }
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
    }
}
