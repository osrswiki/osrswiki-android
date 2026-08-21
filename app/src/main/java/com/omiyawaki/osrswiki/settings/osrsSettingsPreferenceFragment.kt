package com.omiyawaki.osrswiki.settings

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.R as MaterialR

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
        tintSettingsTypography()
        listView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            tintSettingsTypography()
        }
    }

    protected fun tintSettingsTypography() {
        val list = listView ?: return
        val onSurface = resolveThemeColor(MaterialR.attr.colorOnSurface)
        val onVariant = resolveThemeColor(MaterialR.attr.colorOnSurfaceVariant)
        tintSettingsTypography(list, onSurface, onVariant)
    }

    private fun tintSettingsTypography(view: View, onSurface: Int, onVariant: Int) {
        if (view is TextView) {
            when (view.id) {
                android.R.id.title -> {
                    val hasSummary = (view.parent as? View)?.findViewById<View>(android.R.id.summary) != null
                    view.setTextColor(if (hasSummary) onSurface else onVariant)
                }
                android.R.id.summary -> view.setTextColor(onVariant)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                tintSettingsTypography(view.getChildAt(index), onSurface, onVariant)
            }
        }
    }

    private fun resolveThemeColor(attr: Int): Int {
        val typed = TypedValue()
        requireContext().theme.resolveAttribute(attr, typed, true)
        return typed.data
    }
}
