package com.omiyawaki.osrswiki.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.omiyawaki.osrswiki.R
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.pow

class osrsMapPrototypeOverviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val viewportBounds = RectF()
    private var mapProvider: (() -> MapLibreMap?)? = null
    private var onInteractionStarted: (() -> Unit)? = null
    private var onInteractionFinished: (() -> Unit)? = null
    private var onCenterRequested: ((Double, Double) -> Unit)? = null
    private var dragging = false
    private var completingTouchGesture = false
    private var lastDispatchMs = 0L

    fun configure(
        mapProvider: () -> MapLibreMap?,
        onInteractionStarted: () -> Unit,
        onInteractionFinished: () -> Unit,
        onCenterRequested: (Double, Double) -> Unit
    ) {
        this.mapProvider = mapProvider
        this.onInteractionStarted = onInteractionStarted
        this.onInteractionFinished = onInteractionFinished
        this.onCenterRequested = onCenterRequested
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.map_semantic_overview_accessibility_description)
        invalidate()
    }

    fun cameraChanged() {
        invalidate()
    }

    fun viewportBoundsForTesting(): osrsMapPrototypeScreenBounds {
        return osrsMapPrototypeScreenBounds(
            viewportBounds.left,
            viewportBounds.top,
            viewportBounds.right,
            viewportBounds.bottom
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val content = RectF(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            (width - paddingRight).toFloat(),
            (height - paddingBottom).toFloat()
        )
        if (content.width() <= 0f || content.height() <= 0f) return

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(68, 125, 155)
        canvas.drawRoundRect(content, dp(3f), dp(3f), paint)

        val land = Path().apply {
            moveTo(content.left + content.width() * 0.12f, content.top + content.height() * 0.36f)
            lineTo(content.left + content.width() * 0.30f, content.top + content.height() * 0.15f)
            lineTo(content.left + content.width() * 0.60f, content.top + content.height() * 0.10f)
            lineTo(content.left + content.width() * 0.88f, content.top + content.height() * 0.32f)
            lineTo(content.left + content.width() * 0.82f, content.top + content.height() * 0.72f)
            lineTo(content.left + content.width() * 0.56f, content.top + content.height() * 0.90f)
            lineTo(content.left + content.width() * 0.24f, content.top + content.height() * 0.82f)
            close()
        }
        paint.color = Color.rgb(112, 139, 79)
        canvas.drawPath(land, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.75f)
        paint.color = Color.argb(90, 255, 255, 255)
        for (step in 1..3) {
            val x = content.left + content.width() * step / 4f
            val y = content.top + content.height() * step / 4f
            canvas.drawLine(x, content.top, x, content.bottom, paint)
            canvas.drawLine(content.left, y, content.right, y, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(250, 214, 107)
        for (feature in osrsMapPrototypeOverlay.features) {
            if (feature.kind != osrsMapFeatureKind.POI) continue
            val point = gameToPoint(content, feature.gameX, feature.gameY)
            if (content.contains(point.first, point.second)) {
                canvas.drawCircle(point.first, point.second, dp(1.3f), paint)
            }
        }

        val map = mapProvider?.invoke()
        val target = map?.cameraPosition?.target ?: osrsMapPrototypeOverlay.initialCenter()
        val game = osrsMapPrototypeOverlay.latLngToGame(target)
        val center = gameToPoint(content, game.first, game.second)
        val zoom = map?.cameraPosition?.zoom ?: osrsMapPrototypeOverlay.initialZoom
        val widthFraction = (0.52 / 2.0.pow(zoom - 5.6)).coerceIn(0.055, 0.42).toFloat()
        val viewportWidth = content.width() * widthFraction
        val viewportHeight = (viewportWidth * content.height() / content.width()).coerceAtLeast(dp(10f))
        viewportBounds.set(
            (center.first - viewportWidth / 2f).coerceIn(content.left, content.right - viewportWidth),
            (center.second - viewportHeight / 2f).coerceIn(content.top, content.bottom - viewportHeight),
            (center.first + viewportWidth / 2f).coerceIn(content.left + viewportWidth, content.right),
            (center.second + viewportHeight / 2f).coerceIn(content.top + viewportHeight, content.bottom)
        )

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(50, 255, 40, 40)
        canvas.drawRect(viewportBounds, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = Color.rgb(215, 32, 32)
        canvas.drawRect(viewportBounds, paint)

        paint.strokeWidth = dp(1.5f)
        paint.color = Color.WHITE
        canvas.drawRoundRect(content, dp(3f), dp(3f), paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                onInteractionStarted?.invoke()
                dispatchCenter(event.x, event.y, force = true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                dispatchCenter(event.x, event.y, force = false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) return false
                dispatchCenter(event.x, event.y, force = true)
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                onInteractionFinished?.invoke()
                completingTouchGesture = true
                performClick()
                completingTouchGesture = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                onInteractionFinished?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (completingTouchGesture) return true
        val center = osrsMapPrototypeOverlay.latLngToGame(osrsMapPrototypeOverlay.initialCenter())
        dispatchAccessibleCenter(center.first, center.second)
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
        info.isClickable = true
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        return when (action) {
            AccessibilityNodeInfo.ACTION_CLICK -> performClick()
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id ->
                moveAccessibilityCenter(-1.0, 0.0)
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id ->
                moveAccessibilityCenter(1.0, 0.0)
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id ->
                moveAccessibilityCenter(0.0, 1.0)
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id ->
                moveAccessibilityCenter(0.0, -1.0)
            else -> super.performAccessibilityAction(action, arguments)
        }
    }

    private fun moveAccessibilityCenter(horizontal: Double, vertical: Double): Boolean {
        val map = mapProvider?.invoke()
        val target = map?.cameraPosition?.target ?: osrsMapPrototypeOverlay.initialCenter()
        val game = osrsMapPrototypeOverlay.latLngToGame(target)
        val zoom = map?.cameraPosition?.zoom ?: osrsMapPrototypeOverlay.initialZoom
        val step = (320.0 / 2.0.pow(zoom - osrsMapPrototypeOverlay.initialZoom))
            .coerceIn(48.0, 512.0)
        val gameX = (game.first + horizontal * step).coerceIn(SURFACE_MIN_X, SURFACE_MAX_X)
        val gameY = (game.second + vertical * step).coerceIn(SURFACE_MIN_Y, SURFACE_MAX_Y)
        dispatchAccessibleCenter(gameX, gameY)
        return true
    }

    private fun dispatchAccessibleCenter(gameX: Double, gameY: Double) {
        onInteractionStarted?.invoke()
        onCenterRequested?.invoke(gameX, gameY)
        onInteractionFinished?.invoke()
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED)
    }

    private fun dispatchCenter(x: Float, y: Float, force: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastDispatchMs < 32L) return
        lastDispatchMs = now
        val content = RectF(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            (width - paddingRight).toFloat(),
            (height - paddingBottom).toFloat()
        )
        val normalizedX = ((x - content.left) / content.width()).coerceIn(0f, 1f)
        val normalizedY = ((y - content.top) / content.height()).coerceIn(0f, 1f)
        val gameX = SURFACE_MIN_X + normalizedX * (SURFACE_MAX_X - SURFACE_MIN_X)
        val gameY = SURFACE_MAX_Y - normalizedY * (SURFACE_MAX_Y - SURFACE_MIN_Y)
        onCenterRequested?.invoke(gameX.toDouble(), gameY.toDouble())
    }

    private fun gameToPoint(content: RectF, gameX: Double, gameY: Double): Pair<Float, Float> {
        val normalizedX = ((gameX - SURFACE_MIN_X) / (SURFACE_MAX_X - SURFACE_MIN_X)).toFloat()
        val normalizedY = ((SURFACE_MAX_Y - gameY) / (SURFACE_MAX_Y - SURFACE_MIN_Y)).toFloat()
        return (content.left + normalizedX * content.width()) to
            (content.top + normalizedY * content.height())
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val SURFACE_MIN_X = 1024.0
        const val SURFACE_MAX_X = 4096.0
        const val SURFACE_MIN_Y = 2048.0
        const val SURFACE_MAX_Y = 4224.0
    }
}
