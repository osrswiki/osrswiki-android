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
    
    // Anti-jitter: prevent micro-oscillations without delays
    private var cumulativeDelta = 0
    private var lastAppliedDirection = 0
    
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
            
            // Immediate response - no filtering, no delays
            if (scrollY <= NEAR_TOP_THRESHOLD) {
                // Always show when near top
                isNearTop = true
                showToolbarImmediate()
            } else {
                isNearTop = false
                // Direct translation - immediate response like iOS
                handleImmediateScroll(scrollDelta)
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
        // Snap to nearest complete state to prevent partial exposure
        if (!isNearTop && totalScrollRange > 0) {
            val visibilityPercent = 1f - (kotlin.math.abs(currentOffset).toFloat() / totalScrollRange)
            
            val targetOffset = if (visibilityPercent >= SNAP_THRESHOLD) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Snap to SHOW (vis=${String.format("%.1f", visibilityPercent * 100)}%)")
                0 // Show
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Snap to HIDE (vis=${String.format("%.1f", visibilityPercent * 100)}%)")
                -totalScrollRange // Hide
            }
            
            // Only snap if not already at target
            if (kotlin.math.abs(currentOffset - targetOffset) > SNAP_TOLERANCE) {
                setOffsetImmediate(targetOffset)
            }
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
            Log.d(TAG, "Scroll: delta=$scrollDelta, cumulative=$cumulativeDelta, movement=$movement, lastDir=$lastAppliedDirection")
        }
    }
    
    /**
     * Anti-jitter calculation: prevent micro-oscillations while maintaining immediate response
     */
    private fun calculateAntiJitterMovement(scrollDelta: Int): Int {
        val currentDirection = if (scrollDelta > 0) 1 else -1
        
        // If direction changed, check if this is micro-oscillation
        if (lastAppliedDirection != 0 && lastAppliedDirection != currentDirection) {
            // Check if this is a small opposing movement (potential jitter)
            if (kotlin.math.abs(scrollDelta) <= JITTER_THRESHOLD) {
                // Accumulate opposing movements
                cumulativeDelta += scrollDelta
                
                // Only apply if accumulated movement overcomes threshold
                if (kotlin.math.abs(cumulativeDelta) > DIRECTION_CHANGE_THRESHOLD) {
                    val movement = -cumulativeDelta
                    cumulativeDelta = 0 // Reset after applying
                    lastAppliedDirection = currentDirection
                    return movement
                }
                
                // Not enough to overcome - ignore this micro-movement
                return 0
            }
        }
        
        // Normal movement or large direction change - apply immediately
        cumulativeDelta = 0 // Reset accumulator
        lastAppliedDirection = currentDirection
        return -scrollDelta
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
        
        // Anti-jitter constants
        private const val JITTER_THRESHOLD = 10 // Max delta considered potential jitter
        private const val DIRECTION_CHANGE_THRESHOLD = 15 // Cumulative movement needed to change direction
        
        // Snap behavior constants  
        private const val SNAP_THRESHOLD = 0.5f // Snap to show if >50% visible
        private const val SNAP_TOLERANCE = 5 // Skip snap if within 5px of target
    }
}