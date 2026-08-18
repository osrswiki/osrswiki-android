package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
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
    val visibleBoundRowCount: Int,
    val explicitOsrsPalette: Boolean,
    val titleTextColor: Int,
    val summaryTextColor: Int,
    val searchTextColor: Int,
    val searchHintColor: Int,
    val searchMinimumHeightPx: Int,
    val searchWidthPx: Int,
    val searchHeightPx: Int,
    val searchFocused: Boolean,
    val compactLandscapeImeChrome: Boolean,
    val listBackgroundResource: Int
)

class osrsRealmLinksDialog(
    private val context: Context,
    private val links: osrsRealmLinkCatalog,
    private val onLinkSelected: (osrsRealmLinkRow) -> Unit,
    private val onFilterMeasured: (query: String, resultCount: Int, elapsedNanos: Long) -> Unit
) {
    private val dialog: BottomSheetDialog
    private val root: LinearLayout
    private val title: TextView
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
    private var compactLandscapeImeChrome = false

    val realmId: String
        get() = links.currentRealm.id

    init {
        var viewConstructionNanos = 0L
        var viewSegmentStarted = SystemClock.elapsedRealtimeNanos()
        val builtDialog = BottomSheetDialog(context)
        val builtRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
            background = ContextCompat.getDrawable(
                context,
                R.drawable.osrs_links_sheet_background
            )
        }
        val builtTitle = TextView(context).apply {
            text = context.getString(R.string.realm_links_dialog_title)
            textSize = 22f
            setTextColor(context.getColor(R.color.osrs_parchment))
            contentDescription = text
        }
        builtRoot.addView(builtTitle, matchWrap())

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
            setTextColor(context.getColor(R.color.osrs_underground_parchment_dark))
            setPadding(0, dp(8), 0, dp(4))
            contentDescription = text
        }
        builtRoot.addView(builtSummary, matchWrap())

        val builtSearch = EditText(context).apply {
            id = R.id.osrs_links_search
            hint = context.getString(R.string.realm_link_search_hint)
            contentDescription = hint
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            minHeight = dp(OSRS_LINK_MINIMUM_TOUCH_TARGET_DP)
            minimumHeight = dp(OSRS_LINK_MINIMUM_TOUCH_TARGET_DP)
            setTextColor(context.getColor(R.color.osrs_parchment))
            setHintTextColor(context.getColor(R.color.osrs_underground_parchment_dark))
            background = ContextCompat.getDrawable(
                context,
                R.drawable.osrs_links_search_background
            )
            backgroundTintList = null
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        builtRoot.addView(builtSearch, matchWrap().apply {
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
            setBackgroundColor(context.getColor(R.color.osrs_map_control_surface))
            addItemDecoration(
                DividerItemDecoration(context, DividerItemDecoration.VERTICAL).apply {
                    setDrawable(
                        requireNotNull(
                            ContextCompat.getDrawable(context, R.drawable.osrs_link_divider)
                        )
                    )
                }
            )
        }
        builtRoot.addView(builtList, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        builtSearch.doAfterTextChanged {
            applyFilter(it?.toString().orEmpty(), report = reportFilterChanges)
        }
        val updateCompactLandscapeImeChrome = { imeVisible: Boolean ->
            val shouldCompact =
                context.resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE &&
                    imeVisible
            if (compactLandscapeImeChrome != shouldCompact) {
                compactLandscapeImeChrome = shouldCompact
                builtTitle.visibility =
                    if (compactLandscapeImeChrome) View.GONE else View.VISIBLE
                builtSummary.visibility =
                    if (compactLandscapeImeChrome) View.GONE else View.VISIBLE
                builtRoot.setPadding(
                    dp(20),
                    dp(if (compactLandscapeImeChrome) 6 else 18),
                    dp(20),
                    dp(if (compactLandscapeImeChrome) 6 else 12)
                )
                builtRoot.post {
                    builtRoot.requestLayout()
                    builtList.requestLayout()
                    (builtRoot.parent as? View)?.requestLayout()
                }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(builtRoot) { _, insets ->
            updateCompactLandscapeImeChrome(
                insets.isVisible(WindowInsetsCompat.Type.ime())
            )
            insets
        }
        builtRoot.viewTreeObserver.addOnGlobalLayoutListener {
            val visibleFrame = Rect()
            builtRoot.rootView.getWindowVisibleDisplayFrame(visibleFrame)
            val obscuredHeight =
                context.resources.displayMetrics.heightPixels - visibleFrame.bottom
            val imeVisible =
                ViewCompat.getRootWindowInsets(builtRoot)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            updateCompactLandscapeImeChrome(
                imeVisible || obscuredHeight > dp(100)
            )
        }
        builtSearch.setOnFocusChangeListener { _, _ ->
            builtRoot.post {
                ViewCompat.requestApplyInsets(builtRoot)
            }
        }

        builtDialog.setContentView(builtRoot)
        builtDialog.setOnShowListener {
            val bottomSheet = builtDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.background = ContextCompat.getDrawable(
                context,
                R.drawable.osrs_links_sheet_background
            )
            bottomSheet?.backgroundTintList = ColorStateList.valueOf(
                context.getColor(R.color.osrs_map_control_surface)
            )
            bottomSheet?.layoutParams = bottomSheet?.layoutParams?.apply {
                height = (context.resources.displayMetrics.heightPixels * 0.88f).toInt()
            }
            builtDialog.window?.navigationBarColor =
                context.getColor(R.color.osrs_map_control_surface)
            builtDialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            builtDialog.behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
            builtList.requestFocus()
            ViewCompat.requestApplyInsets(builtRoot)
        }
        builtDialog.setOnDismissListener { resetAfterDismiss() }
        viewConstructionNanos += SystemClock.elapsedRealtimeNanos() - viewSegmentStarted

        dialog = builtDialog
        root = builtRoot
        title = builtTitle
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
            visibleBoundRowCount = list.childCount,
            explicitOsrsPalette =
                root.background != null &&
                    search.background != null &&
                    list.background != null,
            titleTextColor = title.currentTextColor,
            summaryTextColor = summary.currentTextColor,
            searchTextColor = search.currentTextColor,
            searchHintColor = search.currentHintTextColor,
            searchMinimumHeightPx = search.minimumHeight,
            searchWidthPx = search.width,
            searchHeightPx = search.height,
            searchFocused = search.hasFocus(),
            compactLandscapeImeChrome = compactLandscapeImeChrome,
            listBackgroundResource = R.color.osrs_map_control_surface
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
                setTextColor(context.getColor(R.color.osrs_parchment))
                background = ContextCompat.getDrawable(
                    context,
                    R.drawable.osrs_link_row_background
                )
            }
        } else {
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = dp(64)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                isClickable = viewType == OSRS_AVAILABLE_LINK_ROW
                isFocusable = true
                background = ContextCompat.getDrawable(
                    context,
                    R.drawable.osrs_link_row_background
                )
                addView(TextView(context).apply {
                    tag = OSRS_PRIMARY_TEXT_TAG
                    textSize = 16f
                    maxLines = 2
                    setTextColor(context.getColor(R.color.osrs_parchment))
                }, matchWrap())
                addView(TextView(context).apply {
                    tag = OSRS_SECONDARY_TEXT_TAG
                    textSize = 12f
                    maxLines = if (viewType == OSRS_UNAVAILABLE_LINK_ROW) 3 else 2
                    setTextColor(context.getColor(R.color.osrs_underground_parchment_dark))
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

private const val OSRS_LINK_MINIMUM_TOUCH_TARGET_DP = 48

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
