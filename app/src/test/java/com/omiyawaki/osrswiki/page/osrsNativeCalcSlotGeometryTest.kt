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
    fun viewportCssTopScalesToViewPixelsWithoutUsingDocumentScroll() {
        assertEquals(
            550f,
            osrsNativeCalcSlotGeometry.hostTranslationYFromViewport(
                viewportTopCssPx = 200f,
                scrollDeltaViewPx = 0f,
                webViewScale = 2.75f
            ),
            0.1f
        )
        assertEquals(
            400f,
            osrsNativeCalcSlotGeometry.hostTranslationYFromViewport(
                viewportTopCssPx = 200f,
                scrollDeltaViewPx = 150f,
                webViewScale = 2.75f
            ),
            0.1f
        )
        val fragment = java.io.File("src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt")
        assertTrue(fragment.readText().contains("hostTranslationYFromViewport"))
    }

    @Test
    fun cssSlotTopMinusViewScrollIsNotDoubleScaled() {
        val scale = 2.75f
        val slotTopCss = 837f
        val slotTopView = slotTopCss * scale
        assertEquals(
            400f,
            osrsNativeCalcSlotGeometry.hostTranslationYFromViewScroll(
                slotTopCssPx = slotTopCss,
                scrollYViewPx = slotTopView - 400f,
                webViewScale = scale
            ),
            1f
        )
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
        assertTrue(source.contains("view.minimumWidth = width"))
        val popupBlock = source.substringAfter("android.widget.PopupWindow(")
            .substringBefore("private fun dismissNativeCalcPopup")
        assertTrue(popupBlock.contains("isFocusable = false"))
        assertFalse(popupBlock.contains("isFocusable = true"))
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
    fun nativePopupMustNotShowWhileGadgetSelectsRemain() {
        assertFalse(
            "leftover Method <select> must block the native popup so the first tap cannot open Chromium radios",
            osrsNativeCalcSlotGeometry.popupMayShow(
                selectCount = 3,
                slotActive = true,
                missing = false
            )
        )
        assertFalse(
            osrsNativeCalcSlotGeometry.popupMayShow(
                selectCount = 0,
                slotActive = false,
                missing = false
            )
        )
        assertFalse(
            osrsNativeCalcSlotGeometry.popupMayShow(
                selectCount = 0,
                slotActive = true,
                missing = true
            )
        )
        assertTrue(
            osrsNativeCalcSlotGeometry.popupMayShow(
                selectCount = 0,
                slotActive = true,
                missing = false
            )
        )
        assertFalse(
            "collapsed article header must stay tappable; overlay hides",
            osrsNativeCalcSlotGeometry.popupMayShow(
                selectCount = 0,
                slotActive = true,
                missing = false,
                collapsed = true
            )
        )
        assertFalse(
            osrsNativeCalcSlotGeometry.overlayCoversArticleHeader(
                overlayIncludesHeader = false,
                collapsed = true
            )
        )
        val fragment = java.io.File("src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt")
        val source = fragment.readText()
        assertTrue(source.contains("popupMayShow"))
        assertTrue(source.contains("nativeCalcSelectsCleared"))
        val install = source.substringAfter("private fun installNativeCalcSlot")
            .substringBefore("private data class NativeCalcSlotPayload")
        assertTrue(
            "install must not position the popup while gadget selects remain",
            install.contains("popupMayShow")
        )
        assertTrue(install.contains("nativeCalcSelectsCleared = false") || install.contains("dismissNativeCalcPopup"))
        assertTrue(
            "stale install callbacks must not re-show the popup after gadget selects return",
            source.contains("nativeCalcInstallGeneration")
        )
        val ready = source.substringAfter("override fun onPageReadyForDisplay")
            .substringBefore("override fun")
        assertTrue(
            "slot install must run after the article WebView is ready, not only from the native bind post",
            ready.contains("installNativeCalcSlot")
        )
        val position = source.substringAfter("private fun positionNativeCalcHost")
            .substringBefore("private fun dismissNativeCalcPopup")
        assertTrue(
            "scroll/position must refuse to show the popup until selects are cleared",
            position.contains("nativeCalcSelectsCleared")
        )
    }

    @Test
    fun popupDecorParentMustNotDetachHostOrEmptyFormPaints() {
        val host = Any()
        val popupDecor = Any()
        val xmlHost = Any()
        assertFalse(
            "after showAtLocation the host parent is PopupDecorView, not contentView; stripping it leaves an empty parchment popup",
            osrsNativeCalcSlotGeometry.shouldDetachHostForPopup(
                hostParent = popupDecor,
                popupContent = host,
                host = host
            )
        )
        assertFalse(
            osrsNativeCalcSlotGeometry.shouldDetachHostForPopup(
                hostParent = null,
                popupContent = null,
                host = host
            )
        )
        assertFalse(
            osrsNativeCalcSlotGeometry.shouldDetachHostForPopup(
                hostParent = host,
                popupContent = host,
                host = host
            )
        )
        assertTrue(
            "still reparent out of the XML native_calc_host before the first popup show",
            osrsNativeCalcSlotGeometry.shouldDetachHostForPopup(
                hostParent = xmlHost,
                popupContent = null,
                host = host
            )
        )
        val fragment = java.io.File("src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt")
        val position = fragment.readText()
            .substringAfter("private fun positionNativeCalcHost")
            .substringBefore("private fun dismissNativeCalcPopup")
        assertTrue(position.contains("shouldDetachHostForPopup"))
        assertFalse(
            "PopupDecorView !== contentView is true on every scroll; that removeView empties the live form",
            position.contains("view.parent !== nativeCalcPopup?.contentView")
        )
        assertTrue(
            "a popup whose content view was stripped must be recreated so the form can reattach",
            position.contains("view.parent == null") || position.contains("hostHasParent")
        )
    }

    @Test
    fun popupMustLayoutFormAtMeasuredHeightNotWindowHeight() {
        assertEquals(
            "PopupWindow sizes its content to the window; the form must keep its measured height so Method options below the clip are still laid out",
            2089,
            osrsNativeCalcSlotGeometry.popupContentHeight(formHeight = 2089, windowHeight = 1369)
        )
        assertEquals(2800, osrsNativeCalcSlotGeometry.popupContentHeight(formHeight = 2800, windowHeight = 1818))
        val fragment = java.io.File("src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt")
        val position = fragment.readText()
            .substringAfter("private fun positionNativeCalcHost")
            .substringBefore("private fun dismissNativeCalcPopup")
        assertTrue(position.contains("popupContentHeight"))
        assertTrue(
            "the calc view cannot be the PopupWindow content view or Android will relayout it to windowHeight and clip Method options",
            position.contains("nativeCalcPopupHost")
        )
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
