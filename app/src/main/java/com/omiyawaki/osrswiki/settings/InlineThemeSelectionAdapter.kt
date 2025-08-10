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
) : RecyclerView.Adapter<InlineThemeSelectionAdapter.ThemePreviewViewHolder>() {

    inner class ThemePreviewViewHolder(private val binding: ItemThemePreviewCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(theme: Pair<String, String>, isSelected: Boolean) {
            val themeKey = theme.first
            val themeName = theme.second
            
            binding.textThemeName.text = themeName
            binding.textThemeDescription.text = getThemeDescription(themeKey)
            
            // Load theme-specific screenshot
            loadThemePreview(themeKey)
            
            // Set selection state
            binding.root.isChecked = isSelected
            
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
            
            // Load dynamic screenshot
            lifecycleScope.launch {
                try {
                    Log.d("🔧 ADAPTER_DEBUG", "Starting theme preview request for: $themeKey")
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
                        
                        // EXPERT CANONICAL: Fixed container with FIT_XY (final bitmap matches view px exactly)
                        imageView.layoutParams?.width = (96 * dm.density).roundToInt()
                        imageView.layoutParams?.height = (192 * dm.density).roundToInt()
                        imageView.adjustViewBounds = false
                        imageView.scaleType = ImageView.ScaleType.FIT_XY
                    }
                    
                    Log.d("PreviewDiagnosis", "About to call setImageBitmap() with bitmap: ${bitmap?.width ?: "null"}x${bitmap?.height ?: "null"}")
                    
                    // Expert sanity check: bitmap must exactly match ImageView dimensions
                    themePreviewImage?.let { imageView ->
                        bitmap?.let { bmp ->
                            try {
                                check(bmp.width == imageView.width && bmp.height == imageView.height) {
                                    "EXPERT VALIDATION FAILED: Bitmap ${bmp.width}x${bmp.height} must equal view ${imageView.width}x${imageView.height}"
                                }
                                Log.d("PreviewDiagnosis", "✓ Expert validation passed: bitmap=${bmp.width}x${bmp.height} matches view=${imageView.width}x${imageView.height}")
                            } catch (e: IllegalStateException) {
                                Log.e("PreviewDiagnosis", "✗ ${e.message}")
                                // Continue anyway, but this indicates a scaling issue
                            }
                        }
                    }
                    
                    themePreviewImage?.setImageBitmap(bitmap)
                    Log.d("PreviewDiagnosis", "setImageBitmap() completed successfully")
                } catch (e: Exception) {
                    Log.e("PreviewDiagnosis", "Exception during theme preview setup - falling back to placeholder", e)
                    // Fall back to placeholder drawables on error
                    val fallbackDrawable = when (themeKey) {
                        "dark" -> R.drawable.ic_placeholder_dark
                        "auto" -> R.drawable.ic_placeholder_auto
                        else -> R.drawable.ic_placeholder_light
                    }
                    themePreviewImage?.setImageResource(fallbackDrawable)
                    Log.d("PreviewDiagnosis", "Set fallback drawable for theme: $themeKey")
                }
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemePreviewViewHolder {
        val binding = ItemThemePreviewCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ThemePreviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThemePreviewViewHolder, position: Int) {
        val theme = themes[position]
        val isSelected = theme.first == currentSelection
        holder.bind(theme, isSelected)
    }

    override fun getItemCount(): Int = themes.size

}