package com.omiyawaki.osrswiki.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.textview.MaterialTextView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ViewCustomBottomNavBarBinding
import com.omiyawaki.osrswiki.util.log.L

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
            // Initialize all tabs to inactive color (theme-aware)
            setInactiveColor(view)
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
        L.d("CBNV-DEBUG: setSelectedItem called with itemId=$itemId, previous=$_selectedItemId")
        
        // Clear previous selection
        navItems[_selectedItemId]?.let { view: MaterialTextView ->
            view.isSelected = false
            // Set to inactive color (theme-aware)
            setInactiveColor(view)
            L.d("CBNV-DEBUG: Cleared selection for ${getItemName(_selectedItemId)}: isSelected=${view.isSelected}")
        }
        
        // Set new selection
        _selectedItemId = itemId
        navItems[_selectedItemId]?.let { view: MaterialTextView ->
            view.isSelected = true
            // Use theme-aware primary text color (matches iOS primaryTextColor)
            setActiveColor(view)
            L.d("CBNV-DEBUG: Set selection for ${getItemName(_selectedItemId)}: isSelected=${view.isSelected}")
        }
    }
    
    /**
     * Sets the active color for a navigation item (theme-aware)
     */
    private fun setActiveColor(view: MaterialTextView) {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val activeColor = typedValue.data
        view.setTextColor(activeColor)
        view.compoundDrawableTintList = ColorStateList.valueOf(activeColor)
    }
    
    /**
     * Sets the inactive color for a navigation item (theme-aware)
     */
    private fun setInactiveColor(view: MaterialTextView) {
        // Calculate 40% alpha of the active color directly to bypass any resource caching issues
        val typedValue = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val activeColor = typedValue.data
        
        // Create 40% alpha version by modifying the alpha channel
        val inactiveColor = (activeColor and 0x00FFFFFF) or (0x66 shl 24)
        
        view.setTextColor(inactiveColor)
        view.compoundDrawableTintList = ColorStateList.valueOf(inactiveColor)
    }
    
    private fun getItemName(itemId: Int): String {
        return when (itemId) {
            R.id.nav_news -> "Home"
            R.id.nav_saved -> "Saved"
            R.id.nav_search -> "Search"
            R.id.nav_map -> "Map"
            R.id.nav_more -> "More"
            else -> "Unknown($itemId)"
        }
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
            view.alpha = if (enabled) 1.0f else 0.4f
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