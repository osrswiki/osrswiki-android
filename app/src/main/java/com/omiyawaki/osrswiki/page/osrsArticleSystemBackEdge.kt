package com.omiyawaki.osrswiki.page

/**
 * Android gesture navigation reserves the left and right screen edges for the
 * system back gesture. Article chrome swipes must start inside the page, not
 * in those inset bands.
 */
object osrsArticleSystemBackEdge {
    fun contains(
        downX: Float,
        viewWidth: Float,
        leftInsetPx: Int,
        rightInsetPx: Int
    ): Boolean {
        if (viewWidth <= 0f) return false
        val left = leftInsetPx.coerceAtLeast(0)
        val right = rightInsetPx.coerceAtLeast(0)
        if (left == 0 && right == 0) return false
        return downX <= left || downX >= viewWidth - right
    }
}
