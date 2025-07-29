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
    
    // Oscillation pattern detection
    private val recentDeltas = ArrayDeque<Int>(8) // Track last 8 scroll deltas
    private var oscillationDetected = false
    
    // Scroll-end detection for snapping
    private val scrollEndHandler = Handler(Looper.getMainLooper())
    private var scrollEndRunnable: Runnable? = null
    private var lastScrollTime = 0L
    
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
                setOffsetImmediate(targetOffset)
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
        
        // Add to recent deltas for oscillation detection
        recentDeltas.add(scrollDelta)
        if (recentDeltas.size > OSCILLATION_WINDOW) {
            recentDeltas.removeFirst()
        }
        
        // Detect oscillation pattern
        oscillationDetected = detectOscillation()
        
        // If direction changed, check if this is oscillation or jitter
        if (lastAppliedDirection != 0 && lastAppliedDirection != currentDirection) {
            // Check if this is oscillation or small opposing movement (potential jitter)
            if (kotlin.math.abs(scrollDelta) <= JITTER_THRESHOLD || oscillationDetected) {
                // Accumulate opposing movements
                cumulativeDelta += scrollDelta
                
                // Apply stronger damping for detected oscillation
                val threshold = if (oscillationDetected) {
                    DIRECTION_CHANGE_THRESHOLD * 2 // Require more movement to overcome oscillation
                } else {
                    DIRECTION_CHANGE_THRESHOLD
                }
                
                // Only apply if accumulated movement overcomes threshold
                if (kotlin.math.abs(cumulativeDelta) > threshold) {
                    val movement = -cumulativeDelta
                    cumulativeDelta = 0 // Reset after applying
                    lastAppliedDirection = currentDirection
                    clearOscillationHistory() // Reset pattern detection
                    return movement
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
        
        return alternationRatio >= 0.7f && similarityRatio >= 0.5f
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
    
    companion object {
        private const val TAG = "ViewHideHandler"
        private const val NEAR_TOP_THRESHOLD = 50 // Always show toolbar within 50px of top
        
        // Enhanced anti-jitter constants
        private const val JITTER_THRESHOLD = 20 // Max delta considered potential jitter (increased from 10)
        private const val DIRECTION_CHANGE_THRESHOLD = 15 // Cumulative movement needed to change direction
        private const val OSCILLATION_WINDOW = 6 // Number of recent deltas to analyze for oscillation
        private const val OSCILLATION_SIMILARITY_THRESHOLD = 0.7f // How similar alternating deltas must be
        
        // Snap behavior constants  
        private const val SNAP_THRESHOLD = 0.5f // Snap to show if >50% visible
        private const val SNAP_TOLERANCE = 5 // Skip snap if within 5px of target
        private const val SCROLL_END_TIMEOUT = 100L // Milliseconds to wait before considering scroll ended
    }
}