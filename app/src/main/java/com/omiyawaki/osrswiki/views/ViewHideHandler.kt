package com.omiyawaki.osrswiki.views

import android.animation.ValueAnimator
import android.util.Log
import android.view.animation.DecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Browser-style proportional handler for AppBarLayout that mimics mobile browser URL bar behavior.
 * Features smooth proportional movement during scroll and snap-to-complete on release.
 * 
 * @param appBarLayout The AppBarLayout to control
 * @param coordinatorLayout The parent CoordinatorLayout
 */
class ViewHideHandler(
    private val appBarLayout: AppBarLayout,
    private val coordinatorLayout: CoordinatorLayout
) {
    private var behavior: AppBarLayout.Behavior? = null
    private var totalScrollRange = 0
    private var currentOffset = 0
    private var isAnimating = false
    private var snapAnimator: ValueAnimator? = null
    
    // Touch tracking
    private var isTouching = false
    private var lastTouchY = 0f
    private var touchStartOffset = 0
    private var velocityTracker = VelocityTracker()
    
    init {
        // Get the AppBarLayout behavior
        val params = appBarLayout.layoutParams as? CoordinatorLayout.LayoutParams
        behavior = params?.behavior as? AppBarLayout.Behavior
        
        // If no behavior exists, create one
        if (behavior == null) {
            behavior = AppBarLayout.Behavior()
            params?.behavior = behavior
        }
        
        // Track the total scroll range
        appBarLayout.addOnOffsetChangedListener { _, verticalOffset ->
            totalScrollRange = appBarLayout.totalScrollRange
            currentOffset = verticalOffset
            
            Log.d(TAG, "Offset changed: $verticalOffset / -$totalScrollRange")
        }
    }
    
    /**
     * Sets the scrollable view that will drive the toolbar movement
     */
    fun setScrollView(scrollView: ObservableWebView) {
        // Track touch state
        scrollView.addOnDownMotionEventListener {
            isTouching = true
            lastTouchY = scrollView.lastTouchY
            touchStartOffset = currentOffset
            velocityTracker.clear()
            
            // Cancel any ongoing snap animation
            snapAnimator?.cancel()
            
            Log.d(TAG, "Touch DOWN at Y=$lastTouchY, offset=$currentOffset")
        }
        
        scrollView.addOnUpOrCancelMotionEventListener {
            isTouching = false
            
            // Perform snap-to-complete animation
            val velocity = velocityTracker.getVelocity()
            performSnapAnimation(velocity)
            
            Log.d(TAG, "Touch UP, velocity=$velocity")
        }
        
        // Handle scroll events
        scrollView.addOnScrollChangeListener { _, scrollY, isHumanScroll ->
            if (!isHumanScroll) return@addOnScrollChangeListener
            
            // Always show toolbar when near top
            if (scrollY <= NEAR_TOP_THRESHOLD) {
                setOffset(0)
                return@addOnScrollChangeListener
            }
            
            // During touch, update position proportionally
            if (isTouching && !isAnimating) {
                val currentTouchY = scrollView.lastTouchY
                val touchDelta = currentTouchY - lastTouchY
                
                // Update velocity tracker
                velocityTracker.addMovement(touchDelta)
                
                // Map touch delta to offset change (inverted because finger up = scroll down)
                val offsetDelta = -touchDelta * SCROLL_TO_OFFSET_RATIO
                val newOffset = currentOffset + offsetDelta.toInt()
                
                // Clamp to valid range
                val clampedOffset = max(-totalScrollRange, min(0, newOffset))
                setOffset(clampedOffset)
                
                lastTouchY = currentTouchY
                
                Log.d(TAG, "Touch move: delta=$touchDelta, newOffset=$clampedOffset")
            }
        }
    }
    
    /**
     * Directly set the toolbar offset
     */
    private fun setOffset(offset: Int) {
        behavior?.let {
            it.topAndBottomOffset = offset
            appBarLayout.requestLayout()
        }
    }
    
    /**
     * Perform snap animation to nearest state
     */
    private fun performSnapAnimation(velocity: Float) {
        if (totalScrollRange == 0) return
        
        // Calculate current visibility percentage
        val visibilityPercent = 1f - (abs(currentOffset).toFloat() / totalScrollRange)
        
        // Determine target based on position and velocity
        val targetOffset = when {
            // Strong velocity overrides position
            velocity > VELOCITY_THRESHOLD -> 0 // Show
            velocity < -VELOCITY_THRESHOLD -> -totalScrollRange // Hide
            // Otherwise use position
            visibilityPercent >= SNAP_THRESHOLD -> 0 // Show if more than 50% visible
            else -> -totalScrollRange // Hide if less than 50% visible
        }
        
        // Skip if already at target
        if (currentOffset == targetOffset) return
        
        Log.d(TAG, "Snapping from $currentOffset to $targetOffset (visibility=$visibilityPercent, velocity=$velocity)")
        
        // Animate to target
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(currentOffset, targetOffset).apply {
            duration = SNAP_ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                setOffset(value)
            }
            
            start()
        }
        
        isAnimating = true
        snapAnimator?.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                isAnimating = false
            }
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationCancel(animation: android.animation.Animator) {
                isAnimating = false
            }
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
    }
    
    /**
     * Simple velocity tracker for gesture detection
     */
    private class VelocityTracker {
        private val movements = mutableListOf<Movement>()
        
        data class Movement(val delta: Float, val timestamp: Long)
        
        fun clear() {
            movements.clear()
        }
        
        fun addMovement(delta: Float) {
            movements.add(Movement(delta, System.currentTimeMillis()))
            
            // Keep only recent movements
            val cutoff = System.currentTimeMillis() - VELOCITY_WINDOW
            movements.removeAll { it.timestamp < cutoff }
        }
        
        fun getVelocity(): Float {
            if (movements.size < 2) return 0f
            
            val recent = movements.takeLast(VELOCITY_SAMPLE_SIZE)
            if (recent.size < 2) return 0f
            
            val totalDelta = recent.sumOf { it.delta.toDouble() }.toFloat()
            val timeDelta = recent.last().timestamp - recent.first().timestamp
            
            return if (timeDelta > 0) {
                (totalDelta / timeDelta) * 1000f // pixels per second
            } else {
                0f
            }
        }
    }
    
    companion object {
        private const val TAG = "ViewHideHandler"
        
        // Constants
        private const val NEAR_TOP_THRESHOLD = 50 // Always show toolbar within 50px of top
        private const val SCROLL_TO_OFFSET_RATIO = 1.0f // 1:1 mapping of scroll to toolbar movement
        private const val SNAP_THRESHOLD = 0.5f // Snap to show if >50% visible
        private const val SNAP_ANIMATION_DURATION = 200L // Duration of snap animation
        private const val VELOCITY_THRESHOLD = 500f // Velocity (px/s) to override position-based snap
        
        // Velocity tracking
        private const val VELOCITY_WINDOW = 100L // Time window for velocity calculation
        private const val VELOCITY_SAMPLE_SIZE = 5 // Number of recent movements to consider
    }
}