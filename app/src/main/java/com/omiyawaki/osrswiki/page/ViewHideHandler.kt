package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.widget.NestedScrollView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.util.DimenUtil
import com.omiyawaki.osrswiki.views.ObservableWebView
import com.omiyawaki.osrswiki.util.log.L // Assuming your logger L
import kotlin.math.abs

class ViewHideHandler(
    private val hideableView: View,
    private val anchoredView: View?,
    private val gravity: Int,
    private val updateElevation: Boolean = true,
    private val shouldAlwaysShow: () -> Boolean
) : ObservableWebView.OnScrollChangeListener,
    ObservableWebView.OnUpOrCancelMotionEventListener,
    ObservableWebView.OnDownMotionEventListener,
    ObservableWebView.OnClickListener {

    private var currentScrollView: View? = null
    private var lastScrollY: Int = 0
    private var isTouchInProgress: Boolean = false

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchDownTime = 0L
    private val viewConfig = ViewConfiguration.get(hideableView.context)
    private val touchSlop = viewConfig.scaledTouchSlop
    private val tapTimeout = ViewConfiguration.getTapTimeout().toLong()

    var enabled = true
        set(value) {
            field = value
            if (!enabled) {
                ensureDisplayed()
            }
        }

    @SuppressLint("ClickableViewAccessibility")
    private val nestedScrollViewTouchListener = View.OnTouchListener { _, event ->
        if (!enabled) return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                touchDownTime = SystemClock.uptimeMillis()
                onDownMotionEvent()
            }
            MotionEvent.ACTION_UP -> {
                val clickDuration = SystemClock.uptimeMillis() - touchDownTime
                onUpOrCancelMotionEvent() // Sets isTouchInProgress = false

                if (clickDuration < tapTimeout &&
                    abs(event.x - touchStartX) < touchSlop &&
                    abs(event.y - touchStartY) < touchSlop) {
                    onClick(event.x, event.y)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                onUpOrCancelMotionEvent()
            }
        }
        false
    }

    private val nestedScrollViewScrollListener = NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
        onScrollChanged(oldScrollY, scrollY, true)
    }

    private fun clearListeners() {
        val viewToClear = currentScrollView
        currentScrollView = null
        lastScrollY = 0

        if (viewToClear is ObservableWebView) {
            viewToClear.removeOnScrollChangeListener(this)
            viewToClear.removeOnDownMotionEventListener(this)
            viewToClear.removeOnUpOrCancelMotionEventListener(this)
            viewToClear.removeOnClickListener(this)
        } else if (viewToClear is NestedScrollView) {
            viewToClear.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
            viewToClear.setOnTouchListener(null)
        }
    }

    fun setScrollableSource(view: View?) {
        if (currentScrollView == view) {
            if (view == null) ensureDisplayed()
            return
        }
        clearListeners()
        if (view == null) {
            ensureDisplayed()
            return
        }
        currentScrollView = view
        lastScrollY = 0
        when (view) {
            is ObservableWebView -> {
                view.addOnScrollChangeListener(this)
                view.addOnDownMotionEventListener(this)
                view.addOnUpOrCancelMotionEventListener(this)
                view.addOnClickListener(this)
            }
            is NestedScrollView -> {
                view.setOnScrollChangeListener(nestedScrollViewScrollListener)
                view.setOnTouchListener(nestedScrollViewTouchListener)
                lastScrollY = view.scrollY
            }
            else -> {
                ensureDisplayed()
            }
        }
    }

    override fun onScrollChanged(oldScrollY: Int, scrollY: Int, isHumanScroll: Boolean) {
        if (!enabled || !isHumanScroll) {
            return
        }
        this.lastScrollY = scrollY

        if (gravity == Gravity.TOP && shouldAlwaysShow()) {
            ensureDisplayed()
            return
        }

        var animMargin = 0
        val scrollDelta = scrollY - oldScrollY
        val currentTranslationY = hideableView.translationY.toInt()

        if (gravity == Gravity.BOTTOM) {
            animMargin = if (scrollY > oldScrollY) {
                hideableView.height.coerceAtMost(currentTranslationY + scrollDelta)
            } else {
                0.coerceAtLeast(currentTranslationY + scrollDelta)
            }
        } else if (gravity == Gravity.TOP) {
            animMargin = if (scrollY < oldScrollY) {
                0.coerceAtMost(currentTranslationY - scrollDelta)
            } else {
                (-hideableView.height).coerceAtLeast(currentTranslationY - scrollDelta)
            }
        }

        if (hideableView.translationY.toInt() != animMargin) {
             hideableView.translationY = animMargin.toFloat()
             anchoredView?.translationY = animMargin.toFloat()
        }

        if (updateElevation) {
            val defaultToolbarElevation = try { DimenUtil.dpToPx(4f) } catch (e: Throwable) { 12f }
            val elevation = if (scrollY == 0 && oldScrollY != 0) 0F else defaultToolbarElevation
            if (elevation != hideableView.elevation) {
                hideableView.elevation = elevation
            }
        }
    }

    override fun onUpOrCancelMotionEvent() {
        isTouchInProgress = false
        // No snapping behavior
    }

    override fun onDownMotionEvent() {
        isTouchInProgress = true
    }

    override fun onClick(x: Float, y: Float): Boolean {
        L.d("ViewHideHandler: onClick invoked. enabled=$enabled, isTouchInProgress=$isTouchInProgress, currentTransY=${hideableView.translationY}, height=${hideableView.height}")
        if (enabled && !isTouchInProgress) {
            val currentTransY = hideableView.translationY.toInt()
            val viewHeight = hideableView.height

            if (gravity == Gravity.TOP) {
                // If any part is visible (translationY > -height) -> hide it.
                // Else (it's fully hidden: translationY <= -height) -> show it.
                if (currentTransY > -viewHeight) {
                    L.d("ViewHideHandler: onClick - Top bar is visible or partially visible. Hiding.")
                    ensureHidden()
                } else {
                    L.d("ViewHideHandler: onClick - Top bar is fully hidden. Displaying.")
                    ensureDisplayed()
                }
                return true
            } else if (gravity == Gravity.BOTTOM) {
                // If any part is visible (translationY < height) -> hide it.
                // Else (it's fully hidden: translationY >= height) -> show it.
                if (currentTransY < viewHeight) {
                    L.d("ViewHideHandler: onClick - Bottom bar is visible or partially visible. Hiding.")
                    ensureHidden()
                } else {
                    L.d("ViewHideHandler: onClick - Bottom bar is fully hidden. Displaying.")
                    ensureDisplayed()
                }
                return true
            }
        } else {
             L.d("ViewHideHandler: onClick - conditions not met (enabled=$enabled, isTouchInProgress=$isTouchInProgress)")
        }
        return false
    }

    fun ensureDisplayed() {
        L.d("ViewHideHandler: ensureDisplayed called. Current transY=${hideableView.translationY}")
        ensureTranslationYInternal(hideableView, 0)
        anchoredView?.let { ensureTranslationYInternal(it, 0) }
    }

    private fun ensureHidden() {
        val translation = if (gravity == Gravity.BOTTOM) hideableView.height else -hideableView.height
        L.d("ViewHideHandler: ensureHidden called. Target transY=$translation, current transY=${hideableView.translationY}")
        ensureTranslationYInternal(hideableView, translation)
        anchoredView?.let { ensureTranslationYInternal(it, translation) }
    }

    private fun ensureTranslationYInternal(view: View, translation: Int) {
        if (view.translationY.toInt() != translation) {
            view.animate().translationY(translation.toFloat())
                .setDuration(250L)
                .start()
        }
    }
}
