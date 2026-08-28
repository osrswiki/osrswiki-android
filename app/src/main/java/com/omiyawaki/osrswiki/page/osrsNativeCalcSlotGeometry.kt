package com.omiyawaki.osrswiki.page

object osrsNativeCalcSlotGeometry {
    /**
     * Above the article WebView hardware surface. Search/chrome stay uncovered
     * because the host is held invisible until slot Y is known and is never
     * clamped to the top (pin-to-top + this elevation is what covered chrome).
     */
    const val HOST_ELEVATION = 24f
    const val APP_BAR_ELEVATION = 9.75f

    /**
     * Native host Y relative to the WebView. CSS slot coordinates are scaled
     * into view pixels. Negative values mean the slot scrolled off the top —
     * do not clamp to 0 (that pins the form over chrome).
     */
    fun hostTranslationY(slotTopCssPx: Float, scrollYCssPx: Float, webViewScale: Float): Float {
        return viewportTranslationY(slotTopCssPx - scrollYCssPx, webViewScale)
    }

    fun hostTranslationYFromViewScroll(
        slotTopCssPx: Float,
        scrollYViewPx: Float,
        webViewScale: Float
    ): Float {
        val scale = when {
            webViewScale <= 0f -> 1f
            else -> webViewScale
        }
        return slotTopCssPx * scale - scrollYViewPx
    }

    fun hostTranslationYFromViewport(
        viewportTopCssPx: Float,
        scrollDeltaViewPx: Float,
        webViewScale: Float
    ): Float {
        val scale = when {
            webViewScale <= 0f -> 1f
            else -> webViewScale
        }
        return viewportTopCssPx * scale - scrollDeltaViewPx
    }

    fun viewportTranslationY(boundingClientRectTopCss: Float, webViewScale: Float): Float {
        val scale = when {
            webViewScale <= 0f -> 1f
            // WebView.getScale() is often density; overlay Y is already in view
            // pixels when CSS viewport width matches the WebView width (scale~1).
            kotlin.math.abs(webViewScale - 1f) < 0.08f -> 1f
            else -> webViewScale
        }
        return boundingClientRectTopCss * scale
    }

    fun cssToViewScale(webViewWidthPx: Int, cssClientWidth: Float): Float {
        if (cssClientWidth <= 0f || webViewWidthPx <= 0) return 1f
        return webViewWidthPx / cssClientWidth
    }

    fun isPinnedToWebViewTop(
        translationY: Float,
        slotTopCssPx: Float,
        scrollYCssPx: Float,
        webViewScale: Float
    ): Boolean {
        val unclamped = hostTranslationY(slotTopCssPx, scrollYCssPx, webViewScale)
        return unclamped < -1f && kotlin.math.abs(translationY) < 1f
    }

    fun coversArticleChrome(
        hostElevation: Float,
        pinnedToTop: Boolean,
        appBarElevation: Float = APP_BAR_ELEVATION
    ): Boolean {
        return pinnedToTop && hostElevation >= appBarElevation
    }

    data class PopupFrame(
        val windowY: Int,
        val windowHeight: Int,
        val contentTranslationY: Float,
        val visible: Boolean
    )

    /**
     * Intersect the translated form with the WebView rect. Negative
     * [translationY] shrinks from the top (content shifts up) instead of
     * painting over search chrome above [webViewTopOnScreen].
     */
    fun clippedPopupFrame(
        webViewTopOnScreen: Int,
        webViewHeight: Int,
        translationY: Float,
        formHeight: Int
    ): PopupFrame {
        if (webViewHeight <= 0 || formHeight <= 0) {
            return PopupFrame(webViewTopOnScreen, 0, 0f, false)
        }
        val unclippedTop = webViewTopOnScreen + translationY
        val unclippedBottom = unclippedTop + formHeight
        val clipTop = webViewTopOnScreen.toFloat()
        val clipBottom = (webViewTopOnScreen + webViewHeight).toFloat()
        val windowTop = maxOf(unclippedTop, clipTop)
        val windowBottom = minOf(unclippedBottom, clipBottom)
        val windowHeight = (windowBottom - windowTop).toInt()
        if (windowHeight <= 0) {
            return PopupFrame(webViewTopOnScreen, 0, 0f, false)
        }
        return PopupFrame(
            windowY = windowTop.toInt(),
            windowHeight = windowHeight,
            contentTranslationY = unclippedTop - windowTop,
            visible = true
        )
    }

    /**
     * The overlay is laid out at the top of the article shell and moved with
     * [hostTranslationY]. If the parent clips children to that untranslated
     * layout box, the scrolled-on-screen form becomes an empty/inverted rect
     * and does not paint. Parent clipChildren must be false while the overlay
     * is shown.
     */
    fun parentClipHidesTranslatedHost(
        clipChildren: Boolean,
        layoutTop: Float,
        layoutHeight: Float,
        translationY: Float
    ): Boolean {
        if (!clipChildren) return false
        val drawnTop = layoutTop + translationY
        val drawnBottom = drawnTop + layoutHeight
        val clipTop = layoutTop
        val clipBottom = layoutTop + layoutHeight
        return minOf(clipBottom, drawnBottom) <= maxOf(clipTop, drawnTop)
    }

    fun containsRawPoint(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        rawX: Float,
        rawY: Float
    ): Boolean {
        return rawX >= left && rawX < right && rawY >= top && rawY < bottom
    }

    fun hitClickable(root: android.view.View, rawX: Float, rawY: Float): android.view.View? {
        if (root.visibility != android.view.View.VISIBLE) return null
        if (root is android.view.ViewGroup) {
            for (i in root.childCount - 1 downTo 0) {
                hitClickable(root.getChildAt(i), rawX, rawY)?.let { return it }
            }
        }
        // EditText is focusable-in-touch-mode but not clickable by default.
        // Fields are interactive controls; a DOWN on them is a tap candidate,
        // not an owned gesture — vertical slop becomes article scroll.
        if (!root.isClickable && !root.isLongClickable && !root.isFocusableInTouchMode) {
            return null
        }
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        if (!containsRawPoint(loc[0], loc[1], loc[0] + root.width, loc[1] + root.height, rawX, rawY)) {
            return null
        }
        return root
    }

    /**
     * WebView [MotionEvent] local x/y are in WebView space. A synthetic
     * dispatch onto the popup host must use screen/raw minus the host's
     * screen origin, or buttons miss while EditText IME (raw hit-test)
     * still works.
     */
    fun hostLocalX(rawX: Float, hostScreenX: Int): Float = rawX - hostScreenX.toFloat()

    fun hostLocalY(rawY: Float, hostScreenY: Int): Float = rawY - hostScreenY.toFloat()

    fun popupMayShow(
        selectCount: Int,
        slotActive: Boolean,
        missing: Boolean,
        collapsed: Boolean = false
    ): Boolean {
        return !missing && slotActive && selectCount == 0 && !collapsed
    }

    fun overlayCoversArticleHeader(overlayIncludesHeader: Boolean, collapsed: Boolean): Boolean {
        return overlayIncludesHeader && collapsed
    }

    /**
     * After [android.widget.PopupWindow.showAtLocation], the host's parent is
     * PopupDecorView, not [popupContent]. Treating that as a foreign parent
     * and calling removeView strips the form, leaving an empty parchment popup.
     */
    fun shouldDetachHostForPopup(hostParent: Any?, popupContent: Any?, host: Any): Boolean {
        if (hostParent == null) return false
        if (popupContent === host) return false
        if (hostParent === popupContent) return false
        return true
    }

    /**
     * PopupWindow lays its content view out to the window size. Keep the form
     * at its measured height inside a wrapper so Method options below the
     * clip still exist and [contentTranslationY] can reveal them.
     */
    fun popupContentHeight(formHeight: Int, windowHeight: Int): Int {
        return formHeight.coerceAtLeast(1)
    }

    /**
     * Host width on first layout. Leftover gadget/slot shrink-wrap that is
     * much smaller than the article content column is ignored. [intersected]
     * must not change the result — width is not gated on viewport intersection.
     */
    @JvmOverloads
    fun firstLayoutWidthCss(
        slotWidthCss: Float,
        contentColumnWidthCss: Float,
        viewportWidthCss: Float,
        intersected: Boolean = false
    ): Float {
        val column = when {
            contentColumnWidthCss > 1f -> contentColumnWidthCss
            viewportWidthCss > 1f -> viewportWidthCss
            else -> slotWidthCss
        }
        if (contentColumnWidthCss > 1f && slotWidthCss > 1f &&
            slotWidthCss < contentColumnWidthCss * 0.7f
        ) {
            return contentColumnWidthCss
        }
        if (slotWidthCss > 1f) return slotWidthCss
        return if (intersected) column else column
    }

    /**
     * How much of the intrinsic form still intersects the article viewport.
     * Used to clip the PopupWindow to the WebView, not to shrink the DOM slot
     * or wrap an inner NestedScrollView. The article owns vertical scroll.
     */
    @JvmOverloads
    fun overlayVisibleHeight(
        formHeight: Float,
        viewportHeight: Float,
        formTopY: Float,
        boxHeight: Float = 0f
    ): Float {
        if (formHeight <= 0f) return 0f
        val remaining = maxOf(0f, viewportHeight - maxOf(formTopY, 0f))
        var cap = remaining
        if (boxHeight > 1f && (remaining < 1f || boxHeight >= remaining * 0.35f)) {
            cap = if (remaining < 1f) boxHeight else minOf(cap, boxHeight)
        }
        if (cap < 1f) return 0f
        return minOf(formHeight, cap)
    }

    /**
     * Overlay width from the 5/s probe. The disclosure-body interior ([bodyWCss])
     * is authoritative: pairing the slot's left with the box/column width spills
     * past the collapsible by the box padding (named on ip; Android same class).
     */
    fun overlayWidthFromProbeCss(
        bodyWCss: Float,
        slotWidthCss: Float,
        contentColumnWidthCss: Float,
        viewportWidthCss: Float,
        intersected: Boolean = false
    ): Float {
        val slot = if (bodyWCss > 1f) bodyWCss else slotWidthCss
        return overlayClipWidthCss(
            slotWidthCss = slot,
            contentColumnWidthCss = contentColumnWidthCss,
            viewportWidthCss = viewportWidthCss,
            intersected = intersected
        )
    }

    /**
     * Off-control pans must reach the article WebView. A control-started
     * gesture is consumed only when it stays a tap (never exceeded slop).
     */
    fun popupConsumesWebViewTouch(consume: Boolean): Boolean = consume

    data class osrsNativeCalcControlTouchDecision(
        val consume: Boolean,
        val candidate: Boolean,
        val blockHorizontalSwipe: Boolean,
        val dispatchTap: Boolean,
        val cancelControl: Boolean,
        val offerIme: Boolean,
        val releaseIme: Boolean,
        val downX: Float,
        val downY: Float
    )

    fun movementExceededSlop(dx: Float, dy: Float, slopPx: Int): Boolean {
        val slop = slopPx.coerceAtLeast(0).toFloat()
        return dx * dx + dy * dy > slop * slop
    }

    fun isVerticalArticlePan(dx: Float, dy: Float): Boolean = kotlin.math.abs(dy) >= kotlin.math.abs(dx)

    /**
     * Tap-vs-pan for a pointer that may have started on a native-calc control.
     * DOWN over a clickable is not consumed: the WebView keeps the pointer so a
     * later vertical pan can scroll. UP inside slop dispatches the tap.
     * Horizontal pans that started on a control must not become back-swipe.
     */
    fun decideControlTouch(
        actionMasked: Int,
        hitClickable: Boolean,
        candidate: Boolean,
        blockHorizontalSwipe: Boolean,
        deliveredDown: Boolean,
        downX: Float,
        downY: Float,
        x: Float,
        y: Float,
        slopPx: Int
    ): osrsNativeCalcControlTouchDecision {
        val dx = x - downX
        val dy = y - downY
        val exceeded = movementExceededSlop(dx, dy, slopPx)
        return when (actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                if (hitClickable) {
                    osrsNativeCalcControlTouchDecision(
                        consume = false,
                        candidate = true,
                        blockHorizontalSwipe = true,
                        dispatchTap = false,
                        cancelControl = false,
                        offerIme = false,
                        releaseIme = false,
                        downX = x,
                        downY = y
                    )
                } else {
                    osrsNativeCalcControlTouchDecision(
                        consume = false,
                        candidate = false,
                        blockHorizontalSwipe = false,
                        dispatchTap = false,
                        cancelControl = false,
                        offerIme = false,
                        releaseIme = true,
                        downX = x,
                        downY = y
                    )
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (!candidate && !blockHorizontalSwipe) {
                    idleTouchDecision(downX, downY)
                } else if (candidate && exceeded) {
                    osrsNativeCalcControlTouchDecision(
                        consume = false,
                        candidate = false,
                        blockHorizontalSwipe = true,
                        dispatchTap = false,
                        cancelControl = deliveredDown,
                        offerIme = false,
                        releaseIme = false,
                        downX = downX,
                        downY = downY
                    )
                } else {
                    osrsNativeCalcControlTouchDecision(
                        consume = false,
                        candidate = candidate,
                        blockHorizontalSwipe = true,
                        dispatchTap = false,
                        cancelControl = false,
                        offerIme = false,
                        releaseIme = false,
                        downX = downX,
                        downY = downY
                    )
                }
            }
            android.view.MotionEvent.ACTION_UP -> {
                if (candidate && !exceeded) {
                    osrsNativeCalcControlTouchDecision(
                        consume = true,
                        candidate = false,
                        blockHorizontalSwipe = false,
                        dispatchTap = true,
                        cancelControl = false,
                        offerIme = true,
                        releaseIme = false,
                        downX = downX,
                        downY = downY
                    )
                } else {
                    osrsNativeCalcControlTouchDecision(
                        consume = false,
                        candidate = false,
                        blockHorizontalSwipe = blockHorizontalSwipe,
                        dispatchTap = false,
                        cancelControl = deliveredDown,
                        offerIme = false,
                        releaseIme = false,
                        downX = downX,
                        downY = downY
                    )
                }
            }
            android.view.MotionEvent.ACTION_CANCEL -> osrsNativeCalcControlTouchDecision(
                consume = false,
                candidate = false,
                blockHorizontalSwipe = blockHorizontalSwipe,
                dispatchTap = false,
                cancelControl = deliveredDown,
                offerIme = false,
                releaseIme = false,
                downX = downX,
                downY = downY
            )
            else -> osrsNativeCalcControlTouchDecision(
                consume = false,
                candidate = candidate,
                blockHorizontalSwipe = blockHorizontalSwipe,
                dispatchTap = false,
                cancelControl = false,
                offerIme = false,
                releaseIme = false,
                downX = downX,
                downY = downY
            )
        }
    }

    private fun idleTouchDecision(downX: Float, downY: Float) = osrsNativeCalcControlTouchDecision(
        consume = false,
        candidate = false,
        blockHorizontalSwipe = false,
        dispatchTap = false,
        cancelControl = false,
        offerIme = false,
        releaseIme = false,
        downX = downX,
        downY = downY
    )

    /**
     * Overlay frame width. Wider-than-box chrome clips to the collapsible
     * column the same way wide article tables do.
     */
    @JvmOverloads
    fun overlayClipWidthCss(
        slotWidthCss: Float,
        contentColumnWidthCss: Float,
        viewportWidthCss: Float,
        intersected: Boolean = false
    ): Float {
        val fitted = firstLayoutWidthCss(
            slotWidthCss = slotWidthCss,
            contentColumnWidthCss = contentColumnWidthCss,
            viewportWidthCss = viewportWidthCss,
            intersected = intersected
        )
        val cap = when {
            contentColumnWidthCss > 1f -> contentColumnWidthCss
            viewportWidthCss > 1f -> viewportWidthCss
            else -> fitted
        }
        return minOf(fitted, cap)
    }
}
