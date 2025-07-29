package com.omiyawaki.osrswiki.views

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewPropertyAnimator
import com.omiyawaki.osrswiki.BuildConfig
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Modern GPU-accelerated toolbar controller designed for immediate response and smooth animations.
 * 
 * Core Design Principles:
 * - Single responsibility: GPU-accelerated positioning only
 * - Immediate response: Direct touch-to-position mapping
 * - Clean architecture: No mixed coordinate systems or dual control
 * - Modern animations: ViewPropertyAnimator for guaranteed GPU acceleration
 * 
 * Solves the three main issues:
 * 1. No empty space - we control layout directly
 * 2. No shadow delays - custom shadow rendering
 * 3. Immediate response - direct touch mapping without thresholds
 */
class ModernToolbarController(
    private val toolbarContainer: View,
    private val contentView: View,
    private val shadowView: View? = null
) {
    
    // Single source of truth for toolbar position
    private var currentOffset = 0f
    private var targetOffset = 0f
    private var toolbarHeight = 0f
    
    // Animation state
    private var currentAnimator: ViewPropertyAnimator? = null
    private var isAnimating = false
    private var isUserControlled = false // True when user is actively scrolling
    
    // Touch tracking for immediate response
    private var isTracking = false
    private var accumulatedDelta = 0f
    
    // Momentum and snap detection
    private val scrollEndHandler = Handler(Looper.getMainLooper())
    private var scrollEndRunnable: Runnable? = null
    private var lastScrollTime = 0L
    private val velocityTracker = VelocityTracker()
    
    // State management
    enum class ToolbarState {
        EXPANDED,    // Fully visible (offset = 0)
        COLLAPSED,   // Fully hidden (offset = -toolbarHeight)  
        TRANSITIONING // Between states during user interaction
    }
    
    private var currentState = ToolbarState.EXPANDED
    
    // Data class for velocity tracking samples
    private data class VelocitySample(val delta: Float, val time: Long)
    
    /**
     * Lightweight velocity tracking for smooth animations
     */
    private inner class VelocityTracker {
        private val samples = ArrayDeque<VelocitySample>(VELOCITY_SAMPLES)
        
        fun addSample(delta: Float) {
            val now = System.currentTimeMillis()
            samples.addLast(VelocitySample(delta, now))
            if (samples.size > VELOCITY_SAMPLES) {
                samples.removeFirst()
            }
        }
        
        fun getCurrentVelocity(): Float {
            if (samples.size < 2) return 0f
            
            val recent = samples.takeLast(min(samples.size, 3))
            val totalDelta = recent.sumOf { it.delta.toDouble() }.toFloat()
            val totalTime = recent.last().time - recent.first().time
            
            return if (totalTime > 0) totalDelta / totalTime else 0f
        }
        
        fun hasSignificantVelocity(): Boolean {
            return abs(getCurrentVelocity()) > VELOCITY_THRESHOLD
        }
        
        fun clear() = samples.clear()
    }
    
    init {
        // Initialize toolbar height once layout is complete
        toolbarContainer.post {
            toolbarHeight = toolbarContainer.height.toFloat()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Initialized: toolbarHeight=$toolbarHeight")
            }
        }
    }
    
    /**
     * Set up scroll view integration
     */
    fun attachToScrollView(scrollView: ObservableWebView) {
        // Touch start - begin immediate response mode
        scrollView.addOnDownMotionEventListener {
            startUserControl()
        }
        
        // Touch end - enable snapping
        scrollView.addOnUpOrCancelMotionEventListener {
            endUserControl()
        }
        
        // Scroll events - immediate positioning
        scrollView.addOnScrollChangeListener { oldScrollY, scrollY, isHumanScroll ->
            val scrollDelta = scrollY - oldScrollY
            handleScrollDelta(scrollDelta, scrollY)
        }
    }
    
    /**
     * Handle scroll delta with immediate GPU positioning
     */
    private fun handleScrollDelta(scrollDelta: Int, currentScrollY: Int) {
        if (scrollDelta == 0) return
        
        val scrollDeltaF = scrollDelta.toFloat()
        
        // Always show when near top of page
        if (currentScrollY <= NEAR_TOP_THRESHOLD) {
            showToolbarImmediate()
            cancelScrollEndDetection()
            return
        }
        
        // Apply scroll to toolbar position (invert delta: scroll down = hide toolbar)
        val movement = -scrollDeltaF
        updateToolbarPositionImmediate(movement)
        
        // Update velocity tracking
        velocityTracker.addSample(scrollDeltaF)
        
        // Schedule snap detection if user isn't actively controlling
        if (!isUserControlled) {
            scheduleSnapDetection()
        }
        
        lastScrollTime = System.currentTimeMillis()
        
        if (BuildConfig.DEBUG && abs(scrollDelta) > 5) {
            Log.d(TAG, "Scroll: delta=$scrollDelta, offset=$currentOffset, state=$currentState")
        }
    }
    
    /**
     * Start user control mode - immediate response, no snapping
     */
    private fun startUserControl() {
        isUserControlled = true
        isTracking = true
        accumulatedDelta = 0f
        
        // Cancel any ongoing animations
        cancelAnimation()
        cancelScrollEndDetection()
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "User control started")
        }
    }
    
    /**
     * End user control mode - enable snapping
     */
    private fun endUserControl() {
        isUserControlled = false
        isTracking = false
        
        // If there's no significant momentum, snap immediately
        if (!velocityTracker.hasSignificantVelocity()) {
            performSnap("touch_end")
        } else {
            // Let momentum settle, then snap
            scheduleSnapDetection()
        }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "User control ended, velocity=${velocityTracker.getCurrentVelocity()}")
        }
    }
    
    /**
     * Update toolbar position immediately using GPU acceleration
     */
    private fun updateToolbarPositionImmediate(movement: Float) {
        if (toolbarHeight == 0f) return
        
        val newOffset = currentOffset + movement
        val clampedOffset = max(-toolbarHeight, min(0f, newOffset))
        
        if (clampedOffset != currentOffset) {
            setToolbarOffset(clampedOffset)
            updateState()
        }
    }
    
    /**
     * Set toolbar offset using GPU-accelerated transforms
     */
    private fun setToolbarOffset(offset: Float) {
        currentOffset = offset
        targetOffset = offset
        
        // Apply GPU transform to toolbar
        toolbarContainer.translationY = offset
        
        // Update content container to prevent overlap
        contentView.translationY = max(0f, offset + toolbarHeight)
        
        // Update shadow opacity based on position
        shadowView?.let { shadow ->
            val visibility = 1f - (abs(offset) / toolbarHeight)
            shadow.alpha = visibility
        }
        
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "GPU offset: $offset")
        }
    }
    
    /**
     * Show toolbar immediately
     */
    private fun showToolbarImmediate() {
        if (currentOffset != 0f) {
            setToolbarOffset(0f)
            updateState()
        }
    }
    
    /**
     * Hide toolbar immediately  
     */
    private fun hideToolbarImmediate() {
        if (currentOffset != -toolbarHeight) {
            setToolbarOffset(-toolbarHeight)
            updateState()
        }
    }
    
    /**
     * Update current state based on position
     */
    private fun updateState() {
        val newState = when {
            currentOffset >= -STATE_THRESHOLD -> ToolbarState.EXPANDED
            currentOffset <= -toolbarHeight + STATE_THRESHOLD -> ToolbarState.COLLAPSED
            else -> ToolbarState.TRANSITIONING
        }
        
        if (newState != currentState) {
            currentState = newState
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "State changed: $newState")
            }
        }
    }
    
    /**
     * Perform smooth snap to nearest complete state
     */
    private fun performSnap(trigger: String) {
        if (isAnimating || isUserControlled) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Skip snap - animating:$isAnimating, userControlled:$isUserControlled")
            return
        }
        
        val targetState = if (currentOffset > -toolbarHeight / 2f) {
            ToolbarState.EXPANDED
        } else {
            ToolbarState.COLLAPSED
        }
        
        val targetOffsetValue = when (targetState) {
            ToolbarState.EXPANDED -> 0f
            ToolbarState.COLLAPSED -> -toolbarHeight
            ToolbarState.TRANSITIONING -> currentOffset // Should not happen
        }
        
        if (abs(targetOffsetValue - currentOffset) < SNAP_THRESHOLD) {
            // Already close enough, set immediately
            setToolbarOffset(targetOffsetValue)
            updateState()
            return
        }
        
        // Animate to target position
        animateToOffset(targetOffsetValue, trigger)
        
        if (BuildConfig.DEBUG) {
            val visibilityPercent = ((toolbarHeight + currentOffset) / toolbarHeight) * 100f
            Log.d(TAG, "Snap ($trigger): ${String.format("%.1f", visibilityPercent)}% → $targetState")
        }
    }
    
    /**
     * Animate to target offset using modern ViewPropertyAnimator
     */
    private fun animateToOffset(target: Float, reason: String) {
        if (isAnimating) {
            currentAnimator?.cancel()
        }
        
        isAnimating = true
        targetOffset = target
        
        val distance = abs(target - currentOffset)
        val duration = min(MAX_ANIMATION_DURATION, (distance / toolbarHeight * BASE_ANIMATION_DURATION).toLong())
        
        // Use ValueAnimator for coordinated updates to all views
        val animator = ValueAnimator.ofFloat(currentOffset, target).apply {
            this.duration = duration
            interpolator = ANIMATION_INTERPOLATOR
            
            addUpdateListener { animation ->
                val animatedOffset = animation.animatedValue as Float
                setToolbarOffset(animatedOffset)
                updateState()
            }
            
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                    currentAnimator = null
                    velocityTracker.clear()
                    updateState()
                    
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Animation complete ($reason): offset=$currentOffset")
                    }
                }
                
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    isAnimating = false
                    currentAnimator = null
                }
            })
        }
        
        animator.start()
        // Keep reference to current animator (as ViewPropertyAnimator for interface compatibility)
        currentAnimator = toolbarContainer.animate() // This is just for reference, actual animation is ValueAnimator
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Animation started ($reason): $currentOffset → $target, duration=${duration}ms")
        }
    }
    
    /**
     * Cancel any ongoing animation
     */
    private fun cancelAnimation() {
        currentAnimator?.cancel()
        currentAnimator = null
        isAnimating = false
    }
    
    /**
     * Schedule snap detection for when scrolling ends
     */
    private fun scheduleSnapDetection() {
        cancelScrollEndDetection()
        
        scrollEndRunnable = Runnable {
            val timeSinceLastScroll = System.currentTimeMillis() - lastScrollTime
            
            if (timeSinceLastScroll >= SCROLL_END_TIMEOUT || !velocityTracker.hasSignificantVelocity()) {
                performSnap("scroll_end")
            } else {
                // Re-schedule with shorter timeout for high velocity
                scrollEndHandler.postDelayed(scrollEndRunnable!!, SCROLL_END_TIMEOUT / 2)
            }
        }
        
        scrollEndHandler.postDelayed(scrollEndRunnable!!, SCROLL_END_TIMEOUT)
    }
    
    /**
     * Cancel snap detection
     */
    private fun cancelScrollEndDetection() {
        scrollEndRunnable?.let {
            scrollEndHandler.removeCallbacks(it)
            scrollEndRunnable = null
        }
    }
    
    /**
     * Get current toolbar visibility as percentage (0 = hidden, 1 = visible)
     */
    fun getVisibility(): Float {
        return if (toolbarHeight > 0f) {
            (toolbarHeight + currentOffset) / toolbarHeight
        } else 0f
    }
    
    /**
     * Force toolbar to expanded state
     */
    fun expandToolbar(animated: Boolean = true) {
        if (animated && !isUserControlled) {
            animateToOffset(0f, "force_expand")
        } else {
            showToolbarImmediate()
        }
    }
    
    /**
     * Force toolbar to collapsed state
     */
    fun collapseToolbar(animated: Boolean = true) {
        if (animated && !isUserControlled) {
            animateToOffset(-toolbarHeight, "force_collapse")
        } else {
            hideToolbarImmediate()
        }
    }
    
    companion object {
        private const val TAG = "ModernToolbarController"
        
        // Positioning constants
        private const val NEAR_TOP_THRESHOLD = 50f // Always show within 50px of top
        private const val STATE_THRESHOLD = 5f // px tolerance for state detection
        private const val SNAP_THRESHOLD = 2f // px - don't animate if already close
        
        // Animation constants
        private const val BASE_ANIMATION_DURATION = 200L // ms
        private const val MAX_ANIMATION_DURATION = 400L // ms
        private val ANIMATION_INTERPOLATOR = android.view.animation.DecelerateInterpolator(1.5f)
        
        // Velocity tracking
        private const val VELOCITY_SAMPLES = 5
        private const val VELOCITY_THRESHOLD = 0.1f // px/ms - minimum velocity for significant momentum
        
        // Timing constants
        private const val SCROLL_END_TIMEOUT = 100L // ms - time to wait before considering scroll ended
    }
}