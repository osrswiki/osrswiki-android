package com.omiyawaki.osrswiki.settings

import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.google.android.material.appbar.MaterialToolbar
import com.omiyawaki.osrswiki.R

/**
 * Settings list type is system sans at a compact weight. Preference XML
 * appearances are not enough: Material row inflation still picks the app's
 * Alegreya title roles. Bind-time restyle is the layer that actually reaches
 * Appearance, Downloads, and every other [osrsSettingsPreferenceFragment].
 */
internal object osrsSettingsTypography {
    const val TITLE_SIZE_SP = 14f
    const val SUMMARY_SIZE_SP = 12f
    const val CATEGORY_SIZE_SP = 12f
    const val TOOLBAR_SIZE_SP = 20f

    fun applyToRow(row: View, isCategory: Boolean) {
        val title = row.findViewById<TextView>(android.R.id.title)
        val summary = row.findViewById<TextView>(android.R.id.summary)
        if (isCategory) {
            title?.let(::applyCategory)
        } else {
            title?.let(::applyTitle)
            summary?.let(::applySummary)
            row.findViewById<TextView>(R.id.osrs_choice_value)?.let(::applySummary)
            row.findViewById<TextView>(androidx.preference.R.id.seekbar_value)?.let(::applySummary)
        }
    }

    fun bindToolbar(toolbar: MaterialToolbar) {
        toolbar.setTitleTextAppearance(toolbar.context, R.style.AppTextAppearance_SettingsToolbar)
        for (index in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(index)
            if (child is TextView) {
                applyToolbarTitle(child)
            }
        }
    }

    fun applyTitle(textView: TextView) {
        applySans(
            textView,
            R.style.AppTextAppearance_PreferenceTitle,
            TITLE_SIZE_SP,
            Typeface.NORMAL
        )
    }

    fun applySummary(textView: TextView) {
        applySans(
            textView,
            R.style.AppTextAppearance_PreferenceSummary,
            SUMMARY_SIZE_SP,
            Typeface.NORMAL
        )
    }

    fun applyCategory(textView: TextView) {
        applySans(
            textView,
            R.style.AppTextAppearance_PreferenceCategory,
            CATEGORY_SIZE_SP,
            Typeface.NORMAL
        )
    }

    fun applyToolbarTitle(textView: TextView) {
        applySans(
            textView,
            R.style.AppTextAppearance_SettingsToolbar,
            TOOLBAR_SIZE_SP,
            Typeface.NORMAL
        )
    }

    private fun applySans(
        textView: TextView,
        appearance: Int,
        sizeSp: Float,
        style: Int
    ) {
        TextViewCompat.setTextAppearance(textView, appearance)
        val applyFace = Runnable {
            textView.typeface = Typeface.create("sans-serif", style)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            textView.paint.isFakeBoldText = false
            textView.letterSpacing = 0f
        }
        applyFace.run()
        // AppCompatTextView can re-apply the inflated appearance when bind
        // calls setText. Re-assert after the current traversal.
        textView.post(applyFace)
    }
}
