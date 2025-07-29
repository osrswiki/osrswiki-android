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
    
    // Scroll-end detection for snapping
    private val scrollEndHandler = Handler(Looper.getMainLooper())
    private var scrollEndRunnable: Runnable? = null
    private var lastScrollTime = 0L
    
    // Snap feedback prevention and debouncing
    private var isPerformingSnap = false
    private var lastSnapTrigger: String? = null
    private var lastSnapTime = 0L
    private var currentSnapAnimator: ValueAnimator? = null
    private var snapDirection = 0 // Direction of current snap: 1 for hiding, -1 for showing
    
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
            
            // Check if scroll should interrupt ongoing snap
            if (isPerformingSnap && shouldInterruptSnap(scrollDelta)) {
                interruptSnap("significant opposing scroll detected")
            }
            
            // Skip processing if we're still performing a snap
            if (isPerformingSnap) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Skip scroll processing - snap in progress")
                return@addOnScrollChangeListener
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
                
                // PRIMARY: Use touch-based proportional positioning if gesture is active
                if (gestureTracker.isActivelyTracking()) {
                    val currentTouchY = scrollView.lastTouchY
                    val continuousState = gestureTracker.onTouchMove(currentTouchY)
                    handleTouchBasedPositioning(continuousState)
                } else {
                    // FALLBACK: Use scroll events for non-touch input (keyboard, mouse wheel, etc.)
                    handleScrollBasedPositioning(scrollDelta)
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
     * Perform snap to nearest complete state with momentum awareness
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
            // If touch_end comes after scroll_end, prioritize touch_end
            if (trigger == "touch_end" && lastSnapTrigger == "scroll_end") {
                if (BuildConfig.DEBUG) Log.d(TAG, "Prioritize touch_end over recent scroll_end")
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Skip snap - too soon after last snap ($trigger after $lastSnapTrigger)")
                return
            }
        }
        
        lastSnapTrigger = trigger
        lastSnapTime = currentTime
        
        // Snap to nearest complete state to prevent partial exposure
        if (!isNearTop && totalScrollRange > 0) {
            val targetOffset = calculateMomentumAwareSnapTarget()
            
            // Only snap if not already at target
            if (kotlin.math.abs(currentOffset - targetOffset) > SNAP_TOLERANCE) {
                // Set flag to prevent feedback loops
                isPerformingSnap = true
                
                // Use smooth animated snapping instead of immediate jump
                animateToOffset(targetOffset)
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Skip snap - already at target (offset=$currentOffset, target=$targetOffset)")
            }
        } else {
            if (BuildConfig.DEBUG) Log.d(TAG, "Skip snap - near top or invalid range (isNearTop=$isNearTop, totalScrollRange=$totalScrollRange)")
        }
    }
    
    /**
     * Handle touch-based proportional positioning (primary input method)
     * Now provides immediate response for smooth browser-like behavior
     */
    private fun handleTouchBasedPositioning(continuousState: TouchGestureTracker.ContinuousState) {
        if (!continuousState.isActive) return
        
        // Calculate proposed target offset based on finger movement
        val proposedTargetOffset = (-continuousState.totalDelta).toInt().coerceIn(-totalScrollRange, 0)
        
        // Smart direction logic: prevent hiding when already visible and scrolling up to see content above
        val scrollDirection = if (continuousState.instantDelta > 0) 1 else -1 // 1 = down/hide, -1 = up/show
        val isAlreadyVisible = currentOffset >= -VISIBILITY_THRESHOLD
        val isScrollingUp = scrollDirection == -1
        
        val targetOffset = if (isAlreadyVisible && isScrollingUp) {
            // Keep toolbar visible when already visible and scrolling up to see content above
            max(0, proposedTargetOffset)
        } else {
            proposedTargetOffset
        }
        
        if (targetOffset != currentOffset) {
            // Always apply immediately for browser-like responsiveness
            setOffsetImmediate(targetOffset)
            
            if (BuildConfig.DEBUG) {
                val preventedHiding = isAlreadyVisible && isScrollingUp && targetOffset != proposedTargetOffset
                Log.d(TAG, "Touch positioning: fingerDelta=${String.format("%.1f", continuousState.totalDelta)}, offset=$currentOffset→$targetOffset, velocity=${String.format("%.1f", continuousState.velocity)}${if (preventedHiding) " [prevented hiding]" else ""}")
            }
        }
        
        // Update momentum tracking with finger-based velocity
        updateMomentumFromTouch(continuousState)
    }
    
    /**
     * Handle scroll-based positioning (fallback for non-touch input)
     * Simplified version without complex anti-jitter since feedback loops are eliminated
     */
    private fun handleScrollBasedPositioning(scrollDelta: Int) {
        if (scrollDelta == 0) return
        
        // Simple direct translation for non-touch input (keyboard, mouse wheel, etc.)
        val movement = -scrollDelta // Invert for toolbar movement
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
     * Check if current scroll should interrupt ongoing snap animation
     */
    private fun shouldInterruptSnap(scrollDelta: Int): Boolean {
        if (!isPerformingSnap || currentSnapAnimator == null) return false
        
        val scrollDirection = if (scrollDelta > 0) 1 else -1
        
        // Interrupt if:
        // 1. Scroll direction opposes snap direction, AND
        // 2. Scroll magnitude is significant enough
        val opposesSnap = snapDirection != 0 && scrollDirection != snapDirection
        val significantMagnitude = kotlin.math.abs(scrollDelta) >= SNAP_INTERRUPT_THRESHOLD
        
        val shouldInterrupt = opposesSnap && significantMagnitude
        
        if (BuildConfig.DEBUG && shouldInterrupt) {
            Log.d(TAG, "Snap interrupt: scrollDelta=$scrollDelta (dir=$scrollDirection) opposes snapDir=$snapDirection, magnitude=${kotlin.math.abs(scrollDelta)} >= $SNAP_INTERRUPT_THRESHOLD")
        }
        
        return shouldInterrupt
    }
    
    /**
     * Interrupt ongoing snap animation to follow real-time scroll
     */
    private fun interruptSnap(reason: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Interrupting snap: $reason")
        
        currentSnapAnimator?.cancel() // This will trigger onAnimationCancel
        // Note: onAnimationCancel will clean up isPerformingSnap, currentSnapAnimator, and snapDirection
    }
    
    /**
     * Calculate snap target considering current momentum and predicted final position
     */
    private fun calculateMomentumAwareSnapTarget(): Int {
        val visibilityPercent = 1f - (kotlin.math.abs(currentOffset).toFloat() / totalScrollRange)
        
        // Basic snap decision based on current visibility
        val basicTarget = if (visibilityPercent >= SNAP_THRESHOLD) {
            0 // Show
        } else {
            -totalScrollRange // Hide
        }
        
        // If we have momentum, predict where we'd end up and factor that in
        val predictedOffset = predictFinalOffset()
        
        val finalTarget = if (predictedOffset != null) {
            // Use momentum-predicted position for snap decision
            val predictedVisibility = 1f - (kotlin.math.abs(predictedOffset).toFloat() / totalScrollRange)
            
            val momentumTarget = if (predictedVisibility >= SNAP_THRESHOLD) {
                0 // Show
            } else {
                -totalScrollRange // Hide
            }
            
            // If momentum prediction differs from basic decision, use momentum prediction
            if (momentumTarget != basicTarget) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Momentum-aware snap: basic=${if (basicTarget == 0) "SHOW" else "HIDE"} (vis=${String.format("%.1f", visibilityPercent * 100)}%), predicted=${if (momentumTarget == 0) "SHOW" else "HIDE"} (predVis=${String.format("%.1f", predictedVisibility * 100)}%), using predicted")
                }
                momentumTarget
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Snap to ${if (basicTarget == 0) "SHOW" else "HIDE"} (vis=${String.format("%.1f", visibilityPercent * 100)}%) - momentum agrees")
                }
                basicTarget
            }
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Snap to ${if (basicTarget == 0) "SHOW" else "HIDE"} (vis=${String.format("%.1f", visibilityPercent * 100)}%) - no momentum prediction")
            }
            basicTarget
        }
        
        return finalTarget
    }
    
    /**
     * Predict final toolbar offset based on current momentum
     */
    private fun predictFinalOffset(): Int? {
        if (currentVelocity < MOMENTUM_PREDICTION_MIN_VELOCITY) {
            return null // Not enough momentum to make a prediction
        }
        
        // Determine momentum direction from recent scroll events
        val momentumDirection = if (recentScrollEvents.isNotEmpty()) {
            val recentDelta = recentScrollEvents.takeLast(3).sumOf { it.delta }
            if (recentDelta > 0) -1 else 1 // Positive scroll delta (down) hides toolbar (negative offset)
        } else {
            0 // No clear direction
        }
        
        if (momentumDirection == 0) return null
        
        // Estimate how much further the toolbar will move based on velocity
        // Using simplified physics: distance = velocity * time * deceleration_factor
        val estimatedAdditionalMovement = (currentVelocity * MOMENTUM_PREDICTION_TIME * MOMENTUM_DECELERATION_FACTOR).toInt()
        
        val predictedOffset = currentOffset + (momentumDirection * estimatedAdditionalMovement)
        val clampedPrediction = max(-totalScrollRange, min(0, predictedOffset))
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Momentum prediction: velocity=${String.format("%.1f", currentVelocity)}, direction=$momentumDirection, movement=$estimatedAdditionalMovement, current=$currentOffset, predicted=$clampedPrediction")
        }
        
        return clampedPrediction
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
     * Immediate toolbar position update - no animation delays
     */
    private fun updateToolbarPositionImmediate(movement: Int) {
        val newOffset = currentOffset + movement
        val clampedOffset = max(-totalScrollRange, min(0, newOffset))
        
        if (clampedOffset != currentOffset) {
            setOffsetImmediate(clampedOffset)
        }
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
     * Set toolbar offset immediately - no animation delays
     */
    private fun setOffsetImmediate(offset: Int) {
        behavior?.let {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Immediate offset: $currentOffset → $offset")
            }
            
            // Set the offset directly on the behavior
            it.topAndBottomOffset = offset
            
            // Force immediate layout update
            appBarLayout.requestLayout()
        }
    }
    
    /**
     * Animate toolbar to target offset with smooth transition
     */
    private fun animateToOffset(targetOffset: Int) {
        behavior?.let { behavior ->
            val startOffset = currentOffset
            
            // Determine snap direction for interrupt detection
            snapDirection = when {
                targetOffset < startOffset -> 1  // Hiding (moving toward negative)
                targetOffset > startOffset -> -1 // Showing (moving toward positive)
                else -> 0 // No change
            }
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Animate snap: $startOffset → $targetOffset (direction=$snapDirection)")
            }
            
            // Create smooth animation from current to target offset
            currentSnapAnimator = ValueAnimator.ofInt(startOffset, targetOffset).apply {
                duration = SNAP_ANIMATION_DURATION
                interpolator = DecelerateInterpolator()
                
                addUpdateListener { animation ->
                    val animatedOffset = animation.animatedValue as Int
                    behavior.topAndBottomOffset = animatedOffset
                    appBarLayout.requestLayout()
                }
                
                addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {}
                    override fun onAnimationRepeat(animation: android.animation.Animator) {}
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        // Clear flag if animation is cancelled
                        isPerformingSnap = false
                        currentSnapAnimator = null
                        snapDirection = 0
                        if (BuildConfig.DEBUG) Log.d(TAG, "Snap animation cancelled - resumed normal processing")
                    }
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        // Clear flag when animation completes
                        isPerformingSnap = false
                        currentSnapAnimator = null
                        snapDirection = 0
                        if (BuildConfig.DEBUG) Log.d(TAG, "Snap animation completed - resumed normal processing")
                    }
                })
            }
            
            currentSnapAnimator?.start()
        }
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
        private const val SNAP_THRESHOLD = 0.5f // Snap to show if >50% visible
        private const val SNAP_TOLERANCE = 5 // Skip snap if within 5px of target
        private const val SCROLL_END_TIMEOUT = 100L // Milliseconds to wait before considering scroll ended
        private const val SNAP_ANIMATION_DURATION = 150L // Duration for smooth snap animations (reduced for responsiveness)
        private const val SNAP_DEBOUNCE_DELAY = 100L // Prevent multiple snaps within this time window
        
        // Smart behavior constants
        private const val VISIBILITY_THRESHOLD = 10 // px - consider toolbar "visible" when within this offset from 0
        
        // Momentum detection constants
        private const val MOMENTUM_WINDOW = 8 // Number of recent scroll events to track
        private const val MOMENTUM_VELOCITY_THRESHOLD = 0.3f // px/ms - minimum velocity to consider significant momentum
        private const val MOMENTUM_TIMEOUT = 50L // ms - max time since last scroll to still consider momentum active
        
        // Gesture completion constants
        private const val GESTURE_COMPLETION_TIMEOUT = 80L // ms - max time to wait for complete gesture end
        
        // Snap interruption constants
        private const val SNAP_INTERRUPT_THRESHOLD = 15 // px - minimum scroll magnitude to interrupt snap
        
        // Momentum prediction constants
        private const val MOMENTUM_PREDICTION_MIN_VELOCITY = 0.5f // px/ms - minimum velocity to make predictions
        private const val MOMENTUM_PREDICTION_TIME = 100L // ms - time window for momentum prediction
        private const val MOMENTUM_DECELERATION_FACTOR = 0.6f // factor to account for momentum decay
        
        // Stationary touch and feedback loop constants
        private const val STATIONARY_TOUCH_THRESHOLD = 5f // px - max movement to consider touch stationary
        private const val STATIONARY_TOUCH_TIME = 200L // ms - min time to consider touch stationary
        private const val STATIONARY_FEEDBACK_THRESHOLD = 30 // px - scroll delta that indicates feedback loop
        private const val STATIONARY_DAMPING_FACTOR = 10 // Heavy damping for feedback loops
        private const val FEEDBACK_LOOP_PATTERN_SIZE = 77 // The exact scroll delta we see in feedback loops
    }
}