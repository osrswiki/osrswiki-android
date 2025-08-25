package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ItemThemePreviewCardBinding
import com.omiyawaki.osrswiki.databinding.ItemThemePreviewCardHorizontalBinding
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Adapter for inline theme selection with authentic OSRS app previews.
 * Shows miniature representations of the actual app UI in different themes.
 */
class InlineThemeSelectionAdapter(
    private val themes: List<Pair<String, String>>,
    private var currentSelection: String,
    private val onThemeSelected: (String) -> Unit,
    private val lifecycleScope: LifecycleCoroutineScope
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_VERTICAL = 0
        private const val VIEW_TYPE_HORIZONTAL = 1
    }
    
    private var isHorizontalLayout: Boolean = false

    /**
     * Updates the layout mode and safely schedules adapter refresh.
     * Uses Handler.post() to avoid crashes during RecyclerView layout computation.
     */
    fun updateLayoutMode(isHorizontal: Boolean, recyclerView: androidx.recyclerview.widget.RecyclerView?) {
        if (this.isHorizontalLayout != isHorizontal) {
            this.isHorizontalLayout = isHorizontal
            
            // Post the adapter update to the next frame to avoid "Cannot call this method while 
            // RecyclerView is computing a layout" crashes during orientation changes
            recyclerView?.post {
                try {
                    notifyDataSetChanged() // Full refresh needed due to layout change
                    Log.d("InlineThemeSelectionAdapter", "Layout mode updated to ${if (isHorizontal) "horizontal" else "vertical"}")
                } catch (e: Exception) {
                    // If we still hit timing issues, log and ignore to prevent crashes
                    Log.w("InlineThemeSelectionAdapter", "Failed to update adapter layout mode: ${e.message}")
                }
            }
        }
    }

    inner class VerticalThemePreviewViewHolder(private val binding: ItemThemePreviewCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(theme: Pair<String, String>, isSelected: Boolean) {
            val themeKey = theme.first
            val themeName = theme.second
            
            binding.textThemeName.text = themeName
            // Remove description to match iOS compact design
            
            // Load theme-specific screenshot
            loadThemePreview(themeKey)
            
            // Set selection state using iOS-style checkmark overlay
            binding.root.isChecked = isSelected
            binding.selectionCheckmark.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
            
            // Handle click
            binding.root.setOnClickListener {
                if (currentSelection != themeKey) {
                    val oldSelection = currentSelection
                    currentSelection = themeKey
                    
                    // Update UI immediately
                    notifyItemChanged(themes.indexOfFirst { it.first == oldSelection })
                    notifyItemChanged(bindingAdapterPosition)
                    
                    // Announce selection change for accessibility
                    binding.root.announceForAccessibility("$themeName theme selected")
                    
                    // Notify selection
                    onThemeSelected(themeKey)
                }
            }
            
            // Enhanced accessibility for card-based selection
            val description = getThemeDescription(themeKey)
            binding.root.contentDescription = "$themeName theme. $description.${if (isSelected) " Currently selected." else " Tap to select."}"
            
            // Set role for screen readers - use RadioButton semantics for exclusive selection
            binding.root.accessibilityDelegate = object : android.view.View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityEvent(host: android.view.View, event: android.view.accessibility.AccessibilityEvent) {
                    super.onInitializeAccessibilityEvent(host, event)
                    event.className = "android.widget.RadioButton"
                    event.isChecked = isSelected
                    event.text.clear()
                    event.text.add("$themeName theme. $description.${if (isSelected) " Currently selected." else " Tap to select."}")
                }
                
                override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.RadioButton"
                    info.isCheckable = true
                    info.isChecked = isSelected
                    info.contentDescription = "$themeName theme. $description.${if (isSelected) " Currently selected." else " Tap to select."}"
                    info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                }
            }
        }

        private fun getThemeDescription(themeKey: String): String {
            return when (themeKey) {
                "light" -> "Clean and bright interface"
                "dark" -> "Easy on the eyes in low light"
                "auto" -> "Follows your system setting"
                else -> "OSRS-themed parchment styling"
            }
        }

        private fun loadThemePreview(themeKey: String) {
            val themePreviewImage = binding.root.findViewById<android.widget.ImageView>(R.id.themePreviewImage)
            val splitDivider = binding.root.findViewById<android.view.View>(R.id.splitDivider)
            
            // Hide divider - it's now part of the composite bitmap for "auto"
            splitDivider?.visibility = android.view.View.GONE
            
            // Set accessibility description
            val description = getThemeDescription(themeKey)
            themePreviewImage?.contentDescription = "${getThemeName(themeKey)} theme preview. $description"
            
            // PHASE 1: Show static placeholder immediately (instant display)
            val staticPlaceholder = getThemeStaticPlaceholder(themeKey)
            themePreviewImage?.setImageResource(staticPlaceholder)
            Log.d("🔧 ADAPTER_DEBUG", "Displayed static placeholder immediately for theme: $themeKey")
            
            // PHASE 2: Load high-quality theme preview asynchronously and upgrade
            lifecycleScope.launch {
                try {
                    Log.d("🔧 ADAPTER_DEBUG", "Starting enhanced theme preview request for: $themeKey")
                    Log.d("PreviewDiagnosis", "Requesting theme preview for: $themeKey")
                    val bitmap = when (themeKey) {
                        "light" -> ThemePreviewRenderer.getPreview(
                            binding.root.context, 
                            R.style.Theme_OSRSWiki_OSRSLight, 
                            "light"
                        )
                        "dark" -> ThemePreviewRenderer.getPreview(
                            binding.root.context, 
                            R.style.Theme_OSRSWiki_OSRSDark, 
                            "dark"
                        )
                        "auto" -> ThemePreviewRenderer.getPreview(
                            binding.root.context, 
                            0, // Not used for auto theme
                            "auto"
                        )
                        else -> ThemePreviewRenderer.getPreview(
                            binding.root.context, 
                            R.style.Theme_OSRSWiki_OSRSLight, 
                            "light"
                        )
                    }
                    Log.d("PreviewDiagnosis", "Received bitmap from renderer: ${bitmap?.width ?: "null"}x${bitmap?.height ?: "null"}, isRecycled=${bitmap?.isRecycled ?: "null"}")
                    
                    // EXPERT DIAGNOSTIC: Log all key values to identify density/scaling issues
                    themePreviewImage?.let { imageView ->
                        val context = binding.root.context
                        val dm = context.resources.displayMetrics
                        val (targetDpW, targetDpH) = ThemePreviewRenderer.getPreviewDimensionsForUI(context)
                        val targetPxW = (targetDpW * dm.density).roundToInt()
                        val targetPxH = (targetDpH * dm.density).roundToInt()
                        
                        Log.d("PreviewDiagnosis", """
                        targetDp=($targetDpW x $targetDpH)
                        targetPx=($targetPxW x $targetPxH)
                        bitmap=(${bitmap?.width ?: "null"} x ${bitmap?.height ?: "null"})
                        bitmap.density=${bitmap?.density ?: "null"}
                        imageView=(${imageView.width} x ${imageView.height}) scaleType=${imageView.scaleType}
                        deviceDensity=${dm.density} densityDpi=${dm.densityDpi}
                        """.trimIndent())
                        
                        // Let ImageView handle scaling of 111x234 bitmaps naturally
                        // Use centerInside for fit-to-width, top-aligned behavior
                        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }
                    
                    // Replace static placeholder with high-quality generated preview
                    bitmap?.let {
                        themePreviewImage?.setImageBitmap(it)
                        Log.d("🔧 ADAPTER_DEBUG", "Upgraded to high-quality theme preview for: $themeKey")
                        Log.d("PreviewDiagnosis", "Applied enhanced bitmap for theme: $themeKey")
                    } ?: run {
                        Log.w("🔧 ADAPTER_DEBUG", "Generated preview was null, keeping static placeholder for: $themeKey")
                    }
                } catch (e: Exception) {
                    Log.e("🔧 ADAPTER_DEBUG", "Exception during enhanced theme preview loading - keeping static placeholder", e)
                    // Static placeholder already displayed, no need to change anything
                }
            }
        }
        
        /**
         * Gets the appropriate static placeholder for instant display.
         */
        private fun getThemeStaticPlaceholder(themeKey: String): Int {
            return when (themeKey) {
                "dark" -> R.drawable.ic_placeholder_dark
                "auto" -> R.drawable.ic_placeholder_auto
                else -> R.drawable.ic_placeholder_light
            }
        }
        
        private fun getThemeName(themeKey: String): String {
            return when (themeKey) {
                "light" -> "Light"
                "dark" -> "Dark" 
                "auto" -> "Follow System"
                else -> "OSRS"
            }
        }




    }

    inner class HorizontalThemePreviewViewHolder(private val binding: ItemThemePreviewCardHorizontalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(theme: Pair<String, String>, isSelected: Boolean) {
            val themeKey = theme.first
            val themeName = theme.second
            
            binding.textThemeName.text = themeName
            // Remove description to match iOS compact design
            
            // Load theme-specific screenshot (smaller size for horizontal layout)
            loadThemePreview(themeKey)
            
            // Set selection state using iOS-style checkmark overlay
            binding.root.isChecked = isSelected
            binding.selectionCheckmark.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
            
            // Handle click
            binding.root.setOnClickListener {
                if (currentSelection != themeKey) {
                    val oldSelection = currentSelection
                    currentSelection = themeKey
                    
                    // Update UI immediately
                    notifyItemChanged(themes.indexOfFirst { it.first == oldSelection })
                    notifyItemChanged(bindingAdapterPosition)
                    
                    // Announce selection change for accessibility
                    binding.root.announceForAccessibility("$themeName theme selected")
                    
                    // Notify selection
                    onThemeSelected(themeKey)
                }
            }
            
            // Enhanced accessibility for card-based selection
            val description = getThemeDescription(themeKey)
            binding.root.contentDescription = "$themeName theme. $description.${if (isSelected) " Currently selected." else " Tap to select."}"
            
            // Set role for screen readers - use RadioButton semantics for exclusive selection
            binding.root.accessibilityDelegate = object : android.view.View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityEvent(host: android.view.View, event: android.view.accessibility.AccessibilityEvent) {
                    super.onInitializeAccessibilityEvent(host, event)
                    event.className = "android.widget.RadioButton"
                    event.isChecked = isSelected
                    event.text.clear()
                    event.text.add("$themeName theme. $description.${if (isSelected) " Currently selected." else " Tap to select."}")
                }
                
                override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.RadioButton"
                    info.isCheckable = true
                    info.isChecked = isSelected
                    info.contentDescription = "$themeName theme. $description.${if (isSelected) " Currently selected." else " Tap to select."}"
                    info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                }
            }
        }

        private fun getThemeDescription(themeKey: String): String {
            return when (themeKey) {
                "light" -> "Clean and bright interface"
                "dark" -> "Easy on the eyes in low light"
                "auto" -> "Follows your system setting"
                else -> "OSRS-themed parchment styling"
            }
        }

        private fun loadThemePreview(themeKey: String) {
            val themePreviewImage = binding.root.findViewById<android.widget.ImageView>(R.id.themePreviewImage)
            val splitDivider = binding.root.findViewById<android.view.View>(R.id.splitDivider)
            
            // Hide divider - it's now part of the composite bitmap for "auto"
            splitDivider?.visibility = android.view.View.GONE
            
            // Set accessibility description
            val description = getThemeDescription(themeKey)
            themePreviewImage?.contentDescription = "${getThemeName(themeKey)} theme preview. $description"
            
            // PHASE 1: Show static placeholder immediately (instant display)
            val staticPlaceholder = getThemeStaticPlaceholder(themeKey)
            themePreviewImage?.setImageResource(staticPlaceholder)
            Log.d("🔧 ADAPTER_DEBUG", "Displayed static placeholder immediately for theme: $themeKey")
            
            // PHASE 2: Load high-quality theme preview asynchronously and upgrade
            lifecycleScope.launch {
                try {
                    Log.d("🔧 ADAPTER_DEBUG", "Starting enhanced theme preview request for: $themeKey")
                    Log.d("PreviewDiagnosis", "Requesting theme preview for: $themeKey")
                    val bitmap = when (themeKey) {
                        "light" -> ThemePreviewRenderer.getPreview(
                            binding.root.context, 
                            R.style.Theme_OSRSWiki_OSRSLight, 
                            "light"
                        )
                        "dark" -> ThemePreviewRenderer.getPreview(
                            binding.root.context, 
                            R.style.Theme_OSRSWiki_OSRSDark, 
                            "dark"
                        )
                        "auto" -> ThemePreviewRenderer.getPreview(
                            binding.root.context, 
                            0, // Not used for auto theme
                            "auto"
                        )
                        else -> ThemePreviewRenderer.getPreview(
                            binding.root.context, 
                            R.style.Theme_OSRSWiki_OSRSLight, 
                            "light"
                        )
                    }
                    Log.d("PreviewDiagnosis", "Received bitmap from renderer: ${bitmap?.width ?: "null"}x${bitmap?.height ?: "null"}, isRecycled=${bitmap?.isRecycled ?: "null"}")
                    
                    // EXPERT DIAGNOSTIC: Log all key values to identify density/scaling issues
                    themePreviewImage?.let { imageView ->
                        val context = binding.root.context
                        val dm = context.resources.displayMetrics
                        val (targetDpW, targetDpH) = ThemePreviewRenderer.getPreviewDimensionsForUI(context)
                        val targetPxW = (targetDpW * dm.density).roundToInt()
                        val targetPxH = (targetDpH * dm.density).roundToInt()
                        
                        Log.d("PreviewDiagnosis", """
                        targetDp=($targetDpW x $targetDpH)
                        targetPx=($targetPxW x $targetPxH)
                        bitmap=(${bitmap?.width ?: "null"} x ${bitmap?.height ?: "null"})
                        bitmap.density=${bitmap?.density ?: "null"}
                        imageView=(${imageView.width} x ${imageView.height}) scaleType=${imageView.scaleType}
                        deviceDensity=${dm.density} densityDpi=${dm.densityDpi}
                        """.trimIndent())
                        
                        // Let ImageView handle scaling of 111x234 bitmaps naturally
                        // Use centerInside for fit-to-width, top-aligned behavior  
                        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }
                    
                    // Replace static placeholder with high-quality generated preview
                    bitmap?.let {
                        themePreviewImage?.setImageBitmap(it)
                        Log.d("🔧 ADAPTER_DEBUG", "Upgraded to high-quality theme preview for: $themeKey")
                        Log.d("PreviewDiagnosis", "Applied enhanced bitmap for theme: $themeKey")
                    } ?: run {
                        Log.w("🔧 ADAPTER_DEBUG", "Generated preview was null, keeping static placeholder for: $themeKey")
                    }
                } catch (e: Exception) {
                    Log.e("🔧 ADAPTER_DEBUG", "Exception during enhanced theme preview loading - keeping static placeholder", e)
                    // Static placeholder already displayed, no need to change anything
                }
            }
        }
        
        /**
         * Gets the appropriate static placeholder for instant display.
         */
        private fun getThemeStaticPlaceholder(themeKey: String): Int {
            return when (themeKey) {
                "dark" -> R.drawable.ic_placeholder_dark
                "auto" -> R.drawable.ic_placeholder_auto
                else -> R.drawable.ic_placeholder_light
            }
        }
        
        private fun getThemeName(themeKey: String): String {
            return when (themeKey) {
                "light" -> "Light"
                "dark" -> "Dark" 
                "auto" -> "Follow System"
                else -> "OSRS"
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isHorizontalLayout) VIEW_TYPE_HORIZONTAL else VIEW_TYPE_VERTICAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HORIZONTAL -> {
                val binding = ItemThemePreviewCardHorizontalBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                HorizontalThemePreviewViewHolder(binding)
            }
            VIEW_TYPE_VERTICAL -> {
                val binding = ItemThemePreviewCardBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                VerticalThemePreviewViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val theme = themes[position]
        val isSelected = theme.first == currentSelection
        
        when (holder) {
            is VerticalThemePreviewViewHolder -> holder.bind(theme, isSelected)
            is HorizontalThemePreviewViewHolder -> holder.bind(theme, isSelected)
        }
    }

    override fun getItemCount(): Int = themes.size

}