package com.omiyawaki.osrswiki.settings

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import com.omiyawaki.osrswiki.R

/** Shared PreferenceFragment chrome: dividerless inset rows, one card per setting. */
abstract class osrsSettingsPreferenceFragment : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDivider(null)
        setDividerHeight(0)
        val horizontalInset = (16 * resources.displayMetrics.density).toInt()
        val rowGap = (8 * resources.displayMetrics.density).toInt()
        listView.apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(horizontalInset, rowGap, horizontalInset, rowGap)
            setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
            itemAnimator?.changeDuration = 0
            addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(child: View) {
                    applyRowChrome(child, rowGap)
                    tintSettingsTypography()
                }

                override fun onChildViewDetachedFromWindow(child: View) = Unit
            })
        }
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
        tintSettingsTypography()
        listView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            tintSettingsTypography()
            for (index in 0 until listView.childCount) {
                applyRowChrome(listView.getChildAt(index), rowGap)
            }
        }
    }

    private fun applyRowChrome(row: View, rowGap: Int) {
        val params = row.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (isCategoryRow(row)) {
            row.background = null
            params.topMargin = rowGap
            params.bottomMargin = 0
        } else {
            row.setBackgroundResource(R.drawable.osrs_settings_row_inset)
            params.topMargin = 0
            params.bottomMargin = rowGap
        }
        row.layoutParams = params
    }

    private fun isCategoryRow(row: View): Boolean {
        val widget = row.findViewById<View>(android.R.id.widget_frame)
        val icon = row.findViewById<View>(android.R.id.icon)
        val summary = row.findViewById<View>(android.R.id.summary)
        val widgetGone = widget == null || widget.visibility == View.GONE ||
            (widget as? ViewGroup)?.childCount == 0
        val iconGone = icon == null || icon.visibility == View.GONE
        val summaryGone = summary == null || summary.visibility == View.GONE
        return widgetGone && iconGone && summaryGone
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
