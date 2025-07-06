package com.omiyawaki.osrswiki.views

import android.view.View
import com.google.android.material.appbar.AppBarLayout

/**
 * Handles the showing and hiding of an AppBarLayout based on the scroll state of an ObservableWebView.
 * This handler maintains its own state to avoid querying view properties during high-frequency scroll events.
 *
 * @param targetView The AppBarLayout that will be hidden or shown.
 */
class ViewHideHandler(private val targetView: View) {
    private var lastScrollY = 0
    private var state = State.EXPANDED

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

            val dy = scrollY - lastScrollY
            lastScrollY = scrollY

            if (dy > HIDE_THRESHOLD && state == State.EXPANDED) {
                // Scrolling down, so collapse the AppBarLayout.
                state = State.COLLAPSED
                targetView.setExpanded(false, true)
            } else if (dy < -HIDE_THRESHOLD && state == State.COLLAPSED) {
                // Scrolling up, so expand the AppBarLayout.
                state = State.EXPANDED
                targetView.setExpanded(true, true)
            }
        }
    }

    companion object {
        private const val HIDE_THRESHOLD = 15
    }
}
