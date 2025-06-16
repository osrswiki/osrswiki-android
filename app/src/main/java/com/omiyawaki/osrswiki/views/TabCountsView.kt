package com.omiyawaki.osrswiki.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.AnimationUtils
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.util.DimenUtil

class TabCountsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint().apply { isAntiAlias = true }
    private val borderPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val textPaint = Paint().apply { isAntiAlias = true }
    private val textBounds = Rect()

    private var text: String = ""
    private var cornerRadius = DimenUtil.dpToPx(4f)
    // Set stroke width to the requested midpoint thickness.
    private val strokeWidth = DimenUtil.dpToPx(2.25f)

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        fun getAttrColor(@AttrRes attrId: Int): Int {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(attrId, typedValue, true)
            return typedValue.data
        }

        val styledAttributes = context.theme.obtainStyledAttributes(attrs, R.styleable.TabCountsView, 0, 0)
        val backgroundColor = styledAttributes.getColor(
            R.styleable.TabCountsView_tabCountBackgroundColor,
            ContextCompat.getColor(context, android.R.color.transparent)
        )
        val textColor = styledAttributes.getColor(
            R.styleable.TabCountsView_tabCountTextColor,
            getAttrColor(com.google.android.material.R.attr.colorOnSurface)
        )
        val borderColor = styledAttributes.getColor(
            R.styleable.TabCountsView_tabCountBorderColor,
            ContextCompat.getColor(context, android.R.color.transparent)
        )
        styledAttributes.recycle()

        backgroundPaint.color = backgroundColor
        borderPaint.color = borderColor
        borderPaint.strokeWidth = strokeWidth

        textPaint.color = textColor
        textPaint.textSize = DimenUtil.dpToPx(10f)
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textAlign = Paint.Align.CENTER

        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        setBackgroundResource(outValue.resourceId)
        isFocusable = true
        isClickable = true

        updateTabCount(false)
    }

    fun updateTabCount(animation: Boolean) {
        val count = OSRSWikiApp.instance.tabList.size
        text = count.toString()
        invalidate()

        if (animation && count > 0) {
            try {
                startAnimation(AnimationUtils.loadAnimation(context, R.anim.tab_list_zoom_enter))
            } catch (e: Exception) {
                alpha = 0f
                animate().alpha(1f).duration = 150
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Force the drawing area to be a square centered in the view
        val side = width.coerceAtMost(height) - strokeWidth
        val cx = width / 2f
        val cy = height / 2f
        val left = cx - side / 2f
        val top = cy - side / 2f
        val right = cx + side / 2f
        val bottom = cy + side / 2f
        val effectiveCornerRadius = cornerRadius.coerceAtMost(side / 2)

        if (backgroundPaint.color != ContextCompat.getColor(context, android.R.color.transparent)) {
            canvas.drawRoundRect(left, top, right, bottom, effectiveCornerRadius, effectiveCornerRadius, backgroundPaint)
        }

        canvas.drawRoundRect(left, top, right, bottom, effectiveCornerRadius, effectiveCornerRadius, borderPaint)

        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val textY = cy + textBounds.height() / 2f - textBounds.bottom
        canvas.drawText(text, cx, textY, textPaint)
    }
}
