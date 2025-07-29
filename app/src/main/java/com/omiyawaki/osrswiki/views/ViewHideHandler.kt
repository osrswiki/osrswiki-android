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
     * Handle immediate scroll response - no filtering, no delays
     */
    private fun handleImmediateScroll(scrollDelta: Int) {
        if (scrollDelta == 0) return
        
        // Update scroll direction immediately
        scrollDirection = when {
            scrollDelta > 0 -> 1  // Scrolling down
            scrollDelta < 0 -> -1 // Scrolling up
            else -> 0
        }
        
        // Immediate toolbar movement - like iOS
        val movement = -scrollDelta // Convert to toolbar movement
        updateToolbarPositionImmediate(movement)
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
    }
}