package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.content.Context
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.omiyawaki.osrswiki.undergroundmaps.R
import com.omiyawaki.osrswiki.undergroundmaps.model.OSRS_REALM_GROUPS
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmGroupLabel
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmRecord

class osrsRealmSelector(
    private val context: Context,
    private val selectorIndex: osrsRealmSelectorIndex,
    private val activeRealmId: String,
    private val onRealmSelected: (osrsRealmRecord) -> Unit,
    private val onFilterMeasured: (query: String, resultCount: Int, elapsedNanos: Long) -> Unit
) {
    fun show(): BottomSheetDialog {
        val dialog = BottomSheetDialog(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
        }
        root.addView(TextView(context).apply {
            text = context.getString(R.string.realm_selector_title)
            textSize = 22f
            setTextColor(context.getColor(R.color.osrs_ink))
            contentDescription = text
        }, matchWrap())

        val search = EditText(context).apply {
            id = R.id.osrs_selector_search
            hint = context.getString(R.string.realm_search_hint)
            contentDescription = hint
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(search, matchWrap().apply { topMargin = dp(10); bottomMargin = dp(8) })

        val adapter = osrsRealmSelectorAdapter(
            context,
            activeRealmId,
            selectorIndex.realmPresentations
        ) { realm ->
            dialog.dismiss()
            onRealmSelected(realm)
        }
        val list = RecyclerView(context).apply {
            id = R.id.osrs_selector_list
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            contentDescription = "Map selector results"
            isVerticalScrollBarEnabled = true
            itemAnimator = null
        }
        root.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        fun filter(query: String) {
            val started = SystemClock.elapsedRealtimeNanos()
            val result = selectorIndex.filter(query)
            adapter.submit(result.sections)
            onFilterMeasured(query, result.resultCount, SystemClock.elapsedRealtimeNanos() - started)
        }
        filter("")
        search.doAfterTextChanged { filter(it?.toString().orEmpty()) }

        dialog.setContentView(root)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.layoutParams = bottomSheet?.layoutParams?.apply {
                height = (context.resources.displayMetrics.heightPixels * 0.88f).toInt()
            }
            dialog.behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
            search.requestFocus()
        }
        return dialog.apply { show() }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}

data class osrsRealmSelectorResult(
    val normalizedQuery: String,
    val sections: Map<String, List<osrsRealmRecord>>,
    val resultCount: Int,
    val evaluatedRealmCount: Int
)

/**
 * Immutable normalized search material plus the last narrowing result for the 1,097-item selector.
 * This index is created with the manifest, outside the keystroke path, and reused across dialogs.
 */
class osrsRealmSelectorIndex(
    realmsByGroup: Map<String, List<osrsRealmRecord>>,
    val realmPresentations: osrsRealmPresentationCatalog
) {
    private val allSections: Map<String, List<osrsRealmRecord>> = OSRS_REALM_GROUPS.associateWith {
        realmsByGroup[it].orEmpty().toList()
    }
    private val allResult = osrsRealmSelectorResult(
        normalizedQuery = "",
        sections = allSections,
        resultCount = allSections.values.sumOf(List<*>::size),
        evaluatedRealmCount = 0
    )
    private var previousResult: osrsRealmSelectorResult = allResult

    fun filter(query: String): osrsRealmSelectorResult {
        val terms = realmPresentations.normalizedTerms(query)
        val normalizedQuery = terms.joinToString(" ")
        if (normalizedQuery.isEmpty()) {
            previousResult = allResult
            return allResult
        }
        if (normalizedQuery == previousResult.normalizedQuery) return previousResult

        val candidates = if (
            previousResult.normalizedQuery.isNotEmpty() &&
            normalizedQuery.startsWith(previousResult.normalizedQuery)
        ) {
            previousResult.sections
        } else {
            allSections
        }
        val filteredSections = OSRS_REALM_GROUPS.associateWith { group ->
            candidates.getValue(group).filter { realm ->
                realmPresentations.matches(realm, terms)
            }
        }
        return osrsRealmSelectorResult(
            normalizedQuery = normalizedQuery,
            sections = filteredSections,
            resultCount = filteredSections.values.sumOf(List<*>::size),
            evaluatedRealmCount = candidates.values.sumOf(List<*>::size)
        ).also { previousResult = it }
    }
}

private sealed interface osrsSelectorRow {
    data class Header(val title: String) : osrsSelectorRow
    data class Realm(val realm: osrsRealmRecord) : osrsSelectorRow
    data object Empty : osrsSelectorRow
}

private class osrsRealmSelectorAdapter(
    private val context: Context,
    private val activeRealmId: String,
    private val realmPresentations: osrsRealmPresentationCatalog,
    private val onRealmSelected: (osrsRealmRecord) -> Unit
) : RecyclerView.Adapter<osrsRealmSelectorAdapter.osrsSelectorViewHolder>() {
    private var rows: List<osrsSelectorRow> = emptyList()

    fun submit(sections: Map<String, List<osrsRealmRecord>>) {
        val replacement = buildList {
            OSRS_REALM_GROUPS.forEach { group ->
                val realms = sections[group].orEmpty()
                if (realms.isNotEmpty()) {
                    add(osrsSelectorRow.Header(osrsRealmGroupLabel(group)))
                    realms.forEach { add(osrsSelectorRow.Realm(it)) }
                }
            }
            if (isEmpty()) add(osrsSelectorRow.Empty)
        }
        if (replacement == rows) return
        val oldSize = rows.size
        rows = replacement
        val sharedSize = minOf(oldSize, replacement.size)
        if (sharedSize > 0) notifyItemRangeChanged(0, sharedSize)
        if (replacement.size > oldSize) {
            notifyItemRangeInserted(oldSize, replacement.size - oldSize)
        } else if (oldSize > replacement.size) {
            notifyItemRangeRemoved(replacement.size, oldSize - replacement.size)
        }
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is osrsSelectorRow.Header -> OSRS_ROW_HEADER
        is osrsSelectorRow.Realm -> OSRS_ROW_REALM
        osrsSelectorRow.Empty -> OSRS_ROW_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): osrsSelectorViewHolder {
        val view = when (viewType) {
            OSRS_ROW_HEADER -> TextView(context).apply {
                setPadding(dp(4), dp(18), dp(4), dp(6))
                textSize = 13f
                setTextColor(context.getColor(R.color.osrs_ink))
                isAllCaps = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            OSRS_ROW_EMPTY -> TextView(context).apply {
                setPadding(dp(4), dp(24), dp(4), dp(24))
                gravity = Gravity.CENTER
                text = context.getString(R.string.no_search_results)
                textSize = 16f
            }
            else -> LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = dp(56)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                isClickable = true
                isFocusable = true
                val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                background = attributes.getDrawable(0)
                attributes.recycle()
                addView(TextView(context).apply {
                    tag = OSRS_PRIMARY_TEXT_TAG
                    textSize = 16f
                    osrsApplyRealmIdentityLayout()
                    setTextColor(context.getColor(R.color.osrs_ink))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(TextView(context).apply {
                    tag = OSRS_SECONDARY_TEXT_TAG
                    textSize = 12f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(context.getColor(R.color.osrs_ink))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
        return osrsSelectorViewHolder(view)
    }

    override fun onBindViewHolder(holder: osrsSelectorViewHolder, position: Int) {
        when (val row = rows[position]) {
            is osrsSelectorRow.Header -> (holder.itemView as TextView).apply {
                text = row.title
                contentDescription = "${row.title} section"
            }
            is osrsSelectorRow.Realm -> bindRealm(holder.itemView as LinearLayout, row.realm)
            osrsSelectorRow.Empty -> Unit
        }
    }

    private fun bindRealm(view: LinearLayout, realm: osrsRealmRecord) {
        val selected = realm.id == activeRealmId
        val presentation = realmPresentations[realm]
        view.findViewWithTag<TextView>(OSRS_PRIMARY_TEXT_TAG).text = presentation.visibleName
        view.findViewWithTag<TextView>(OSRS_SECONDARY_TEXT_TAG).apply {
            text = buildString {
                append(if (realm.planes.size == 1) "1 floor" else "${realm.planes.size} floors")
                realm.article?.let { append("  •  ").append(it.removePrefix("Map:")) }
            }
        }
        view.isSelected = selected
        view.contentDescription = presentation.selectorAccessibilityLabel(selected)
        view.setOnClickListener { onRealmSelected(realm) }
    }

    override fun getItemCount(): Int = rows.size

    class osrsSelectorViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val OSRS_ROW_HEADER = 1
        private const val OSRS_ROW_REALM = 2
        private const val OSRS_ROW_EMPTY = 3
        private const val OSRS_PRIMARY_TEXT_TAG = "primary"
        private const val OSRS_SECONDARY_TEXT_TAG = "secondary"
    }
}
