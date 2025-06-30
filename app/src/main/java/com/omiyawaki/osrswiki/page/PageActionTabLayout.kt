package com.omiyawaki.osrswiki.page

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.widget.TextViewCompat
import com.google.android.material.textview.MaterialTextView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.page.action.PageActionItem

// A simple DimenUtil placeholder - replace with a proper one
object DimenUtilPlaceholder {
    fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

// A simple ResourceUtil placeholder - replace with a proper one
object ResourceUtilPlaceholder {
    fun getThemedColorStateList(context: Context, attrId: Int): android.content.res.ColorStateList? {
        val typedValue = TypedValue()
        val theme = context.theme
        if (theme.resolveAttribute(attrId, typedValue, true)) {
            return if (typedValue.resourceId != 0) {
                androidx.core.content.ContextCompat.getColorStateList(context, typedValue.resourceId)
            } else {
                android.content.res.ColorStateList.valueOf(typedValue.data)
            }
        }
        return null
    }
    fun getThemedAttributeId(context: Context, attrId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.resourceId
    }
}

class PageActionTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var callback: PageActionItem.Callback? = null
    private val CLICK_DEBUG_TAG = "PageActionTabLayout_Click"

    init {
        orientation = HORIZONTAL
        update()
    }

    fun update() {
        removeAllViews()
        Log.d(CLICK_DEBUG_TAG, "update() called. Callback is null: ${callback == null}")

        val itemsToDisplay = PageActionItem.DEFAULT_BOTTOM_BAR_ITEMS_ORDER
        val typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val tintColor = ResourceUtilPlaceholder.getThemedColorStateList(context, com.google.android.material.R.attr.colorOnSurface)
            ?: android.content.res.ColorStateList.valueOf(0xFF808080.toInt())
        val backgroundResId = ResourceUtilPlaceholder.getThemedAttributeId(context, androidx.appcompat.R.attr.selectableItemBackgroundBorderless)
        val actualBackgroundResId = if (backgroundResId != 0) backgroundResId else android.R.drawable.list_selector_background

        itemsToDisplay.forEach { pageAction ->
            val view = MaterialTextView(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
                gravity = Gravity.CENTER
                setPadding(
                    DimenUtilPlaceholder.dpToPx(context,2f),
                    DimenUtilPlaceholder.dpToPx(context,12f),
                    DimenUtilPlaceholder.dpToPx(context,2f),
                    0
                )
                setBackgroundResource(actualBackgroundResId)
                setTextColor(tintColor)
                textAlignment = TEXT_ALIGNMENT_CENTER
                setTypeface(typeface, Typeface.NORMAL)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.page_action_label_text_size).toFloat())
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                isFocusable = true
                id = pageAction.viewIdRes

                // Configure the icon before the text. This ensures the view's measurement
                // pass accounts for the icon's size before laying out the text.
                setCompoundDrawablesWithIntrinsicBounds(0, pageAction.defaultIconResId, 0, 0)
                TextViewCompat.setCompoundDrawableTintList(this, tintColor)
                compoundDrawablePadding = -DimenUtilPlaceholder.dpToPx(context,4f)

                // Configure the text.
                text = context.getString(pageAction.titleResId)
                contentDescription = this.text

                setOnClickListener { clickedView ->
                    Log.d(CLICK_DEBUG_TAG, "View clicked! Action: $pageAction (View ID: ${clickedView.id}), Current PageActionTabLayout.callback is null: ${this@PageActionTabLayout.callback == null}")
                    this@PageActionTabLayout.callback?.let { cb ->
                        Log.d(CLICK_DEBUG_TAG, "Callback is NOT null. Calling pageAction.select() for $pageAction")
                        pageAction.select(cb)
                    } ?: run {
                        Log.w(CLICK_DEBUG_TAG, "Callback IS NULL. Cannot select action $pageAction.")
                    }
                }
            }
            addView(view)
        }
        requestLayout()
    }

    fun updateActionItemIcon(action: PageActionItem, @DrawableRes iconResId: Int) {
        findViewById<MaterialTextView>(action.viewIdRes)?.let { view ->
            val tintColor = ResourceUtilPlaceholder.getThemedColorStateList(context, com.google.android.material.R.attr.colorOnSurface)
                ?: android.content.res.ColorStateList.valueOf(0xFF808080.toInt()) // Fallback
            TextViewCompat.setCompoundDrawableTintList(view, tintColor)
            view.setCompoundDrawablesWithIntrinsicBounds(0, iconResId, 0, 0)
        }
    }
}
