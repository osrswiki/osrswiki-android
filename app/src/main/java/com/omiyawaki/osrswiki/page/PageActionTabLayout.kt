package com.omiyawaki.osrswiki.page

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.widget.TextViewCompat
import com.google.android.material.textview.MaterialTextView
import com.omiyawaki.osrswiki.R // Your app's R class
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
        return null // Return null if attribute not found
    }
    fun getThemedAttributeId(context: Context, attrId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.resourceId // This might be 0 if not found, handle appropriately
    }
}


class PageActionTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var callback: PageActionItem.Callback? = null

    init {
        orientation = HORIZONTAL
        update()
    }

    fun update() {
        removeAllViews()

        val itemsToDisplay = PageActionItem.DEFAULT_BOTTOM_BAR_ITEMS_ORDER
        val typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        val tintColor = ResourceUtilPlaceholder.getThemedColorStateList(context, R.attr.placeholder_color)
                        ?: android.content.res.ColorStateList.valueOf(0xFF808080.toInt()) // Fallback to a gray

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
                // Use the renamed, more generic dimension resource
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.page_action_label_text_size).toFloat())
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END

                id = pageAction.viewIdRes
                text = context.getString(pageAction.titleResId)
                contentDescription = this.text

                TextViewCompat.setCompoundDrawableTintList(this, tintColor)
                setCompoundDrawablesWithIntrinsicBounds(0, pageAction.defaultIconResId, 0, 0)
                compoundDrawablePadding = -DimenUtilPlaceholder.dpToPx(context,4f)

                setOnClickListener {
                    callback?.let { cb -> pageAction.select(cb) }
                }
                isFocusable = true
            }
            addView(view)
        }
        requestLayout()
    }

    fun updateActionItemIcon(action: PageActionItem, @DrawableRes iconResId: Int) {
        findViewById<MaterialTextView>(action.viewIdRes)?.let { view ->
            val tintColor = ResourceUtilPlaceholder.getThemedColorStateList(context, R.attr.placeholder_color)
                            ?: android.content.res.ColorStateList.valueOf(0xFF808080.toInt()) // Fallback
            TextViewCompat.setCompoundDrawableTintList(view, tintColor)
            view.setCompoundDrawablesWithIntrinsicBounds(0, iconResId, 0, 0)
        }
    }
}
