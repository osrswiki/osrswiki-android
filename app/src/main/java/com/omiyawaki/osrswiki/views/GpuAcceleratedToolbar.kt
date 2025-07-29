package com.omiyawaki.osrswiki.views

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.omiyawaki.osrswiki.R
import kotlin.math.max
import kotlin.math.min

/**
 * GPU-accelerated toolbar container with custom shadow rendering.
 * 
 * Replaces AppBarLayout with a modern implementation designed for:
 * - Full GPU acceleration
 * - Custom shadow control
 * - No layout thrashing
 * - Material Design 3 elevation system
 * 
 * This view handles:
 * - Drawing Material Design shadows
 * - Managing toolbar background
 * - Coordinating with ModernToolbarController
 */
class GpuAcceleratedToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    // Shadow rendering components
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPath = Path()
    private var shadowBitmap: Bitmap? = null
    private var shadowCanvas: Canvas? = null
    
    // Material Design elevation
    private var currentElevation = 0f
    private var targetElevation = DEFAULT_ELEVATION
    
    // Background and styling
    private var toolbarBackground: Drawable? = null
    private var shadowColor: Int = 0
    private var surfaceColor: Int = 0
    
    // Shadow properties
    private var shadowRadius = 0f
    private var shadowOffsetY = 0f
    private var shadowAlpha = 1f
    
    init {
        initializeView(attrs)
        setupShadowRendering()
    }
    
    private fun initializeView(attrs: AttributeSet?) {
        // Enable hardware acceleration
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // Set default background
        surfaceColor = getThemeColor(android.R.attr.colorBackground)
        toolbarBackground = ContextCompat.getDrawable(context, R.color.white)
        setBackgroundColor(surfaceColor)
        
        // Initialize shadow properties from theme
        shadowColor = getThemeColor(android.R.attr.shadowColor)
        
        // Set elevation (this will trigger shadow setup)
        setElevation(targetElevation)
        
        // Handle custom attributes if provided
        attrs?.let { parseAttributes(it) }
    }
    
    private fun parseAttributes(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, intArrayOf(
            android.R.attr.elevation,
            android.R.attr.background
        ))
        
        try {
            // Override elevation if specified
            val customElevation = typedArray.getDimension(0, targetElevation)
            if (customElevation != targetElevation) {
                setToolbarElevation(customElevation)
            }
            
            // Override background if specified
            val customBackground = typedArray.getDrawable(1)
            customBackground?.let { toolbarBackground = it }
            
        } finally {
            typedArray.recycle()
        }
    }
    
    private fun setupShadowRendering() {
        calculateShadowProperties()
        setupShadowPaint()
    }
    
    private fun calculateShadowProperties() {
        // Material Design elevation to shadow mapping
        val density = resources.displayMetrics.density
        shadowRadius = currentElevation * SHADOW_RADIUS_MULTIPLIER * density
        shadowOffsetY = currentElevation * SHADOW_OFFSET_MULTIPLIER * density
    }
    
    private fun setupShadowPaint() {
        shadowPaint.apply {
            color = shadowColor
            alpha = (shadowAlpha * MAX_SHADOW_ALPHA).toInt()
            maskFilter = BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL)
            isAntiAlias = true
        }
    }
    
    /**
     * Set toolbar elevation with Material Design shadow
     */
    fun setToolbarElevation(elevation: Float) {
        if (elevation != currentElevation) {
            currentElevation = elevation
            targetElevation = elevation
            calculateShadowProperties()
            setupShadowPaint()
            invalidateShadow()
        }
    }
    
    /**
     * Set shadow opacity (0.0 = invisible, 1.0 = full opacity)
     */
    fun setShadowAlpha(alpha: Float) {
        val clampedAlpha = max(0f, min(1f, alpha))
        if (clampedAlpha != shadowAlpha) {
            shadowAlpha = clampedAlpha
            setupShadowPaint()
            invalidate()
        }
    }
    
    /**
     * Get current shadow alpha
     */
    fun getShadowAlpha(): Float = shadowAlpha
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            invalidateShadow()
        }
    }
    
    private fun invalidateShadow() {
        // Recreate shadow bitmap with new dimensions
        createShadowBitmap()
        invalidate()
    }
    
    private fun createShadowBitmap() {
        val width = this.width
        val height = this.height
        
        if (width <= 0 || height <= 0) return
        
        // Create bitmap for shadow rendering (include shadow bounds)
        val shadowBounds = (shadowRadius + shadowOffsetY).toInt()
        val bitmapWidth = width
        val bitmapHeight = height + shadowBounds
        
        try {
            shadowBitmap?.recycle()
            shadowBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            shadowCanvas = Canvas(shadowBitmap!!)
            
            // Draw shadow shape
            drawShadowShape(shadowCanvas!!, width, height)
            
        } catch (e: OutOfMemoryError) {
            // Fallback: disable custom shadow
            shadowBitmap = null
            shadowCanvas = null
        }
    }
    
    private fun drawShadowShape(canvas: Canvas, width: Int, height: Int) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        
        if (shadowRadius <= 0f) return
        
        // Create rounded rectangle path for shadow
        shadowPath.reset()
        val rect = RectF(
            shadowRadius,
            shadowRadius,
            width - shadowRadius,
            height + shadowOffsetY
        )
        
        shadowPath.addRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, Path.Direction.CW)
        
        // Draw shadow
        canvas.drawPath(shadowPath, shadowPaint)
    }
    
    override fun onDraw(canvas: Canvas) {
        // Draw custom shadow if available
        shadowBitmap?.let { bitmap ->
            if (!bitmap.isRecycled && shadowAlpha > 0f) {
                val shadowPaintAlpha = Paint().apply { 
                    alpha = (shadowAlpha * 255).toInt() 
                }
                canvas.drawBitmap(bitmap, 0f, 0f, shadowPaintAlpha)
            }
        }
        
        // Draw toolbar background
        toolbarBackground?.let { background ->
            background.setBounds(0, 0, width, height)
            background.draw(canvas)
        }
        
        super.onDraw(canvas)
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up shadow bitmap
        shadowBitmap?.recycle()
        shadowBitmap = null
        shadowCanvas = null
    }
    
    /**
     * Animate elevation change
     */
    fun animateElevationTo(targetElevation: Float, duration: Long = ELEVATION_ANIMATION_DURATION) {
        if (currentElevation == targetElevation) return
        
        val startElevation = currentElevation
        val elevationAnimator = android.animation.ValueAnimator.ofFloat(startElevation, targetElevation)
        
        elevationAnimator.apply {
            this.duration = duration
            interpolator = android.view.animation.DecelerateInterpolator()
            
            addUpdateListener { animation ->
                val animatedElevation = animation.animatedValue as Float
                setToolbarElevation(animatedElevation)
            }
        }
        
        elevationAnimator.start()
    }
    
    /**
     * Get theme color by attribute
     */
    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
                   typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            typedValue.data
        } else {
            Color.BLACK // fallback
        }
    }
    
    companion object {
        // Material Design elevation constants
        private const val DEFAULT_ELEVATION = 9.75f // dp - matches original AppBarLayout
        private const val SHADOW_RADIUS_MULTIPLIER = 0.5f
        private const val SHADOW_OFFSET_MULTIPLIER = 0.3f
        private const val MAX_SHADOW_ALPHA = 0.3f
        private const val CORNER_RADIUS = 0f // Sharp corners for toolbar
        
        // Animation constants
        private const val ELEVATION_ANIMATION_DURATION = 200L
    }
}