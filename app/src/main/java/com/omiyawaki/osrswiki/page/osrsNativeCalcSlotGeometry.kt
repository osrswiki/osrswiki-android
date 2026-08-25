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
        if (!root.isClickable && !root.isLongClickable) return null
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        if (!containsRawPoint(loc[0], loc[1], loc[0] + root.width, loc[1] + root.height, rawX, rawY)) {
            return null
        }
        return root
    }

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
}
