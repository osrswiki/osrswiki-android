package com.omiyawaki.osrswiki.settings

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.util.log.L
import kotlin.math.max

/**
 * A responsive layout manager for theme selection that automatically switches between:
 * - Vertical stacking (1 column) for narrow screens
 * - Horizontal grid (2-3 columns) for wide screens
 * 
 * Determines layout based on available screen width and switches card layouts accordingly.
 */
class ResponsiveThemeLayoutManager(
    private val context: Context,
    private val onLayoutModeChanged: (isHorizontal: Boolean) -> Unit
) : GridLayoutManager(context, 1) {
    
    companion object {
        private const val MIN_CARD_WIDTH_DP = 90 // iOS card width (90dp)
        private const val HORIZONTAL_THRESHOLD_DP = 300 // Very low threshold - almost always horizontal like iOS
        private const val MAX_COLUMNS = 3 // Maximum number of columns in horizontal layout
    }
    
    private var currentIsHorizontal: Boolean = false
    private var lastScreenWidth: Int = 0

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
        
        // Check if we need to recalculate layout after layout completion
        val currentScreenWidth = getScreenWidthDp()
        if (currentScreenWidth != lastScreenWidth) {
            calculateAndUpdateSpanCount()
            lastScreenWidth = currentScreenWidth
        }
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        // Calculate optimal span count before laying out children
        calculateAndUpdateSpanCount()
        super.onLayoutChildren(recycler, state)
    }

    /**
     * Calculates the optimal number of columns based on screen width and updates the span count.
     * Also notifies the adapter about layout mode changes.
     */
    private fun calculateAndUpdateSpanCount() {
        val screenWidthDp = getScreenWidthDp()
        val newSpanCount = calculateOptimalSpanCount(screenWidthDp)
        val newIsHorizontal = newSpanCount > 1
        
        if (spanCount != newSpanCount) {
            spanCount = newSpanCount
            L.d("ResponsiveThemeLayoutManager: Updated span count to $newSpanCount (screen width: ${screenWidthDp}dp)")
        }
        
        // Notify adapter if layout mode changed
        if (currentIsHorizontal != newIsHorizontal) {
            currentIsHorizontal = newIsHorizontal
            onLayoutModeChanged(newIsHorizontal)
            L.d("ResponsiveThemeLayoutManager: Layout mode changed to ${if (newIsHorizontal) "horizontal" else "vertical"}")
        }
    }

    /**
     * Calculates the optimal number of columns based on available screen width.
     * 
     * @param screenWidthDp Available screen width in dp
     * @return Optimal number of columns (1 for vertical, 2-3 for horizontal)
     */
    private fun calculateOptimalSpanCount(screenWidthDp: Int): Int {
        // iOS always uses horizontal layout, so we should too unless screen is extremely narrow
        if (screenWidthDp < HORIZONTAL_THRESHOLD_DP) {
            return 1
        }
        
        // Calculate how many cards can fit horizontally with padding
        val availableWidth = screenWidthDp - 32 // Account for RecyclerView padding  
        val cardWidthWithMargin = MIN_CARD_WIDTH_DP + 8 // Card width (90dp) + horizontal margins (4dp each side)
        val possibleColumns = availableWidth / cardWidthWithMargin
        
        // For theme cards, iOS uses exactly 3 columns (Light, Dark, Auto)
        // For table cards, iOS uses exactly 2 columns (Expanded, Collapsed)
        // Return 3 for themes, but this will be adjusted by the adapter based on item count
        return possibleColumns.coerceAtMost(MAX_COLUMNS)
    }
    
    /**
     * Gets the current screen width in dp.
     */
    private fun getScreenWidthDp(): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels
        return (screenWidthPx / displayMetrics.density).toInt()
    }
    
    /**
     * Returns whether the current layout is horizontal (multi-column).
     */
    fun isHorizontalLayout(): Boolean {
        return currentIsHorizontal
    }
    
    /**
     * Forces a recalculation of the layout, useful when screen configuration changes.
     */
    fun recalculateLayout() {
        lastScreenWidth = 0 // Force recalculation
        calculateAndUpdateSpanCount()
        requestLayout()
        L.d("ResponsiveThemeLayoutManager: Layout recalculation requested")
    }
}