package com.omiyawaki.osrswiki.views

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class NavMenuTriggerLayout(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    interface Callback {
        fun onNavMenuSwipeRequest(gravity: Int)
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX = 0f
    private var initialY = 0f
    private var maybeSwiping = false
    var callback: Callback? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            initialX = ev.x
            initialY = ev.y
            maybeSwiping = true
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            maybeSwiping = false
        } else if (action == MotionEvent.ACTION_MOVE && maybeSwiping) {
            val dx = ev.x - initialX
            val dy = ev.y - initialY
            if (abs(dy) > touchSlop) {
                // If vertical movement is significant, it's a scroll, not a swipe.
                maybeSwiping = false
            } else if (abs(dx) > touchSlop * 2) {
                // If horizontal movement is significant, it's a swipe.
                maybeSwiping = false

                // Tell the children to cancel their current gesture.
                val cancelEvent = MotionEvent.obtain(ev)
                cancelEvent.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancelEvent)

                // Notify the listener of the swipe.
                val gravity = if (dx > 0) Gravity.START else Gravity.END
                callback?.onNavMenuSwipeRequest(gravity)
            }
        }
        // Never intercept events, just spy on them.
        return false
    }
}
