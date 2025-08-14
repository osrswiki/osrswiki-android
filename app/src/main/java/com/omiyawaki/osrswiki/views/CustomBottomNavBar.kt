package com.omiyawaki.osrswiki.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.textview.MaterialTextView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ViewCustomBottomNavBarBinding

/**
 * Custom bottom navigation bar that avoids the Material3 BottomNavigationView overlap bug
 * by using a simple LinearLayout with MaterialTextView items (similar to PageFragment's approach).
 * 
 * This implementation provides the same functionality as BottomNavigationView but with 
 * predictable layout behavior that doesn't suffer from the gesture navigation spacing issues.
 */
class CustomBottomNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewCustomBottomNavBarBinding
    private var _selectedItemId: Int = -1
    private var onItemSelectedListener: OnItemSelectedListener? = null
    
    // Navigation items mapped to their views
    private val navItems = mutableMapOf<Int, MaterialTextView>()
    
    interface OnItemSelectedListener {
        fun onItemSelected(itemId: Int): Boolean
    }
    
    init {
        binding = ViewCustomBottomNavBarBinding.inflate(LayoutInflater.from(context), this, true)
        setupNavigationItems()
        
        // Add diagnostic logging after layout
        post {
            // Give the view time to lay out properly
            postDelayed({
                BottomNavDiagnostics.analyzeBottomNavigation(this, "CustomBottomNavBar")
            }, 100)
        }
    }
    
    private fun setupNavigationItems() {
        // Map each navigation item ID to its corresponding view
        navItems[R.id.nav_news] = binding.navNews
        navItems[R.id.nav_saved] = binding.navSaved
        navItems[R.id.nav_search] = binding.navSearch
        navItems[R.id.nav_map] = binding.navMap
        navItems[R.id.nav_more] = binding.navMore
        
        // Set up click listeners for each item
        navItems.forEach { (itemId, view) ->
            view.setOnClickListener {
                selectItem(itemId)
            }
        }
    }
    
    /**
     * Selects a navigation item and notifies the listener
     */
    private fun selectItem(itemId: Int) {
        // Only proceed if this is a different item or if listener approves the selection
        if (itemId != _selectedItemId) {
            val shouldSelect = onItemSelectedListener?.onItemSelected(itemId) ?: true
            if (shouldSelect) {
                setSelectedItem(itemId)
            }
        }
    }
    
    /**
     * Sets the selected item without triggering the listener (for programmatic selection)
     */
    fun setSelectedItem(itemId: Int) {
        // Clear previous selection
        navItems[_selectedItemId]?.isSelected = false
        
        // Set new selection
        _selectedItemId = itemId
        navItems[_selectedItemId]?.isSelected = true
    }
    
    /**
     * Property accessor for compatibility with BottomNavigationView API
     */
    val selectedItemId: Int
        get() = _selectedItemId
    
    /**
     * Sets the listener for item selection events
     */
    fun setOnItemSelectedListener(listener: OnItemSelectedListener?) {
        onItemSelectedListener = listener
    }
    
    /**
     * Sets the listener for item selection events using a lambda
     */
    fun setOnItemSelectedListener(listener: (Int) -> Boolean) {
        onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(itemId: Int): Boolean = listener(itemId)
        }
    }
    
    /**
     * Enables or disables a specific navigation item
     */
    fun setItemEnabled(itemId: Int, enabled: Boolean) {
        navItems[itemId]?.let { view ->
            view.isEnabled = enabled
            view.alpha = if (enabled) 1.0f else 0.5f
        }
    }
    
    /**
     * Sets a badge count for a specific navigation item
     * Note: This is a placeholder for future badge functionality
     */
    fun setBadge(itemId: Int, count: Int) {
        // Placeholder for badge functionality if needed in the future
        // Could be implemented by adding a badge view overlay
    }
}