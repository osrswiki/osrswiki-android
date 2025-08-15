package com.omiyawaki.osrswiki.settings

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ItemInlineSelectionCardBinding
import com.omiyawaki.osrswiki.databinding.ItemInlineSelectionCardHorizontalBinding
import com.omiyawaki.osrswiki.OSRSWikiApp
import kotlinx.coroutines.launch
import com.omiyawaki.osrswiki.util.log.L

/**
 * Modular adapter for table collapse selection with live article previews.
 * Uses the new modular architecture with GridLayoutManager and shared layouts.
 * Shows horizontal comparison of collapsed vs expanded table states using Varrock article.
 */
class ModularTableSelectionAdapter(
    options: List<Pair<Boolean, String>>, // (collapseTablesValue, displayName)
    currentSelection: Boolean,
    onTablePreviewSelected: (Boolean) -> Unit,
    lifecycleScope: LifecycleCoroutineScope
) : BaseInlineSelectionAdapter<Pair<Boolean, String>, RecyclerView.ViewHolder>(
    items = options,
    currentSelection = options.first { it.first == currentSelection },
    config = InlineSelectionConfig.TABLE_CONFIG,
    onItemSelected = { option -> onTablePreviewSelected(option.first) },
    lifecycleScope = lifecycleScope
) {

    companion object {
        private const val TAG = "ModularTableAdapter"
    }
    
    init {
        L.d("$TAG: Created with ${items.size} options")
    }

    /**
     * ViewHolder for vertical layout (full-width cards with side-by-side content)
     */
    inner class VerticalTableViewHolder(private val binding: ItemInlineSelectionCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(option: Pair<Boolean, String>, isSelected: Boolean, position: Int) {
            val collapseTablesValue = option.first
            val displayName = option.second
            
            
            binding.textSelectionName.text = displayName
            binding.textSelectionDescription.text = getTableSettingDescription(collapseTablesValue)
            
            // Load table preview
            loadTablePreview(collapseTablesValue, binding.selectionPreviewImage)
            
            // Set selection state
            binding.root.isChecked = isSelected
            
            // Handle click
            binding.root.setOnClickListener {
                handleSelection(option, position, displayName)
            }
            
            // Accessibility
            setupAccessibility(binding.root, displayName, collapseTablesValue, isSelected)
        }
    }

    /**
     * ViewHolder for horizontal layout (compact cards stacked horizontally)
     */
    inner class HorizontalTableViewHolder(private val binding: ItemInlineSelectionCardHorizontalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(option: Pair<Boolean, String>, isSelected: Boolean, position: Int) {
            val collapseTablesValue = option.first
            val displayName = option.second
            
            
            binding.textSelectionName.text = displayName
            binding.textSelectionDescription.text = getTableSettingDescription(collapseTablesValue)
            
            // Load table preview (smaller size for horizontal layout)
            loadTablePreview(collapseTablesValue, binding.selectionPreviewImage)
            
            // Set selection state
            binding.root.isChecked = isSelected
            
            // Handle click
            binding.root.setOnClickListener {
                handleSelection(option, position, displayName)
            }
            
            // Accessibility
            setupAccessibility(binding.root, displayName, collapseTablesValue, isSelected)
        }
    }

    override fun createVerticalViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemInlineSelectionCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VerticalTableViewHolder(binding)
    }

    override fun createHorizontalViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemInlineSelectionCardHorizontalBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HorizontalTableViewHolder(binding)
    }

    override fun bindViewHolder(holder: RecyclerView.ViewHolder, item: Pair<Boolean, String>, isSelected: Boolean, position: Int) {
        
        when (holder) {
            is VerticalTableViewHolder -> holder.bind(item, isSelected, position)
            is HorizontalTableViewHolder -> holder.bind(item, isSelected, position)
            else -> throw IllegalArgumentException("Unknown ViewHolder type: ${holder::class.simpleName}")
        }
    }

    private fun getTableSettingDescription(collapseTablesEnabled: Boolean): String {
        return if (collapseTablesEnabled) {
            "Tables start collapsed"
        } else {
            "Tables start expanded"
        }
    }

    private fun loadTablePreview(collapseTablesEnabled: Boolean, imageView: android.widget.ImageView) {
        // Set accessibility description
        val description = getTableSettingDescription(collapseTablesEnabled)
        imageView.contentDescription = "${getTableSettingName(collapseTablesEnabled)} table setting preview. $description"
        
        // Load dynamic table preview
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Starting table preview request for collapseTablesEnabled=$collapseTablesEnabled")
                
                // Get current theme from the app
                val app = imageView.context.applicationContext as OSRSWikiApp
                val currentTheme = app.getCurrentTheme()
                
                // Get the side-by-side preview bitmap from TablePreviewRenderer
                val bitmap = TablePreviewRenderer.getPreview(
                    imageView.context,
                    collapseTablesEnabled,
                    currentTheme
                )
                
                Log.d(TAG, "Received table preview bitmap: ${bitmap?.width ?: "null"}x${bitmap?.height ?: "null"}")
                
                // Display the complete side-by-side preview
                imageView.setImageBitmap(bitmap)
                
                Log.d(TAG, "Table preview loaded successfully for collapseTablesEnabled=$collapseTablesEnabled")
            } catch (e: Exception) {
                Log.e(TAG, "Exception during table preview setup - using fallback", e)
                
                // Fall back to simple placeholder
                imageView.setImageDrawable(null) // Clear any existing image
                // In a real implementation, you might want to create a simple placeholder drawable
            }
        }
    }
    
    private fun getTableSettingName(collapseTablesEnabled: Boolean): String {
        return if (collapseTablesEnabled) "Collapsed" else "Expanded"
    }

    private fun setupAccessibility(cardView: android.view.View, displayName: String, collapseTablesEnabled: Boolean, isSelected: Boolean) {
        val description = getTableSettingDescription(collapseTablesEnabled)
        cardView.contentDescription = "$displayName table setting. $description.${if (isSelected) " Currently selected." else " Tap to select."}"
        
        // Set role for screen readers - use RadioButton semantics for exclusive selection
        cardView.accessibilityDelegate = object : android.view.View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityEvent(host: android.view.View, event: android.view.accessibility.AccessibilityEvent) {
                super.onInitializeAccessibilityEvent(host, event)
                event.className = "android.widget.RadioButton"
                event.isChecked = isSelected
                event.text.clear()
                event.text.add("$displayName table setting. $description.${if (isSelected) " Currently selected." else " Tap to select."}")
            }
            
            override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.RadioButton"
                info.isCheckable = true
                info.isChecked = isSelected
                info.contentDescription = "$displayName table setting. $description.${if (isSelected) " Currently selected." else " Tap to select."}"
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            }
        }
    }
}