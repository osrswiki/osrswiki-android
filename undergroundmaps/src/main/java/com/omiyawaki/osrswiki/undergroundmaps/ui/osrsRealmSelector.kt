package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.omiyawaki.osrswiki.undergroundmaps.R
import com.omiyawaki.osrswiki.undergroundmaps.model.OSRS_REALM_GROUPS
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmRecord
import kotlin.math.roundToInt

internal const val OSRS_SELECTOR_HORIZONTAL_MARGIN_DP = 32
internal const val OSRS_SELECTOR_BOTTOM_GAP_DP = 16

internal data class osrsRealmSelectorRowStyle(
    val minimumHeightDp: Int,
    val highlightHorizontalInsetDp: Int,
    val highlightVerticalInsetDp: Int,
    val highlightCornerRadiusDp: Int,
    val drawsSelectionHighlight: Boolean
)

internal fun osrsRealmSelectorRowAppearance(
    selected: Boolean,
    compactLandscapeImeLayout: Boolean
): osrsRealmSelectorRowStyle = osrsRealmSelectorRowStyle(
    minimumHeightDp = if (compactLandscapeImeLayout) 48 else 56,
    highlightHorizontalInsetDp = 8,
    highlightVerticalInsetDp = if (compactLandscapeImeLayout) 2 else 4,
    highlightCornerRadiusDp = 14,
    drawsSelectionHighlight = selected
)

data class osrsRealmSelectorResult(
    val normalizedQuery: String,
    val sections: Map<String, List<osrsRealmRecord>>,
    val resultCount: Int,
    val evaluatedRealmCount: Int
)

/**
 * Immutable normalized search material plus the last narrowing result for the canonical selector.
 * This index is created with the manifest, outside the keystroke path, and reused for the lifetime
 * of the activity.
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

data class osrsRealmSelectorBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class osrsRealmSelectorHorizontalBounds(
    val left: Int,
    val right: Int
)

/**
 * Pure geometry for the selected Direction 3 relationship.
 *
 * Horizontal bounds never depend on expansion or IME state. Expansion grows upward from the
 * current active-viewport bottom. When the IME is visible its inset defines that bottom.
 */
object osrsRealmSelectorGeometry {
    fun calculateHorizontalBounds(
        viewportWidthPx: Int,
        horizontalMarginPx: Int,
        maximumWidthPx: Int
    ): osrsRealmSelectorHorizontalBounds {
        require(viewportWidthPx > 0)
        require(horizontalMarginPx >= 0 && maximumWidthPx > 0)
        val width = minOf(
            maximumWidthPx,
            (viewportWidthPx - horizontalMarginPx * 2).coerceAtLeast(1)
        )
        val left = (viewportWidthPx - width) / 2
        return osrsRealmSelectorHorizontalBounds(left = left, right = left + width)
    }

    fun calculate(
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        systemTopInsetPx: Int,
        systemBottomInsetPx: Int,
        imeBottomInsetPx: Int,
        imeVisible: Boolean,
        expanded: Boolean,
        collapsedHeightPx: Int,
        desiredExpandedHeightPx: Int,
        minimumExpandedHeightPx: Int,
        horizontalMarginPx: Int,
        maximumWidthPx: Int,
        bottomGapPx: Int,
        topObstructionPx: Int
    ): osrsRealmSelectorBounds {
        require(viewportWidthPx > 0 && viewportHeightPx > 0)
        require(collapsedHeightPx > 0)
        require(desiredExpandedHeightPx >= collapsedHeightPx)
        require(minimumExpandedHeightPx >= collapsedHeightPx)
        require(horizontalMarginPx >= 0 && maximumWidthPx > 0 && bottomGapPx >= 0)

        val horizontalBounds = calculateHorizontalBounds(
            viewportWidthPx = viewportWidthPx,
            horizontalMarginPx = horizontalMarginPx,
            maximumWidthPx = maximumWidthPx
        )
        val activeBottomInset = if (imeVisible) {
            maxOf(systemBottomInsetPx, imeBottomInsetPx)
        } else {
            systemBottomInsetPx
        }
        val bottom = (viewportHeightPx - activeBottomInset - bottomGapPx)
            .coerceAtLeast(systemTopInsetPx + collapsedHeightPx)
        val minimumTop = maxOf(
            systemTopInsetPx + bottomGapPx,
            topObstructionPx
        )
        val availableHeight = (bottom - minimumTop).coerceAtLeast(collapsedHeightPx)
        val height = if (expanded) {
            val desired = minOf(desiredExpandedHeightPx, availableHeight)
            desired.coerceAtLeast(minOf(minimumExpandedHeightPx, availableHeight))
        } else {
            minOf(collapsedHeightPx, availableHeight)
        }
        return osrsRealmSelectorBounds(
            left = horizontalBounds.left,
            top = bottom - height,
            right = horizontalBounds.right,
            bottom = bottom
        )
    }
}

data class osrsRealmSelectorDebugState(
    val expanded: Boolean,
    val imeVisible: Boolean,
    val searchFocused: Boolean,
    val query: String,
    val visibleResultCount: Int,
    val topObstructionPx: Int,
    val bounds: osrsRealmSelectorBounds?,
    val outsideDismissAvailable: Boolean,
    val baseRowTop: Int,
    val baseRowBottom: Int,
    val searchLeft: Int,
    val searchTop: Int,
    val searchRight: Int,
    val searchBottom: Int,
    val listLeft: Int,
    val listTop: Int,
    val listRight: Int,
    val listBottom: Int,
    val firstResultLeft: Int,
    val firstResultTop: Int,
    val firstResultRight: Int,
    val firstResultBottom: Int,
    val firstResultClickable: Boolean,
    val firstResultFocusable: Boolean,
    val firstResultText: String?,
    val firstResultAccessibilityText: String?
)

/**
 * One persistent map-overlay component for all selector states.
 *
 * The active-realm button is always the final row. Opening the picker reveals search and the
 * virtualized realm list above it without replacing the component or requesting search focus.
 */
class osrsRealmSelector(
    private val context: Context,
    private val host: FrameLayout,
    private val selectorIndex: osrsRealmSelectorIndex,
    initialActiveRealmId: String,
    initialVisibleName: String,
    initialAccessibilityName: String,
    private val onRealmSelected: (osrsRealmRecord) -> Unit,
    private val onFilterMeasured: (query: String, resultCount: Int, elapsedNanos: Long) -> Unit,
    private val onToggleMeasured: (expanded: Boolean, elapsedNanos: Long) -> Unit,
    private val onOutsideDismissMeasured: (elapsedNanos: Long) -> Unit,
    private val onExpandedChanged: (Boolean) -> Unit
) {
    private val density = context.resources.displayMetrics.density
    private var activeRealmId = initialActiveRealmId
    private var activeAccessibilityName = initialAccessibilityName
    private var expanded = false
    private var imeVisible = false
    private var lastBounds: osrsRealmSelectorBounds? = null
    private var systemTopInsetPx = 0
    private var systemBottomInsetPx = 0
    private var imeBottomInsetPx = 0
    private var topObstructionPx = 0
    private var visibleResultCount = 0
    private var compactLandscapeImeLayout = false

    private val adapter = osrsRealmSelectorAdapter(
        context = context,
        activeRealmId = initialActiveRealmId,
        realmPresentations = selectorIndex.realmPresentations
    ) { realm ->
        collapse(resetQuery = true)
        if (realm.id != activeRealmId) onRealmSelected(realm)
    }

    val outsideDismissTarget: View = View(context).apply {
        id = R.id.osrs_realm_selector_outside_dismiss
        setBackgroundColor(Color.TRANSPARENT)
        visibility = View.GONE
        isEnabled = false
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setOnClickListener {
            if (!expanded) return@setOnClickListener
            val started = SystemClock.elapsedRealtimeNanos()
            collapse(resetQuery = false)
            onOutsideDismissMeasured(SystemClock.elapsedRealtimeNanos() - started)
        }
        isEnabled = false
        isClickable = false
    }

    val surface: MaterialCardView = MaterialCardView(context).apply {
        id = R.id.osrs_realm_selector_surface
        radius = dp(20).toFloat()
        cardElevation = dp(8).toFloat()
        strokeWidth = dp(1)
        strokeColor = context.getColor(R.color.osrs_underground_parchment_dark)
        setCardBackgroundColor(context.getColor(R.color.osrs_map_control_surface))
        isFocusableInTouchMode = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val content = osrsRealmSelectorContentLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
    }

    val search: EditText = EditText(context).apply {
        id = R.id.osrs_selector_search
        hint = context.getString(R.string.realm_search_hint)
        contentDescription = context.getString(R.string.realm_search_description)
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setTextColor(context.getColor(R.color.osrs_map_control_ink))
        setHintTextColor(context.getColor(R.color.osrs_map_control_disabled))
        background = ContextCompat.getDrawable(context, R.drawable.osrs_selector_search_background)
        setCompoundDrawablesRelativeWithIntrinsicBounds(
            R.drawable.osrs_ic_search,
            0,
            0,
            0
        )
        compoundDrawablePadding = dp(10)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        gravity = Gravity.CENTER_VERTICAL
        minWidth = dp(OSRS_MINIMUM_TOUCH_TARGET_DP)
        minimumWidth = dp(OSRS_MINIMUM_TOUCH_TARGET_DP)
        minHeight = dp(OSRS_MINIMUM_TOUCH_TARGET_DP)
        minimumHeight = dp(OSRS_MINIMUM_TOUCH_TARGET_DP)
        visibility = View.GONE
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                windowInsetsController()?.show(WindowInsetsCompat.Type.ime())
            }
        }
    }

    val list: RecyclerView = RecyclerView(context).apply {
        id = R.id.osrs_selector_list
        layoutManager = LinearLayoutManager(context)
        adapter = this@osrsRealmSelector.adapter
        contentDescription = context.getString(R.string.realm_selector_results)
        isVerticalScrollBarEnabled = true
        itemAnimator = null
        setHasFixedSize(false)
        visibility = View.GONE
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }

    private val baseRow = FrameLayout(context)

    val baseButton: MaterialButton = MaterialButton(
        context,
        null,
        com.google.android.material.R.attr.materialButtonStyle
    ).apply {
        id = R.id.osrs_realm_selector
        text = initialVisibleName
        isAllCaps = false
        textSize = 17f
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        icon = ContextCompat.getDrawable(context, R.drawable.osrs_ic_globe)
        iconTint = ColorStateList.valueOf(context.getColor(R.color.osrs_map_control_ink))
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        iconPadding = dp(12)
        setTextColor(context.getColor(R.color.osrs_map_control_ink))
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        insetTop = 0
        insetBottom = 0
        minHeight = dp(58)
        minimumHeight = dp(58)
        setPaddingRelative(dp(16), dp(8), dp(52), dp(8))
        osrsApplyRealmIdentityLayout()
        setOnClickListener { this@osrsRealmSelector.toggle() }
    }

    private val chevron = ImageView(context).apply {
        id = R.id.osrs_realm_selector_chevron
        setImageResource(R.drawable.osrs_ic_chevron_up)
        contentDescription = null
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    init {
        content.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dp(12)
            topMargin = dp(12)
            rightMargin = dp(12)
            bottomMargin = dp(8)
        })
        content.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        content.addView(View(context).apply {
            setBackgroundColor(context.getColor(R.color.osrs_map_control_divider))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1)
        ))
        baseRow.addView(baseButton, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL
        ))
        baseRow.addView(chevron, FrameLayout.LayoutParams(
            dp(48),
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.END or Gravity.CENTER_VERTICAL
        ).apply { marginEnd = dp(4) })
        content.addView(baseRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        surface.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        host.addView(outsideDismissTarget, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        host.addView(surface, FrameLayout.LayoutParams(
            dp(OSRS_SELECTOR_MAX_WIDTH_DP),
            dp(OSRS_SELECTOR_COLLAPSED_MIN_HEIGHT_DP),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ))

        search.doAfterTextChanged { editable ->
            filter(editable?.toString().orEmpty())
        }
        filter("")
        updateBaseAccessibility()
        surface.post {
            surface.requestFocus()
            updateLayout()
        }
    }

    fun toggle() {
        val started = SystemClock.elapsedRealtimeNanos()
        if (expanded) collapse(resetQuery = false) else expand()
        onToggleMeasured(expanded, SystemClock.elapsedRealtimeNanos() - started)
    }

    fun expand() {
        if (expanded) return
        expanded = true
        search.visibility = View.VISIBLE
        list.visibility = View.VISIBLE
        updateOutsideDismissTarget()
        surface.requestFocus()
        chevron.setImageResource(R.drawable.osrs_ic_chevron_down)
        updateBaseAccessibility()
        updateLayout()
        onExpandedChanged(true)
    }

    fun collapse(resetQuery: Boolean) {
        if (!expanded && !search.hasFocus()) return
        expanded = false
        search.clearFocus()
        windowInsetsController()?.hide(WindowInsetsCompat.Type.ime())
        if (resetQuery && search.text.isNotEmpty()) search.setText("")
        search.visibility = View.GONE
        list.visibility = View.GONE
        updateOutsideDismissTarget()
        surface.requestFocus()
        chevron.setImageResource(R.drawable.osrs_ic_chevron_up)
        updateBaseAccessibility()
        updateLayout()
        onExpandedChanged(false)
    }

    fun hideImeWithoutCollapsing() {
        if (!expanded) return
        search.clearFocus()
        surface.requestFocus()
        windowInsetsController()?.hide(WindowInsetsCompat.Type.ime())
    }

    fun focusSearch(): Boolean {
        if (!expanded) expand()
        val focused = search.requestFocus()
        if (focused) {
            windowInsetsController()?.show(WindowInsetsCompat.Type.ime())
        }
        return focused
    }

    fun updateActiveRealm(
        realmId: String,
        visibleName: String,
        accessibilityName: String
    ) {
        activeRealmId = realmId
        activeAccessibilityName = accessibilityName
        baseButton.text = visibleName
        adapter.updateActiveRealm(realmId)
        updateBaseAccessibility()
        surface.post(::updateLayout)
    }

    fun projectedHorizontalBounds(viewportWidthPx: Int): osrsRealmSelectorHorizontalBounds =
        osrsRealmSelectorGeometry.calculateHorizontalBounds(
            viewportWidthPx = viewportWidthPx,
            horizontalMarginPx = dp(OSRS_SELECTOR_HORIZONTAL_MARGIN_DP),
            maximumWidthPx = dp(OSRS_SELECTOR_MAX_WIDTH_DP)
        )

    fun updateWindowGeometry(
        systemTopInsetPx: Int,
        systemBottomInsetPx: Int,
        imeBottomInsetPx: Int,
        imeVisible: Boolean,
        topObstructionPx: Int
    ) {
        val imeWasVisible = this.imeVisible
        this.systemTopInsetPx = systemTopInsetPx
        this.systemBottomInsetPx = systemBottomInsetPx
        this.imeBottomInsetPx = imeBottomInsetPx
        this.imeVisible = imeVisible
        this.topObstructionPx = topObstructionPx
        if (imeWasVisible && !imeVisible && expanded && search.hasFocus()) {
            search.clearFocus()
            surface.requestFocus()
        }
        updateLayout()
    }

    fun restore(expanded: Boolean, query: String, searchFocused: Boolean) {
        if (expanded) {
            expand()
            if (search.text.toString() != query) {
                search.setText(query)
                search.setSelection(search.text.length)
            }
            if (searchFocused) search.post(::focusSearch)
        } else {
            collapse(resetQuery = true)
        }
    }

    fun release() {
        windowInsetsController()?.hide(WindowInsetsCompat.Type.ime())
    }

    fun debugState(): osrsRealmSelectorDebugState {
        val firstResult = firstVisibleResult()
        return osrsRealmSelectorDebugState(
            expanded = expanded,
            imeVisible = imeVisible,
            searchFocused = search.hasFocus(),
            query = search.text.toString(),
            visibleResultCount = visibleResultCount,
            topObstructionPx = topObstructionPx,
            bounds = lastBounds,
            outsideDismissAvailable = outsideDismissTarget.visibility == View.VISIBLE &&
                outsideDismissTarget.isEnabled &&
                outsideDismissTarget.isClickable,
            baseRowTop = baseRow.top + surface.top,
            baseRowBottom = baseRow.bottom + surface.top,
            searchLeft = if (search.visibility == View.VISIBLE) search.left + surface.left else -1,
            searchTop = if (search.visibility == View.VISIBLE) search.top + surface.top else -1,
            searchRight = if (search.visibility == View.VISIBLE) search.right + surface.left else -1,
            searchBottom = if (search.visibility == View.VISIBLE) search.bottom + surface.top else -1,
            listLeft = if (list.visibility == View.VISIBLE) list.left + surface.left else -1,
            listTop = if (list.visibility == View.VISIBLE) list.top + surface.top else -1,
            listRight = if (list.visibility == View.VISIBLE) list.right + surface.left else -1,
            listBottom = if (list.visibility == View.VISIBLE) list.bottom + surface.top else -1,
            firstResultLeft = firstResult?.effectiveBounds?.left ?: -1,
            firstResultTop = firstResult?.effectiveBounds?.top ?: -1,
            firstResultRight = firstResult?.effectiveBounds?.right ?: -1,
            firstResultBottom = firstResult?.effectiveBounds?.bottom ?: -1,
            firstResultClickable = firstResult?.view?.isClickable == true,
            firstResultFocusable = firstResult?.view?.isFocusable == true,
            firstResultText = (firstResult?.view as? TextView)?.text?.toString(),
            firstResultAccessibilityText = firstResult?.view?.contentDescription?.toString()
        )
    }

    private fun updateOutsideDismissTarget() {
        outsideDismissTarget.visibility = if (expanded) View.VISIBLE else View.GONE
        outsideDismissTarget.isEnabled = expanded
        outsideDismissTarget.isClickable = expanded
    }

    private fun firstVisibleResult(): osrsVisibleRealmResult? {
        if (list.visibility != View.VISIBLE || list.width <= 0 || list.height <= 0) return null
        val manager = list.layoutManager as? LinearLayoutManager ?: return null
        val position = manager.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) return null
        val child = manager.findViewByPosition(position) ?: return null
        val clipped = Rect(
            child.left.coerceAtLeast(0),
            child.top.coerceAtLeast(0),
            child.right.coerceAtMost(list.width),
            child.bottom.coerceAtMost(list.height)
        )
        if (clipped.isEmpty) return null
        clipped.offset(list.left + surface.left, list.top + surface.top)
        return osrsVisibleRealmResult(view = child, effectiveBounds = clipped)
    }

    private fun filter(query: String) {
        val started = SystemClock.elapsedRealtimeNanos()
        val result = selectorIndex.filter(query)
        adapter.submit(result.sections)
        visibleResultCount = result.resultCount
        onFilterMeasured(
            query,
            result.resultCount,
            SystemClock.elapsedRealtimeNanos() - started
        )
    }

    private fun updateBaseAccessibility() {
        baseButton.contentDescription = context.getString(
            if (expanded) R.string.realm_selector_collapse else R.string.realm_selector_expand,
            activeAccessibilityName
        )
    }

    private fun updateLayout() {
        val viewportWidth = host.width
        val viewportHeight = host.height
        if (viewportWidth <= 0 || viewportHeight <= 0) return

        applyCompactLandscapeImeLayout(
            compact = imeVisible && viewportWidth > viewportHeight
        )
        val maximumWidthPx = dp(OSRS_SELECTOR_MAX_WIDTH_DP)
        val expectedWidth = minOf(
            maximumWidthPx,
            viewportWidth - dp(OSRS_SELECTOR_HORIZONTAL_MARGIN_DP) * 2
        ).coerceAtLeast(1)
        baseButton.measure(
            View.MeasureSpec.makeMeasureSpec(expectedWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val collapsedHeight = maxOf(
            dp(OSRS_SELECTOR_COLLAPSED_MIN_HEIGHT_DP),
            baseButton.measuredHeight + dp(1)
        )
        val activeViewportHeight = viewportHeight - if (imeVisible) {
            maxOf(systemBottomInsetPx, imeBottomInsetPx)
        } else {
            systemBottomInsetPx
        }
        val desiredExpandedHeight = minOf(
            dp(OSRS_SELECTOR_EXPANDED_MAX_HEIGHT_DP),
            (activeViewportHeight * OSRS_SELECTOR_EXPANDED_VIEWPORT_FRACTION).roundToInt()
        ).coerceAtLeast(collapsedHeight)
        val searchParams = search.layoutParams as LinearLayout.LayoutParams
        val searchWidth = (
            expectedWidth - searchParams.leftMargin - searchParams.rightMargin
            ).coerceAtLeast(1)
        search.measure(
            View.MeasureSpec.makeMeasureSpec(searchWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val requiredSearchHeight = maxOf(
            search.measuredHeight,
            dp(OSRS_MINIMUM_TOUCH_TARGET_DP)
        ) + searchParams.topMargin + searchParams.bottomMargin
        val minimumExpandedHeight = collapsedHeight +
            requiredSearchHeight +
            dp(OSRS_SELECTOR_MINIMUM_LIST_HEIGHT_DP)
        val bounds = osrsRealmSelectorGeometry.calculate(
            viewportWidthPx = viewportWidth,
            viewportHeightPx = viewportHeight,
            systemTopInsetPx = systemTopInsetPx,
            systemBottomInsetPx = systemBottomInsetPx,
            imeBottomInsetPx = imeBottomInsetPx,
            imeVisible = imeVisible,
            expanded = expanded,
            collapsedHeightPx = collapsedHeight,
            desiredExpandedHeightPx = desiredExpandedHeight,
            minimumExpandedHeightPx = minimumExpandedHeight,
            horizontalMarginPx = dp(OSRS_SELECTOR_HORIZONTAL_MARGIN_DP),
            maximumWidthPx = maximumWidthPx,
            bottomGapPx = dp(OSRS_SELECTOR_BOTTOM_GAP_DP),
            topObstructionPx = topObstructionPx
        )
        lastBounds = bounds
        val params = surface.layoutParams as FrameLayout.LayoutParams
        val expectedGravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        if (
            params.width != bounds.width ||
            params.height != bounds.height ||
            params.gravity != expectedGravity ||
            params.topMargin != bounds.top
        ) {
            surface.layoutParams = params.apply {
                width = bounds.width
                height = bounds.height
                gravity = expectedGravity
                topMargin = bounds.top
            }
        }
    }

    /**
     * A non-extract landscape IME can leave less vertical room than three stacked 48 dp targets.
     * The single selector therefore uses one compact top band: search remains actionable on the
     * left, at least one virtualized result remains actionable on the right, and the active-realm
     * row remains the full-width final row. Dismissing the IME restores the regular stacked list.
     */
    private fun applyCompactLandscapeImeLayout(compact: Boolean) {
        if (compactLandscapeImeLayout == compact) return
        compactLandscapeImeLayout = compact
        content.compactLandscapeImeLayout = compact
        adapter.updateCompactLandscapeImeLayout(compact)

        (search.layoutParams as LinearLayout.LayoutParams).apply {
            leftMargin = dp(if (compact) 4 else OSRS_SELECTOR_SEARCH_HORIZONTAL_MARGIN_DP)
            topMargin = dp(if (compact) 0 else OSRS_SELECTOR_SEARCH_TOP_MARGIN_DP)
            rightMargin = dp(if (compact) 4 else OSRS_SELECTOR_SEARCH_HORIZONTAL_MARGIN_DP)
            bottomMargin = dp(if (compact) 0 else OSRS_SELECTOR_SEARCH_BOTTOM_MARGIN_DP)
            search.layoutParams = this
        }
        search.setPadding(
            dp(14),
            dp(if (compact) 0 else OSRS_SELECTOR_SEARCH_VERTICAL_PADDING_DP),
            dp(14),
            dp(if (compact) 0 else OSRS_SELECTOR_SEARCH_VERTICAL_PADDING_DP)
        )
        val baseMinimumHeight = dp(
            if (compact) OSRS_MINIMUM_TOUCH_TARGET_DP else OSRS_SELECTOR_COLLAPSED_MIN_HEIGHT_DP
        )
        baseButton.minHeight = baseMinimumHeight
        baseButton.minimumHeight = baseMinimumHeight
        baseButton.setPaddingRelative(
            dp(16),
            dp(if (compact) 0 else OSRS_SELECTOR_BASE_VERTICAL_PADDING_DP),
            dp(52),
            dp(if (compact) 0 else OSRS_SELECTOR_BASE_VERTICAL_PADDING_DP)
        )
        baseButton.maxLines = if (compact) 1 else OSRS_REALM_IDENTITY_MAX_LINES
        surface.requestLayout()
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private fun windowInsetsController(): WindowInsetsControllerCompat? =
        (context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, search) }

    private companion object {
        const val OSRS_SELECTOR_MAX_WIDTH_DP = 560
        const val OSRS_SELECTOR_COLLAPSED_MIN_HEIGHT_DP = 58
        const val OSRS_SELECTOR_EXPANDED_MAX_HEIGHT_DP = 440
        const val OSRS_SELECTOR_MINIMUM_LIST_HEIGHT_DP = 72
        const val OSRS_SELECTOR_EXPANDED_VIEWPORT_FRACTION = 0.52f
        const val OSRS_SELECTOR_SEARCH_HORIZONTAL_MARGIN_DP = 12
        const val OSRS_SELECTOR_SEARCH_TOP_MARGIN_DP = 12
        const val OSRS_SELECTOR_SEARCH_BOTTOM_MARGIN_DP = 8
        const val OSRS_SELECTOR_SEARCH_VERTICAL_PADDING_DP = 8
        const val OSRS_SELECTOR_BASE_VERTICAL_PADDING_DP = 8
        const val OSRS_MINIMUM_TOUCH_TARGET_DP = 48
    }
}

private data class osrsVisibleRealmResult(
    val view: View,
    val effectiveBounds: Rect
)

/**
 * Retains the regular vertical selector everywhere except the physically constrained landscape
 * IME state. In that state the search and virtualized results share a non-overlapping top band,
 * leaving the active-realm row full-width and final without changing the selector surface bounds.
 */
private class osrsRealmSelectorContentLayout(context: Context) : LinearLayout(context) {
    var compactLandscapeImeLayout: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (
            !compactLandscapeImeLayout ||
            childCount != OSRS_EXPECTED_CHILD_COUNT ||
            getChildAt(OSRS_SEARCH_INDEX).visibility != View.VISIBLE ||
            getChildAt(OSRS_LIST_INDEX).visibility != View.VISIBLE ||
            MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY ||
            MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY
        ) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val search = getChildAt(OSRS_SEARCH_INDEX)
        val list = getChildAt(OSRS_LIST_INDEX)
        val divider = getChildAt(OSRS_DIVIDER_INDEX)
        val base = getChildAt(OSRS_BASE_INDEX)

        measureChildWithMargins(base, widthMeasureSpec, 0, heightMeasureSpec, 0)
        val dividerParams = divider.layoutParams as ViewGroup.MarginLayoutParams
        divider.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dividerParams.height.coerceAtLeast(0), MeasureSpec.EXACTLY)
        )
        val topBandHeight = (
            height -
                base.measuredHeight -
                divider.measuredHeight
            ).coerceAtLeast(0)
        val searchParams = search.layoutParams as ViewGroup.MarginLayoutParams
        val minimumTarget = dp(OSRS_MINIMUM_TOUCH_TARGET_DP)
        val minimumSearchPane = minimumTarget + searchParams.leftMargin + searchParams.rightMargin
        val minimumListPane = minimumTarget
        val desiredSplit = (width * OSRS_COMPACT_SEARCH_WIDTH_FRACTION).roundToInt()
        val split = if (width >= minimumSearchPane + minimumListPane) {
            desiredSplit.coerceIn(minimumSearchPane, width - minimumListPane)
        } else {
            width / 2
        }
        val searchWidth = (
            split - searchParams.leftMargin - searchParams.rightMargin
            ).coerceAtLeast(0)
        val searchHeight = (
            topBandHeight - searchParams.topMargin - searchParams.bottomMargin
            ).coerceAtLeast(0)
        search.measure(
            MeasureSpec.makeMeasureSpec(searchWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(searchHeight, MeasureSpec.EXACTLY)
        )
        list.measure(
            MeasureSpec.makeMeasureSpec((width - split).coerceAtLeast(0), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(
                minOf(topBandHeight, minimumTarget),
                MeasureSpec.EXACTLY
            )
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (
            !compactLandscapeImeLayout ||
            childCount != OSRS_EXPECTED_CHILD_COUNT ||
            getChildAt(OSRS_SEARCH_INDEX).visibility != View.VISIBLE ||
            getChildAt(OSRS_LIST_INDEX).visibility != View.VISIBLE
        ) {
            super.onLayout(changed, left, top, right, bottom)
            return
        }

        val width = right - left
        val height = bottom - top
        val search = getChildAt(OSRS_SEARCH_INDEX)
        val list = getChildAt(OSRS_LIST_INDEX)
        val divider = getChildAt(OSRS_DIVIDER_INDEX)
        val base = getChildAt(OSRS_BASE_INDEX)
        val baseTop = (height - base.measuredHeight).coerceAtLeast(0)
        val dividerTop = (baseTop - divider.measuredHeight).coerceAtLeast(0)
        val searchParams = search.layoutParams as ViewGroup.MarginLayoutParams
        val split = width - list.measuredWidth

        search.layout(
            searchParams.leftMargin,
            searchParams.topMargin,
            split - searchParams.rightMargin,
            dividerTop - searchParams.bottomMargin
        )
        list.layout(split, 0, width, list.measuredHeight)
        divider.layout(0, dividerTop, width, baseTop)
        base.layout(0, baseTop, width, height)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val OSRS_SEARCH_INDEX = 0
        const val OSRS_LIST_INDEX = 1
        const val OSRS_DIVIDER_INDEX = 2
        const val OSRS_BASE_INDEX = 3
        const val OSRS_EXPECTED_CHILD_COUNT = 4
        const val OSRS_MINIMUM_TOUCH_TARGET_DP = 48
        const val OSRS_COMPACT_SEARCH_WIDTH_FRACTION = 0.60f
    }
}

private class osrsRealmSelectorAdapter(
    private val context: Context,
    activeRealmId: String,
    private val realmPresentations: osrsRealmPresentationCatalog,
    private val onRealmSelected: (osrsRealmRecord) -> Unit
) : RecyclerView.Adapter<osrsRealmSelectorAdapter.osrsSelectorViewHolder>() {
    private var activeRealmId = activeRealmId
    private var realms: List<osrsRealmRecord> = emptyList()
    private var compactLandscapeImeLayout = false

    fun submit(sections: Map<String, List<osrsRealmRecord>>) {
        val replacement = buildList {
            OSRS_REALM_GROUPS.forEach { group ->
                addAll(sections[group].orEmpty())
            }
        }
        if (replacement == realms) return
        val priorSize = realms.size
        realms = replacement
        val sharedSize = minOf(priorSize, replacement.size)
        if (sharedSize > 0) notifyItemRangeChanged(0, sharedSize)
        when {
            replacement.size > priorSize ->
                notifyItemRangeInserted(priorSize, replacement.size - priorSize)
            replacement.size < priorSize ->
                notifyItemRangeRemoved(replacement.size, priorSize - replacement.size)
        }
    }

    fun updateActiveRealm(realmId: String) {
        if (activeRealmId == realmId) return
        val priorPosition = realms.indexOfFirst { it.id == activeRealmId }
        activeRealmId = realmId
        val nextPosition = realms.indexOfFirst { it.id == activeRealmId }
        if (priorPosition >= 0) notifyItemChanged(priorPosition)
        if (nextPosition >= 0 && nextPosition != priorPosition) notifyItemChanged(nextPosition)
    }

    fun updateCompactLandscapeImeLayout(compact: Boolean) {
        if (compactLandscapeImeLayout == compact) return
        compactLandscapeImeLayout = compact
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): osrsSelectorViewHolder {
        val view = TextView(context).apply {
            id = R.id.osrs_selector_result
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            minHeight = dp(56)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
            textSize = 16f
            osrsApplyRealmIdentityLayout()
            setTextColor(context.getColor(R.color.osrs_map_control_ink))
            isClickable = true
            isFocusable = true
        }
        return osrsSelectorViewHolder(view)
    }

    override fun onBindViewHolder(holder: osrsSelectorViewHolder, position: Int) {
        val realm = realms[position]
        val presentation = realmPresentations[realm]
        val selected = realm.id == activeRealmId
        (holder.itemView as TextView).apply {
            val appearance = osrsRealmSelectorRowAppearance(
                selected = selected,
                compactLandscapeImeLayout = compactLandscapeImeLayout
            )
            val minimumHeightPx = dp(appearance.minimumHeightDp)
            minHeight = minimumHeightPx
            minimumHeight = minimumHeightPx
            background = osrsRealmSelectorRowBackground(appearance)
            setPadding(
                dp(18),
                dp(if (compactLandscapeImeLayout) 0 else 10),
                dp(18),
                dp(if (compactLandscapeImeLayout) 0 else 10)
            )
            maxLines = if (compactLandscapeImeLayout) 1 else OSRS_REALM_IDENTITY_MAX_LINES
            text = presentation.visibleName
            isSelected = selected
            alpha = if (selected) 1f else 0.9f
            contentDescription = presentation.selectorAccessibilityLabel(selected)
            setOnClickListener { onRealmSelected(realm) }
        }
    }

    override fun getItemCount(): Int = realms.size

    class osrsSelectorViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private fun osrsRealmSelectorRowBackground(
        appearance: osrsRealmSelectorRowStyle
    ): RippleDrawable {
        fun rounded(color: Int): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(appearance.highlightCornerRadiusDp).toFloat()
            setColor(color)
        }

        val horizontalInset = dp(appearance.highlightHorizontalInsetDp)
        val verticalInset = dp(appearance.highlightVerticalInsetDp)
        val content = InsetDrawable(
            rounded(
                if (appearance.drawsSelectionHighlight) {
                    context.getColor(R.color.osrs_map_control_selected)
                } else {
                    Color.TRANSPARENT
                }
            ),
            horizontalInset,
            verticalInset,
            horizontalInset,
            verticalInset
        )
        val mask = InsetDrawable(
            rounded(Color.WHITE),
            horizontalInset,
            verticalInset,
            horizontalInset,
            verticalInset
        )
        val highlight = TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.colorControlHighlight, it, true)
        }
        val rippleColors = if (highlight.resourceId != 0) {
            context.getColorStateList(highlight.resourceId)
        } else {
            ColorStateList.valueOf(highlight.data)
        }
        return RippleDrawable(rippleColors, content, mask)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
