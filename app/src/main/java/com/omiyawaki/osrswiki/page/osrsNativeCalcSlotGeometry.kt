package com.omiyawaki.osrswiki.page

object osrsNativeCalcSlotGeometry {
    /** Above the article WebView surface, below the page AppBar (9.75dp). */
    const val HOST_ELEVATION = 2f
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

    fun coversArticleChrome(hostElevation: Float, appBarElevation: Float = APP_BAR_ELEVATION): Boolean {
        return hostElevation >= appBarElevation
    }
}
