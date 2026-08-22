package com.omiyawaki.osrswiki.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import com.omiyawaki.osrswiki.util.DimenUtil.roundedDpToPx
import com.omiyawaki.osrswiki.util.L10nUtil
import com.omiyawaki.osrswiki.util.osrsArticleSwipeGravity
import kotlin.math.abs

class FrameLayoutNavMenuTriggerer(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    interface Callback {
        fun onNavMenuSwipeRequest(gravity: Int)
    }

    private var initialX = 0f
    private var initialY = 0f
    private var maybeSwiping = false
    var callback: Callback? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (CHILD_VIEW_SCROLLED) {
            CHILD_VIEW_SCROLLED = false
            initialX = ev.x
            initialY = ev.y
        }
        if (action == MotionEvent.ACTION_DOWN) {
            initialX = ev.x
            initialY = ev.y
            maybeSwiping = true
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            maybeSwiping = false
        } else if (action == MotionEvent.ACTION_MOVE && maybeSwiping) {
            if (abs((ev.y - initialY).toInt()) > SWIPE_SLOP_Y) {
                maybeSwiping = false
            } else if (abs(ev.x - initialX) > SWIPE_SLOP_X) {
                maybeSwiping = false
                callback?.let {
                    // send an explicit event to children to cancel the current gesture that
                    // they thought was occurring.
                    val moveEvent = MotionEvent.obtain(ev)
                    moveEvent.action = MotionEvent.ACTION_CANCEL
                    post { super.dispatchTouchEvent(moveEvent) }

                    // and trigger our custom swipe request!
                    it.onNavMenuSwipeRequest(
                        osrsArticleSwipeGravity(ev.x - initialX, L10nUtil.isDeviceRTL)
                    )
                }
            }
        }
        return false
    }

    companion object {
        private val SWIPE_SLOP_Y = roundedDpToPx(32f)
        private val SWIPE_SLOP_X = roundedDpToPx(100f)
        private var CHILD_VIEW_SCROLLED = false

        fun setChildViewScrolled() {
            CHILD_VIEW_SCROLLED = true
        }
    }
}
