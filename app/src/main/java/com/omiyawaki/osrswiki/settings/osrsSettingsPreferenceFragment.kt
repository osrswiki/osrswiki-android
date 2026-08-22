package com.omiyawaki.osrswiki.settings

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
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
                    osrsSettingsTypography.applyToRow(child, isCategoryRow(child))
                    tintSettingsTypography()
                }

                override fun onChildViewDetachedFromWindow(child: View) = Unit
            })
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                restyleSettingsType()
                for (index in 0 until childCount) {
                    applyRowChrome(getChildAt(index), rowGap)
                }
            }
        }
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
        restyleSettingsType()
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
        @Suppress("UNCHECKED_CAST")
        val inner = super.onCreateAdapter(preferenceScreen) as RecyclerView.Adapter<RecyclerView.ViewHolder>
        return osrsSettingsBindAdapter(inner) { holder ->
            val row = holder.itemView
            osrsSettingsTypography.applyToRow(row, isCategoryRow(row))
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

    protected fun restyleSettingsType() {
        val list = listView ?: return
        for (index in 0 until list.childCount) {
            val child = list.getChildAt(index)
            osrsSettingsTypography.applyToRow(child, isCategoryRow(child))
        }
        tintSettingsTypography()
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

/**
 * Preference bind can run on an already-attached row without a new attach
 * event, which would otherwise restore Alegreya title roles after restyle.
 */
private class osrsSettingsBindAdapter(
    private val inner: RecyclerView.Adapter<RecyclerView.ViewHolder>,
    private val restyle: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    init {
        setHasStableIds(inner.hasStableIds())
        inner.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = notifyDataSetChanged()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                notifyItemRangeChanged(positionStart, itemCount)
            }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                notifyItemRangeChanged(positionStart, itemCount, payload)
            }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                notifyItemRangeInserted(positionStart, itemCount)
            }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                notifyItemRangeRemoved(positionStart, itemCount)
            }
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                notifyItemMoved(fromPosition, toPosition)
            }
        })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        inner.onCreateViewHolder(parent, viewType)

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        inner.onBindViewHolder(holder, position)
        restyle(holder)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        inner.onBindViewHolder(holder, position, payloads)
        restyle(holder)
    }

    override fun getItemCount() = inner.itemCount
    override fun getItemViewType(position: Int) = inner.getItemViewType(position)
    override fun getItemId(position: Int) = inner.getItemId(position)
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) = inner.onViewRecycled(holder)
    override fun onFailedToRecycleView(holder: RecyclerView.ViewHolder) =
        inner.onFailedToRecycleView(holder)

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        inner.onViewAttachedToWindow(holder)
        restyle(holder)
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) =
        inner.onViewDetachedFromWindow(holder)
}
