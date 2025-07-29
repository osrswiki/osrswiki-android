package com.omiyawaki.osrswiki.views

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.DecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import com.omiyawaki.osrswiki.BuildConfig
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Modern browser-style toolbar handler that provides smooth, responsive hiding behavior.
 * Implements all-scroll responsiveness, GPU-accelerated animations, and proper state management.
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
    private var snapAnimator: ValueAnimator? = null
    
    // Enhanced state management
    private var state = State.IDLE
    private var lastScrollY = 0
    private var lastScrollTime = 0L
    private var scrollVelocity = 0f
    private var pendingScrollUpdate: Runnable? = null
    
    // iOS-style scroll smoothing with stronger filtering
    private var smoothedDelta = 0f
    private var lastMovementDirection = 0 // -1, 0, 1 for up, none, down
    private var scrollMomentum = 0f // Track momentum for natural snapping
    
    // Touch tracking
    private var isTouching = false
    private var touchStartY = 0f
    private var touchStartOffset = 0
    private var velocityTracker = VelocityTracker()
    
    // Frame rate optimization
    private val mainHandler = Handler(Looper.getMainLooper())
    
    enum class State {
        IDLE,           // No interaction
        SCROLLING,      // Active scrolling (touch or momentum)
        TOUCHING,       // User finger is down
        ANIMATING       // Snap animation in progress
    }
    
    init {
        setupBehavior()
        setupOffsetTracking()
    }
    
    private fun setupBehavior() {
        val params = appBarLayout.layoutParams as? CoordinatorLayout.LayoutParams
        behavior = params?.behavior as? AppBarLayout.Behavior
        
        if (behavior == null) {
            behavior = AppBarLayout.Behavior()
            params?.behavior = behavior
        }
    }
    
    private fun setupOffsetTracking() {
        appBarLayout.addOnOffsetChangedListener { _, verticalOffset ->
            totalScrollRange = appBarLayout.totalScrollRange
            currentOffset = verticalOffset
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Offset: $verticalOffset / -$totalScrollRange (state=$state)")
            }
        }
    }
    
    /**
     * Sets the scrollable view that will drive the toolbar movement
     */
    fun setScrollView(scrollView: ObservableWebView) {
        setupTouchTracking(scrollView)
        setupScrollTracking(scrollView)
    }
    
    private fun setupTouchTracking(scrollView: ObservableWebView) {
        scrollView.addOnDownMotionEventListener {
            handleTouchStart(scrollView)
        }
        
        scrollView.addOnUpOrCancelMotionEventListener {
            handleTouchEnd()
        }
    }
    
    private fun setupScrollTracking(scrollView: ObservableWebView) {
        scrollView.addOnScrollChangeListener { oldScrollY, scrollY, isHumanScroll ->
            // Always show toolbar when near top
            if (scrollY <= NEAR_TOP_THRESHOLD) {
                showToolbar()
                return@addOnScrollChangeListener
            }
            
            // Handle all scroll events (not just human/touch)
            handleScrollEvent(oldScrollY, scrollY, isHumanScroll, scrollView)
        }
    }
    
    private fun handleTouchStart(scrollView: ObservableWebView) {
        isTouching = true
        touchStartY = scrollView.lastTouchY
        touchStartOffset = currentOffset
        state = State.TOUCHING
        
        velocityTracker.clear()
        cancelSnapAnimation()
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Touch START at Y=$touchStartY, offset=$currentOffset")
        }
    }
    
    private fun handleTouchEnd() {
        isTouching = false
        
        // iOS-style: immediate momentum-based snapping to prevent intermediate states
        if (state == State.TOUCHING) {
            state = State.SCROLLING
            
            // Snap immediately based on momentum instead of delayed position-based snapping
            performMomentumBasedSnap()
        }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Touch END, performing momentum-based snap")
        }
    }
    
    private fun handleScrollEvent(oldScrollY: Int, scrollY: Int, isHumanScroll: Boolean, scrollView: ObservableWebView) {
        val currentTime = System.currentTimeMillis()
        val scrollDelta = scrollY - oldScrollY
        
        // Update velocity tracking
        if (currentTime - lastScrollTime > 0) {
            scrollVelocity = scrollDelta.toFloat() / (currentTime - lastScrollTime) * 1000f
        }
        
        lastScrollY = scrollY
        lastScrollTime = currentTime
        
        // Throttle scroll updates for performance (60fps = 16.67ms)
        pendingScrollUpdate?.let { mainHandler.removeCallbacks(it) }
        pendingScrollUpdate = Runnable {
            processScrollMovement(scrollDelta, scrollView)
        }
        mainHandler.post(pendingScrollUpdate!!)
    }
    
    private fun processScrollMovement(scrollDelta: Int, scrollView: ObservableWebView) {
        // Cancel ongoing snap animation if user starts scrolling
        // This prevents the interface from feeling unresponsive during animations
        if (state == State.ANIMATING) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Canceling snap animation due to new scroll input")
            }
            cancelSnapAnimation()
            state = if (isTouching) State.TOUCHING else State.SCROLLING
        }
        
        // Use consistent scroll delta approach for both touch and momentum scrolling
        // This eliminates coordinate system conflicts and provides more reliable behavior
        val movement = calculateScrollMovement(scrollDelta)
        
        if (BuildConfig.DEBUG && movement != 0) {
            Log.d(TAG, "Scroll: delta=$scrollDelta, movement=$movement, velocity=${String.format("%.1f", scrollVelocity)}, state=$state")
        }
        
        if (movement != 0) {
            updateToolbarPosition(movement)
            updateVelocityTracking(movement)
        }
        
        // Update state
        if (!isTouching && state != State.ANIMATING) {
            state = if (abs(scrollVelocity) > MIN_SCROLL_VELOCITY) State.SCROLLING else State.IDLE
        }
    }
    
    
    private fun calculateScrollMovement(scrollDelta: Int): Int {
        // Apply smoothing filter to eliminate jitter from micro-movements
        val smoothedMovement = applySmoothingFilter(scrollDelta)
        
        if (BuildConfig.DEBUG && smoothedMovement != -scrollDelta) {
            Log.d(TAG, "Smoothing: delta=$scrollDelta → movement=$smoothedMovement (was ${-scrollDelta})")
        }
        
        return smoothedMovement
    }
    
    /**
     * iOS-style low-pass filter to eliminate high-frequency jitter
     * Uses more aggressive smoothing similar to iOS (ALPHA = 0.2)
     */
    private fun applySmoothingFilter(scrollDelta: Int): Int {
        // iOS-style low-pass filter with stronger smoothing
        smoothedDelta = smoothedDelta * IOS_SMOOTHING_FACTOR + scrollDelta * (1f - IOS_SMOOTHING_FACTOR)
        
        // Track momentum for natural snapping behavior
        scrollMomentum = scrollMomentum * MOMENTUM_DECAY + scrollDelta * (1f - MOMENTUM_DECAY)
        
        // Only apply movement if accumulated movement is significant
        val absSmoothed = kotlin.math.abs(smoothedDelta)
        if (absSmoothed < IOS_MOVEMENT_THRESHOLD) {
            return 0 // Not enough movement yet (iOS uses higher threshold)
        }
        
        // Direction consistency check with stronger requirements
        val smoothedDirection = if (smoothedDelta > 0) 1 else -1
        if (lastMovementDirection != 0 && lastMovementDirection != smoothedDirection) {
            // iOS requires more movement to change direction (prevents jitter)
            if (absSmoothed < IOS_DIRECTION_CHANGE_THRESHOLD) {
                return 0
            }
        }
        
        // Apply the movement and reset accumulator
        val movement = -smoothedDirection * kotlin.math.ceil(absSmoothed).toInt()
        smoothedDelta = 0f
        lastMovementDirection = smoothedDirection
        
        if (BuildConfig.DEBUG && movement != 0) {
            Log.d(TAG, "iOS Filter: momentum=${String.format("%.1f", scrollMomentum)}, movement=$movement")
        }
        
        return movement
    }
    
    private fun updateToolbarPosition(movement: Int) {
        val newOffset = currentOffset + movement
        val clampedOffset = max(-totalScrollRange, min(0, newOffset))
        
        if (clampedOffset != currentOffset) {
            setOffsetSmooth(clampedOffset)
        }
    }
    
    private fun updateVelocityTracking(movement: Int) {
        velocityTracker.addMovement(movement.toFloat())
    }
    
    /**
     * Set toolbar offset using optimized method to prevent layout thrashing
     */
    private fun setOffsetSmooth(offset: Int) {
        behavior?.let {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Setting offset: $currentOffset → $offset")
            }
            
            // Set the offset directly on the behavior
            it.topAndBottomOffset = offset
            
            // Force a layout update using requestLayout on the AppBarLayout
            appBarLayout.requestLayout()
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "After setting: behavior.offset=${it.topAndBottomOffset}, currentOffset=$currentOffset")
            }
        }
    }
    
    /**
     * Immediately show the toolbar (used for near-top scenarios)
     */
    private fun showToolbar() {
        if (currentOffset != 0) {
            cancelSnapAnimation()
            setOffsetSmooth(0)
            state = State.IDLE
        }
    }
    
    private fun cancelSnapAnimation() {
        snapAnimator?.cancel()
        snapAnimator = null
    }
    
    /**
     * iOS-style momentum-based snapping that prevents intermediate states
     * Always snaps immediately based on scroll momentum, not position
     */
    private fun performMomentumBasedSnap() {
        if (totalScrollRange == 0) return
        
        state = State.ANIMATING
        
        // Get final velocity from tracker and momentum
        val finalVelocity = velocityTracker.getVelocity()
        val currentVisibility = 1f - (abs(currentOffset).toFloat() / totalScrollRange)
        
        // iOS-style: momentum dominates snap decision
        val targetOffset = when {
            // Strong momentum overrides position completely
            scrollMomentum > IOS_MOMENTUM_THRESHOLD -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Momentum snap → HIDE (momentum=${String.format("%.1f", scrollMomentum)})")
                -totalScrollRange // Hide
            }
            scrollMomentum < -IOS_MOMENTUM_THRESHOLD -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Momentum snap → SHOW (momentum=${String.format("%.1f", scrollMomentum)})")
                0 // Show
            }
            // Fallback to touch velocity if momentum is weak
            finalVelocity > VELOCITY_THRESHOLD -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Touch velocity snap → SHOW (vel=$finalVelocity)")
                0 // Show
            }
            finalVelocity < -VELOCITY_THRESHOLD -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Touch velocity snap → HIDE (vel=$finalVelocity)")
                -totalScrollRange // Hide
            }
            // Only use position if no clear momentum/velocity
            else -> {
                val target = if (currentVisibility >= 0.5f) 0 else -totalScrollRange
                if (BuildConfig.DEBUG) Log.d(TAG, "Position snap → ${if (target == 0) "SHOW" else "HIDE"} (vis=${String.format("%.1f", currentVisibility * 100)}%)")
                target
            }
        }
        
        // Skip if already at target
        if (abs(currentOffset - targetOffset) < SNAP_TOLERANCE) {
            state = State.IDLE
            return
        }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "iOS Snap: $currentOffset → $targetOffset (momentum=${String.format("%.1f", scrollMomentum)}, vel=$finalVelocity)")
        }
        
        animateToTarget(targetOffset)
    }
    
    /**
     * Legacy snap method for comparison (now unused)
     */
    private fun performSnapToNearestState() {
        if (totalScrollRange == 0 || state == State.ANIMATING) return
        
        state = State.ANIMATING
        
        // Get final velocity from tracker
        val finalVelocity = velocityTracker.getVelocity()
        
        // Calculate current visibility percentage
        val visibilityPercent = 1f - (abs(currentOffset).toFloat() / totalScrollRange)
        
        // Determine target with improved logic
        val targetOffset = determineSnapTarget(visibilityPercent, finalVelocity, scrollVelocity)
        
        // Skip if already at target
        if (abs(currentOffset - targetOffset) < SNAP_TOLERANCE) {
            state = State.IDLE
            return
        }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Snapping: $currentOffset → $targetOffset (vis=${String.format("%.1f", visibilityPercent * 100)}%, vel=$finalVelocity)")
        }
        
        animateToTarget(targetOffset)
    }
    
    private fun determineSnapTarget(visibilityPercent: Float, touchVelocity: Float, scrollVel: Float): Int {
        val target = when {
            // Strong touch velocity overrides everything
            touchVelocity > VELOCITY_THRESHOLD -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Snap: Touch velocity override -> SHOW (touchVel=$touchVelocity)")
                0 // Show
            }
            touchVelocity < -VELOCITY_THRESHOLD -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Snap: Touch velocity override -> HIDE (touchVel=$touchVelocity)")
                -totalScrollRange // Hide
            }
            
            // Medium scroll velocity influences decision - FIXED: reversed logic
            scrollVel > SCROLL_VELOCITY_THRESHOLD && visibilityPercent < 0.8f -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Snap: Scroll down fast -> HIDE (scrollVel=$scrollVel, vis=${String.format("%.1f", visibilityPercent * 100)}%)")
                -totalScrollRange // Hide when scrolling down fast
            }
            scrollVel < -SCROLL_VELOCITY_THRESHOLD && visibilityPercent > 0.2f -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Snap: Scroll up fast -> SHOW (scrollVel=$scrollVel, vis=${String.format("%.1f", visibilityPercent * 100)}%)")
                0 // Show when scrolling up fast
            }
            
            // Default to position-based snapping
            visibilityPercent >= SNAP_THRESHOLD -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Snap: Position-based -> SHOW (vis=${String.format("%.1f", visibilityPercent * 100)}%)")
                0 // Show if more than 50% visible
            }
            else -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Snap: Position-based -> HIDE (vis=${String.format("%.1f", visibilityPercent * 100)}%)")
                -totalScrollRange // Hide if less than 50% visible
            }
        }
        return target
    }
    
    private fun animateToTarget(targetOffset: Int) {
        cancelSnapAnimation()
        
        snapAnimator = ValueAnimator.ofInt(currentOffset, targetOffset).apply {
            duration = calculateAnimationDuration(currentOffset, targetOffset)
            interpolator = DecelerateInterpolator()
            
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                setOffsetSmooth(value)
            }
            
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    state = State.IDLE
                    snapAnimator = null
                }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    state = State.IDLE
                    snapAnimator = null
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            
            start()
        }
    }
    
    private fun calculateAnimationDuration(from: Int, to: Int): Long {
        val distance = abs(to - from)
        val maxDistance = totalScrollRange
        
        if (maxDistance == 0) return SNAP_ANIMATION_DURATION
        
        val ratio = distance.toFloat() / maxDistance
        return (SNAP_ANIMATION_DURATION * (0.3f + 0.7f * ratio)).toLong()
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
        
        // Behavior constants
        private const val NEAR_TOP_THRESHOLD = 50 // Always show toolbar within 50px of top
        private const val SNAP_THRESHOLD = 0.5f // Snap to show if >50% visible
        private const val SNAP_TOLERANCE = 5 // Skip animation if within 5px of target
        
        // Animation constants
        private const val SNAP_ANIMATION_DURATION = 200L // Base duration of snap animation
        private const val MOMENTUM_SETTLE_DELAY = 100L // Delay before snap after touch release (increased for less aggressive snapping)
        
        // Velocity thresholds (adjusted to reduce excessive snapping)
        private const val VELOCITY_THRESHOLD = 1200f // Touch velocity (px/s) to override position (increased)
        private const val SCROLL_VELOCITY_THRESHOLD = 500f // Scroll velocity threshold (increased from 300f)
        private const val MIN_SCROLL_VELOCITY = 50f // Minimum velocity to consider "scrolling"
        
        // Velocity tracking
        private const val VELOCITY_WINDOW = 100L // Time window for velocity calculation
        private const val VELOCITY_SAMPLE_SIZE = 8 // Number of recent movements to consider
        
        // iOS-style smoothing constants (more aggressive filtering)
        private const val IOS_SMOOTHING_FACTOR = 0.2f // iOS-like low-pass filter (stronger than 0.7)
        private const val IOS_MOVEMENT_THRESHOLD = 4.0f // Higher threshold like iOS
        private const val IOS_DIRECTION_CHANGE_THRESHOLD = 8.0f // iOS requires more movement to change direction
        private const val MOMENTUM_DECAY = 0.15f // Track momentum with decay
        private const val IOS_MOMENTUM_THRESHOLD = 2.0f // Momentum threshold for snap decisions
        
        // Legacy smoothing constants (for comparison)
        private const val SMOOTHING_FACTOR = 0.7f // Higher = more smoothing (0.0 to 1.0)
        private const val MOVEMENT_THRESHOLD = 2.0f // Minimum accumulated movement to apply
        private const val DIRECTION_CHANGE_THRESHOLD = 5.0f // Extra movement needed when direction changes
    }
}