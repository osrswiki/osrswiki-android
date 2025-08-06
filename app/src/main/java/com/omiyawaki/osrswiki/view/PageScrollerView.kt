package com.omiyawaki.osrswiki.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView

class PageScrollerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    interface Callback {
        fun onScrollStart()
        fun onScrollStop()
        fun onVerticalScroll(dy: Float)
    }

    private var dragging = false
    private var prevY = 0f
    var callback: Callback? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                prevY = event.rawY
                if (!dragging) {
                    dragging = true
                    callback?.onScrollStart()
                }
                // Consume the event to receive subsequent move/up events
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    callback?.onScrollStop()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val dy = event.rawY - prevY
                    callback?.onVerticalScroll(dy)
                    prevY = event.rawY
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
