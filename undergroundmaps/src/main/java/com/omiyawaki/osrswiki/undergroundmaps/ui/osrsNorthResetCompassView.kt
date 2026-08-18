package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal const val OSRS_COMPASS_SIZE_DP = 48
internal const val OSRS_COMPASS_NORTH_TOLERANCE_DEGREES = 1.0
internal const val OSRS_COMPASS_FADE_DELAY_MILLIS = 500L
internal const val OSRS_COMPASS_FADE_DURATION_MILLIS = 500L
internal const val OSRS_COMPASS_RESET_DURATION_MILLIS = 150L

internal fun osrsNormalizeCompassBearing(bearing: Double): Double {
    if (!bearing.isFinite()) return 0.0
    val normalized = bearing % 360.0
    return if (normalized < 0.0) normalized + 360.0 else normalized
}

internal fun osrsCompassNeedleRotationDegrees(cameraBearing: Double): Float =
    -osrsNormalizeCompassBearing(cameraBearing).toFloat()

internal fun osrsCompassIsFacingNorth(cameraBearing: Double): Boolean {
    val normalized = osrsNormalizeCompassBearing(cameraBearing)
    return normalized <= OSRS_COMPASS_NORTH_TOLERANCE_DEGREES ||
        normalized >= 360.0 - OSRS_COMPASS_NORTH_TOLERANCE_DEGREES
}

/**
 * App-owned north-reset compass for Android 16 and newer renderers.
 *
 * MapLibre rotates its complete 48 dp CompassView. Android 16 can clip that transformed view at
 * its original render bounds. This view is never transformed: the disc and ring remain fixed,
 * while only the needle is rotated inside [onDraw].
 */
internal class osrsNorthResetCompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val redNeedlePath = Path()
    private val whiteNeedlePath = Path()
    private var normalizedBearingDegrees = 0.0
    private var needleRotationDegrees = 0f
    private var facingNorth = true
    private var fadePending = false

    private val fadeRunnable = Runnable {
        fadePending = false
        if (!facingNorth) return@Runnable
        animate().cancel()
        animate()
            .alpha(0f)
            .setDuration(OSRS_COMPASS_FADE_DURATION_MILLIS)
            .withEndAction {
                if (facingNorth) visibility = INVISIBLE
            }
            .start()
    }

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        rotation = 0f
        alpha = 0f
        visibility = INVISIBLE
    }

    fun updateBearing(cameraBearing: Double) {
        normalizedBearingDegrees = osrsNormalizeCompassBearing(cameraBearing)
        needleRotationDegrees = osrsCompassNeedleRotationDegrees(cameraBearing)
        facingNorth = osrsCompassIsFacingNorth(cameraBearing)
        rotation = 0f
        invalidate()

        if (facingNorth) {
            scheduleFadeIfVisible()
        } else {
            showImmediately()
        }
    }

    fun release() {
        removeCallbacks(fadeRunnable)
        fadePending = false
        animate().cancel()
    }

    fun normalizedBearingForTesting(): Double = normalizedBearingDegrees

    fun needleRotationForTesting(): Float = needleRotationDegrees

    fun facingNorthForTesting(): Boolean = facingNorth

    fun fadePendingForTesting(): Boolean = fadePending

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = min(width, height).toFloat()
        if (diameter <= 0f) return

        val centerX = width / 2f
        val centerY = height / 2f
        val artworkInset = dp(1f)
        val ringRadius = diameter / 2f - artworkInset - ringPaint.strokeWidth / 2f

        fillPaint.color = Color.BLACK
        canvas.drawCircle(centerX, centerY, ringRadius + ringPaint.strokeWidth / 2f, fillPaint)
        canvas.drawCircle(centerX, centerY, ringRadius, ringPaint)

        val needleTop = centerY - diameter * 0.28f
        val needleBottom = centerY + diameter * 0.28f
        val needleHalfWidth = diameter * 0.13f

        redNeedlePath.reset()
        redNeedlePath.moveTo(centerX, needleTop)
        redNeedlePath.lineTo(centerX + needleHalfWidth, centerY)
        redNeedlePath.lineTo(centerX - needleHalfWidth, centerY)
        redNeedlePath.close()

        whiteNeedlePath.reset()
        whiteNeedlePath.moveTo(centerX - needleHalfWidth, centerY)
        whiteNeedlePath.lineTo(centerX + needleHalfWidth, centerY)
        whiteNeedlePath.lineTo(centerX, needleBottom)
        whiteNeedlePath.close()

        val saveCount = canvas.save()
        canvas.rotate(needleRotationDegrees, centerX, centerY)
        fillPaint.color = OSRS_COMPASS_NORTH_COLOR
        canvas.drawPath(redNeedlePath, fillPaint)
        fillPaint.color = Color.WHITE
        canvas.drawPath(whiteNeedlePath, fillPaint)
        fillPaint.color = Color.BLACK
        canvas.drawCircle(centerX, centerY, dp(2.5f), fillPaint)
        canvas.restoreToCount(saveCount)
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private fun scheduleFadeIfVisible() {
        if (visibility != VISIBLE || fadePending) return
        fadePending = true
        postDelayed(fadeRunnable, OSRS_COMPASS_FADE_DELAY_MILLIS)
    }

    private fun showImmediately() {
        removeCallbacks(fadeRunnable)
        fadePending = false
        animate().cancel()
        alpha = 1f
        visibility = VISIBLE
    }

    private fun dp(value: Float): Float = value * density

    private companion object {
        const val OSRS_COMPASS_NORTH_COLOR = 0xFFFF806B.toInt()
    }
}
