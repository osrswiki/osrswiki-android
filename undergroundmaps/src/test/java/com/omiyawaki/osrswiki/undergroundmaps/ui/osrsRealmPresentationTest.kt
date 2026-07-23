package com.omiyawaki.osrswiki.undergroundmaps.ui

import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmRecord
import com.omiyawaki.osrswiki.undergroundmaps.osrsTestCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsRealmPresentationTest {
    @Test
    fun `three Fishing Trawler maps use their distinct structured map IDs`() {
        val realms = listOf(
            duplicateRealm("other-map-10066", "Fishing Trawler", mapId = 10066),
            duplicateRealm("other-map-10064", "Fishing Trawler", mapId = 10064),
            duplicateRealm("other-map-10065", "Fishing Trawler", mapId = 10065)
        )

        val presentations = osrsRealmPresentationCatalog(realms)

        assertEquals(
            "Fishing Trawler — Map ID 10064",
            presentations["other-map-10064"].visibleName
        )
        assertEquals(
            "Fishing Trawler — Map ID 10065",
            presentations["other-map-10065"].visibleName
        )
        assertEquals(
            "Fishing Trawler — Map ID 10066",
            presentations["other-map-10066"].visibleName
        )
        assertTrue(realms.all { presentations.matches(it, "Fishing Trawler") })
        assertEquals(
            listOf("other-map-10064"),
            realms.filter { presentations.matches(it, "Map ID 10064") }.map { it.id }
        )
        assertEquals(
            listOf("other-map-10066"),
            realms.filter {
                presentations.matches(it, "Fishing Trawler Map ID 10066")
            }.map { it.id }
        )
    }

    @Test
    fun `duplicate selector visible and accessibility labels are unique`() {
        val realms = listOf(
            duplicateRealm("other-map-10064", "Fishing Trawler", mapId = 10064),
            duplicateRealm("other-map-10065", "Fishing Trawler", mapId = 10065),
            duplicateRealm("other-map-10066", "Fishing Trawler", mapId = 10066)
        )
        val labels = osrsRealmPresentationCatalog(realms).orderedLabels

        assertEquals(labels.size, labels.map { it.visibleName }.distinct().size)
        assertEquals(labels.size, labels.map { it.accessibilityName }.distinct().size)
        assertEquals(
            listOf(
                "Select map Fishing Trawler, Map ID 10064",
                "Select map Fishing Trawler, Map ID 10065",
                "Select map Fishing Trawler, Map ID 10066"
            ),
            labels.map { it.selectorAccessibilityLabel(selected = false) }
        )
        assertEquals(
            "Selected map, Fishing Trawler, Map ID 10064",
            labels.first().selectorAccessibilityLabel(selected = true)
        )
    }

    @Test
    fun `generic duplicate names fall back to stable realm identity`() {
        val realms = listOf(
            duplicateRealm("instance:beta", "Shared Instance", mapId = null),
            duplicateRealm("instance:alpha", "Shared Instance", mapId = null)
        )

        val presentations = osrsRealmPresentationCatalog(realms)

        assertEquals("Realm ID instance:alpha", presentations["instance:alpha"].qualifier)
        assertEquals("Realm ID instance:beta", presentations["instance:beta"].qualifier)
        assertEquals(
            2,
            presentations.orderedLabels.map { it.selectorAccessibilityLabel(false) }.distinct().size
        )
    }

    @Test
    fun `presentation order and labels do not depend on manifest iteration order`() {
        val realms = listOf(
            duplicateRealm("instance:beta", "Shared Instance", mapId = null),
            duplicateRealm("other-map-10066", "Fishing Trawler", mapId = 10066),
            duplicateRealm("instance:alpha", "Shared Instance", mapId = null),
            duplicateRealm("other-map-10064", "Fishing Trawler", mapId = 10064)
        )

        val forward = osrsRealmPresentationCatalog(realms).orderedLabels
        val reverse = osrsRealmPresentationCatalog(realms.reversed()).orderedLabels

        assertEquals(forward, reverse)
        assertEquals(
            listOf("other-map-10064", "other-map-10066", "instance:alpha", "instance:beta"),
            forward.map { it.realmId }
        )
    }

    @Test
    fun `unique canonical name keeps its unqualified presentation`() {
        val surface = osrsTestCatalog().surface

        val label = osrsRealmPresentationCatalog(listOf(surface))[surface]

        assertNull(label.qualifier)
        assertEquals("Gielinor Surface", label.visibleName)
        assertEquals("Gielinor Surface", label.accessibilityName)
        assertEquals("Select map Gielinor Surface", label.selectorAccessibilityLabel(false))
    }

    @Test
    fun `selector index preserves results while incrementally narrowing candidates`() {
        val catalog = osrsTestCatalog()
        val presentations = osrsRealmPresentationCatalog(catalog.manifest.realms)
        val index = osrsRealmSelectorIndex(catalog.sections, presentations)

        val first = index.filter("player")
        val narrower = index.filter("player house")
        val duplicateQuery = index.filter("player   house")

        assertEquals(listOf("other-map-10042"), first.sections.values.flatten().map { it.id })
        assertEquals(listOf("other-map-10042"), narrower.sections.values.flatten().map { it.id })
        assertEquals(first.resultCount, narrower.evaluatedRealmCount)
        assertTrue(duplicateQuery === narrower)
        assertEquals("player house", duplicateQuery.normalizedQuery)
    }

    @Test
    fun `selector index retains structured duplicate identity search`() {
        val realms = listOf(
            duplicateRealm("other-map-10064", "Fishing Trawler", mapId = 10064),
            duplicateRealm("other-map-10065", "Fishing Trawler", mapId = 10065),
            duplicateRealm("other-map-10066", "Fishing Trawler", mapId = 10066)
        )
        val presentations = osrsRealmPresentationCatalog(realms)
        val index = osrsRealmSelectorIndex(
            realmsByGroup = mapOf("other_maps" to realms),
            realmPresentations = presentations
        )

        assertEquals(
            listOf("other-map-10065"),
            index.filter("Fishing Trawler Map ID 10065")
                .sections
                .values
                .flatten()
                .map { it.id }
        )
    }

    private fun duplicateRealm(
        id: String,
        canonicalName: String,
        mapId: Int?
    ): osrsRealmRecord = osrsTestCatalog().surface.copy(
        id = id,
        canonicalName = canonicalName,
        aliases = emptyList(),
        isSurface = false,
        group = "other_maps",
        nativeFileId = null,
        mapId = mapId,
        article = null
    )
}
