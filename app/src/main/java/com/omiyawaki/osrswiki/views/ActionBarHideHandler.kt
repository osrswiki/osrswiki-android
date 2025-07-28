package com.omiyawaki.osrswiki.views

import android.view.View
import kotlin.math.abs
import kotlin.math.sign

/**
 * Modern action bar hide/show handler with sophisticated scroll detection.
 * Addresses common UX issues: slow scroll detection, flicker prevention, and reliable recall.
 * 
 * Features:
 * - Velocity-based thresholds (different for fast/slow scrolls)
 * - Hysteresis to prevent flickering at threshold boundaries  
 * - Position-aware behavior (always show at top, smart middle behavior)
 * - Smooth translation animations instead of binary show/hide
 * 
 * @param targetView The view to animate (typically bottom action bar)
 */
class ActionBarHideHandler(private val targetView: View) {
    
    private var lastScrollY = 0
    private var scrollVelocity = 0f
    private var lastScrollTime = System.currentTimeMillis()
    private var accumulatedDelta = 0
    private var state = State.SHOWN
    private var isAnimating = false
    
    // Track scroll momentum for better gesture detection
    private var scrollHistory = ArrayDeque<ScrollEvent>(VELOCITY_SAMPLE_SIZE)
    
    private data class ScrollEvent(val delta: Int, val timestamp: Long)
    
    private enum class State {
        SHOWN,
        HIDDEN,
        TRANSITIONING
    }
    
    /**
     * Sets the scrollable view that will trigger the hide/show behavior.
     */
    fun setScrollView(scrollView: ObservableWebView) {
        scrollView.addOnScrollChangeListener { _, scrollY, isHumanScroll ->
            if (!isHumanScroll) return@addOnScrollChangeListener
            
            val currentTime = System.currentTimeMillis()
            val dy = scrollY - lastScrollY
            
            // Skip if no actual scroll change
            if (dy == 0) return@addOnScrollChangeListener
            
            updateScrollVelocity(dy, currentTime)
            processScrollChange(scrollY, dy)
            
            lastScrollY = scrollY
            lastScrollTime = currentTime
        }
    }
    
    private fun updateScrollVelocity(dy: Int, currentTime: Long) {
        val timeDelta = currentTime - lastScrollTime
        if (timeDelta > 0) {
            // Calculate velocity as pixels per millisecond
            scrollVelocity = dy.toFloat() / timeDelta.toFloat()
            
            // Maintain rolling window of recent scroll events for momentum detection
            scrollHistory.addLast(ScrollEvent(dy, currentTime))
            while (scrollHistory.size > VELOCITY_SAMPLE_SIZE) {
                scrollHistory.removeFirst()
            }
        }
    }
    
    private fun processScrollChange(scrollY: Int, dy: Int) {
        // Position-aware behavior: always show at top of page
        if (scrollY <= TOP_POSITION_THRESHOLD) {
            showActionBar()
            accumulatedDelta = 0
            return
        }
        
        val absVelocity = abs(scrollVelocity)
        val scrollDirection = sign(dy.toFloat()).toInt()
        
        // Determine threshold based on scroll velocity
        val currentThreshold = when {
            absVelocity >= FAST_SCROLL_VELOCITY -> FAST_SCROLL_THRESHOLD
            absVelocity >= MEDIUM_SCROLL_VELOCITY -> MEDIUM_SCROLL_THRESHOLD
            else -> SLOW_SCROLL_THRESHOLD
        }
        
        // Accumulate scroll delta for threshold comparison
        accumulatedDelta += dy
        
        // Apply hysteresis: different thresholds for hide vs show
        val hideThreshold = currentThreshold
        val showThreshold = (currentThreshold * HYSTERESIS_FACTOR).toInt()
        
        when (state) {
            State.SHOWN -> {
                if (scrollDirection > 0 && accumulatedDelta >= hideThreshold) {
                    // Scrolling down enough to hide
                    hideActionBar()
                    accumulatedDelta = 0
                } else if (scrollDirection < 0) {
                    // Scrolling up while shown - reset accumulator
                    accumulatedDelta = 0
                }
            }
            
            State.HIDDEN -> {
                if (scrollDirection < 0 && abs(accumulatedDelta) >= showThreshold) {
                    // Scrolling up enough to show
                    showActionBar()
                    accumulatedDelta = 0
                } else if (scrollDirection > 0) {
                    // Scrolling down while hidden - reset accumulator
                    accumulatedDelta = 0
                }
            }
            
            State.TRANSITIONING -> {
                // Don't process new gestures while animating
                return
            }
        }
        
        // Reset accumulator if we've changed scroll direction significantly
        if (hasScrollDirectionChanged()) {
            accumulatedDelta = 0
        }
    }
    
    private fun hasScrollDirectionChanged(): Boolean {
        if (scrollHistory.size < 3) return false
        
        val recent = scrollHistory.takeLast(3)
        val signs = recent.map { sign(it.delta.toFloat()).toInt() }
        
        // Direction changed if we don't have consistent signs
        return signs.distinct().size > 1
    }
    
    private fun hideActionBar() {
        if (state == State.HIDDEN || isAnimating) return
        
        state = State.TRANSITIONING
        isAnimating = true
        
        val hideDistance = targetView.height
        ViewAnimations.ensureTranslationY(targetView, hideDistance)
        
        // Schedule state update after animation
        targetView.postDelayed({
            state = State.HIDDEN
            isAnimating = false
        }, ANIMATION_DURATION)
    }
    
    private fun showActionBar() {
        if (state == State.SHOWN || isAnimating) return
        
        state = State.TRANSITIONING
        isAnimating = true
        
        ViewAnimations.ensureTranslationY(targetView, 0)
        
        // Schedule state update after animation
        targetView.postDelayed({
            state = State.SHOWN
            isAnimating = false
        }, ANIMATION_DURATION)
    }
    
    /**
     * Forces the action bar to show (e.g., when user performs explicit gesture)
     */
    fun forceShow() {
        accumulatedDelta = 0
        showActionBar()
    }
    
    /**
     * Forces the action bar to hide
     */
    fun forceHide() {
        accumulatedDelta = 0
        hideActionBar()
    }
    
    companion object {
        // Velocity thresholds (pixels per millisecond)
        private const val FAST_SCROLL_VELOCITY = 2.0f
        private const val MEDIUM_SCROLL_VELOCITY = 0.8f
        
        // Distance thresholds for different scroll speeds
        private const val FAST_SCROLL_THRESHOLD = 10
        private const val MEDIUM_SCROLL_THRESHOLD = 25
        private const val SLOW_SCROLL_THRESHOLD = 50
        
        // Hysteresis factor: show threshold = hide threshold * this factor
        private const val HYSTERESIS_FACTOR = 1.5f
        
        // Position-based behavior
        private const val TOP_POSITION_THRESHOLD = 100
        
        // Animation and timing
        private const val ANIMATION_DURATION = 200L
        private const val VELOCITY_SAMPLE_SIZE = 5
    }
}