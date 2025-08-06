package com.omiyawaki.osrswiki.views

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import com.omiyawaki.osrswiki.util.log.L
import kotlin.math.abs

class NavMenuTriggerLayout(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    interface Callback {
        fun onNavMenuSwipeRequest(gravity: Int)
    }

    private val verticalSlop = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics)
    private val horizontalSlop = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100f, resources.displayMetrics)

    private var initialX = 0f
    private var initialY = 0f
    private var maybeSwiping = false
    var callback: Callback? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked

        if (CHILD_VIEW_SCROLLED) {
            // The child has started scrolling, so cancel swipe detection for this gesture.
            L.d("NavMenu: Child view scrolled, canceling swipe detection")
            CHILD_VIEW_SCROLLED = false
            maybeSwiping = false
            return false
        }

        if (action == MotionEvent.ACTION_DOWN) {
            initialX = ev.x
            initialY = ev.y
            maybeSwiping = true
            L.d("NavMenu: ACTION_DOWN at x=${ev.x.toInt()}, y=${ev.y.toInt()}")
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (maybeSwiping) {
                L.d("NavMenu: Gesture ended without swipe trigger")
            }
            maybeSwiping = false
        } else if (action == MotionEvent.ACTION_MOVE && maybeSwiping) {
            val dx = ev.x - initialX
            val dy = ev.y - initialY
            L.d("NavMenu: MOVE dx=${dx.toInt()}, dy=${dy.toInt()}, vertSlop=${verticalSlop.toInt()}, horizSlop=${horizontalSlop.toInt()}")
            if (abs(dy) > verticalSlop) {
                // It's a vertical scroll, disqualify the swipe.
                L.d("NavMenu: Vertical scroll detected, disqualifying swipe")
                maybeSwiping = false
            } else if (abs(dx) > horizontalSlop) {
                // It's a qualifying horizontal swipe.
                val direction = if (dx > 0) "START" else "END"
                L.d("NavMenu: Horizontal swipe triggered, direction=$direction, canceling children")
                maybeSwiping = false

                // Tell the children to cancel their current gesture (e.g. link highlighting).
                val cancelEvent = MotionEvent.obtain(ev)
                cancelEvent.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancelEvent)

                // Notify the listener of the swipe direction.
                val gravity = if (dx > 0) Gravity.START else Gravity.END
                callback?.onNavMenuSwipeRequest(gravity)
            }
        }
        // Never intercept events ourselves, just spy on them.
        return false
    }

    companion object {
        private var CHILD_VIEW_SCROLLED = false

        fun setChildViewScrolled() {
            CHILD_VIEW_SCROLLED = true
        }
    }
}
