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
    
    // Simple state tracking for immediate response
    private var isNearTop = true
    private var lastScrollY = 0
    private var scrollDirection = 0 // -1 up, 0 none, 1 down
    
    // Enhanced anti-jitter: prevent oscillations without delays
    private var cumulativeDelta = 0
    private var lastAppliedDirection = 0
    private var lastDirectionChangeTime = 0L
    private var sustainedDirectionTime = 0L
    
    // Oscillation pattern detection
    private val recentDeltas = ArrayDeque<Int>(8) // Track last 8 scroll deltas
    private var oscillationDetected = false
    
    // Scroll-end detection for snapping
    private val scrollEndHandler = Handler(Looper.getMainLooper())
    private var scrollEndRunnable: Runnable? = null
    private var lastScrollTime = 0L
    
    // Snap feedback prevention and debouncing
    private var isPerformingSnap = false
    private var lastSnapTrigger: String? = null
    private var lastSnapTime = 0L
    
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
        // Add touch end listener for snap behavior
        scrollView.addOnUpOrCancelMotionEventListener {
            handleTouchEnd()
        }
        
        scrollView.addOnScrollChangeListener { oldScrollY, scrollY, isHumanScroll ->
            val scrollDelta = scrollY - oldScrollY
            
            // Skip processing if we're performing a snap to prevent feedback loops
            if (isPerformingSnap) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Skip scroll processing - snap in progress")
                return@addOnScrollChangeListener
            }
            
            // Update last scroll time for scroll-end detection
            lastScrollTime = System.currentTimeMillis()
            
            // Immediate response - no filtering, no delays
            if (scrollY <= NEAR_TOP_THRESHOLD) {
                // Always show when near top
                isNearTop = true
                showToolbarImmediate()
                cancelScrollEndDetection() // No snapping needed near top
            } else {
                isNearTop = false
                // Direct translation - immediate response like iOS
                handleImmediateScroll(scrollDelta)
                
                // Schedule scroll-end detection for snapping
                scheduleScrollEndDetection()
            }
            
            lastScrollY = scrollY
            
            if (BuildConfig.DEBUG && scrollDelta != 0) {
                Log.d(TAG, "Immediate scroll: delta=$scrollDelta, scrollY=$scrollY, offset=$currentOffset")
            }
        }
    }
    
    /**
     * Handle touch end to prevent intermediate states
     */
    private fun handleTouchEnd() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Touch end detected - checking for snap")
        performSnap("touch_end")
    }
    
    /**
     * Handle scroll end to prevent intermediate states
     */
    private fun handleScrollEnd() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Scroll end detected - checking for snap")
        performSnap("scroll_end")
    }
    
    /**
     * Perform snap to nearest complete state
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
            val visibilityPercent = 1f - (kotlin.math.abs(currentOffset).toFloat() / totalScrollRange)
            
            val targetOffset = if (visibilityPercent >= SNAP_THRESHOLD) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Snap to SHOW (vis=${String.format("%.1f", visibilityPercent * 100)}%) - trigger=$trigger")
                0 // Show
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Snap to HIDE (vis=${String.format("%.1f", visibilityPercent * 100)}%) - trigger=$trigger")
                -totalScrollRange // Hide
            }
            
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
     * Handle scroll with anti-jitter deadband - immediate for real movement, dampened for oscillations
     */
    private fun handleImmediateScroll(scrollDelta: Int) {
        if (scrollDelta == 0) return
        
        // Update scroll direction immediately
        scrollDirection = when {
            scrollDelta > 0 -> 1  // Scrolling down
            scrollDelta < 0 -> -1 // Scrolling up
            else -> 0
        }
        
        // Anti-jitter logic: accumulate small opposing movements
        val movement = calculateAntiJitterMovement(scrollDelta)
        
        if (movement != 0) {
            updateToolbarPositionImmediate(movement)
        }
        
        if (BuildConfig.DEBUG && scrollDelta != 0) {
            Log.d(TAG, "Scroll: delta=$scrollDelta, cumulative=$cumulativeDelta, movement=$movement, lastDir=$lastAppliedDirection, oscillation=$oscillationDetected")
        }
    }
    
    /**
     * Enhanced anti-jitter calculation: prevent oscillations while maintaining immediate response
     */
    private fun calculateAntiJitterMovement(scrollDelta: Int): Int {
        val currentDirection = if (scrollDelta > 0) 1 else -1
        val currentTime = System.currentTimeMillis()
        
        // Add to recent deltas for oscillation detection
        recentDeltas.add(scrollDelta)
        if (recentDeltas.size > OSCILLATION_WINDOW) {
            recentDeltas.removeFirst()
        }
        
        // Detect oscillation pattern
        oscillationDetected = detectOscillation()
        
        // Track sustained scrolling time for smoother direction changes
        if (lastAppliedDirection == currentDirection) {
            sustainedDirectionTime = currentTime - lastDirectionChangeTime
        } else if (lastAppliedDirection != 0) {
            // Direction is changing, record the time
            lastDirectionChangeTime = currentTime
            sustainedDirectionTime = 0L
        }
        
        // If direction changed, check if this is oscillation or jitter
        if (lastAppliedDirection != 0 && lastAppliedDirection != currentDirection) {
            // Check if this is oscillation or small opposing movement (potential jitter)
            if (kotlin.math.abs(scrollDelta) <= JITTER_THRESHOLD || oscillationDetected) {
                // Accumulate opposing movements
                cumulativeDelta += scrollDelta
                
                // Calculate adaptive threshold based on context
                val baseThreshold = if (oscillationDetected) {
                    DIRECTION_CHANGE_THRESHOLD * 2 // Strong damping for oscillation
                } else {
                    // Reduce threshold for sustained scrolling to make direction changes smoother
                    val sustainedBonus = min(4, (sustainedDirectionTime / 200L).toInt()) // Max 4px reduction after 800ms
                    max(DIRECTION_CHANGE_THRESHOLD_MIN, DIRECTION_CHANGE_THRESHOLD - sustainedBonus)
                }
                
                // Only apply if accumulated movement overcomes threshold
                if (kotlin.math.abs(cumulativeDelta) > baseThreshold) {
                    // Graduated release: don't release all at once to prevent stuttering
                    val maxRelease = max(DIRECTION_CHANGE_THRESHOLD_MIN, baseThreshold / 2)
                    val releaseAmount = if (kotlin.math.abs(cumulativeDelta) <= maxRelease) {
                        -cumulativeDelta // Release all if small enough
                    } else {
                        // Release partial amount to prevent large jumps
                        if (cumulativeDelta > 0) maxRelease else -maxRelease
                    }
                    
                    cumulativeDelta -= releaseAmount // Subtract what we're releasing
                    lastAppliedDirection = currentDirection
                    
                    // Only clear oscillation history if we've released all accumulated movement
                    if (cumulativeDelta == 0) {
                        clearOscillationHistory()
                    }
                    
                    return releaseAmount
                }
                
                // Not enough to overcome - ignore this micro-movement/oscillation
                return 0
            }
        }
        
        // Normal movement or large direction change - apply immediately
        cumulativeDelta = 0 // Reset accumulator
        lastAppliedDirection = currentDirection
        clearOscillationHistory() // Reset pattern detection on legitimate movement
        return -scrollDelta
    }
    
    /**
     * Detect oscillation pattern in recent scroll deltas
     */
    private fun detectOscillation(): Boolean {
        if (recentDeltas.size < 4) return false // Need at least 4 samples
        
        val deltas = recentDeltas.toList()
        var alternatingCount = 0
        var similarMagnitudeCount = 0
        
        // Check for alternating directions
        for (i in 1 until deltas.size) {
            val current = deltas[i]
            val previous = deltas[i - 1]
            
            // Check if direction alternated
            if ((current > 0) != (previous > 0)) {
                alternatingCount++
                
                // Check if magnitudes are similar (potential oscillation)
                val ratio = kotlin.math.abs(current).toFloat() / kotlin.math.abs(previous).toFloat()
                if (ratio >= OSCILLATION_SIMILARITY_THRESHOLD && ratio <= (1f / OSCILLATION_SIMILARITY_THRESHOLD)) {
                    similarMagnitudeCount++
                }
            }
        }
        
        // Oscillation detected if most recent movements alternate with similar magnitudes
        val alternationRatio = alternatingCount.toFloat() / (deltas.size - 1)
        val similarityRatio = similarMagnitudeCount.toFloat() / alternatingCount.coerceAtLeast(1)
        
        val isOscillating = alternationRatio >= 0.7f && similarityRatio >= 0.5f
        
        // Enhanced debugging for oscillation detection
        if (BuildConfig.DEBUG && recentDeltas.size >= 4) {
            Log.d(TAG, "Oscillation check: deltas=$deltas, alt=$alternatingCount/${deltas.size-1} (${String.format("%.2f", alternationRatio)}), similar=$similarMagnitudeCount/$alternatingCount (${String.format("%.2f", similarityRatio)}), result=$isOscillating")
        }
        
        return isOscillating
    }
    
    /**
     * Clear oscillation detection history
     */
    private fun clearOscillationHistory() {
        recentDeltas.clear()
        oscillationDetected = false
    }
    
    /**
     * Schedule scroll-end detection for snapping
     */
    private fun scheduleScrollEndDetection() {
        // Cancel any existing scroll-end detection
        cancelScrollEndDetection()
        
        // Schedule new scroll-end detection
        scrollEndRunnable = Runnable {
            // Check if scroll has actually stopped
            val timeSinceLastScroll = System.currentTimeMillis() - lastScrollTime
            if (timeSinceLastScroll >= SCROLL_END_TIMEOUT) {
                handleScrollEnd()
            } else {
                // Reschedule if still scrolling
                scheduleScrollEndDetection()
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
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Animate snap: $startOffset → $targetOffset")
            }
            
            // Create smooth animation from current to target offset
            val animator = ValueAnimator.ofInt(startOffset, targetOffset).apply {
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
                    }
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        // Clear flag when animation completes
                        isPerformingSnap = false
                        if (BuildConfig.DEBUG) Log.d(TAG, "Snap animation completed - resumed normal processing")
                    }
                })
            }
            
            animator.start()
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
        private const val SNAP_ANIMATION_DURATION = 250L // Duration for smooth snap animations
        private const val SNAP_DEBOUNCE_DELAY = 100L // Prevent multiple snaps within this time window
    }
}