package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.undergroundmaps.R
import com.omiyawaki.osrswiki.undergroundmaps.model.OSRS_REALM_GROUPS
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCatalog
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLink
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLinkPosition
import com.omiyawaki.osrswiki.undergroundmaps.osrsTestCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class osrsRealmLinksDialogTest {
    @Test
    fun `unavailable reason row is rendered but cannot invoke navigation`() {
        val base = osrsTestCatalog()
        val surface = base.surface.copy(links = listOf(availableLink(), unavailableLink()))
        val realms = base.manifest.realms.map { if (it.id == surface.id) surface else it }
        val catalog = osrsRealmCatalog(
            manifest = base.manifest.copy(realms = realms),
            byId = realms.associateBy { it.id },
            surface = surface,
            sections = OSRS_REALM_GROUPS.associateWith { group -> realms.filter { it.group == group } }
        )
        val presentations = osrsRealmPresentationCatalog(realms)
        val links = osrsRealmLinkCatalog(surface, catalog, presentations)
        val baseContext: Context = ApplicationProvider.getApplicationContext()
        val context = ContextThemeWrapper(baseContext, R.style.Theme_OsrsUndergroundMaps)
        var selected: osrsRealmLinkRow? = null
        val controller = osrsRealmLinksDialog(
            context = context,
            links = links,
            onLinkSelected = { selected = it },
            onFilterMeasured = { _, _, _ -> }
        )
        val firstShow = controller.show()
        val dialog = firstShow.dialog

        assertEquals("direct_before_attach", firstShow.initialUpdateStrategy)
        assertTrue(firstShow.viewConstructionNanos >= 0L)
        assertTrue(firstShow.initialRowConversionNanos >= 0L)
        assertTrue(firstShow.initialAdapterSubmissionNanos >= 0L)
        val palette = controller.debugState()
        assertTrue(palette.explicitOsrsPalette)
        assertEquals(context.getColor(R.color.osrs_parchment), palette.titleTextColor)
        assertEquals(context.getColor(R.color.osrs_underground_parchment_dark), palette.summaryTextColor)
        assertEquals(context.getColor(R.color.osrs_parchment), palette.searchTextColor)
        assertEquals(context.getColor(R.color.osrs_underground_parchment_dark), palette.searchHintColor)
        assertTrue(palette.searchMinimumHeightPx >= (48 * context.resources.displayMetrics.density).toInt())
        assertFalse(palette.compactLandscapeImeChrome)
        val search = requireNotNull(dialog.findViewById<EditText>(R.id.osrs_links_search))
        assertTrue(search.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI != 0)

        val summary = requireNotNull(dialog.findViewById<android.widget.TextView>(R.id.osrs_links_summary))
        assertEquals(
            "1 authoritative link available. 1 unresolved link remains unavailable.",
            summary.text.toString()
        )
        val list = requireNotNull(dialog.findViewById<RecyclerView>(R.id.osrs_links_list))
        @Suppress("UNCHECKED_CAST")
        val adapter = list.adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
        assertEquals(2, adapter.itemCount)
        val unavailablePosition = 1
        val holder = adapter.createViewHolder(list, adapter.getItemViewType(unavailablePosition))
        adapter.bindViewHolder(holder, unavailablePosition)

        assertFalse(holder.itemView.isClickable)
        assertTrue(holder.itemView.contentDescription.toString().contains("to endpoint unowned"))
        assertTrue(holder.itemView.contentDescription.toString().contains("Not selectable"))
        assertFalse(holder.itemView.performClick())
        assertNull(selected)
        controller.dismiss()
    }

    @Test
    fun `repeated show reuses populated view and resets search outside next open`() {
        val base = osrsTestCatalog()
        val surface = base.surface.copy(links = listOf(availableLink(), unavailableLink()))
        val realms = base.manifest.realms.map { if (it.id == surface.id) surface else it }
        val catalog = osrsRealmCatalog(
            manifest = base.manifest.copy(realms = realms),
            byId = realms.associateBy { it.id },
            surface = surface,
            sections = OSRS_REALM_GROUPS.associateWith { group -> realms.filter { it.group == group } }
        )
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_OsrsUndergroundMaps
        )
        var filterCallbacks = 0
        val controller = osrsRealmLinksDialog(
            context = context,
            links = osrsRealmLinkCatalog(
                surface,
                catalog,
                osrsRealmPresentationCatalog(realms)
            ),
            onLinkSelected = {},
            onFilterMeasured = { _, _, _ -> filterCallbacks += 1 }
        )

        val first = controller.show()
        assertEquals(2, controller.debugState().displayedRowCount)
        assertEquals(0, filterCallbacks)
        first.dialog.findViewById<android.widget.EditText>(R.id.osrs_links_search)!!
            .setText("unavailable")
        assertEquals(1, controller.debugState().displayedRowCount)
        assertEquals(1, filterCallbacks)
        controller.dismiss()
        assertEquals("", controller.debugState().query)
        assertEquals(2, controller.debugState().displayedRowCount)
        assertEquals(1, filterCallbacks)

        val repeated = controller.show()
        assertEquals("reused", repeated.initialUpdateStrategy)
        assertEquals(0L, repeated.viewConstructionNanos)
        assertEquals(0L, repeated.initialFilterNanos)
        assertEquals(0L, repeated.initialRowConversionNanos)
        assertEquals(0L, repeated.initialAdapterSubmissionNanos)
        assertTrue(repeated.showingAfterReturn)
        assertEquals(2, controller.debugState().displayedRowCount)
        controller.dismiss()
    }

    private fun availableLink(): osrsRealmLink = osrsRealmLink(
        id = "intermap-available",
        fromRealmId = "cache-world-map:main",
        toRealmId = "cache-world-map:lms-desert-island",
        fromPosition = osrsRealmLinkPosition(plane = 0, x = 3222, y = 3218),
        toPosition = osrsRealmLinkPosition(plane = 0, x = 3400, y = 5800),
        direction = "client_script_1705_start_to_script_1706_jump_target",
        availability = "available",
        authoritative = true,
        confidence = 1.0
    )

    private fun unavailableLink(): osrsRealmLink = osrsRealmLink(
        id = "intermap-unavailable",
        fromRealmId = "cache-world-map:main",
        toRealmId = null,
        fromPosition = osrsRealmLinkPosition(plane = 0, x = 3222, y = 3218),
        toPosition = osrsRealmLinkPosition(plane = 0, x = 1000, y = 10000),
        direction = "client_script_1705_start_to_script_1706_jump_target",
        availability = "unavailable",
        authoritative = false,
        confidence = 0.0,
        unavailableReasons = listOf("to_endpoint_unowned")
    )
}
