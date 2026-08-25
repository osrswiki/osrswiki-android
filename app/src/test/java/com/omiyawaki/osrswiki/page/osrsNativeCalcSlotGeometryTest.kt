package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsNativeCalcSlotGeometryTest {
    @Test
    fun formSitsBelowHeadingAtScrollZeroAndLeavesViewportWhenScrolledPast() {
        val headingBottom = 180f
        val slotTop = 220f
        val scale = osrsNativeCalcSlotGeometry.cssToViewScale(1080, 1080f)
        val atRest = osrsNativeCalcSlotGeometry.hostTranslationY(slotTop, 0f, scale)
        assertTrue("form top must be below the Calculator heading", atRest > headingBottom)
        assertEquals(slotTop, atRest, 0.01f)
        assertEquals(2.75f, osrsNativeCalcSlotGeometry.cssToViewScale(1080, 392.7f), 0.02f)

        val scrolledPast = osrsNativeCalcSlotGeometry.hostTranslationY(slotTop, 800f, scale)
        assertTrue("form must be allowed to leave the top of the article", scrolledPast < 0f)
        assertFalse(
            osrsNativeCalcSlotGeometry.isPinnedToWebViewTop(
                translationY = scrolledPast,
                slotTopCssPx = slotTop,
                scrollYCssPx = 800f,
                webViewScale = scale
            )
        )
        assertTrue(
            osrsNativeCalcSlotGeometry.isPinnedToWebViewTop(
                translationY = 0f,
                slotTopCssPx = slotTop,
                scrollYCssPx = 800f,
                webViewScale = scale
            )
        )
    }

    @Test
    fun hostMustNotStackOverSearchChrome() {
        assertTrue(osrsNativeCalcSlotGeometry.HOST_ELEVATION >= 8f)
        assertFalse(
            osrsNativeCalcSlotGeometry.coversArticleChrome(
                hostElevation = osrsNativeCalcSlotGeometry.HOST_ELEVATION,
                pinnedToTop = false
            )
        )
        assertTrue(
            osrsNativeCalcSlotGeometry.coversArticleChrome(
                hostElevation = osrsNativeCalcSlotGeometry.HOST_ELEVATION,
                pinnedToTop = true
            )
        )
    }

    @Test
    fun negativeTranslationYIsClippedToWebViewTopSoSearchStaysUncovered() {
        val webViewTop = 220
        val webViewHeight = 1800
        val formHeight = 1818
        val scrolled = osrsNativeCalcSlotGeometry.clippedPopupFrame(
            webViewTopOnScreen = webViewTop,
            webViewHeight = webViewHeight,
            translationY = -80f,
            formHeight = formHeight
        )
        assertTrue(scrolled.visible)
        assertEquals("popup top must not rise above the WebView into search chrome", webViewTop, scrolled.windowY)
        assertTrue(scrolled.windowHeight < formHeight)
        assertTrue(scrolled.contentTranslationY < 0f)
        assertEquals(-80f, scrolled.contentTranslationY, 0.01f)

        val gone = osrsNativeCalcSlotGeometry.clippedPopupFrame(
            webViewTopOnScreen = webViewTop,
            webViewHeight = webViewHeight,
            translationY = -2000f,
            formHeight = formHeight
        )
        assertFalse("form that has left the WebView must not paint", gone.visible)
        assertTrue(gone.windowHeight <= 0)

        val atRest = osrsNativeCalcSlotGeometry.clippedPopupFrame(
            webViewTopOnScreen = webViewTop,
            webViewHeight = webViewHeight,
            translationY = 400f,
            formHeight = formHeight
        )
        assertTrue(atRest.visible)
        assertEquals(webViewTop + 400, atRest.windowY)
        assertEquals(0f, atRest.contentTranslationY, 0.01f)
        assertTrue(atRest.windowY + atRest.windowHeight <= webViewTop + webViewHeight)
    }

    @Test
    fun viewportRectTopScalesIntoViewPixelsIncludingNegative() {
        assertEquals(-110f, osrsNativeCalcSlotGeometry.viewportTranslationY(-40f, 2.75f), 0.01f)
        assertEquals(-40f, osrsNativeCalcSlotGeometry.viewportTranslationY(-40f, 1f), 0.01f)
        assertEquals(0f, osrsNativeCalcSlotGeometry.viewportTranslationY(0f, 2.75f), 0.01f)
    }

    @Test
    fun parentMustNotClipTranslatedHostToEmptyRect() {
        assertTrue(
            osrsNativeCalcSlotGeometry.parentClipHidesTranslatedHost(
                clipChildren = true,
                layoutTop = 0f,
                layoutHeight = 1818f,
                translationY = 2241f
            )
        )
        assertFalse(
            osrsNativeCalcSlotGeometry.parentClipHidesTranslatedHost(
                clipChildren = false,
                layoutTop = 0f,
                layoutHeight = 1818f,
                translationY = 242f
            )
        )
        val fragment = java.io.File("src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt")
        val source = fragment.readText()
        val layout = java.io.File("src/main/res/layout/fragment_page.xml").takeIf { it.exists() }
            ?: java.io.File("app/src/main/res/layout/fragment_page.xml")
        val xml = layout.readText()
        assertTrue(source.contains("binding.root.clipChildren = false"))
        assertFalse(source.contains("binding.root.clipChildren = true"))
        assertTrue(source.contains("PopupWindow"))
        assertTrue(source.contains("showAtLocation"))
        assertTrue(source.contains("clippedPopupFrame"))
        val popupBlock = source.substringAfter("android.widget.PopupWindow(")
            .substringBefore("private fun dismissNativeCalcPopup")
        assertTrue(popupBlock.contains("isFocusable = true"))
        assertFalse(popupBlock.contains("isFocusable = false"))
        assertTrue(xml.contains("android:id=\"@+id/native_calc_host\""))
        assertTrue(xml.contains("android:clipChildren=\"false\""))
        assertTrue(source.contains("hitClickable"))
        assertTrue(source.contains("dispatchTouchEvent"))
        assertTrue(source.contains("setOnTouchListener"))
        assertTrue(source.contains("nativeCalcPopupScreenRect"))
        assertTrue(source.contains("selectCount"))
        assertTrue(source.contains("slotActive"))
        assertTrue(source.contains("installNativeCalcSlot()"))
    }

    @Test
    fun rawPointInsideNativeControlIsHitSoWebViewSelectDoesNotStealTap() {
        assertTrue(
            osrsNativeCalcSlotGeometry.containsRawPoint(
                left = 80,
                top = 500,
                right = 1000,
                bottom = 620,
                rawX = 540f,
                rawY = 530f
            )
        )
        assertFalse(
            osrsNativeCalcSlotGeometry.containsRawPoint(
                left = 80,
                top = 500,
                right = 1000,
                bottom = 620,
                rawX = 540f,
                rawY = 1750f
            )
        )
    }
}
