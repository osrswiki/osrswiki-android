package com.omiyawaki.osrswiki.settings

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ItemTablePreviewCardBinding
import com.omiyawaki.osrswiki.OSRSWikiApp
import kotlinx.coroutines.launch
import com.omiyawaki.osrswiki.util.log.L

/**
 * Adapter for inline table collapse selection with live article previews.
 * Shows horizontal comparison of collapsed vs expanded table states using Varrock article.
 */
class InlineTableSelectionAdapter(
    private val options: List<Pair<Boolean, String>>, // (collapseTablesValue, displayName)
    private var currentSelection: Boolean,
    private val onTablePreviewSelected: (Boolean) -> Unit,
    private val lifecycleScope: LifecycleCoroutineScope
) : RecyclerView.Adapter<InlineTableSelectionAdapter.TablePreviewViewHolder>() {

    companion object {
        private const val TAG = "InlineTableAdapter"
    }
    
    init {
        L.d("$TAG: Created with ${options.size} options, currentSelection=$currentSelection")
        options.forEachIndexed { index, (value, name) -> 
            L.d("$TAG: Option $index: $value -> '$name'")
        }
    }

    inner class TablePreviewViewHolder(private val binding: ItemTablePreviewCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(option: Pair<Boolean, String>, isSelected: Boolean) {
            val collapseTablesValue = option.first
            val displayName = option.second
            
            L.d("$TAG: TablePreviewViewHolder.bind() called with $displayName (value=$collapseTablesValue), isSelected=$isSelected")
            
            binding.textTableSettingName.text = displayName
            // Remove description to match iOS compact design
            
            // Load table preview
            loadTablePreview(collapseTablesValue)
            
            // Set selection state using iOS-style checkmark overlay
            binding.root.isChecked = isSelected
            binding.selectionCheckmark.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
            
            // Handle click
            binding.root.setOnClickListener {
                if (currentSelection != collapseTablesValue) {
                    val oldSelection = currentSelection
                    currentSelection = collapseTablesValue
                    
                    // Update UI immediately
                    notifyItemChanged(options.indexOfFirst { it.first == oldSelection })
                    notifyItemChanged(bindingAdapterPosition)
                    
                    // Announce selection change for accessibility
                    binding.root.announceForAccessibility("$displayName table setting selected")
                    
                    // Notify selection
                    onTablePreviewSelected(collapseTablesValue)
                }
            }
            
            // Enhanced accessibility for card-based selection
            val description = getTableSettingDescription(collapseTablesValue)
            binding.root.contentDescription = "$displayName table setting. $description.${if (isSelected) " Currently selected." else " Tap to select."}"
            
            // Set role for screen readers - use RadioButton semantics for exclusive selection
            binding.root.accessibilityDelegate = object : android.view.View.AccessibilityDelegate() {
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

        private fun getTableSettingDescription(collapseTablesEnabled: Boolean): String {
            return if (collapseTablesEnabled) {
                "Tables start collapsed"
            } else {
                "Tables start expanded"
            }
        }

        private fun loadTablePreview(collapseTablesEnabled: Boolean) {
            val tablePreviewImage = binding.tablePreviewImage
            
            // Set accessibility description
            val description = getTableSettingDescription(collapseTablesEnabled)
            tablePreviewImage.contentDescription = "${getTableSettingName(collapseTablesEnabled)} table setting preview. $description"
            
            // Get current theme for progressive loading
            val app = binding.root.context.applicationContext as OSRSWikiApp
            val currentTheme = app.getCurrentTheme()
            
            // PHASE 1: Show static preview immediately (instant display)
            val staticPreviewRes = getStaticPreviewResource(collapseTablesEnabled, currentTheme)
            tablePreviewImage.setImageResource(staticPreviewRes)
            Log.d(TAG, "Displayed static preview immediately for collapseTablesEnabled=$collapseTablesEnabled, theme=${currentTheme.tag}")
            
            // PHASE 2: Load cached/generated preview asynchronously and upgrade
            lifecycleScope.launch {
                try {
                    Log.d(TAG, "Starting enhanced table preview request for collapseTablesEnabled=$collapseTablesEnabled")
                    
                    // Get the high-quality generated preview from TablePreviewRenderer
                    val bitmap = TablePreviewRenderer.getPreview(
                        binding.root.context,
                        collapseTablesEnabled,
                        currentTheme
                    )
                    
                    Log.d(TAG, "Received enhanced table preview bitmap: ${bitmap?.width ?: "null"}x${bitmap?.height ?: "null"}")
                    
                    // Replace static preview with high-quality generated one
                    bitmap?.let {
                        tablePreviewImage.setImageBitmap(it)
                        Log.d(TAG, "Upgraded to high-quality table preview for collapseTablesEnabled=$collapseTablesEnabled")
                    } ?: run {
                        Log.w(TAG, "Generated preview was null, keeping static preview")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during enhanced table preview loading - keeping static preview", e)
                    // Static preview already displayed, no need to change anything
                }
            }
        }
        
        /**
         * Gets the appropriate static preview resource for instant display.
         * These are always available and provide immediate visual feedback.
         */
        private fun getStaticPreviewResource(collapseTablesEnabled: Boolean, theme: com.omiyawaki.osrswiki.theme.Theme): Int {
            return when (theme) {
                com.omiyawaki.osrswiki.theme.Theme.OSRS_LIGHT -> {
                    if (collapseTablesEnabled) {
                        com.omiyawaki.osrswiki.R.drawable.preview_table_light_collapsed
                    } else {
                        com.omiyawaki.osrswiki.R.drawable.preview_table_light_expanded
                    }
                }
                com.omiyawaki.osrswiki.theme.Theme.OSRS_DARK -> {
                    if (collapseTablesEnabled) {
                        com.omiyawaki.osrswiki.R.drawable.preview_table_dark_collapsed
                    } else {
                        com.omiyawaki.osrswiki.R.drawable.preview_table_dark_expanded
                    }
                }
            }
        }
        
        private fun getTableSettingName(collapseTablesEnabled: Boolean): String {
            return if (collapseTablesEnabled) "Collapsed" else "Expanded"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TablePreviewViewHolder {
        L.d("$TAG: onCreateViewHolder called")
        val binding = ItemTablePreviewCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        
        // Calculate width to fit both cards in screen width
        // Account for RecyclerView padding (4dp * 2) and card margins (4dp * 2 * 2 cards = 16dp total)
        val screenWidth = parent.resources.displayMetrics.widthPixels
        val totalPadding = parent.resources.displayMetrics.density * (8 + 16) // 8dp RecyclerView padding + 16dp card margins
        val availableWidth = screenWidth - totalPadding.toInt()
        val cardWidth = (availableWidth / 2).toInt()
        
        binding.root.layoutParams.width = cardWidth
        
        L.d("$TAG: ViewHolder created successfully with width=$cardWidth")
        return TablePreviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TablePreviewViewHolder, position: Int) {
        val option = options[position]
        val isSelected = option.first == currentSelection
        
        L.d("$TAG: onBindViewHolder position=$position, option=(${option.first}, '${option.second}'), isSelected=$isSelected")
        holder.bind(option, isSelected)
    }

    override fun getItemCount(): Int {
        val count = options.size
        L.d("$TAG: getItemCount() returning $count")
        return count
    }
}