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
        assertTrue(xml.contains("android:id=\"@+id/native_calc_host\""))
        assertTrue(xml.contains("android:clipChildren=\"false\""))
        assertTrue(source.contains("hitClickable"))
        assertTrue(source.contains("dispatchTouchEvent"))
        assertTrue(source.contains("setOnTouchListener"))
        assertTrue(source.contains("nativeCalcPopupScreenRect"))
        assertTrue(source.contains("selectCount"))
        assertTrue(source.contains("slotActive"))
        assertTrue(source.contains("installNativeCalcSlot()"))
        val popupBlock = source.substringAfter("android.widget.PopupWindow(")
            .substringBefore("private fun dismissNativeCalcPopup")
        assertTrue(popupBlock.contains("isFocusable = false"))
        assertFalse(popupBlock.contains("isFocusable = true"))
        assertTrue(
            "popup must not be a touch-modal lid; off-control pans reach the article WebView",
            popupBlock.contains("setTouchModal(false)")
        )
        assertFalse(popupBlock.contains("setTouchModal(true)"))
        assertTrue(popupBlock.contains("isTouchable = false"))
        assertTrue(
            "non-focusable popup still needs FLAG_ALT_FOCUSABLE_IM so EditText can raise the IME",
            popupBlock.contains("INPUT_METHOD_NEEDED")
        )
        assertTrue(source.contains("offerNativeCalcIme"))
        assertTrue(source.contains("showSoftInput"))
        assertTrue(source.contains("popupConsumesWebViewTouch"))
        assertTrue(source.contains("decideControlTouch"))
        assertTrue(source.contains("nativeCalcTouchCandidate"))
        assertTrue(source.contains("nativeCalcBlockHorizontalSwipe"))
        assertTrue(source.contains("scaledTouchSlop"))
        assertTrue(source.contains("hostLocalX"))
        assertTrue(source.contains("setLocation"))
        assertFalse(
            "synthetic control taps must use raw/screen minus host origin, not WebView-local offsetLocation",
            source.substringAfter("private fun dispatchNativeCalcControlEvent")
                .substringBefore("private fun cancelWebViewPointer")
                .contains("offsetLocation")
        )
        assertFalse(
            "IME must not make the popup a touch-modal lid; pans on a focused field still scroll",
            source.contains("popup.isTouchable = true")
        )
        val offer = source.substringAfter("private fun offerNativeCalcIme")
            .substringBefore("private fun releaseNativeCalcIme")
        assertTrue(offer.contains("isFocusable = true"))
        assertTrue(offer.contains("isTouchable = false"))
        assertTrue(offer.contains("isFocusableInTouchMode = false"))
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
    fun firstLayoutWidthUsesContentColumnNotLeftoverOrIntersection() {
        assertEquals(
            "first probe must use the article content column, not a skinny leftover slot",
            366f,
            osrsNativeCalcSlotGeometry.firstLayoutWidthCss(
                slotWidthCss = 96f,
                contentColumnWidthCss = 366f,
                viewportWidthCss = 390f
            ),
            0.1f
        )
        assertEquals(
            366f,
            osrsNativeCalcSlotGeometry.firstLayoutWidthCss(
                slotWidthCss = 96f,
                contentColumnWidthCss = 366f,
                viewportWidthCss = 390f,
                intersected = true
            ),
            0.1f
        )
        assertEquals(
            "matching slot and column stay at the column",
            366f,
            osrsNativeCalcSlotGeometry.firstLayoutWidthCss(
                slotWidthCss = 366f,
                contentColumnWidthCss = 366f,
                viewportWidthCss = 390f
            ),
            0.1f
        )
        assertEquals(
            366f,
            osrsNativeCalcSlotGeometry.firstLayoutWidthCss(
                slotWidthCss = 0f,
                contentColumnWidthCss = 366f,
                viewportWidthCss = 390f
            ),
            0.1f
        )
        val fragment = java.io.File("src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt")
        val source = fragment.readText()
        assertTrue(source.contains("overlayWidthFromProbeCss") || source.contains("firstLayoutWidthCss"))
        val runtime = listOf(
            java.io.File("src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("app/src/main/assets/web/osrs_calculator_runtime.js")
        ).first { it.exists() }.readText()
        assertTrue(runtime.contains("osrsNativeCalcContentColumnWidth"))
        assertTrue(runtime.contains("osrsNativeCalcApplyContentColumnWidth"))
        assertTrue(
            "column measure must skip nested collapsibles inside the calculator box",
            runtime.contains("box.contains")
        )
        assertTrue(runtime.contains("contentColumn"))
    }

    @Test
    fun overlayWidthUsesDisclosureBodyInteriorNotBoxOrColumn() {
        assertEquals(
            "body interior 364 with box/column 388 must stay 364 so chrome is a subset of the collapsible",
            364f,
            osrsNativeCalcSlotGeometry.overlayWidthFromProbeCss(
                bodyWCss = 364f,
                slotWidthCss = 388f,
                contentColumnWidthCss = 388f,
                viewportWidthCss = 390f
            ),
            0.1f
        )
        assertEquals(
            "without bodyW, a matching slot width still wins over leftover pairing",
            364f,
            osrsNativeCalcSlotGeometry.overlayWidthFromProbeCss(
                bodyWCss = 0f,
                slotWidthCss = 364f,
                contentColumnWidthCss = 388f,
                viewportWidthCss = 390f
            ),
            0.1f
        )
        assertFalse(osrsNativeCalcSlotGeometry.popupConsumesWebViewTouch(false))
        assertTrue(osrsNativeCalcSlotGeometry.popupConsumesWebViewTouch(true))
        val fragment = java.io.File("src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt")
        val source = fragment.readText()
        val install = source.substringAfter("private fun installNativeCalcSlot")
            .substringBefore("private data class NativeCalcSlotPayload")
        assertTrue(install.contains("bodyW"))
        assertFalse(
            "slot-left paired with max(slot, box, column) spills past the box by the padding",
            install.contains("Math.max(r.width,boxR?boxR.width:0,column)")
        )
        assertTrue(source.contains("overlayWidthFromProbeCss"))
        assertFalse(
            "full formHeight stamps the DOM slot; viewport-capped overlayVisibleHeight was the inner-scroller cap",
            install.contains("overlayVisibleHeight")
        )
        val position = source.substringAfter("private fun positionNativeCalcHost")
            .substringBefore("private fun dismissNativeCalcPopup")
        assertFalse(position.contains("overlayVisibleHeight"))
        assertEquals(
            366f,
            osrsNativeCalcSlotGeometry.overlayClipWidthCss(
                slotWidthCss = 520f,
                contentColumnWidthCss = 366f,
                viewportWidthCss = 390f
            ),
            0.1f
        )
        val view = java.io.File("src/main/java/com/omiyawaki/osrswiki/page/osrsNativeCalcView.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/omiyawaki/osrswiki/page/osrsNativeCalcView.kt")
        val viewSource = view.readText()
        assertFalse(
            "article owns vertical scrolling; chrome is intrinsic like an on-wiki collapsible",
            viewSource.contains("NestedScrollView")
        )
        assertFalse(viewSource.contains("innerVerticalScrollEnabled"))
        assertFalse(
            "HorizontalScrollView steals article vertical pans; clip width like article tables",
            viewSource.contains("HorizontalScrollView")
        )
    }

    @Test
    fun overlayVisibleHeightIsViewportRemainderNotASlotStamp() {
        assertEquals(
            600f,
            osrsNativeCalcSlotGeometry.overlayVisibleHeight(
                formHeight = 1800f,
                viewportHeight = 800f,
                formTopY = 200f
            ),
            0.1f
        )
        assertEquals(
            480f,
            osrsNativeCalcSlotGeometry.overlayVisibleHeight(
                formHeight = 1800f,
                viewportHeight = 800f,
                formTopY = 200f,
                boxHeight = 480f
            ),
            0.1f
        )
        assertEquals(
            360f,
            osrsNativeCalcSlotGeometry.overlayVisibleHeight(
                formHeight = 360f,
                viewportHeight = 800f,
                formTopY = 200f
            ),
            0.1f
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

    @Test
    fun controlTouchDownOverClickableDoesNotConsumeUntilTapOrPan() {
        val slop = 16
        val down = osrsNativeCalcSlotGeometry.decideControlTouch(
            actionMasked = android.view.MotionEvent.ACTION_DOWN,
            hitClickable = true,
            candidate = false,
            blockHorizontalSwipe = false,
            deliveredDown = false,
            downX = 0f,
            downY = 0f,
            x = 100f,
            y = 200f,
            slopPx = slop
        )
        assertFalse("DOWN over a control must not consume; the WebView keeps the pointer", down.consume)
        assertTrue(down.candidate)
        assertTrue(down.blockHorizontalSwipe)
        assertFalse(down.dispatchTap)
        assertFalse(down.offerIme)

        val within = osrsNativeCalcSlotGeometry.decideControlTouch(
            actionMasked = android.view.MotionEvent.ACTION_MOVE,
            hitClickable = true,
            candidate = true,
            blockHorizontalSwipe = true,
            deliveredDown = false,
            downX = 100f,
            downY = 200f,
            x = 104f,
            y = 208f,
            slopPx = slop
        )
        assertFalse(within.consume)
        assertTrue(within.candidate)
        assertFalse(within.dispatchTap)

        val tap = osrsNativeCalcSlotGeometry.decideControlTouch(
            actionMasked = android.view.MotionEvent.ACTION_UP,
            hitClickable = true,
            candidate = true,
            blockHorizontalSwipe = true,
            deliveredDown = false,
            downX = 100f,
            downY = 200f,
            x = 104f,
            y = 208f,
            slopPx = slop
        )
        assertTrue("UP inside slop is the tap: consume so the WebView does not click", tap.consume)
        assertTrue(tap.dispatchTap)
        assertTrue(tap.offerIme)
        assertFalse(tap.candidate)
    }

    @Test
    fun hostLocalFromRawUsesScreenOriginNotWebViewLocal() {
        // WebView-local (400, 300) plus search-bar offset would miss a button
        // at popup (76, 321) if we subtracted host origin from local x/y.
        assertEquals(604f, osrsNativeCalcSlotGeometry.hostLocalX(680f, 76), 0.01f)
        assertEquals(110f, osrsNativeCalcSlotGeometry.hostLocalY(431f, 321), 0.01f)
        val webViewLocalY = 497f - 200f
        val wrongY = webViewLocalY - 321f
        assertTrue("WebView-local minus host screen Y misses the Lookup row", wrongY < 0f)
        assertTrue(osrsNativeCalcSlotGeometry.hostLocalY(497f, 321) > 0f)
    }

    @Test
    fun controlTouchVerticalSlopCancelsAndDoesNotTap() {
        val slop = 16
        assertFalse(osrsNativeCalcSlotGeometry.movementExceededSlop(0f, 16f, slop))
        assertTrue(osrsNativeCalcSlotGeometry.movementExceededSlop(0f, 17f, slop))
        assertTrue(osrsNativeCalcSlotGeometry.isVerticalArticlePan(3f, 40f))
        assertFalse(osrsNativeCalcSlotGeometry.isVerticalArticlePan(40f, 3f))

        val pan = osrsNativeCalcSlotGeometry.decideControlTouch(
            actionMasked = android.view.MotionEvent.ACTION_MOVE,
            hitClickable = true,
            candidate = true,
            blockHorizontalSwipe = true,
            deliveredDown = true,
            downX = 100f,
            downY = 200f,
            x = 102f,
            y = 240f,
            slopPx = slop
        )
        assertFalse("vertical slop belongs to the article WebView", pan.consume)
        assertFalse(pan.candidate)
        assertTrue(pan.blockHorizontalSwipe)
        assertTrue("already-delivered DOWN must be cancelled so the control does not click", pan.cancelControl)
        assertFalse(pan.dispatchTap)
        assertFalse(pan.offerIme)

        val up = osrsNativeCalcSlotGeometry.decideControlTouch(
            actionMasked = android.view.MotionEvent.ACTION_UP,
            hitClickable = true,
            candidate = false,
            blockHorizontalSwipe = true,
            deliveredDown = false,
            downX = 100f,
            downY = 200f,
            x = 102f,
            y = 280f,
            slopPx = slop
        )
        assertFalse(up.consume)
        assertFalse(up.dispatchTap)
        assertTrue(
            "keep blocking horizontal swipe through UP so a field pan cannot become back-swipe",
            up.blockHorizontalSwipe
        )
    }

    @Test
    fun parchmentDownReleasesImeAndDoesNotConsume() {
        val down = osrsNativeCalcSlotGeometry.decideControlTouch(
            actionMasked = android.view.MotionEvent.ACTION_DOWN,
            hitClickable = false,
            candidate = false,
            blockHorizontalSwipe = false,
            deliveredDown = false,
            downX = 0f,
            downY = 0f,
            x = 50f,
            y = 80f,
            slopPx = 16
        )
        assertFalse(down.consume)
        assertFalse(down.candidate)
        assertTrue(down.releaseIme)
        assertFalse(down.blockHorizontalSwipe)
    }
}
