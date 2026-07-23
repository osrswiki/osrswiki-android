package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.content.Context
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.omiyawaki.osrswiki.undergroundmaps.R

data class osrsRealmLinksDialogShowResult(
    val dialog: BottomSheetDialog,
    val viewConstructionNanos: Long,
    val initialFilterNanos: Long,
    val initialRowConversionNanos: Long,
    val initialAdapterSubmissionNanos: Long,
    val initialUpdateStrategy: String,
    val materialShowNanos: Long,
    val showingAfterReturn: Boolean
)

data class osrsRealmLinksDialogDebugState(
    val realmId: String,
    val isShowing: Boolean,
    val displayedRowCount: Int,
    val query: String,
    val summaryText: String,
    val resultsContentDescription: String,
    val decorAttached: Boolean,
    val decorLaidOut: Boolean,
    val decorShown: Boolean,
    val visibleBoundRowCount: Int
)

class osrsRealmLinksDialog(
    private val context: Context,
    private val links: osrsRealmLinkCatalog,
    private val onLinkSelected: (osrsRealmLinkRow) -> Unit,
    private val onFilterMeasured: (query: String, resultCount: Int, elapsedNanos: Long) -> Unit
) {
    private val dialog: BottomSheetDialog
    private val search: EditText
    private val summary: TextView
    private val list: RecyclerView
    private val adapter: osrsRealmLinksAdapter
    private val firstViewConstructionNanos: Long
    private val firstInitialFilterNanos: Long
    private val firstInitialRowConversionNanos: Long
    private val firstInitialAdapterSubmissionNanos: Long
    private var hasShown = false
    private var reportFilterChanges = true

    val realmId: String
        get() = links.currentRealm.id

    init {
        var viewConstructionNanos = 0L
        var viewSegmentStarted = SystemClock.elapsedRealtimeNanos()
        val builtDialog = BottomSheetDialog(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
        }
        root.addView(TextView(context).apply {
            text = context.getString(R.string.realm_links_dialog_title)
            textSize = 22f
            setTextColor(context.getColor(R.color.osrs_ink))
            contentDescription = text
        }, matchWrap())

        val builtSummary = TextView(context).apply {
            id = R.id.osrs_links_summary
            text = context.getString(
                R.string.realm_links_dialog_summary,
                context.resources.getQuantityString(
                    R.plurals.realm_links_available_count,
                    links.availableRows.size,
                    links.availableRows.size
                ),
                context.resources.getQuantityString(
                    R.plurals.realm_links_unavailable_count,
                    links.unavailableCount,
                    links.unavailableCount
                )
            )
            textSize = 14f
            setTextColor(context.getColor(R.color.osrs_ink))
            setPadding(0, dp(8), 0, dp(4))
            contentDescription = text
        }
        root.addView(builtSummary, matchWrap())

        val builtSearch = EditText(context).apply {
            id = R.id.osrs_links_search
            hint = context.getString(R.string.realm_link_search_hint)
            contentDescription = hint
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(builtSearch, matchWrap().apply {
            topMargin = dp(6)
            bottomMargin = dp(8)
        })

        val builtAdapter = osrsRealmLinksAdapter(context) { row ->
            builtDialog.dismiss()
            onLinkSelected(row)
        }
        viewConstructionNanos += SystemClock.elapsedRealtimeNanos() - viewSegmentStarted

        val initialFilterStarted = SystemClock.elapsedRealtimeNanos()
        val initialRows = links.filter("")
        val initialFilterNanos = SystemClock.elapsedRealtimeNanos() - initialFilterStarted
        val initialAdapterTiming = builtAdapter.setInitial(initialRows)

        viewSegmentStarted = SystemClock.elapsedRealtimeNanos()
        val builtList = RecyclerView(context).apply {
            id = R.id.osrs_links_list
            layoutManager = LinearLayoutManager(context)
            this.adapter = builtAdapter
            contentDescription = context.getString(
                R.string.realm_link_results_description,
                links.allRows.size
            )
            isVerticalScrollBarEnabled = true
            isFocusableInTouchMode = true
        }
        root.addView(builtList, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        builtSearch.doAfterTextChanged {
            applyFilter(it?.toString().orEmpty(), report = reportFilterChanges)
        }

        builtDialog.setContentView(root)
        builtDialog.setOnShowListener {
            val bottomSheet = builtDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.layoutParams = bottomSheet?.layoutParams?.apply {
                height = (context.resources.displayMetrics.heightPixels * 0.88f).toInt()
            }
            builtDialog.behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
            builtList.requestFocus()
        }
        builtDialog.setOnDismissListener { resetAfterDismiss() }
        viewConstructionNanos += SystemClock.elapsedRealtimeNanos() - viewSegmentStarted

        dialog = builtDialog
        search = builtSearch
        summary = builtSummary
        list = builtList
        adapter = builtAdapter
        firstViewConstructionNanos = viewConstructionNanos
        firstInitialFilterNanos = initialFilterNanos
        firstInitialRowConversionNanos = initialAdapterTiming.rowConversionNanos
        firstInitialAdapterSubmissionNanos = initialAdapterTiming.submissionNanos
    }

    fun show(): osrsRealmLinksDialogShowResult {
        check(!dialog.isShowing) { "Links dialog for $realmId is already showing" }
        val firstShow = !hasShown
        val materialShowStarted = SystemClock.elapsedRealtimeNanos()
        dialog.show()
        val materialShowNanos = SystemClock.elapsedRealtimeNanos() - materialShowStarted
        hasShown = true
        return osrsRealmLinksDialogShowResult(
            dialog = dialog,
            viewConstructionNanos = if (firstShow) firstViewConstructionNanos else 0L,
            initialFilterNanos = if (firstShow) firstInitialFilterNanos else 0L,
            initialRowConversionNanos = if (firstShow) firstInitialRowConversionNanos else 0L,
            initialAdapterSubmissionNanos = if (firstShow) firstInitialAdapterSubmissionNanos else 0L,
            initialUpdateStrategy = if (firstShow) "direct_before_attach" else "reused",
            materialShowNanos = materialShowNanos,
            showingAfterReturn = dialog.isShowing
        )
    }

    fun dismiss() {
        resetAfterDismiss()
        if (dialog.isShowing) dialog.dismiss()
    }

    fun debugState(): osrsRealmLinksDialogDebugState {
        val decor = dialog.window?.decorView
        return osrsRealmLinksDialogDebugState(
            realmId = realmId,
            isShowing = dialog.isShowing,
            displayedRowCount = adapter.itemCount,
            query = search.text?.toString().orEmpty(),
            summaryText = summary.text?.toString().orEmpty(),
            resultsContentDescription = list.contentDescription?.toString().orEmpty(),
            decorAttached = decor?.isAttachedToWindow == true,
            decorLaidOut = decor?.isLaidOut == true,
            decorShown = decor?.isShown == true,
            visibleBoundRowCount = list.childCount
        )
    }

    private fun applyFilter(query: String, report: Boolean) {
        val started = SystemClock.elapsedRealtimeNanos()
        val results = links.filter(query)
        adapter.submit(results)
        list.contentDescription = context.getString(
            R.string.realm_link_results_description,
            results.size
        )
        val elapsed = SystemClock.elapsedRealtimeNanos() - started
        if (report) onFilterMeasured(query, results.size, elapsed)
    }

    private fun resetAfterDismiss() {
        if (search.text?.isNotEmpty() == true) {
            reportFilterChanges = false
            try {
                search.setText("")
            } finally {
                reportFilterChanges = true
            }
        }
        search.clearFocus()
        list.scrollToPosition(0)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}

private sealed interface osrsRealmLinkListRow {
    data class Available(val value: osrsRealmLinkRow) : osrsRealmLinkListRow
    data class Unavailable(val value: osrsRealmUnavailableLinkRow) : osrsRealmLinkListRow
    data object Empty : osrsRealmLinkListRow
}

private data class osrsRealmLinksInitialAdapterTiming(
    val rowConversionNanos: Long,
    val submissionNanos: Long
)

private class osrsRealmLinksAdapter(
    private val context: Context,
    private val onLinkSelected: (osrsRealmLinkRow) -> Unit
) : RecyclerView.Adapter<osrsRealmLinksAdapter.osrsRealmLinkViewHolder>() {
    private var rows: List<osrsRealmLinkListRow> = emptyList()

    fun setInitial(links: List<osrsRealmLinksRow>): osrsRealmLinksInitialAdapterTiming {
        check(rows.isEmpty()) { "Initial link rows may only be assigned once" }
        val conversionStarted = SystemClock.elapsedRealtimeNanos()
        val replacement = links.toAdapterRows()
        val rowConversionNanos = SystemClock.elapsedRealtimeNanos() - conversionStarted
        val submissionStarted = SystemClock.elapsedRealtimeNanos()
        rows = replacement
        val submissionNanos = SystemClock.elapsedRealtimeNanos() - submissionStarted
        return osrsRealmLinksInitialAdapterTiming(rowConversionNanos, submissionNanos)
    }

    fun submit(links: List<osrsRealmLinksRow>) {
        val replacement = links.toAdapterRows()
        val oldRows = rows
        rows = replacement
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldRows.size
            override fun getNewListSize(): Int = replacement.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldRows[oldItemPosition].identity() == replacement[newItemPosition].identity()

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldRows[oldItemPosition] == replacement[newItemPosition]
        }).dispatchUpdatesTo(this)
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is osrsRealmLinkListRow.Available -> OSRS_AVAILABLE_LINK_ROW
        is osrsRealmLinkListRow.Unavailable -> OSRS_UNAVAILABLE_LINK_ROW
        osrsRealmLinkListRow.Empty -> OSRS_EMPTY_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): osrsRealmLinkViewHolder {
        val view = if (viewType == OSRS_EMPTY_ROW) {
            TextView(context).apply {
                setPadding(dp(4), dp(24), dp(4), dp(24))
                gravity = Gravity.CENTER
                text = context.getString(R.string.no_link_search_results)
                textSize = 16f
                setTextColor(context.getColor(R.color.osrs_ink))
            }
        } else {
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = dp(64)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                isClickable = viewType == OSRS_AVAILABLE_LINK_ROW
                isFocusable = true
                if (viewType == OSRS_AVAILABLE_LINK_ROW) {
                    val attributes = context.obtainStyledAttributes(
                        intArrayOf(android.R.attr.selectableItemBackground)
                    )
                    background = attributes.getDrawable(0)
                    attributes.recycle()
                } else {
                    alpha = 0.82f
                }
                addView(TextView(context).apply {
                    tag = OSRS_PRIMARY_TEXT_TAG
                    textSize = 16f
                    maxLines = 2
                    setTextColor(context.getColor(R.color.osrs_ink))
                }, matchWrap())
                addView(TextView(context).apply {
                    tag = OSRS_SECONDARY_TEXT_TAG
                    textSize = 12f
                    maxLines = if (viewType == OSRS_UNAVAILABLE_LINK_ROW) 3 else 2
                    setTextColor(context.getColor(R.color.osrs_ink))
                }, matchWrap())
            }
        }
        return osrsRealmLinkViewHolder(view)
    }

    override fun onBindViewHolder(holder: osrsRealmLinkViewHolder, position: Int) {
        when (val row = rows[position]) {
            is osrsRealmLinkListRow.Available -> bindAvailableLink(
                holder.itemView as LinearLayout,
                row.value
            )
            is osrsRealmLinkListRow.Unavailable -> bindUnavailableLink(
                holder.itemView as LinearLayout,
                row.value
            )
            osrsRealmLinkListRow.Empty -> Unit
        }
    }

    private fun bindAvailableLink(view: LinearLayout, row: osrsRealmLinkRow) {
        view.findViewWithTag<TextView>(OSRS_PRIMARY_TEXT_TAG).text = row.visiblePrimary
        view.findViewWithTag<TextView>(OSRS_SECONDARY_TEXT_TAG).text = row.visibleSecondary
        view.contentDescription = row.accessibilityLabel
        view.setOnClickListener { onLinkSelected(row) }
    }

    private fun bindUnavailableLink(view: LinearLayout, row: osrsRealmUnavailableLinkRow) {
        view.findViewWithTag<TextView>(OSRS_PRIMARY_TEXT_TAG).text = row.visiblePrimary
        view.findViewWithTag<TextView>(OSRS_SECONDARY_TEXT_TAG).text = row.visibleSecondary
        view.contentDescription = row.accessibilityLabel
        view.setOnClickListener(null)
        view.isClickable = false
    }

    override fun getItemCount(): Int = rows.size

    class osrsRealmLinkViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val OSRS_AVAILABLE_LINK_ROW = 1
        private const val OSRS_UNAVAILABLE_LINK_ROW = 2
        private const val OSRS_EMPTY_ROW = 3
        private const val OSRS_PRIMARY_TEXT_TAG = "osrs-link-primary"
        private const val OSRS_SECONDARY_TEXT_TAG = "osrs-link-secondary"
    }
}

private fun List<osrsRealmLinksRow>.toAdapterRows(): List<osrsRealmLinkListRow> =
    if (isEmpty()) {
        listOf(osrsRealmLinkListRow.Empty)
    } else {
        map { row ->
            when (row) {
                is osrsRealmLinkRow -> osrsRealmLinkListRow.Available(row)
                is osrsRealmUnavailableLinkRow -> osrsRealmLinkListRow.Unavailable(row)
            }
        }
    }

private fun osrsRealmLinkListRow.identity(): String = when (this) {
    is osrsRealmLinkListRow.Available -> "available:${value.side.key}"
    is osrsRealmLinkListRow.Unavailable -> "unavailable:${value.link.id}"
    osrsRealmLinkListRow.Empty -> "empty"
}
