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
}
