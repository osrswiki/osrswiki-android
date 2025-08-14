package com.omiyawaki.osrswiki.views

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.children

/**
 * Diagnostic utility to measure and log bottom navigation icon/text positions
 * to quantitatively verify overlap issues and fixes.
 */
object BottomNavDiagnostics {
    
    private const val TAG = "BottomNavDiag"
    
    data class ElementPosition(
        val name: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val width: Int,
        val height: Int
    ) {
        fun overlapsVertically(other: ElementPosition): Boolean {
            return !(this.bottom <= other.top || this.top >= other.bottom)
        }
        
        fun verticalGap(other: ElementPosition): Int {
            return when {
                this.bottom <= other.top -> other.top - this.bottom  // Gap between this bottom and other top
                other.bottom <= this.top -> this.top - other.bottom  // Gap between other bottom and this top
                else -> 0 // Overlapping
            }
        }
    }
    
    data class NavigationItemMeasurement(
        val itemIndex: Int,
        val icon: ElementPosition?,
        val text: ElementPosition?,
        val hasOverlap: Boolean,
        val verticalGap: Int
    )
    
    /**
     * Analyzes a BottomNavigationView and logs detailed positioning information
     */
    fun analyzeBottomNavigation(view: ViewGroup, componentName: String = "BottomNavigationView") {
        Log.i(TAG, "=== $componentName Analysis ===")
        
        val measurements = mutableListOf<NavigationItemMeasurement>()
        
        // Find the BottomNavigationMenuView (the container for navigation items)
        val menuView = findMenuView(view)
        if (menuView == null) {
            Log.w(TAG, "Could not find menu view in $componentName")
            return
        }
        
        Log.i(TAG, "Found menu view with ${menuView.childCount} items")
        
        // Analyze each navigation item
        for (i in 0 until menuView.childCount) {
            val itemView = menuView.getChildAt(i) as? ViewGroup ?: continue
            val measurement = analyzeNavigationItem(itemView, i)
            measurements.add(measurement)
            logItemMeasurement(measurement)
        }
        
        // Summary analysis
        val totalItems = measurements.size
        val overlappingItems = measurements.count { it.hasOverlap }
        val avgGap = measurements.mapNotNull { if (it.verticalGap >= 0) it.verticalGap else null }.average()
        
        Log.i(TAG, "=== $componentName Summary ===")
        Log.i(TAG, "Total items: $totalItems")
        Log.i(TAG, "Overlapping items: $overlappingItems")
        Log.i(TAG, "Items with proper spacing: ${totalItems - overlappingItems}")
        if (avgGap.isFinite()) {
            Log.i(TAG, "Average vertical gap: ${avgGap.toInt()}px")
        }
        Log.i(TAG, "Has overlap issue: ${overlappingItems > 0}")
        Log.i(TAG, "=====================================")
    }
    
    private fun findMenuView(parent: ViewGroup): ViewGroup? {
        // Look for the BottomNavigationMenuView or similar container
        parent.children.forEach { child ->
            when {
                child::class.java.simpleName.contains("MenuView") -> return child as? ViewGroup
                child::class.java.simpleName.contains("BottomNavigation") && child is ViewGroup -> {
                    // Recurse into BottomNavigation subviews
                    val found = findMenuView(child)
                    if (found != null) return found
                }
                child is ViewGroup && child.childCount > 1 -> {
                    // Look for a container with multiple children (likely the menu container)
                    val found = findMenuView(child)
                    if (found != null) return found
                }
            }
        }
        
        // Fallback: if parent has multiple children, it might be the menu view itself
        return if (parent.childCount > 1) parent else null
    }
    
    private fun analyzeNavigationItem(itemView: ViewGroup, index: Int): NavigationItemMeasurement {
        var icon: ElementPosition? = null
        var text: ElementPosition? = null
        
        findIconAndText(itemView) { iconView, textView ->
            icon = iconView?.let { measureElement(it, "Icon_$index") }
            text = textView?.let { measureElement(it, "Text_$index") }
        }
        
        val hasOverlap = icon != null && text != null && icon!!.overlapsVertically(text!!)
        val verticalGap = if (icon != null && text != null) icon!!.verticalGap(text!!) else -1
        
        return NavigationItemMeasurement(index, icon, text, hasOverlap, verticalGap)
    }
    
    private fun findIconAndText(parent: ViewGroup, callback: (icon: ImageView?, text: TextView?) -> Unit) {
        var iconView: ImageView? = null
        var textView: TextView? = null
        
        fun searchRecursively(view: View) {
            when (view) {
                is ImageView -> iconView = view
                is TextView -> textView = view
                is ViewGroup -> view.children.forEach { searchRecursively(it) }
            }
        }
        
        searchRecursively(parent)
        callback(iconView, textView)
    }
    
    private fun measureElement(view: View, name: String): ElementPosition {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        
        return ElementPosition(
            name = name,
            left = location[0],
            top = location[1],
            right = location[0] + view.width,
            bottom = location[1] + view.height,
            width = view.width,
            height = view.height
        )
    }
    
    private fun logItemMeasurement(measurement: NavigationItemMeasurement) {
        Log.i(TAG, "--- Item ${measurement.itemIndex} ---")
        
        measurement.icon?.let { icon ->
            Log.i(TAG, "Icon: ${icon.name} at (${icon.left},${icon.top}) to (${icon.right},${icon.bottom}) size:${icon.width}x${icon.height}")
        } ?: Log.w(TAG, "Icon: NOT FOUND")
        
        measurement.text?.let { text ->
            Log.i(TAG, "Text: ${text.name} at (${text.left},${text.top}) to (${text.right},${text.bottom}) size:${text.width}x${text.height}")
        } ?: Log.w(TAG, "Text: NOT FOUND")
        
        if (measurement.icon != null && measurement.text != null) {
            Log.i(TAG, "Overlap: ${measurement.hasOverlap}")
            Log.i(TAG, "Vertical gap: ${measurement.verticalGap}px")
            
            if (measurement.hasOverlap) {
                val overlapHeight = minOf(measurement.icon.bottom, measurement.text.bottom) - 
                                  maxOf(measurement.icon.top, measurement.text.top)
                Log.w(TAG, "⚠️ OVERLAP DETECTED: ${overlapHeight}px overlap")
            }
        }
    }
}