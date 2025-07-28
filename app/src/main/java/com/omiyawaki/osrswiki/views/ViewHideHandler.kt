package com.omiyawaki.osrswiki.views

import android.util.Log
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
    private var isReallyAnimating = false
    private var lastTouchY = 0f
    
    // Touch-based gesture tracking
    private val gestureTracker = TouchGestureTracker()
    
    // Track recent scroll events for momentum detection (fallback only)
    private val scrollHistory = ArrayDeque<ScrollEvent>(MOMENTUM_SAMPLE_SIZE)
    
    private data class ScrollEvent(val delta: Int, val timestamp: Long)
    
    private enum class State {
        EXPANDED,
        COLLAPSED
    }

    /**
     * Sets the scrollable view that will trigger the hide/show behavior.
     * @param scrollView The scrolling view.
     */
    fun setScrollView(scrollView: ObservableWebView) {
        // Set up animation lifecycle tracking
        if (targetView is AppBarLayout) {
            targetView.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
                val totalScrollRange = appBarLayout.totalScrollRange
                val wasAnimating = isReallyAnimating
                
                // Detect if we're in the middle of an animation
                isReallyAnimating = abs(verticalOffset) > 0 && abs(verticalOffset) < totalScrollRange
                
                if (wasAnimating && !isReallyAnimating) {
                    // Animation just completed
                    Log.d(TAG, "Animation completed - offset=$verticalOffset, range=$totalScrollRange")
                    state = if (abs(verticalOffset) >= totalScrollRange) State.COLLAPSED else State.EXPANDED
                }
                
                Log.d(TAG, "AppBar offset changed: $verticalOffset/$totalScrollRange, animating=$isReallyAnimating")
            }
        }
        
        // Track raw touch gestures
        scrollView.addOnDownMotionEventListener {
            lastTouchY = scrollView.lastTouchY
            gestureTracker.onTouchDown(lastTouchY)
            Log.d(TAG, "Touch DOWN: y=$lastTouchY")
        }
        
        scrollView.addOnUpOrCancelMotionEventListener {
            val gesture = gestureTracker.onTouchUp()
            Log.d(TAG, "Touch UP/CANCEL: $gesture")
            
            // Process completed gesture
            if (gesture.isSignificant) {
                processGesture(gesture)
            }
        }
        
        scrollView.addOnScrollChangeListener { _, scrollY, isHumanScroll ->
            if (!isHumanScroll || targetView !is AppBarLayout) {
                return@addOnScrollChangeListener
            }
            
            val dy = scrollY - lastScrollY
            Log.d(TAG, "ScrollEvent: scrollY=$scrollY, dy=$dy, gestureActive=${gestureTracker.isActivelyTracking()}, state=$state")
            
            // Update gesture tracker with current touch position if actively tracking
            if (gestureTracker.isActivelyTracking()) {
                gestureTracker.onTouchMove(scrollView.lastTouchY)
                
                // For active gestures, use touch-based detection
                val currentGesture = gestureTracker.getCurrentGesture()
                if (currentGesture?.isSignificant == true) {
                    Log.d(TAG, "Processing active gesture: $currentGesture")
                    processGesture(currentGesture)
                }
                return@addOnScrollChangeListener
            }
            
            // CRITICAL: Ignore all scrolls during animation to prevent oscillation
            if (isReallyAnimating) {
                Log.d(TAG, "Ignoring scroll during animation")
                return@addOnScrollChangeListener
            }
            
            // Position-aware behavior: always show at absolute top
            if (scrollY <= ABSOLUTE_TOP_THRESHOLD) {
                if (state == State.COLLAPSED) {
                    expandToolbar()
                }
                return@addOnScrollChangeListener
            }

            val currentTime = System.currentTimeMillis()
            
            // Skip if scroll hasn't actually changed
            if (dy == 0) {
                Log.d(TAG, "Skipping: dy=0 (no actual scroll change)")
                return@addOnScrollChangeListener
            }
            
            // Detect phantom scroll events (characteristic patterns)
            val isPhantomEvent = detectPhantomScrollEvent(dy, currentTime)
            if (isPhantomEvent) {
                Log.d(TAG, "Phantom scroll detected - ignoring")
                return@addOnScrollChangeListener
            }
            
            // Skip tiny scroll changes that are likely noise
            if (abs(dy) < MIN_SCROLL_THRESHOLD) {
                Log.d(TAG, "Skipping: abs(dy)=${abs(dy)} < threshold=$MIN_SCROLL_THRESHOLD")
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
                Log.d(TAG, "Skipping: invalid scroll velocity pattern detected")
                return@addOnScrollChangeListener
            }
            
            lastScrollY = scrollY
            
            // Determine if we have consistent scroll direction
            val isConsistentScroll = isScrollDirectionConsistent()
            
            // Choose threshold based on scroll consistency (fallback scroll-based detection)
            val threshold = if (isConsistentScroll) {
                CONSISTENT_SCROLL_THRESHOLD
            } else {
                ERRATIC_SCROLL_THRESHOLD
            }
            Log.d(TAG, "Fallback scroll detection - threshold: $threshold (consistent=$isConsistentScroll)")
            
            // Apply hide/show logic with dynamic threshold
            when (state) {
                State.EXPANDED -> {
                    if (dy > threshold) {
                        // Scrolling down consistently enough to hide
                        Log.d(TAG, "ACTION: Collapsing toolbar (dy=$dy > threshold=$threshold)")
                        collapseToolbar()
                    } else {
                        Log.d(TAG, "No action: dy=$dy <= threshold=$threshold")
                    }
                }
                
                State.COLLAPSED -> {
                    if (dy < -threshold) {
                        // Scrolling up consistently enough to show
                        Log.d(TAG, "ACTION: Expanding toolbar (dy=$dy < -threshold=$-threshold)")
                        expandToolbar()
                    } else {
                        Log.d(TAG, "No action: dy=$dy >= -threshold=$-threshold")
                    }
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
            Log.d(TAG, "Velocity validation failed: sudden reversal (prev=$prevDelta, curr=$currDelta)")
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
        if (isReallyAnimating) {
            Log.d(TAG, "collapseToolbar: Already animating, skipping")
            return
        }
        
        Log.d(TAG, "Starting collapse animation")
        (targetView as AppBarLayout).setExpanded(false, true)
        
        // Clear scroll history to prevent carryover
        scrollHistory.clear()
    }
    
    private fun expandToolbar() {
        if (isReallyAnimating) {
            Log.d(TAG, "expandToolbar: Already animating, skipping")
            return
        }
        
        Log.d(TAG, "Starting expand animation")
        (targetView as AppBarLayout).setExpanded(true, true)
        
        // Clear scroll history to prevent carryover
        scrollHistory.clear()
    }

    /**
     * Process a completed touch gesture to determine toolbar action
     */
    private fun processGesture(gesture: TouchGestureTracker.GestureResult) {
        Log.d(TAG, "Processing gesture: $gesture")
        
        when (gesture.direction) {
            TouchGestureTracker.Direction.DOWN -> {
                if (state == State.EXPANDED && gesture.totalDistance >= GESTURE_HIDE_THRESHOLD) {
                    Log.d(TAG, "Gesture: Hiding toolbar (${gesture.totalDistance}px down)")
                    collapseToolbar()
                }
            }
            TouchGestureTracker.Direction.UP -> {
                if (state == State.COLLAPSED && gesture.totalDistance >= GESTURE_SHOW_THRESHOLD) {
                    Log.d(TAG, "Gesture: Showing toolbar (${gesture.totalDistance}px up)")
                    expandToolbar()
                }
            }
            TouchGestureTracker.Direction.NONE -> {
                Log.d(TAG, "Gesture: No clear direction - ignoring")
            }
        }
    }
    
    /**
     * Detect scroll events that are likely phantom events from layout changes
     */
    private fun detectPhantomScrollEvent(dy: Int, currentTime: Long): Boolean {
        // Large sudden scroll changes are suspicious
        if (abs(dy) > PHANTOM_EVENT_THRESHOLD) {
            Log.d(TAG, "Phantom detection: Large dy=$dy")
            return true
        }
        
        // TODO: Add more sophisticated phantom detection based on timing patterns
        
        return false
    }

    companion object {
        private const val TAG = "ViewHideHandler"
        
        // Threshold values for fallback scroll detection
        private const val CONSISTENT_SCROLL_THRESHOLD = 15      // For intentional scrolling (fallback)
        private const val ERRATIC_SCROLL_THRESHOLD = 30         // For erratic scrolling (fallback)
        private const val MIN_SCROLL_THRESHOLD = 5              // Ignore tiny scroll changes
        private const val ABSOLUTE_TOP_THRESHOLD = 50          // Auto-show within 50px of top
        
        // Phantom event detection
        private const val PHANTOM_EVENT_THRESHOLD = 80         // Large sudden changes likely phantom
        private const val PHANTOM_TIME_WINDOW = 500L           // Time window after state change
        
        // Touch gesture thresholds
        private const val GESTURE_HIDE_THRESHOLD = 100f        // Minimum down gesture to hide
        private const val GESTURE_SHOW_THRESHOLD = 100f        // Minimum up gesture to show
        
        // Momentum detection (fallback)
        private const val MOMENTUM_SAMPLE_SIZE = 4              // Number of recent scrolls to analyze
    }
}