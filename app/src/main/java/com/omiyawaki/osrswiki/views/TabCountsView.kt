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
import com.omiyawaki.osrswiki.util.DimenUtil

class TabCountsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // Corrected binding inflation for <merge> tag when 'this' is the parent
    private val binding = ViewTabsCountBinding.inflate(LayoutInflater.from(context), this)

    init {
        layoutParams = ViewGroup.LayoutParams(DimenUtil.roundedDpToPx(48.0f), ViewGroup.LayoutParams.MATCH_PARENT)
        
        val outValue = TypedValue()
        getContext().theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        setBackgroundResource(outValue.resourceId)
        isFocusable = true
    }

    fun updateTabCount(animation: Boolean) {
        val count = OSRSWikiApp.instance.tabList.size
        binding.tabsCountText.text = count.toString()
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(binding.tabsCountText, 7, 10, 1, TypedValue.COMPLEX_UNIT_SP)
        if (animation && count > 0) {
            try {
                startAnimation(AnimationUtils.loadAnimation(context, R.anim.tab_list_zoom_enter))
            } catch (e: Exception) {
                alpha = 0f
                animate().alpha(1f).duration = 150
            }
        }
    }
}
