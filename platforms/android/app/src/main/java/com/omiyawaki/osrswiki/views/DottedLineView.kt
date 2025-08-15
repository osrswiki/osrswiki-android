package com.omiyawaki.osrswiki.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.google.android.material.R as MaterialR

class DottedLineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotRadiusDp = 1f
    private val dotGapDp = 6f // Increased from 3f to 6f

    init {
        val typedValue = TypedValue()
        // Resolve the color from the current theme
        context.theme.resolveAttribute(MaterialR.attr.colorOnSurfaceVariant, typedValue, true)
        paint.color = typedValue.data
        paint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radiusPx = dotRadiusDp * resources.displayMetrics.density
        val gapPx = dotGapDp * resources.displayMetrics.density
        val pathHeight = height.toFloat()
        val centerX = width / 2f
        var y = radiusPx

        // Draw circles vertically down the view
        while (y < pathHeight) {
            canvas.drawCircle(centerX, y, radiusPx, paint)
            y += (radiusPx * 2) + gapPx
        }
    }
}
