package com.omiyawaki.osrswiki.views

import android.view.MotionEvent
import android.view.View
import com.google.android.material.appbar.AppBarLayout
import kotlin.math.abs

/**
 * Animation-aware handler for showing and hiding an AppBarLayout based on scroll state.
 * Solves common UX issues: slow scroll detection, oscillation loops, and reliable recall.
 * 
 * Key Features:
 * - Animation state tracking to ignore layout-induced scroll events
 * - Touch state detection for stationary finger scenarios  
 * - Velocity-based validation to detect real vs phantom scrolls
 * - Momentum-aware thresholds for responsive behavior
 *
 * @param targetView The AppBarLayout that will be hidden or shown.
 */
class ViewHideHandler(private val targetView: View) {
    private var lastScrollY = 0
    private var state = State.EXPANDED
    private var isAnimating = false
    private var animationStartTime = 0L
    private var isTouchDown = false
    private var lastTouchY = 0f
    private var touchVelocity = 0f
    
    // Track recent scroll events for momentum detection
    private val scrollHistory = ArrayDeque<ScrollEvent>(MOMENTUM_SAMPLE_SIZE)
    
    private data class ScrollEvent(val delta: Int, val timestamp: Long)
    
    private enum class State {
        EXPANDED,
        COLLAPSED,
        ANIMATING_TO_EXPANDED,
        ANIMATING_TO_COLLAPSED
    }

    /**
     * Sets the scrollable view that will trigger the hide/show behavior.
     * @param scrollView The scrolling view.
     */
    fun setScrollView(scrollView: ObservableWebView) {
        // Track touch state
        scrollView.addOnDownMotionEventListener {
            isTouchDown = true
            lastTouchY = scrollView.lastTouchY
            touchVelocity = 0f
        }
        
        scrollView.addOnUpOrCancelMotionEventListener {
            isTouchDown = false
            touchVelocity = 0f
        }
        
        scrollView.addOnScrollChangeListener { _, scrollY, isHumanScroll ->
            if (!isHumanScroll || targetView !is AppBarLayout) {
                return@addOnScrollChangeListener
            }
            
            // CRITICAL: Ignore all scrolls during animation to prevent oscillation
            if (isAnimating) {
                val currentTime = System.currentTimeMillis()
                // Check if animation should be done (typical AppBarLayout animation is ~300ms)
                if (currentTime - animationStartTime > ANIMATION_DURATION_MS) {
                    isAnimating = false
                    // Update state to final state
                    when (state) {
                        State.ANIMATING_TO_COLLAPSED -> state = State.COLLAPSED
                        State.ANIMATING_TO_EXPANDED -> state = State.EXPANDED
                        else -> { /* already in final state */ }
                    }
                } else {
                    // Still animating, ignore this scroll event
                    return@addOnScrollChangeListener
                }
            }
            
            // Position-aware behavior: always show at absolute top
            if (scrollY <= ABSOLUTE_TOP_THRESHOLD) {
                if (state == State.COLLAPSED || state == State.ANIMATING_TO_COLLAPSED) {
                    expandToolbar()
                }
                return@addOnScrollChangeListener
            }

            val dy = scrollY - lastScrollY
            val currentTime = System.currentTimeMillis()
            
            // Update touch velocity if finger is down
            if (isTouchDown && scrollView.lastTouchY != lastTouchY) {
                val touchDelta = scrollView.lastTouchY - lastTouchY
                touchVelocity = if (abs(touchDelta) > 0.1f) touchDelta else 0f
                lastTouchY = scrollView.lastTouchY
            }
            
            // Skip if scroll hasn't actually changed
            if (dy == 0) {
                return@addOnScrollChangeListener
            }
            
            // Detect stationary touch: finger down but not moving
            val isStationaryTouch = isTouchDown && abs(touchVelocity) < STATIONARY_TOUCH_THRESHOLD
            
            // Skip tiny scroll changes that are likely noise or layout adjustments
            // Use higher threshold during stationary touch to prevent false triggers
            val noiseThreshold = if (isStationaryTouch) {
                STATIONARY_NOISE_THRESHOLD
            } else {
                MIN_SCROLL_THRESHOLD
            }
            
            if (abs(dy) < noiseThreshold) {
                return@addOnScrollChangeListener
            }
            
            // Update scroll history for momentum detection
            scrollHistory.addLast(ScrollEvent(dy, currentTime))
            if (scrollHistory.size > MOMENTUM_SAMPLE_SIZE) {
                scrollHistory.removeFirst()
            }
            
            // Validate scroll velocity pattern
            if (!isScrollVelocityValid()) {
                // Erratic velocity pattern suggests layout-induced scrolls
                return@addOnScrollChangeListener
            }
            
            lastScrollY = scrollY
            
            // Determine if we have consistent scroll direction
            val isConsistentScroll = isScrollDirectionConsistent()
            
            // Choose threshold based on scroll consistency and touch state
            val threshold = when {
                isStationaryTouch -> STATIONARY_SCROLL_THRESHOLD  // Very high threshold
                isConsistentScroll -> CONSISTENT_SCROLL_THRESHOLD  // Lower threshold
                else -> ERRATIC_SCROLL_THRESHOLD                   // Higher threshold
            }
            
            // Apply hide/show logic with dynamic threshold
            when (state) {
                State.EXPANDED -> {
                    if (dy > threshold) {
                        // Scrolling down consistently enough to hide
                        collapseToolbar()
                    }
                }
                
                State.COLLAPSED -> {
                    if (dy < -threshold) {
                        // Scrolling up consistently enough to show
                        expandToolbar()
                    }
                }
                
                State.ANIMATING_TO_EXPANDED,
                State.ANIMATING_TO_COLLAPSED -> {
                    // Don't process new actions while animating
                }
            }
        }
    }
    
    /**
     * Validates that scroll velocity follows a reasonable pattern.
     * Animation-induced scrolls tend to have sudden velocity changes.
     */
    private fun isScrollVelocityValid(): Boolean {
        if (scrollHistory.size < 2) return true
        
        val recentEvents = scrollHistory.takeLast(2)
        val timeDelta = recentEvents[1].timestamp - recentEvents[0].timestamp
        
        if (timeDelta == 0L) return false
        
        // Check for sudden direction reversal with high velocity
        val prevDelta = recentEvents[0].delta
        val currDelta = recentEvents[1].delta
        
        // Sudden reversal suggests animation-induced adjustment
        if (prevDelta > 5 && currDelta < -5 || prevDelta < -5 && currDelta > 5) {
            return false
        }
        
        return true
    }
    
    /**
     * Checks if recent scroll events show consistent direction.
     * Consistent scrolling gets lower thresholds for better responsiveness.
     */
    private fun isScrollDirectionConsistent(): Boolean {
        if (scrollHistory.size < MOMENTUM_SAMPLE_SIZE) {
            return false
        }
        
        val signs = scrollHistory.map { 
            when {
                it.delta > 0 -> 1
                it.delta < 0 -> -1
                else -> 0
            }
        }
        
        val positiveCount = signs.count { it > 0 }
        val negativeCount = signs.count { it < 0 }
        
        // Consistent if 80% or more of recent scrolls are in the same direction
        val consistencyThreshold = (MOMENTUM_SAMPLE_SIZE * 0.8).toInt()
        return positiveCount >= consistencyThreshold || negativeCount >= consistencyThreshold
    }
    
    private fun collapseToolbar() {
        if (isAnimating) return
        
        state = State.ANIMATING_TO_COLLAPSED
        isAnimating = true
        animationStartTime = System.currentTimeMillis()
        
        (targetView as AppBarLayout).setExpanded(false, true)
        
        // Clear scroll history to prevent carryover
        scrollHistory.clear()
    }
    
    private fun expandToolbar() {
        if (isAnimating) return
        
        state = State.ANIMATING_TO_EXPANDED
        isAnimating = true
        animationStartTime = System.currentTimeMillis()
        
        (targetView as AppBarLayout).setExpanded(true, true)
        
        // Clear scroll history to prevent carryover
        scrollHistory.clear()
    }

    companion object {
        // Threshold values
        private const val CONSISTENT_SCROLL_THRESHOLD = 10      // For intentional scrolling
        private const val ERRATIC_SCROLL_THRESHOLD = 25         // For erratic scrolling
        private const val STATIONARY_SCROLL_THRESHOLD = 40     // Very high for stationary touch
        private const val MIN_SCROLL_THRESHOLD = 3              // Ignore tiny scroll changes
        private const val STATIONARY_NOISE_THRESHOLD = 8       // Higher noise threshold when stationary
        private const val ABSOLUTE_TOP_THRESHOLD = 50          // Auto-show within 50px of top
        
        // Touch detection
        private const val STATIONARY_TOUCH_THRESHOLD = 2f      // Velocity threshold for stationary
        
        // Animation tracking
        private const val ANIMATION_DURATION_MS = 300L         // Typical AppBarLayout animation
        
        // Momentum detection
        private const val MOMENTUM_SAMPLE_SIZE = 4              // Number of recent scrolls to analyze
    }
}