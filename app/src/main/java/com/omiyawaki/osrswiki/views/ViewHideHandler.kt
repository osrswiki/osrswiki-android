package com.omiyawaki.osrswiki.views

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import com.omiyawaki.osrswiki.BuildConfig
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Native Android AppBarLayout toolbar handler using Material Design behavior.
 * Provides immediate response without filtering delays by leveraging framework optimizations.
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
    
    // Touch-based gesture tracking (primary input source)
    private val gestureTracker = TouchGestureTracker()
    
    // Touch state tracking
    private var isTouchActive = false
    
    // Simple state tracking for immediate response
    private var isNearTop = true
    private var lastScrollY = 0
    
    // Micro-movement filtering to eliminate jitter
    private var accumulatedMovement = 0
    private var lastMovementDirection = 0 // -1 = up, 0 = none, 1 = down
    
    // Scroll-end detection for snapping
    private val scrollEndHandler = Handler(Looper.getMainLooper())
    private var scrollEndRunnable: Runnable? = null
    private var lastScrollTime = 0L
    
    // Snap feedback prevention and debouncing
    private var isPerformingSnap = false
    private var lastSnapTrigger: String? = null
    private var lastSnapTime = 0L
    private var toolbarState = ToolbarState.EXPANDED
    
    private enum class ToolbarState {
        EXPANDED,      // Fully visible - AppBarLayout handles this
        COLLAPSED,     // Fully hidden - AppBarLayout handles this  
        TRANSITIONING  // Moving between states - GPU translationY handles this
    }
    
    // Momentum/velocity tracking for gesture completion detection (fallback for non-touch scrolling)
    private val recentScrollEvents = ArrayDeque<ScrollEvent>(10) // Track last 10 scroll events
    private var currentVelocity = 0f // pixels/ms
    private var lastScrollEventTime = 0L
    
    private data class ScrollEvent(
        val delta: Int,
        val timestamp: Long
    )
    
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
                Log.d(TAG, "Offset: $verticalOffset / -$totalScrollRange (isNearTop=$isNearTop)")
            }
        }
    }
    
    /**
     * Sets the scrollable view that will drive the toolbar movement using native AppBarLayout behavior
     */
    fun setScrollView(scrollView: ObservableWebView) {
        // Setup native AppBarLayout scroll behavior - immediate response, no filtering
        setupNativeScrollBehavior(scrollView)
    }
    
    private fun setupNativeScrollBehavior(scrollView: ObservableWebView) {
        // Setup touch-based gesture tracking (primary input source)
        scrollView.addOnDownMotionEventListener {
            handleTouchStart(scrollView)
        }
        
        // Add touch end listener for snap behavior
        scrollView.addOnUpOrCancelMotionEventListener {
            handleTouchEnd()
        }
        
        scrollView.addOnScrollChangeListener { oldScrollY, scrollY, isHumanScroll ->
            val scrollDelta = scrollY - oldScrollY
            
            // Allow scrolling to continue even during snap - user input takes priority
            if (isPerformingSnap && abs(scrollDelta) > 10) {
                isPerformingSnap = false  // Cancel snap, user is actively scrolling
                if (BuildConfig.DEBUG) Log.d(TAG, "User scroll overrides snap")
            }
            
            // Update last scroll time for scroll-end detection
            lastScrollTime = System.currentTimeMillis()
            
            // Always show when near page top
            if (scrollY <= NEAR_TOP_THRESHOLD) {
                isNearTop = true
                showToolbarImmediate()
                cancelScrollEndDetection() // No snapping needed near top
            } else {
                isNearTop = false
                
                // Handle scroll-based positioning with smart direction logic
                handleScrollBasedPositioning(scrollDelta)
                
                // Update touch gesture tracking for momentum and snap decisions
                if (gestureTracker.isActivelyTracking()) {
                    val currentTouchY = scrollView.lastTouchY
                    val continuousState = gestureTracker.onTouchMove(currentTouchY)
                    updateMomentumFromTouch(continuousState)
                }
                
                // Schedule scroll-end detection for snapping
                scheduleScrollEndDetection()
            }
            
            lastScrollY = scrollY
            
            if (BuildConfig.DEBUG && scrollDelta != 0) {
                Log.d(TAG, "Scroll event: delta=$scrollDelta, scrollY=$scrollY, gestureActive=${gestureTracker.isActivelyTracking()}")
            }
        }
    }
    
    /**
     * Handle touch start to track touch state and start gesture tracking
     */
    private fun handleTouchStart(scrollView: ObservableWebView) {
        val touchY = scrollView.lastTouchY
        gestureTracker.onTouchDown(touchY)
        isTouchActive = true
        
        if (BuildConfig.DEBUG) Log.d(TAG, "Touch start detected - gesture tracking started at y=$touchY")
    }
    
    /**
     * Handle touch end to complete gesture and trigger snapping
     */
    private fun handleTouchEnd() {
        val gestureResult = gestureTracker.onTouchUp()
        isTouchActive = false
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Touch end detected - gesture: $gestureResult")
        }
        
        // Don't snap immediately if there's significant momentum - let scroll_end handle it
        if (hasSignificantMomentum()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Skip touch_end snap - significant momentum detected, will wait for scroll_end")
            return
        }
        
        performSnap("touch_end")
    }
    
    /**
     * Handle scroll end to prevent intermediate states
     */
    private fun handleScrollEnd() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Scroll end detected - checking for snap")
        
        // Double-check that momentum has actually stopped
        if (hasSignificantMomentum()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "False scroll_end - momentum still detected, rescheduling")
            scheduleScrollEndDetection() // Reschedule for later
            return
        }
        
        // Momentum has stopped, safe to snap
        performSnap("scroll_end")
    }
    
    /**
     * Perform snap to nearest complete state with immediate transition
     */
    private fun performSnap(trigger: String) {
        // Prevent concurrent snaps
        if (isPerformingSnap) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Skip snap - already in progress")
            return
        }
        
        // Debouncing: prevent multiple snaps in quick succession
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSnapTime < SNAP_DEBOUNCE_DELAY) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Skip snap - too soon after last snap ($trigger)")
            return
        }
        
        lastSnapTrigger = trigger
        lastSnapTime = currentTime
        isPerformingSnap = true
        
        // Simple snap decision based on current visibility
        val targetState = if (isNearTop) {
            ToolbarState.EXPANDED
        } else if (currentOffset > -totalScrollRange / 2) {
            ToolbarState.EXPANDED  // More than 50% visible
        } else {
            ToolbarState.COLLAPSED  // Less than 50% visible
        }
        
        if (BuildConfig.DEBUG) {
            val visibilityPercent = if (totalScrollRange > 0) {
                (1f - (kotlin.math.abs(currentOffset).toFloat() / totalScrollRange)) * 100f
            } else 0f
            Log.d(TAG, "Snap decision: ${String.format("%.1f", visibilityPercent)}% visible → $targetState")
        }
        
        transitionToFinalState(targetState)
    }
    
    
    /**
     * Handle scroll-based positioning with smart direction logic for immediate browser-like response
     */
    private fun handleScrollBasedPositioning(scrollDelta: Int) {
        if (scrollDelta == 0) return
        
        // Determine scroll direction and intent
        // Negative delta = scrolling up (revealing content above) = should show toolbar when appropriate
        // Positive delta = scrolling down (revealing content below) = should hide toolbar when appropriate
        val isScrollingUp = scrollDelta < 0
        val isScrollingDown = scrollDelta > 0
        val isAlreadyVisible = currentOffset >= -VISIBILITY_THRESHOLD
        val isAlreadyHidden = currentOffset <= -totalScrollRange + VISIBILITY_THRESHOLD
        
        // Smart direction logic: respect user's intent based on scroll direction
        val shouldApplySmartLogic = (isScrollingUp && isAlreadyVisible) || (isScrollingDown && isAlreadyHidden)
        
        if (shouldApplySmartLogic) {
            // Keep current state when scrolling in a direction that suggests content viewing intent
            if (BuildConfig.DEBUG) {
                val reason = if (isScrollingUp && isAlreadyVisible) "keep visible while scrolling up" else "keep hidden while scrolling down"
                Log.d(TAG, "Smart direction: delta=$scrollDelta, $reason")
            }
            // Don't move toolbar, but still update momentum tracking
            updateMomentumFromScroll(scrollDelta)
            return
        }
        
        // Normal proportional movement for other cases
        toolbarState = ToolbarState.TRANSITIONING  // Mark as transitioning during scroll
        val movement = -scrollDelta // Invert for toolbar movement (scroll down = hide toolbar)
        updateToolbarPositionImmediate(movement)
        
        // Update momentum tracking for scroll-based input
        updateMomentumFromScroll(scrollDelta)
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Scroll positioning: delta=$scrollDelta, movement=$movement, offset=$currentOffset")
        }
    }
    
    
    
    /**
     * Update momentum tracking from touch gesture
     */
    private fun updateMomentumFromTouch(continuousState: TouchGestureTracker.ContinuousState) {
        val currentTime = System.currentTimeMillis()
        lastScrollEventTime = currentTime
        
        // Convert touch velocity (px/s) to momentum velocity (px/ms) and use instant delta as scroll equivalent
        val scrollEquivalent = (-continuousState.instantDelta).toInt() // Convert finger movement to scroll equivalent
        currentVelocity = continuousState.velocity / 1000f // Convert px/s to px/ms
        
        // Add to scroll events for momentum tracking consistency
        recentScrollEvents.add(ScrollEvent(scrollEquivalent, currentTime))
        if (recentScrollEvents.size > MOMENTUM_WINDOW) {
            recentScrollEvents.removeFirst()
        }
        
        if (BuildConfig.DEBUG && continuousState.velocity > 50f) {
            Log.d(TAG, "Touch momentum: velocity=${String.format("%.1f", currentVelocity)} px/ms (${String.format("%.0f", continuousState.velocity)} px/s)")
        }
    }
    
    /**
     * Update momentum tracking from scroll events (fallback)
     */
    private fun updateMomentumFromScroll(scrollDelta: Int) {
        val currentTime = System.currentTimeMillis()
        lastScrollEventTime = currentTime
        
        // Add new scroll event
        recentScrollEvents.add(ScrollEvent(scrollDelta, currentTime))
        if (recentScrollEvents.size > MOMENTUM_WINDOW) {
            recentScrollEvents.removeFirst()
        }
        
        // Calculate velocity from recent events
        currentVelocity = calculateCurrentVelocity()
        
        if (BuildConfig.DEBUG && recentScrollEvents.size >= 3) {
            Log.d(TAG, "Scroll momentum: velocity=${String.format("%.1f", currentVelocity)} px/ms, events=${recentScrollEvents.size}")
        }
    }
    
    /**
     * Update momentum/velocity tracking from scroll events
     * DEPRECATED: Legacy method, replaced by updateMomentumFromTouch and updateMomentumFromScroll
     */
    private fun updateMomentumTracking(scrollDelta: Int, fingerMovement: Float = 0f) {
        val currentTime = System.currentTimeMillis()
        lastScrollEventTime = currentTime
        
        // Add new scroll event
        recentScrollEvents.add(ScrollEvent(scrollDelta, currentTime))
        if (recentScrollEvents.size > MOMENTUM_WINDOW) {
            recentScrollEvents.removeFirst()
        }
        
        // Calculate velocity from recent events
        currentVelocity = calculateCurrentVelocity()
        
        if (BuildConfig.DEBUG && recentScrollEvents.size >= 3) {
            Log.d(TAG, "Legacy momentum: velocity=${String.format("%.1f", currentVelocity)} px/ms, events=${recentScrollEvents.size}")
        }
    }
    
    /**
     * Calculate current velocity from recent scroll events
     */
    private fun calculateCurrentVelocity(): Float {
        if (recentScrollEvents.size < 2) return 0f
        
        val events = recentScrollEvents.toList()
        val recentEvents = events.takeLast(min(5, events.size)) // Use last 5 events for velocity
        
        if (recentEvents.size < 2) return 0f
        
        // Calculate weighted average velocity (more recent events have higher weight)
        var totalWeightedDelta = 0f
        var totalWeightedTime = 0f
        var totalWeight = 0f
        
        for (i in 1 until recentEvents.size) {
            val deltaPixels = recentEvents[i].delta.toFloat()
            val deltaTime = (recentEvents[i].timestamp - recentEvents[i - 1].timestamp).toFloat()
            
            if (deltaTime > 0) {
                // Higher weight for more recent events
                val weight = i.toFloat() / (recentEvents.size - 1)
                totalWeightedDelta += kotlin.math.abs(deltaPixels) * weight
                totalWeightedTime += deltaTime * weight
                totalWeight += weight
            }
        }
        
        return if (totalWeight > 0 && totalWeightedTime > 0) {
            totalWeightedDelta / totalWeightedTime // pixels per millisecond
        } else {
            0f
        }
    }
    
    /**
     * Check if there's significant momentum that suggests gesture is still active
     */
    private fun hasSignificantMomentum(): Boolean {
        val timeSinceLastScroll = System.currentTimeMillis() - lastScrollEventTime
        
        // If too much time has passed, no momentum
        if (timeSinceLastScroll > MOMENTUM_TIMEOUT) {
            return false
        }
        
        // Check velocity threshold
        val velocityThreshold = MOMENTUM_VELOCITY_THRESHOLD
        
        // Also check if recent events show consistent direction
        val hasConsistentDirection = hasConsistentScrollDirection()
        
        val hasMomentum = currentVelocity > velocityThreshold && hasConsistentDirection
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Momentum check: velocity=${String.format("%.1f", currentVelocity)}, threshold=$velocityThreshold, consistent=$hasConsistentDirection, hasMomentum=$hasMomentum")
        }
        
        return hasMomentum
    }
    
    /**
     * Check if recent scroll events show consistent direction (not alternating)
     */
    private fun hasConsistentScrollDirection(): Boolean {
        if (recentScrollEvents.size < 3) return false
        
        val recentEvents = recentScrollEvents.takeLast(3)
        val directions = recentEvents.map { if (it.delta > 0) 1 else -1 }
        
        // Check if at least 2 out of 3 recent events have same direction
        val positiveCount = directions.count { it > 0 }
        val negativeCount = directions.count { it < 0 }
        
        return max(positiveCount, negativeCount) >= 2
    }
    
    /**
     * Unified gesture completion check that combines touch_end and scroll_end logic
     */
    private fun isGestureComplete(): Boolean {
        val timeSinceLastScroll = System.currentTimeMillis() - lastScrollEventTime
        val hasTimedOut = timeSinceLastScroll >= SCROLL_END_TIMEOUT
        val momentumStopped = !hasSignificantMomentum()
        
        // Gesture is complete if either:
        // 1. Momentum has clearly stopped, OR
        // 2. Enough time has passed since last scroll event
        val isComplete = momentumStopped || hasTimedOut
        
        if (BuildConfig.DEBUG && recentScrollEvents.isNotEmpty()) {
            Log.d(TAG, "Gesture completion check: timeSince=${timeSinceLastScroll}ms, momentum=$momentumStopped, timedOut=$hasTimedOut, complete=$isComplete")
        }
        
        return isComplete
    }
    
    
    
    /**
     * Schedule scroll-end detection for snapping
     */
    private fun scheduleScrollEndDetection() {
        // Cancel any existing scroll-end detection
        cancelScrollEndDetection()
        
        // Schedule new scroll-end detection with adaptive timing
        scrollEndRunnable = Runnable {
            val timeSinceLastScroll = System.currentTimeMillis() - lastScrollTime
            val hasTimedOut = timeSinceLastScroll >= SCROLL_END_TIMEOUT
            val momentumStopped = !hasSignificantMomentum()
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Scroll end check: timeSinceLastScroll=${timeSinceLastScroll}ms, timedOut=$hasTimedOut, momentumStopped=$momentumStopped")
            }
            
            if (hasTimedOut || momentumStopped) {
                handleScrollEnd()
            } else {
                // Use shorter timeout if momentum is still high
                val adaptiveTimeout = if (currentVelocity > MOMENTUM_VELOCITY_THRESHOLD * 2) {
                    SCROLL_END_TIMEOUT / 2 // Check more frequently for high momentum
                } else {
                    SCROLL_END_TIMEOUT
                }
                
                // Reschedule with adaptive timing
                scrollEndHandler.postDelayed(scrollEndRunnable!!, adaptiveTimeout)
            }
        }
        scrollEndHandler.postDelayed(scrollEndRunnable!!, SCROLL_END_TIMEOUT)
    }
    
    /**
     * Cancel scroll-end detection
     */
    private fun cancelScrollEndDetection() {
        scrollEndRunnable?.let {
            scrollEndHandler.removeCallbacks(it)
            scrollEndRunnable = null
        }
    }
    
    /**
     * Immediate toolbar position update with micro-movement filtering - no animation delays
     */
    private fun updateToolbarPositionImmediate(movement: Int) {
        val newOffset = currentOffset + movement
        val clampedOffset = max(-totalScrollRange, min(0, newOffset))
        
        if (clampedOffset != currentOffset) {
            // Apply micro-movement filter to reduce jitter while maintaining responsiveness
            val offsetDifference = kotlin.math.abs(clampedOffset - currentOffset)
            
            if (offsetDifference >= MICRO_MOVEMENT_THRESHOLD || shouldBypassFilter(movement)) {
                setOffsetImmediate(clampedOffset)
                // Reset accumulated movement when we apply an update
                accumulatedMovement = 0
            } else {
                // Accumulate small movements until threshold is reached
                accumulatedMovement += movement
                
                if (kotlin.math.abs(accumulatedMovement) >= MICRO_MOVEMENT_THRESHOLD) {
                    val finalOffset = max(-totalScrollRange, min(0, currentOffset + accumulatedMovement))
                    setOffsetImmediate(finalOffset)
                    accumulatedMovement = 0
                }
            }
        }
    }
    
    /**
     * Check if we should bypass the micro-movement filter for larger movements or direction changes
     */
    private fun shouldBypassFilter(movement: Int): Boolean {
        // Bypass filter for larger movements (normal scrolling)
        if (kotlin.math.abs(movement) > MICRO_MOVEMENT_THRESHOLD * 2) {
            return true
        }
        
        // Bypass filter for direction changes (with hysteresis)
        val movementDirection = if (movement > 0) 1 else if (movement < 0) -1 else 0
        if (lastMovementDirection != 0 && movementDirection != 0 && movementDirection != lastMovementDirection) {
            // Direction change detected, require minimum movement to confirm
            if (kotlin.math.abs(movement) >= DIRECTION_HYSTERESIS_THRESHOLD) {
                lastMovementDirection = movementDirection
                return true
            }
            return false
        }
        
        lastMovementDirection = movementDirection
        return false
    }
    
    /**
     * Immediate toolbar show - no delays
     */
    private fun showToolbarImmediate() {
        if (currentOffset != 0) {
            setOffsetImmediate(0)
        }
    }
    
    /**
     * Set toolbar offset immediately using GPU transform - eliminates layout thrashing jitter
     */
    private fun setOffsetImmediate(offset: Int) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Immediate offset: $currentOffset → $offset")
        }
        
        // Use GPU-accelerated transform instead of layout changes to eliminate jitter
        appBarLayout.translationY = offset.toFloat()
        
        // Update internal state
        currentOffset = offset
    }
    
    /**
     * Transition toolbar to final state using AppBarLayout.setExpanded() for native Material behavior
     */
    private fun transitionToFinalState(targetState: ToolbarState) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Transitioning to final state: $targetState (from $toolbarState)")
        }
        
        when (targetState) {
            ToolbarState.EXPANDED -> {
                toolbarState = ToolbarState.EXPANDED
                // Reset GPU transform and let AppBarLayout handle native animation + shadows
                appBarLayout.translationY = 0f
                (appBarLayout as AppBarLayout).setExpanded(true, true)
                currentOffset = 0
            }
            ToolbarState.COLLAPSED -> {
                toolbarState = ToolbarState.COLLAPSED  
                // Reset GPU transform and let AppBarLayout handle native animation + shadows
                appBarLayout.translationY = 0f
                (appBarLayout as AppBarLayout).setExpanded(false, true)
                currentOffset = -totalScrollRange
            }
            ToolbarState.TRANSITIONING -> {
                // This state is handled by GPU translationY during scroll
                toolbarState = ToolbarState.TRANSITIONING
            }
        }
        
        // Clear snap flags
        isPerformingSnap = false
    }
    
    
    companion object {
        private const val TAG = "ViewHideHandler"
        private const val NEAR_TOP_THRESHOLD = 50 // Always show toolbar within 50px of top
        
        // Enhanced anti-jitter constants
        private const val JITTER_THRESHOLD = 25 // Max delta considered potential jitter (increased from 20 to catch boundary oscillations)
        private const val DIRECTION_CHANGE_THRESHOLD = 8 // Base cumulative movement needed to change direction (reduced for smoother slow scrolling)
        private const val DIRECTION_CHANGE_THRESHOLD_MIN = 4 // Minimum threshold after time-based reduction
        private const val OSCILLATION_WINDOW = 6 // Number of recent deltas to analyze for oscillation
        private const val OSCILLATION_SIMILARITY_THRESHOLD = 0.6f // How similar alternating deltas must be (relaxed for better detection)
        
        // Snap behavior constants  
        private const val SCROLL_END_TIMEOUT = 100L // Milliseconds to wait before considering scroll ended
        private const val SNAP_DEBOUNCE_DELAY = 100L // Prevent multiple snaps within this time window
        
        // Smart behavior constants
        private const val VISIBILITY_THRESHOLD = 20 // px - consider toolbar "visible" when within this offset from 0
        
        // Anti-jitter constants
        private const val MICRO_MOVEMENT_THRESHOLD = 3 // px - minimum movement to update toolbar position immediately
        private const val DIRECTION_HYSTERESIS_THRESHOLD = 4 // px - minimum movement required to change direction
        
        // Momentum detection constants
        private const val MOMENTUM_WINDOW = 8 // Number of recent scroll events to track
        private const val MOMENTUM_VELOCITY_THRESHOLD = 0.3f // px/ms - minimum velocity to consider significant momentum
        private const val MOMENTUM_TIMEOUT = 50L // ms - max time since last scroll to still consider momentum active
        
        // Gesture completion constants
        private const val GESTURE_COMPLETION_TIMEOUT = 80L // ms - max time to wait for complete gesture end
        
        
        // Stationary touch and feedback loop constants
        private const val STATIONARY_TOUCH_THRESHOLD = 5f // px - max movement to consider touch stationary
        private const val STATIONARY_TOUCH_TIME = 200L // ms - min time to consider touch stationary
        private const val STATIONARY_FEEDBACK_THRESHOLD = 30 // px - scroll delta that indicates feedback loop
        private const val STATIONARY_DAMPING_FACTOR = 10 // Heavy damping for feedback loops
        private const val FEEDBACK_LOOP_PATTERN_SIZE = 77 // The exact scroll delta we see in feedback loops
    }
}