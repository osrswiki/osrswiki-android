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
        
        // Transition to scrolling state briefly to handle momentum
        if (state == State.TOUCHING) {
            state = State.SCROLLING
            
            // Perform snap after a brief delay to allow momentum to settle
            mainHandler.postDelayed({
                performSnapToNearestState()
            }, MOMENTUM_SETTLE_DELAY)
        }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Touch END, transitioning to scrolling")
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
        // Map scroll movement to toolbar movement
        return -scrollDelta
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
            // Set the offset directly on the behavior
            it.topAndBottomOffset = offset
            
            // Request a lightweight layout pass instead of full requestLayout()
            appBarLayout.invalidate()
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
     * Perform snap animation to nearest state with improved velocity detection
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
    }
}