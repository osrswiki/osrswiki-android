package com.omiyawaki.osrswiki.views

import android.view.View
import com.google.android.material.appbar.AppBarLayout
import kotlin.math.abs

/**
 * Smart handler for showing and hiding an AppBarLayout based on scroll state.
 * Solves common UX issues: slow scroll detection, oscillation loops, and reliable recall.
 * 
 * Features:
 * - Momentum-aware thresholds (lower threshold for consistent scrolling)
 * - Cooldown period to prevent rapid toggling 
 * - Position-aware behavior (auto-show at absolute top)
 * - Scroll consistency detection to prevent false triggers
 *
 * @param targetView The AppBarLayout that will be hidden or shown.
 */
class ViewHideHandler(private val targetView: View) {
    private var lastScrollY = 0
    private var state = State.EXPANDED
    private var lastActionTime = 0L
    
    // Track recent scroll events for momentum detection
    private val scrollHistory = ArrayDeque<Int>(MOMENTUM_SAMPLE_SIZE)
    
    private enum class State {
        EXPANDED,
        COLLAPSED
    }

    /**
     * Sets the scrollable view that will trigger the hide/show behavior.
     * @param scrollView The scrolling view.
     */
    fun setScrollView(scrollView: ObservableWebView) {
        scrollView.addOnScrollChangeListener { _, scrollY, isHumanScroll ->
            if (!isHumanScroll || targetView !is AppBarLayout) {
                return@addOnScrollChangeListener
            }
            
            // Position-aware behavior: always show at absolute top
            if (scrollY <= ABSOLUTE_TOP_THRESHOLD) {
                if (state == State.COLLAPSED) {
                    expandToolbar()
                }
                return@addOnScrollChangeListener
            }
            
            // Cooldown period: prevent rapid toggling
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastActionTime < COOLDOWN_PERIOD_MS) {
                return@addOnScrollChangeListener
            }

            val dy = scrollY - lastScrollY
            lastScrollY = scrollY
            
            // Skip tiny scroll changes that are likely noise
            if (abs(dy) < MIN_SCROLL_THRESHOLD) {
                return@addOnScrollChangeListener
            }
            
            // Update scroll history for momentum detection
            scrollHistory.addLast(dy)
            if (scrollHistory.size > MOMENTUM_SAMPLE_SIZE) {
                scrollHistory.removeFirst()
            }
            
            // Determine if we have consistent scroll direction
            val isConsistentScroll = isScrollDirectionConsistent()
            
            // Choose threshold based on scroll consistency
            val threshold = if (isConsistentScroll) {
                CONSISTENT_SCROLL_THRESHOLD  // Lower threshold for consistent scrolling
            } else {
                ERRATIC_SCROLL_THRESHOLD     // Higher threshold for erratic scrolling
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
            }
        }
    }
    
    /**
     * Checks if recent scroll events show consistent direction.
     * Consistent scrolling gets lower thresholds for better responsiveness.
     */
    private fun isScrollDirectionConsistent(): Boolean {
        if (scrollHistory.size < MOMENTUM_SAMPLE_SIZE) {
            return false
        }
        
        val signs = scrollHistory.map { if (it > 0) 1 else if (it < 0) -1 else 0 }
        val positiveCount = signs.count { it > 0 }
        val negativeCount = signs.count { it < 0 }
        
        // Consistent if 80% or more of recent scrolls are in the same direction
        val consistencyThreshold = (MOMENTUM_SAMPLE_SIZE * 0.8).toInt()
        return positiveCount >= consistencyThreshold || negativeCount >= consistencyThreshold
    }
    
    private fun collapseToolbar() {
        state = State.COLLAPSED
        (targetView as AppBarLayout).setExpanded(false, true)
        lastActionTime = System.currentTimeMillis()
    }
    
    private fun expandToolbar() {
        state = State.EXPANDED
        (targetView as AppBarLayout).setExpanded(true, true)
        lastActionTime = System.currentTimeMillis()
    }

    companion object {
        // Threshold values
        private const val CONSISTENT_SCROLL_THRESHOLD = 8   // Lower threshold for consistent scrolling
        private const val ERRATIC_SCROLL_THRESHOLD = 20     // Higher threshold for erratic scrolling
        private const val MIN_SCROLL_THRESHOLD = 2         // Ignore tiny scroll changes
        private const val ABSOLUTE_TOP_THRESHOLD = 50      // Auto-show within 50px of top
        
        // Timing values
        private const val COOLDOWN_PERIOD_MS = 200L        // Prevent rapid toggling
        
        // Momentum detection
        private const val MOMENTUM_SAMPLE_SIZE = 4          // Number of recent scrolls to analyze
    }
}