package com.omiyawaki.osrswiki.settings

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.util.log.L
import kotlin.math.max

/**
 * A responsive layout manager for inline selection components that automatically switches between:
 * - Vertical stacking (1 column) for narrow screens or when configured
 * - Horizontal grid (2-N columns) for wide screens based on configuration
 * 
 * This is a generalized version of ResponsiveThemeLayoutManager that works with any
 * InlineSelectionConfig to provide consistent behavior across all appearance settings.
 */
class ResponsiveInlineLayoutManager(
    private val context: Context,
    private val config: InlineSelectionConfig,
    private val onLayoutModeChanged: (isHorizontal: Boolean) -> Unit
) : GridLayoutManager(context, 1) {
    
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
     * Calculates the optimal number of columns based on configuration and updates the span count.
     * Also notifies the adapter about layout mode changes.
     */
    private fun calculateAndUpdateSpanCount() {
        val screenWidthDp = getScreenWidthDp()
        val newSpanCount = calculateOptimalSpanCount(screenWidthDp)
        val newIsHorizontal = newSpanCount > 1
        
        if (spanCount != newSpanCount) {
            spanCount = newSpanCount
            L.d("ResponsiveInlineLayoutManager: Updated span count to $newSpanCount (screen width: ${screenWidthDp}dp)")
        }
        
        // Notify adapter if layout mode changed
        if (currentIsHorizontal != newIsHorizontal) {
            currentIsHorizontal = newIsHorizontal
            onLayoutModeChanged(newIsHorizontal)
            L.d("ResponsiveInlineLayoutManager: Layout mode changed to ${if (newIsHorizontal) "horizontal" else "vertical"}")
        }
    }

    /**
     * Calculates the optimal number of columns based on configuration and available screen width.
     * 
     * @param screenWidthDp Available screen width in dp
     * @return Optimal number of columns based on configuration
     */
    private fun calculateOptimalSpanCount(screenWidthDp: Int): Int {
        // If fixed span count is set, always use it
        config.fixedSpanCount?.let { return it }
        
        // If responsive layout is disabled
        if (!config.enableResponsiveLayout) {
            return if (config.forceHorizontalLayout) 2 else 1
        }
        
        // Always use single column if screen is too narrow
        if (screenWidthDp < config.horizontalThreshold) {
            return 1
        }
        
        // Calculate how many cards can fit horizontally with padding
        val availableWidth = screenWidthDp - 32 // Account for RecyclerView padding
        val cardWidthWithMargin = config.minCardWidth + 4 // Card width + horizontal margins
        val possibleColumns = availableWidth / cardWidthWithMargin
        
        // Return optimal column count (min 2 for horizontal, max from config)
        return max(2, possibleColumns.coerceAtMost(config.maxColumns))
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
        L.d("ResponsiveInlineLayoutManager: Layout recalculation requested")
    }
}