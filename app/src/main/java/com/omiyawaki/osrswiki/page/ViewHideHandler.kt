package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.util.DimenUtil
import com.omiyawaki.osrswiki.views.ObservableWebView
import com.omiyawaki.osrswiki.util.log.L
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
    private var isTouchInProgress: Boolean = false

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchDownTime = 0L
    private val viewConfig = ViewConfiguration.get(hideableView.context)
    private val touchSlop = viewConfig.scaledTouchSlop
    private val tapTimeout = ViewConfiguration.getTapTimeout().toLong()

    var enabled = true
        // Setter side-effect removed to allow external disabling without forcing display.
        // MainActivity will now manage visibility and enabled state more directly.

    @SuppressLint("ClickableViewAccessibility")
    private val nestedScrollViewTouchListener = View.OnTouchListener { _, event ->
        if (!enabled) return@OnTouchListener false
        handleGenericTouchEvent(event)
        false
    }

    private val nestedScrollViewScrollListener = NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
        handleScroll(scrollY - oldScrollY, scrollY, true)
    }

    private val recyclerViewScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            // Assuming dy is a result of human interaction or a scroll we want to react to.
            handleScroll(dy, recyclerView.computeVerticalScrollOffset(), true)
        }
    }

    private val recyclerViewTouchListener = object : RecyclerView.SimpleOnItemTouchListener() {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            if (!enabled) return false
            handleGenericTouchEvent(e)
            return false
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
    }

    private fun handleGenericTouchEvent(event: MotionEvent) {
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
    }

    private fun clearListeners() {
        val viewToClear = currentScrollView
        currentScrollView = null

        when (viewToClear) {
            is ObservableWebView -> {
                viewToClear.removeOnScrollChangeListener(this)
                viewToClear.removeOnDownMotionEventListener(this)
                viewToClear.removeOnUpOrCancelMotionEventListener(this)
                viewToClear.removeOnClickListener(this)
            }
            is NestedScrollView -> {
                viewToClear.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
                viewToClear.setOnTouchListener(null)
            }
            is RecyclerView -> {
                viewToClear.removeOnScrollListener(recyclerViewScrollListener)
                viewToClear.removeOnItemTouchListener(recyclerViewTouchListener)
            }
        }
    }

    fun setScrollableSource(view: View?) {
        if (currentScrollView == view) {
            if (view == null && enabled) ensureDisplayed() // Only ensureDisplayed if enabled and view becomes null
            return
        }
        clearListeners()
        if (view == null) {
            if (enabled) ensureDisplayed()  // Only ensureDisplayed if enabled and new view is null
            return
        }
        currentScrollView = view

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
            }
            is RecyclerView -> {
                view.addOnScrollListener(recyclerViewScrollListener)
                view.addOnItemTouchListener(recyclerViewTouchListener)
            }
            else -> {
                if (enabled) ensureDisplayed() // If unknown type and enabled, ensure displayed
            }
        }
    }

    private fun handleScroll(deltaY: Int, currentAbsoluteScrollY: Int, isHumanScroll: Boolean) {
        if (!enabled) { // Simplified guard: if not enabled, do nothing.
            L.d("ViewHideHandler: handleScroll rejected. Handler disabled.")
            return
        }
        // The isHumanScroll check might be more nuanced; for now, 'enabled' is the primary gate.
        // Original check: if (!enabled || !isHumanScroll && !isTouchInProgress)
        // If isHumanScroll is false (programmatic scroll), we might still want to react if isTouchInProgress (fling)
        // However, if handler is disabled, none of that matters.

        L.d("ViewHideHandler: handleScroll called. deltaY=$deltaY, currentAbsoluteScrollY=$currentAbsoluteScrollY, isHumanScroll=$isHumanScroll")

        if (gravity == Gravity.TOP && shouldAlwaysShow()) {
            ensureDisplayed() // ensureDisplayed itself will check 'enabled' if we add it there
            return
        }

        var animMargin: Int 
        val currentTranslationY = hideableView.translationY.toInt()

        if (gravity == Gravity.BOTTOM) {
            animMargin = if (deltaY > 0) {
                hideableView.height.coerceAtMost(currentTranslationY + deltaY)
            } else {
                0.coerceAtLeast(currentTranslationY + deltaY)
            }
        } else { // Assuming Gravity.TOP
            animMargin = if (deltaY < 0) { // Scrolling down content (finger moves down), show top bar
                0.coerceAtMost(currentTranslationY - deltaY)
            } else { // Scrolling up content (finger moves up), hide top bar
                (-hideableView.height).coerceAtLeast(currentTranslationY - deltaY)
            }
        }

        if (hideableView.translationY.toInt() != animMargin) {
            hideableView.translationY = animMargin.toFloat()
            anchoredView?.translationY = animMargin.toFloat()
        }

        if (updateElevation) {
            val defaultToolbarElevation = try { DimenUtil.dpToPx(4f) } catch (e: Throwable) { 12f }
            val elevation = if (currentAbsoluteScrollY == 0) 0F else defaultToolbarElevation
            if (elevation != hideableView.elevation) {
                hideableView.elevation = elevation
            }
        }
    }

    override fun onScrollChanged(oldScrollY: Int, scrollY: Int, isHumanScroll: Boolean) {
        handleScroll(scrollY - oldScrollY, scrollY, isHumanScroll)
    }

    override fun onUpOrCancelMotionEvent() {
        isTouchInProgress = false
    }

    override fun onDownMotionEvent() {
        isTouchInProgress = true
    }

    override fun onClick(x: Float, y: Float): Boolean {
        if (!enabled) return false // Guard added
        L.d("ViewHideHandler: onClick invoked. isTouchInProgress=$isTouchInProgress (should be false for tap), currentTransY=${hideableView.translationY}, height=${hideableView.height}")
        if (!isTouchInProgress) { // Check isTouchInProgress directly, as it's set by onUpOrCancelMotionEvent
            val currentTransY = hideableView.translationY.toInt()
            val viewHeight = hideableView.height

            if (gravity == Gravity.TOP) {
                if (currentTransY > -viewHeight) {
                    L.d("ViewHideHandler: onClick - Top bar is visible or partially visible. Hiding.")
                    ensureHidden()
                } else {
                    L.d("ViewHideHandler: onClick - Top bar is fully hidden. Displaying.")
                    ensureDisplayed()
                }
                return true
            } else if (gravity == Gravity.BOTTOM) {
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
             L.d("ViewHideHandler: onClick - conditions not met (isTouchInProgress=$isTouchInProgress)")
        }
        return false
    }

    fun ensureDisplayed() {
        if (!enabled && hideableView.visibility == View.VISIBLE) {
             // Allow resetting a visible bar even if handler is disabled for other interactions.
             // However, for full GONE state managed by MainActivity, this shouldn't fight.
             // Let's make it simple: if not enabled, don't do animations.
        }
         if (!enabled && !(hideableView.visibility == View.VISIBLE && hideableView.translationY != 0f) ) {
            // If not enabled, only proceed if it's to reset a visible, translated view.
            // This is getting complex. Simplest: if !enabled, return.
            // MainActivity will handle the VISIBLE/GONE state.
            // If it's VISIBLE, then this handler being disabled means it should stay put.
            // If it's VISIBLE and enabled=false, then shouldAlwaysShow might have been the reason, which ensures display.
             // The original logic: if(!enabled) { ensureDisplayed() } in setter. This was for shouldAlwaysShow case.
             // Let's assume this method is called when we *want* it displayed, and 'enabled' controls general reaction.
        }

        L.d("ViewHideHandler: ensureDisplayed called. Current transY=${hideableView.translationY}")
        // No explicit 'enabled' check here, trusting callers or 'shouldAlwaysShow' path
        ensureTranslationYInternal(hideableView, 0)
        anchoredView?.let { ensureTranslationYInternal(it, 0) }
    }

    private fun ensureHidden() {
        // No explicit 'enabled' check here for similar reasons to ensureDisplayed.
        val translation = if (gravity == Gravity.BOTTOM) hideableView.height else -hideableView.height
        L.d("ViewHideHandler: ensureHidden called. Target transY=$translation, current transY=${hideableView.translationY}")
        ensureTranslationYInternal(hideableView, translation)
        anchoredView?.let { ensureTranslationYInternal(it, translation) }
    }

    private fun ensureTranslationYInternal(view: View, translation: Int) {
        // This is the core animation method. It should probably run if called.
        // The guards should be in the public methods like handleScroll, onClick.
        if (view.translationY.toInt() != translation) {
            view.animate().translationY(translation.toFloat())
                .setDuration(250L)
                .start()
        }
    }
}
