package com.omiyawaki.osrswiki.views

import android.view.View
import com.google.android.material.appbar.AppBarLayout

/**
 * Enhanced handler for showing and hiding an AppBarLayout based on scroll state.
 * Improved with hysteresis, position awareness, and accumulated delta for better UX.
 *
 * @param targetView The AppBarLayout that will be hidden or shown.
 */
class ViewHideHandler(private val targetView: View) {
    private var lastScrollY = 0
    private var state = State.EXPANDED
    private var accumulatedDelta = 0

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

            // Position-aware behavior: always show at top of page
            if (scrollY <= TOP_POSITION_THRESHOLD) {
                if (state == State.COLLAPSED) {
                    state = State.EXPANDED
                    targetView.setExpanded(true, true)
                }
                accumulatedDelta = 0
                lastScrollY = scrollY
                return@addOnScrollChangeListener
            }

            val dy = scrollY - lastScrollY
            lastScrollY = scrollY

            // Accumulate scroll delta for more reliable threshold detection
            accumulatedDelta += dy

            // Apply hysteresis: different thresholds for hide vs show
            when (state) {
                State.EXPANDED -> {
                    if (accumulatedDelta > HIDE_THRESHOLD) {
                        // Scrolling down enough to collapse
                        state = State.COLLAPSED
                        targetView.setExpanded(false, true)
                        accumulatedDelta = 0
                    } else if (accumulatedDelta < 0) {
                        // Scrolling up while expanded - reset accumulator
                        accumulatedDelta = 0
                    }
                }
                
                State.COLLAPSED -> {
                    if (accumulatedDelta < -SHOW_THRESHOLD) {
                        // Scrolling up enough to expand
                        state = State.EXPANDED
                        targetView.setExpanded(true, true)
                        accumulatedDelta = 0
                    } else if (accumulatedDelta > 0) {
                        // Scrolling down while collapsed - reset accumulator
                        accumulatedDelta = 0
                    }
                }
            }
        }
    }

    companion object {
        private const val HIDE_THRESHOLD = 20
        private const val SHOW_THRESHOLD = 30  // Hysteresis: harder to show than hide
        private const val TOP_POSITION_THRESHOLD = 100
    }
}
