package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.undergroundmaps.R
import com.omiyawaki.osrswiki.undergroundmaps.OSRS_CONTROL_GESTURE_SAFETY_DP
import com.omiyawaki.osrswiki.undergroundmaps.OSRS_REALM_LINKS_UI_ENABLED
import com.omiyawaki.osrswiki.undergroundmaps.OSRS_TOP_LEFT_CONTROL_CORNER_RADIUS_DP
import com.omiyawaki.osrswiki.undergroundmaps.OSRS_TOP_LEFT_CONTROL_WIDTH_DP
import com.omiyawaki.osrswiki.undergroundmaps.osrsHorizontalRangesOverlapWithSeparation
import com.omiyawaki.osrswiki.undergroundmaps.osrsLinksTopMarginPx
import com.omiyawaki.osrswiki.undergroundmaps.osrsSafeDrawingEdgeMarginPx
import com.omiyawaki.osrswiki.undergroundmaps.osrsSymmetricControlSideMarginPx
import com.omiyawaki.osrswiki.undergroundmaps.osrsTestCatalog
import com.omiyawaki.osrswiki.undergroundmaps.osrsSelectorTopObstructionPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

private const val OSRS_CONTROLLER_IME_BOTTOM_INSET_PX = 833
private const val OSRS_CONTROLLER_TOP_OBSTRUCTION_PX = 180

class osrsRealmSelectorGeometryTest {
    @Test
    fun `selected realm row uses a restrained inset highlight without shrinking its tap target`() {
        val selected = osrsRealmSelectorRowAppearance(
            selected = true,
            compactLandscapeImeLayout = false
        )
        val unselected = osrsRealmSelectorRowAppearance(
            selected = false,
            compactLandscapeImeLayout = false
        )
        val compactSelected = osrsRealmSelectorRowAppearance(
            selected = true,
            compactLandscapeImeLayout = true
        )

        assertEquals(56, selected.minimumHeightDp)
        assertEquals(8, selected.highlightHorizontalInsetDp)
        assertEquals(4, selected.highlightVerticalInsetDp)
        assertEquals(14, selected.highlightCornerRadiusDp)
        assertTrue(selected.drawsSelectionHighlight)
        assertEquals(selected.copy(drawsSelectionHighlight = false), unselected)

        assertEquals(48, compactSelected.minimumHeightDp)
        assertEquals(8, compactSelected.highlightHorizontalInsetDp)
        assertEquals(2, compactSelected.highlightVerticalInsetDp)
        assertEquals(14, compactSelected.highlightCornerRadiusDp)
        assertTrue(compactSelected.drawsSelectionHighlight)
    }
    @Test
    fun `floor control retains square geometry while map links ui is dormant`() {
        assertEquals(48, OSRS_TOP_LEFT_CONTROL_WIDTH_DP)
        assertEquals(22, OSRS_TOP_LEFT_CONTROL_CORNER_RADIUS_DP)
        assertFalse(OSRS_REALM_LINKS_UI_ENABLED)
    }

    @Test
    fun `compass and floor share selector horizontal margin while top uses selector gap`() {
        assertEquals(32, OSRS_SELECTOR_HORIZONTAL_MARGIN_DP)
        assertEquals(16, OSRS_SELECTOR_BOTTOM_GAP_DP)
        assertEquals(1, OSRS_CONTROL_GESTURE_SAFETY_DP)
        assertEquals(
            40,
            osrsSafeDrawingEdgeMarginPx(
                systemBarInsetPx = 24,
                displayCutoutInsetPx = 0,
                visualMarginPx = OSRS_SELECTOR_BOTTOM_GAP_DP
            )
        )
        assertEquals(
            52,
            osrsSafeDrawingEdgeMarginPx(
                systemBarInsetPx = 0,
                displayCutoutInsetPx = 36,
                visualMarginPx = OSRS_SELECTOR_BOTTOM_GAP_DP
            )
        )
        assertEquals(
            32,
            osrsSymmetricControlSideMarginPx(
                horizontalMarginPx = OSRS_SELECTOR_HORIZONTAL_MARGIN_DP,
                systemBarSideInsetPx = 0,
                displayCutoutSideInsetPx = 0,
                systemGestureSideInsetPx = 0,
                gestureSafetyPx = OSRS_CONTROL_GESTURE_SAFETY_DP
            )
        )
        assertEquals(
            121,
            osrsSymmetricControlSideMarginPx(
                horizontalMarginPx = OSRS_SELECTOR_HORIZONTAL_MARGIN_DP,
                systemBarSideInsetPx = 0,
                displayCutoutSideInsetPx = 0,
                systemGestureSideInsetPx = 120,
                gestureSafetyPx = OSRS_CONTROL_GESTURE_SAFETY_DP
            )
        )
    }

    @Test
    fun `side controls obstruct only when their horizontal ranges approach the selector`() {
        assertTrue(
            osrsHorizontalRangesOverlapWithSeparation(
                selectorLeftPx = 88,
                selectorRightPx = 992,
                controlLeftPx = 44,
                controlRightPx = 198,
                separationPx = 33
            )
        )
        assertFalse(
            osrsHorizontalRangesOverlapWithSeparation(
                selectorLeftPx = 332,
                selectorRightPx = 1872,
                controlLeftPx = 44,
                controlRightPx = 198,
                separationPx = 33
            )
        )
    }

    @Test
    fun `visible status participates in obstruction while hidden status contributes nothing`() {
        val visible = osrsSelectorTopObstructionPx(
            systemObstructionBottomPx = 120,
            controlSeparationPx = 12,
            floorBottomPx = 240,
            linksBottomPx = 200,
            statusBottomPx = 310
        )
        val hidden = osrsSelectorTopObstructionPx(
            systemObstructionBottomPx = 120,
            controlSeparationPx = 12,
            floorBottomPx = 240,
            linksBottomPx = 200,
            statusBottomPx = null
        )

        assertEquals(322, visible)
        assertEquals(252, hidden)
    }

    @Test
    fun `links action stacks below floor controls and falls back to top inset`() {
        assertEquals(
            264,
            osrsLinksTopMarginPx(
                systemTopInsetPx = 120,
                floorVisible = true,
                floorBottomPx = 240,
                controlSeparationPx = 24,
                topMarginPx = 16
            )
        )
        assertEquals(
            136,
            osrsLinksTopMarginPx(
                systemTopInsetPx = 120,
                floorVisible = false,
                floorBottomPx = 0,
                controlSeparationPx = 24,
                topMarginPx = 16
            )
        )
    }

    @Test
    fun `hidden ime expansion keeps horizontal bounds and bottom edge fixed`() {
        val collapsed = geometry(expanded = false)
        val expanded = geometry(expanded = true)

        assertEquals(collapsed.left, expanded.left)
        assertEquals(collapsed.right, expanded.right)
        assertEquals(collapsed.bottom, expanded.bottom)
        assertTrue(expanded.top < collapsed.top)
        assertTrue(expanded.top >= OSRS_TOP_OBSTRUCTION_PX)
    }

    @Test
    fun `visible ime becomes the active viewport bottom without changing width`() {
        val normal = geometry(expanded = true)
        val withIme = geometry(
            expanded = true,
            imeVisible = true,
            imeBottomInsetPx = OSRS_IME_BOTTOM_INSET_PX
        )

        assertEquals(normal.left, withIme.left)
        assertEquals(normal.right, withIme.right)
        assertEquals(
            OSRS_VIEWPORT_HEIGHT_PX - OSRS_IME_BOTTOM_INSET_PX - OSRS_BOTTOM_GAP_PX,
            withIme.bottom
        )
        assertTrue(withIme.top >= OSRS_TOP_OBSTRUCTION_PX)
        assertTrue(withIme.height <= normal.height)
    }

    @Test
    fun `small active viewport preserves the base row and never crosses obstruction`() {
        val bounds = osrsRealmSelectorGeometry.calculate(
            viewportWidthPx = 720,
            viewportHeightPx = 900,
            systemTopInsetPx = 48,
            systemBottomInsetPx = 48,
            imeBottomInsetPx = 480,
            imeVisible = true,
            expanded = true,
            collapsedHeightPx = 144,
            desiredExpandedHeightPx = 600,
            minimumExpandedHeightPx = 400,
            horizontalMarginPx = 48,
            maximumWidthPx = 620,
            bottomGapPx = 24,
            topObstructionPx = 240
        )

        assertEquals(620, bounds.width)
        assertTrue(bounds.height >= 144)
        assertTrue(bounds.top >= 240)
        assertEquals(396, bounds.bottom)
    }

    @Test
    fun `compact landscape ime geometry keeps the fixed surface below visible status`() {
        val bounds = osrsRealmSelectorGeometry.calculate(
            viewportWidthPx = 2204,
            viewportHeightPx = 1080,
            systemTopInsetPx = 0,
            systemBottomInsetPx = 0,
            imeBottomInsetPx = 683,
            imeVisible = true,
            expanded = true,
            collapsedHeightPx = 135,
            desiredExpandedHeightPx = 276,
            minimumExpandedHeightPx = 399,
            horizontalMarginPx = 88,
            maximumWidthPx = 1540,
            bottomGapPx = 44,
            topObstructionPx = 77
        )

        assertEquals(353, bounds.bottom)
        assertEquals(77, bounds.top)
        assertEquals(276, bounds.height)
        assertEquals(1540, bounds.width)
    }

    private fun geometry(
        expanded: Boolean,
        imeVisible: Boolean = false,
        imeBottomInsetPx: Int = 0
    ): osrsRealmSelectorBounds = osrsRealmSelectorGeometry.calculate(
        viewportWidthPx = OSRS_VIEWPORT_WIDTH_PX,
        viewportHeightPx = OSRS_VIEWPORT_HEIGHT_PX,
        systemTopInsetPx = 136,
        systemBottomInsetPx = 66,
        imeBottomInsetPx = imeBottomInsetPx,
        imeVisible = imeVisible,
        expanded = expanded,
        collapsedHeightPx = 163,
        desiredExpandedHeightPx = 1210,
        minimumExpandedHeightPx = 520,
        horizontalMarginPx = 88,
        maximumWidthPx = 904,
        bottomGapPx = OSRS_BOTTOM_GAP_PX,
        topObstructionPx = OSRS_TOP_OBSTRUCTION_PX
    )

    private companion object {
        const val OSRS_VIEWPORT_WIDTH_PX = 1080
        const val OSRS_VIEWPORT_HEIGHT_PX = 2340
        const val OSRS_BOTTOM_GAP_PX = 44
        const val OSRS_TOP_OBSTRUCTION_PX = 180
        const val OSRS_IME_BOTTOM_INSET_PX = 833
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class osrsRealmSelectorControllerTest {
    @Test
    fun `search hint boundary and target meet accessibility thresholds`() {
        val context = themedContext()
        val sampledSelectorSurface = ColorUtils.compositeColors(
            context.getColor(R.color.osrs_map_control_surface),
            Color.BLACK
        )
        val hint = ColorUtils.compositeColors(
            context.getColor(R.color.osrs_map_control_disabled),
            sampledSelectorSurface
        )
        val boundary = ColorUtils.compositeColors(
            context.getColor(R.color.osrs_map_control_field_border),
            sampledSelectorSurface
        )

        assertTrue(
            ColorUtils.calculateContrast(hint, sampledSelectorSurface) >= 4.5
        )
        assertTrue(
            ColorUtils.calculateContrast(boundary, sampledSelectorSurface) >= 3.0
        )

        val catalog = osrsTestCatalog()
        val presentations = osrsRealmPresentationCatalog(catalog.manifest.realms)
        val selector = osrsRealmSelector(
            context = context,
            host = FrameLayout(context),
            selectorIndex = osrsRealmSelectorIndex(catalog.sections, presentations),
            initialActiveRealmId = catalog.surface.id,
            initialVisibleName = presentations[catalog.surface].visibleName,
            initialAccessibilityName = presentations[catalog.surface].accessibilityName,
            onRealmSelected = {},
            onFilterMeasured = { _, _, _ -> },
            onToggleMeasured = { _, _ -> },
            onOutsideDismissMeasured = {},
            onExpandedChanged = {}
        )
        val minimumTargetPx = (48 * context.resources.displayMetrics.density).toInt()
        assertTrue(selector.search.minimumWidth >= minimumTargetPx)
        assertTrue(selector.search.minimumHeight >= minimumTargetPx)
        assertTrue(selector.search.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI != 0)
    }

    @Test
    fun `picker opens in place without focusing search and renders only realm rows`() {
        val catalog = osrsTestCatalog()
        val presentations = osrsRealmPresentationCatalog(catalog.manifest.realms)
        val context = themedContext()
        val host = FrameLayout(context)
        var selectedRealmId: String? = null
        val selector = osrsRealmSelector(
            context = context,
            host = host,
            selectorIndex = osrsRealmSelectorIndex(catalog.sections, presentations),
            initialActiveRealmId = catalog.surface.id,
            initialVisibleName = presentations[catalog.surface].visibleName,
            initialAccessibilityName = presentations[catalog.surface].accessibilityName,
            onRealmSelected = { selectedRealmId = it.id },
            onFilterMeasured = { _, _, _ -> },
            onToggleMeasured = { _, _ -> },
            onOutsideDismissMeasured = {},
            onExpandedChanged = {}
        )
        layout(host)
        selector.updateWindowGeometry(
            systemTopInsetPx = 136,
            systemBottomInsetPx = 66,
            imeBottomInsetPx = 0,
            imeVisible = false,
            topObstructionPx = 180
        )
        layout(host)
        val stableLayoutParams = selector.surface.layoutParams
        selector.updateWindowGeometry(
            systemTopInsetPx = 136,
            systemBottomInsetPx = 66,
            imeBottomInsetPx = 0,
            imeVisible = false,
            topObstructionPx = 180
        )
        assertSame(stableLayoutParams, selector.surface.layoutParams)
        val collapsed = selector.debugState()

        assertFalse(collapsed.expanded)
        assertFalse(collapsed.searchFocused)
        assertEquals(View.GONE, selector.search.visibility)
        assertEquals(View.GONE, selector.list.visibility)

        assertTrue(selector.baseButton.performClick())
        layout(host)
        val expanded = selector.debugState()

        assertTrue(expanded.expanded)
        assertFalse(expanded.searchFocused)
        assertEquals(collapsed.bounds?.left, expanded.bounds?.left)
        assertEquals(collapsed.bounds?.right, expanded.bounds?.right)
        assertEquals(collapsed.bounds?.bottom, expanded.bounds?.bottom)
        assertEquals(View.VISIBLE, selector.search.visibility)
        assertEquals(View.VISIBLE, selector.list.visibility)
        assertEquals(catalog.selectorCount, selector.list.adapter?.itemCount)
        assertEquals(selector.baseButton, selectorBaseRow(selector).getChildAt(0))
        assertEquals(null, selectedRealmId)

        val adapter = requireNotNull(selector.list.adapter)
        val holder = adapter.createViewHolder(
            selector.list,
            adapter.getItemViewType(0)
        )
        adapter.bindViewHolder(holder, 0)
        assertTrue(holder.itemView is android.widget.TextView)
        assertFalse((holder.itemView as android.widget.TextView).text.contains("maps"))
    }

    @Test
    fun `outside dismiss target appears only while expanded and preserves query`() {
        val catalog = osrsTestCatalog()
        val presentations = osrsRealmPresentationCatalog(catalog.manifest.realms)
        val context = themedContext()
        val host = FrameLayout(context)
        var outsideDismissCount = 0
        var outsideDismissNanos: Long? = null
        val selector = osrsRealmSelector(
            context = context,
            host = host,
            selectorIndex = osrsRealmSelectorIndex(catalog.sections, presentations),
            initialActiveRealmId = catalog.surface.id,
            initialVisibleName = presentations[catalog.surface].visibleName,
            initialAccessibilityName = presentations[catalog.surface].accessibilityName,
            onRealmSelected = {},
            onFilterMeasured = { _, _, _ -> },
            onToggleMeasured = { _, _ -> },
            onOutsideDismissMeasured = {
                outsideDismissCount += 1
                outsideDismissNanos = it
            },
            onExpandedChanged = {}
        )
        layout(host)

        assertEquals(View.GONE, selector.outsideDismissTarget.visibility)
        assertFalse(selector.outsideDismissTarget.isClickable)
        assertFalse(selector.debugState().outsideDismissAvailable)

        selector.expand()
        selector.search.setText("Ancient")
        layout(host)
        val expanded = selector.debugState()
        assertTrue(expanded.expanded)
        assertTrue(expanded.outsideDismissAvailable)
        assertEquals(View.VISIBLE, selector.outsideDismissTarget.visibility)

        assertTrue(selector.outsideDismissTarget.performClick())
        layout(host)
        val dismissed = selector.debugState()
        assertFalse(dismissed.expanded)
        assertEquals("Ancient", dismissed.query)
        assertFalse(dismissed.searchFocused)
        assertFalse(dismissed.outsideDismissAvailable)
        assertEquals(View.GONE, selector.outsideDismissTarget.visibility)
        assertEquals(1, outsideDismissCount)
        assertTrue(requireNotNull(outsideDismissNanos) >= 0L)
    }

    @Test
    fun `keyboard dismissal leaves the picker expanded`() {
        val catalog = osrsTestCatalog()
        val presentations = osrsRealmPresentationCatalog(catalog.manifest.realms)
        val context = themedContext()
        val host = FrameLayout(context)
        val selector = osrsRealmSelector(
            context = context,
            host = host,
            selectorIndex = osrsRealmSelectorIndex(catalog.sections, presentations),
            initialActiveRealmId = catalog.surface.id,
            initialVisibleName = presentations[catalog.surface].visibleName,
            initialAccessibilityName = presentations[catalog.surface].accessibilityName,
            onRealmSelected = {},
            onFilterMeasured = { _, _, _ -> },
            onToggleMeasured = { _, _ -> },
            onOutsideDismissMeasured = {},
            onExpandedChanged = {}
        )
        layout(host)

        selector.expand()
        assertTrue(selector.focusSearch())
        assertTrue(selector.search.hasFocus())
        selector.updateWindowGeometry(
            systemTopInsetPx = 136,
            systemBottomInsetPx = 66,
            imeBottomInsetPx = OSRS_CONTROLLER_IME_BOTTOM_INSET_PX,
            imeVisible = true,
            topObstructionPx = OSRS_CONTROLLER_TOP_OBSTRUCTION_PX
        )
        selector.updateWindowGeometry(
            systemTopInsetPx = 136,
            systemBottomInsetPx = 66,
            imeBottomInsetPx = 0,
            imeVisible = false,
            topObstructionPx = OSRS_CONTROLLER_TOP_OBSTRUCTION_PX
        )

        assertTrue(selector.debugState().expanded)
        assertFalse(selector.search.hasFocus())
        assertEquals(View.VISIBLE, selector.search.visibility)
        assertEquals(View.VISIBLE, selector.list.visibility)
    }

    @Test
    fun `compact landscape ime keeps search result and base as separate 48dp targets`() {
        val catalog = osrsTestCatalog()
        val presentations = osrsRealmPresentationCatalog(catalog.manifest.realms)
        val context = themedContext()
        val host = FrameLayout(context)
        val selector = osrsRealmSelector(
            context = context,
            host = host,
            selectorIndex = osrsRealmSelectorIndex(catalog.sections, presentations),
            initialActiveRealmId = catalog.surface.id,
            initialVisibleName = presentations[catalog.surface].visibleName,
            initialAccessibilityName = presentations[catalog.surface].accessibilityName,
            onRealmSelected = {},
            onFilterMeasured = { _, _, _ -> },
            onToggleMeasured = { _, _ -> },
            onOutsideDismissMeasured = {},
            onExpandedChanged = {}
        )
        layout(host, width = 2204, height = 1080)
        selector.expand()
        selector.updateWindowGeometry(
            systemTopInsetPx = 0,
            systemBottomInsetPx = 0,
            imeBottomInsetPx = 683,
            imeVisible = true,
            topObstructionPx = 77
        )
        layout(host, width = 2204, height = 1080)

        val compact = selector.debugState()
        val minimumTargetPx = (48 * context.resources.displayMetrics.density).toInt()
        assertTrue(compact.expanded)
        assertEquals(
            1080 - 683 - (16 * context.resources.displayMetrics.density).toInt(),
            compact.bounds?.bottom
        )
        assertEquals(compact.bounds?.bottom, compact.baseRowBottom)
        assertTrue(compact.searchBottom - compact.searchTop >= minimumTargetPx)
        assertTrue(compact.searchRight - compact.searchLeft >= minimumTargetPx)
        assertTrue(compact.listBottom - compact.listTop >= minimumTargetPx)
        assertTrue(compact.listRight - compact.listLeft >= minimumTargetPx)
        assertTrue(compact.searchRight <= compact.listLeft)
        assertTrue(compact.firstResultBottom - compact.firstResultTop >= minimumTargetPx)
        assertTrue(compact.firstResultRight - compact.firstResultLeft >= minimumTargetPx)
        assertTrue(compact.firstResultClickable)
        assertTrue(compact.firstResultFocusable)

        selector.updateWindowGeometry(
            systemTopInsetPx = 0,
            systemBottomInsetPx = 0,
            imeBottomInsetPx = 0,
            imeVisible = false,
            topObstructionPx = 77
        )
        layout(host, width = 2204, height = 1080)
        val restored = selector.debugState()
        assertTrue(restored.expanded)
        assertTrue(restored.searchBottom <= restored.listTop)
        assertEquals(restored.bounds?.left, restored.listLeft)
    }

    private fun themedContext(): Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_OsrsUndergroundMaps
    )

    private fun layout(
        host: FrameLayout,
        width: Int = 1080,
        height: Int = 2340
    ) {
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        host.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        host.layout(0, 0, width, height)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun selectorBaseRow(selector: osrsRealmSelector): FrameLayout {
        val content = selector.surface.getChildAt(0) as LinearLayout
        return content.getChildAt(content.childCount - 1) as FrameLayout
    }
}
