package com.omiyawaki.osrswiki.settings

import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.util.log.L

/**
 * Base adapter class for inline selection components (themes, tables, etc.).
 * Provides common functionality for responsive layout switching and selection handling.
 * 
 * @param T The type of items being displayed (e.g., Pair<String, String> for themes)
 * @param VH The ViewHolder type (must extend RecyclerView.ViewHolder)
 */
abstract class BaseInlineSelectionAdapter<T, VH : RecyclerView.ViewHolder>(
    protected val items: List<T>,
    protected var currentSelection: T,
    protected val config: InlineSelectionConfig,
    protected val onItemSelected: (T) -> Unit,
    protected val lifecycleScope: LifecycleCoroutineScope
) : RecyclerView.Adapter<VH>() {

    companion object {
        private const val VIEW_TYPE_VERTICAL = 0
        private const val VIEW_TYPE_HORIZONTAL = 1
    }
    
    protected var isHorizontalLayout: Boolean = false
    
    /**
     * Updates the layout mode and safely schedules adapter refresh.
     * Uses Handler.post() to avoid crashes during RecyclerView layout computation.
     */
    fun updateLayoutMode(isHorizontal: Boolean, recyclerView: RecyclerView?) {
        if (this.isHorizontalLayout != isHorizontal) {
            this.isHorizontalLayout = isHorizontal
            
            // Post the adapter update to the next frame to avoid "Cannot call this method while 
            // RecyclerView is computing a layout" crashes during orientation changes
            recyclerView?.post {
                try {
                    notifyDataSetChanged() // Full refresh needed due to layout change
                    L.d("BaseInlineSelectionAdapter: Layout mode updated to ${if (isHorizontal) "horizontal" else "vertical"}")
                } catch (e: Exception) {
                    // If we still hit timing issues, log and ignore to prevent crashes
                    L.w("BaseInlineSelectionAdapter: Failed to update adapter layout mode: ${e.message}")
                }
            }
        }
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (isHorizontalLayout) VIEW_TYPE_HORIZONTAL else VIEW_TYPE_VERTICAL
    }

    override fun getItemCount(): Int = items.size
    
    /**
     * Handle selection change and update UI.
     * Updates currentSelection, refreshes affected items, and notifies callback.
     */
    protected fun handleSelection(newSelection: T, position: Int, itemDisplayName: String) {
        if (currentSelection != newSelection) {
            val oldSelection = currentSelection
            currentSelection = newSelection
            
            // Update UI immediately - find old selection position and refresh both
            val oldPosition = items.indexOf(oldSelection)
            if (oldPosition != -1) {
                notifyItemChanged(oldPosition)
            }
            notifyItemChanged(position)
            
            // Notify selection callback
            onItemSelected(newSelection)
            
            L.d("BaseInlineSelectionAdapter: Selection changed from $oldSelection to $newSelection")
        }
    }
    
    /**
     * Check if an item is currently selected.
     */
    protected fun isItemSelected(item: T): Boolean {
        return currentSelection == item
    }
    
    /**
     * Abstract methods that concrete implementations must provide
     */
    
    /**
     * Create ViewHolder for vertical layout.
     */
    abstract fun createVerticalViewHolder(parent: android.view.ViewGroup): VH
    
    /**
     * Create ViewHolder for horizontal layout.
     */
    abstract fun createHorizontalViewHolder(parent: android.view.ViewGroup): VH
    
    /**
     * Bind data to ViewHolder.
     */
    abstract fun bindViewHolder(holder: VH, item: T, isSelected: Boolean, position: Int)
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        return when (viewType) {
            VIEW_TYPE_HORIZONTAL -> createHorizontalViewHolder(parent)
            VIEW_TYPE_VERTICAL -> createVerticalViewHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val isSelected = isItemSelected(item)
        bindViewHolder(holder, item, isSelected, position)
    }
}