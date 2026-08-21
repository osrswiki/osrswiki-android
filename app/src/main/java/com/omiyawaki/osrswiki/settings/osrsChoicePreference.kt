package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.PopupWindowCompat
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import com.omiyawaki.osrswiki.R

/**
 * Settings choice list that keeps the current value readable while the menu is open.
 *
 * AndroidX [androidx.preference.DropDownPreference] hosts an invisible Spinner whose
 * popup overlaps the title/summary. This preference shows a [PopupWindow] below the
 * preference row so the title and trailing current value stay readable, matching iOS
 * trailing menus.
 */
class osrsChoicePreference : ListPreference {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
        super(context, attrs, defStyleAttr, defStyleRes)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    private var valueView: TextView? = null
    private var boundItemView: View? = null

    init {
        widgetLayoutResource = R.layout.preference_osrs_choice_widget
        isSingleLineTitle = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        boundItemView = holder.itemView
        valueView = holder.findViewById(R.id.osrs_choice_value) as? TextView
        renderTrailingValue()
        // Current value lives on the trailing widget so the row title stays readable
        // while the popup is open. Hide the library summary to avoid duplicating it.
        holder.findViewById(android.R.id.summary)?.visibility = View.GONE
    }

    override fun setValue(value: String?) {
        super.setValue(value)
        renderTrailingValue()
    }

    override fun setSummary(summary: CharSequence?) {
        super.setSummary(summary)
        renderTrailingValue()
    }

    override fun onClick() {
        showTrailingPopup()
    }

    private fun renderTrailingValue() {
        val label = entry?.toString()?.takeIf { it.isNotBlank() }
            ?: summary?.toString()?.takeIf { it.isNotBlank() }
            ?: ""
        valueView?.text = label
    }

    private fun showTrailingPopup() {
        val entries = entries ?: return
        if (entries.isEmpty()) return
        val anchor = boundItemView ?: valueView ?: return
        val density = context.resources.displayMetrics.density
        val minWidthPx = (220 * density).toInt()
        val horizontalPadPx = (48 * density).toInt()
        val paint = (valueView ?: TextView(context)).paint
        val longestLabelPx = entries.maxOf { paint.measureText(it.toString()).toInt() }
        val popupWidth = maxOf(minWidthPx, longestLabelPx + horizontalPadPx, valueView?.width ?: 0)
        lateinit var popup: PopupWindow
        val listView = ListView(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_list_item_1,
                entries
            )
            divider = null
            isFocusable = true
            setOnItemClickListener { _, _, position, _ ->
                if (position in entryValues.indices) {
                    val newValue = entryValues[position].toString()
                    if (callChangeListener(newValue)) {
                        value = newValue
                    }
                }
                popup.dismiss()
            }
        }
        popup = PopupWindow(
            listView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = 8f * density
            setBackgroundDrawable(
                ContextCompat.getDrawable(context, R.drawable.osrs_choice_popup_background)
                    ?: ContextCompat.getDrawable(
                        context,
                        com.google.android.material.R.drawable.mtrl_popupmenu_background
                    )
            )
        }
        PopupWindowCompat.setOverlapAnchor(popup, false)
        val xOff = (anchor.width - popupWidth).coerceAtMost(0)
        popup.showAsDropDown(anchor, xOff, (4 * density).toInt())
    }
}
