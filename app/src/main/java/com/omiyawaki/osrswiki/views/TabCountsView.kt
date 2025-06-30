package com.omiyawaki.osrswiki.views

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import androidx.core.widget.TextViewCompat
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ViewTabsCountBinding

class TabCountsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewTabsCountBinding.inflate(LayoutInflater.from(context), this)

    init {
        // Set the layout params here in the code, which will override any XML attributes.
        // This ensures the view is always the correct size.
        layoutParams = ViewGroup.LayoutParams(dpToPx(48f), ViewGroup.LayoutParams.MATCH_PARENT)
        
        // Find the root of the inflated layout and set its background for ripple effect.
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        setBackgroundResource(outValue.resourceId)
        isFocusable = true
        isClickable = true
        
        updateTabCount(false)
    }

    fun updateTabCount(animation: Boolean) {
        val count = OSRSWikiApp.instance.tabList.size.coerceAtMost(99) // Max at 99
        binding.tabsCountText.text = count.toString()
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(binding.tabsCountText, 7, 10, 1, TypedValue.COMPLEX_UNIT_SP)
        
        if (animation && count > 0) {
            try {
                startAnimation(AnimationUtils.loadAnimation(context, R.anim.tab_list_zoom_enter))
            } catch (e: Exception) {
                // Fallback animation
                alpha = 0f
                animate().alpha(1f).duration = 150
            }
        }
    }

    /**
     * A simple helper function to convert density-independent pixels (dp) to pixels (px).
     */
    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
