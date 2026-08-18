package com.omiyawaki.osrswiki.undergroundmaps.ui

import com.omiyawaki.osrswiki.undergroundmaps.model.OSRS_REALM_GROUPS
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCatalog
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmEndpointMapper
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRelativeLinkZoomForAssets
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLink
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLinkPosition
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLinkTraversalDirection
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsMaximumDisplayExtentDp
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsPositiveDisplayDensity
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCameraEnvelope
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsDefaultZoomForAsset
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsSurfaceDefaultZoomForAsset
import com.omiyawaki.osrswiki.undergroundmaps.model.rasterProjectionOrNull
import com.omiyawaki.osrswiki.undergroundmaps.osrsTestCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class osrsRealmLinkCatalogTest {
    @Test
    fun `immutable catalog cache returns the same presentation instance per realm`() {
        val catalog = catalogWithSurfaceLinks(
            available = (1..12).map(::availableLink),
            unavailable = (1..3).map(::unavailableLink)
        )
        val cache = osrsRealmLinkCatalogCache(
            catalog,
            osrsRealmPresentationCatalog(catalog.manifest.realms)
        )

        val first = cache.get(catalog.surface)
        val repeated = cache.get(catalog.surface)

        assertFalse(first.cacheHit)
        assertTrue(repeated.cacheHit)
        assertSame(first.catalog, repeated.catalog)
        assertEquals(first.catalog.allRows, repeated.catalog.allRows)
        assertEquals(1, cache.size)
    }

    @Test
    fun `mixed surface inventory keeps 335 available rows searchable beside 47 unavailable`() {
        val catalog = catalogWithSurfaceLinks(
            available = (1..335).map(::availableLink),
            unavailable = (1..47).map(::unavailableLink)
        )
        val links = osrsRealmLinkCatalog(
            catalog.surface,
            catalog,
            osrsRealmPresentationCatalog(catalog.manifest.realms)
        )

        assertEquals(335, links.availableRows.size)
        assertEquals(47, links.unavailableCount)
        assertEquals(47, links.unavailableRows.size)
        assertEquals(382, links.allRows.size)
        assertEquals(335, links.filter("last man standing").size)
        assertEquals(listOf("intermap-0335"), links.filter("intermap-0335").map { it.link.id })
        assertTrue(links.filter("endpoint 3395 5798").isNotEmpty())
        assertEquals(47, links.filter("to endpoint unowned").size)
        assertTrue(links.unavailableRows.none { it.actionable })
        links.unavailableRows.forEach { row ->
            assertTrue(row.visiblePrimary.startsWith("Unavailable"))
            assertTrue(row.visibleSecondary.contains("to endpoint unowned"))
            assertTrue(row.accessibilityLabel.contains("Not selectable"))
        }
    }

    @Test
    fun `duplicate realm and floor targets have unique visible and accessibility context`() {
        val duplicated = listOf(availableLink(1), availableLink(2))
        val catalog = catalogWithSurfaceLinks(duplicated, emptyList())
        val rows = osrsRealmLinkCatalog(
            catalog.surface,
            catalog,
            osrsRealmPresentationCatalog(catalog.manifest.realms)
        ).availableRows

        assertEquals(1, rows.map { it.visiblePrimary }.distinct().size)
        assertEquals(2, rows.map { it.visiblePrimary to it.visibleSecondary }.distinct().size)
        assertEquals(2, rows.map { it.accessibilityLabel }.distinct().size)
        rows.forEach { row ->
            assertTrue(row.visibleSecondary.contains(row.link.id))
            assertTrue(row.visibleSecondary.contains("endpoint"))
            assertTrue(row.accessibilityLabel.contains("direction"))
            assertTrue(row.accessibilityLabel.contains("floor 0"))
        }
    }

    @Test
    fun `duplicate Fishing Trawler link targets retain structured map ID qualifiers`() {
        val base = osrsTestCatalog()
        val source = base.surface
        val targetTemplate = base.byId.getValue("other-map-10042")
        val targets = listOf(10064, 10065, 10066).map { mapId ->
            targetTemplate.copy(
                id = "other-map-$mapId",
                canonicalName = "Fishing Trawler",
                aliases = emptyList(),
                mapId = mapId,
                article = null
            )
        }
        val links = targets.mapIndexed { index, target ->
            osrsRealmLink(
                id = "fishing-link-$index",
                fromRealmId = source.id,
                toRealmId = target.id,
                fromPosition = osrsRealmLinkPosition(plane = 0, x = 3200, y = 3200),
                toPosition = osrsRealmLinkPosition(plane = 0, x = 2023, y = 9043),
                direction = "fixture_direction",
                availability = "available",
                authoritative = true,
                confidence = 1.0
            )
        }
        val linkedSource = source.copy(links = links)
        val realms = listOf(linkedSource) + targets
        val catalog = osrsRealmCatalog(
            manifest = base.manifest.copy(realms = realms),
            byId = realms.associateBy { it.id },
            surface = linkedSource,
            sections = OSRS_REALM_GROUPS.associateWith { group -> realms.filter { it.group == group } }
        )

        val rows = osrsRealmLinkCatalog(
            linkedSource,
            catalog,
            osrsRealmPresentationCatalog(realms)
        ).availableRows

        assertEquals(
            listOf(
                "Fishing Trawler — Map ID 10064 • floor 0",
                "Fishing Trawler — Map ID 10065 • floor 0",
                "Fishing Trawler — Map ID 10066 • floor 0"
            ),
            rows.map { it.visiblePrimary }
        )
        assertEquals(3, rows.map { it.accessibilityLabel }.distinct().size)
        assertTrue(rows.all { it.accessibilityLabel.contains("Map ID 1006") })
    }

    @Test
    fun `authoritative game endpoint maps to deterministic realm-local MapLibre camera`() {
        val catalog = osrsTestCatalog()
        val realm = catalog.byId.getValue("cache-world-map:lms-desert-island")
        val projection = requireNotNull(catalog.manifest.rasterProjectionOrNull())

        val destination = requireNotNull(
            osrsRealmEndpointMapper(projection).map(
                realm,
                osrsRealmLinkPosition(plane = 0, x = 3400, y = 5800)
            )
        )

        assertEquals(256.0, destination.assetPixelX, 0.0)
        assertEquals(256.0, destination.assetPixelY, 0.0)
        assertEquals(0.0, destination.longitude, 1e-12)
        assertEquals(0.0, destination.latitude, 1e-12)
        assertEquals(8.0, destination.zoom, 0.0)
        assertEquals(1, destination.matchingLayoutCount)
    }

    @Test
    fun `surface default zoom preserves the pre-selector source-pixel scale`() {
        val surfaceAsset = osrsTestCatalog().surface.assetForPlane(0)!!.copy(
            canvasSize = 16_384
        )

        assertEquals(5.3414426741929, osrsSurfaceDefaultZoomForAsset(surfaceAsset), 1e-12)
    }

    @Test
    fun `realm default zoom preserves one source-pixel scale across canvas sizes`() {
        val template = osrsTestCatalog().surface.assetForPlane(0)!!
        val relativeZooms = listOf(1_024, 2_048, 4_096, 32_768).map { canvasSize ->
            val nativeMaximumZoom = kotlin.math.log2(canvasSize / 512.0)
            osrsDefaultZoomForAsset(template.copy(canvasSize = canvasSize)) - nativeMaximumZoom
        }

        relativeZooms.forEach { relativeZoom ->
            assertEquals(0.3414426741929, relativeZoom, 1e-12)
        }
    }

    @Test
    fun `authoritative link zoom preserves visual scale relative to native max zoom`() {
        val realm = osrsTestCatalog().byId.getValue("cache-world-map:lms-desert-island")
        val sourceAsset = realm.assetForPlane(0)!!
        val targetAsset = realm.assetForPlane(1)!!.copy(maxZoom = 4)

        val result = osrsRelativeLinkZoomForAssets(
            currentZoom = 9.25,
            sourceAsset = sourceAsset,
            targetAsset = targetAsset
        )

        assertEquals(6, result.sourceNativeMaxZoom)
        assertEquals(4, result.targetNativeMaxZoom)
        assertEquals(3.25, result.relativeZoom, 0.0)
        assertEquals(7.25, result.requestedTargetZoom, 0.0)
        assertEquals(7.25, result.finalTargetZoom, 0.0)
        assertEquals("none", result.clampState)
    }

    @Test
    fun `authoritative link zoom policy clamps only after applying relative offset`() {
        val asset = osrsTestCatalog()
            .byId.getValue("cache-world-map:lms-desert-island")
            .assetForPlane(0)!!

        val maxClamped = osrsRelativeLinkZoomForAssets(
            currentZoom = 30.0,
            sourceAsset = asset,
            targetAsset = asset
        )
        val minClamped = osrsRelativeLinkZoomForAssets(
            currentZoom = -2.0,
            sourceAsset = asset,
            targetAsset = asset
        )

        assertEquals(30.0, maxClamped.requestedTargetZoom, 0.0)
        assertEquals(osrsRealmCameraEnvelope.maxZoom(asset), maxClamped.finalTargetZoom, 0.0)
        assertEquals("max", maxClamped.clampState)
        assertEquals(-2.0, minClamped.requestedTargetZoom, 0.0)
        assertEquals(osrsRealmCameraEnvelope.minZoom(asset), minClamped.finalTargetZoom, 0.0)
        assertEquals("min", minClamped.clampState)
    }

    @Test
    fun `same-realm link publishes explicit forward and reverse endpoint sides`() {
        val base = osrsTestCatalog()
        val realm = base.byId.getValue("cache-world-map:lms-desert-island")
        val sameRealmLink = osrsRealmLink(
            id = "same-realm-fixture",
            fromRealmId = realm.id,
            toRealmId = realm.id,
            fromPosition = osrsRealmLinkPosition(plane = 0, x = 3400, y = 5800),
            toPosition = osrsRealmLinkPosition(plane = 0, x = 3401, y = 5801),
            direction = "bidirectional_fixture",
            availability = "available",
            authoritative = true,
            confidence = 1.0
        )
        val linkedRealm = realm.copy(links = listOf(sameRealmLink))
        val realms = base.manifest.realms.map {
            if (it.id == realm.id) linkedRealm else it
        }
        val catalog = osrsRealmCatalog(
            manifest = base.manifest.copy(realms = realms),
            byId = realms.associateBy { it.id },
            surface = base.surface,
            sections = OSRS_REALM_GROUPS.associateWith { group ->
                realms.filter { it.group == group }
            }
        )

        val rows = osrsRealmLinkCatalog(
            linkedRealm,
            catalog,
            osrsRealmPresentationCatalog(realms)
        ).availableRows

        assertEquals(2, rows.size)
        assertEquals(2, rows.map { it.side.key }.distinct().size)
        assertEquals(
            setOf(
                osrsRealmLinkTraversalDirection.FORWARD,
                osrsRealmLinkTraversalDirection.REVERSE
            ),
            rows.map { it.side.traversalDirection }.toSet()
        )
        assertEquals(setOf(3400, 3401), rows.map { it.targetPosition.x }.toSet())
        assertEquals(2, rows.map { it.accessibilityLabel }.distinct().size)
    }

    @Test
    fun `shared zoom envelope honors positive sub-one density and eight overzoom levels`() {
        val asset = osrsTestCatalog()
            .byId.getValue("cache-world-map:lms-desert-island")
            .assetForPlane(0)!!

        assertEquals(0.75, osrsPositiveDisplayDensity(0.75, 120), 0.0)
        assertEquals(0.75, osrsPositiveDisplayDensity(0.0, 120), 0.0)
        assertEquals(1600.0, osrsMaximumDisplayExtentDp(900, 1200, 0.75, 120), 0.0)
        assertEquals(1600.0, osrsMaximumDisplayExtentDp(1200, 900, 0.75, 120), 0.0)
        assertEquals(14.0, osrsRealmCameraEnvelope.maxZoom(asset), 0.0)
        assertTrue(osrsRealmCameraEnvelope.contains(asset, 14.0))
        assertFalse(osrsRealmCameraEnvelope.contains(asset, 14.0001))
        assertThrows(IllegalArgumentException::class.java) {
            osrsPositiveDisplayDensity(Double.NaN, 0)
        }
    }

    private fun catalogWithSurfaceLinks(
        available: List<osrsRealmLink>,
        unavailable: List<osrsRealmLink>
    ): osrsRealmCatalog {
        val base = osrsTestCatalog()
        val surface = base.surface.copy(links = available + unavailable)
        val realms = base.manifest.realms.map { if (it.id == surface.id) surface else it }
        val manifest = base.manifest.copy(realms = realms)
        val byId = realms.associateBy { it.id }
        val sections = OSRS_REALM_GROUPS.associateWith { group ->
            realms.filter { it.group == group }
        }
        return osrsRealmCatalog(manifest, byId, surface, sections)
    }

    private fun availableLink(index: Int): osrsRealmLink {
        val sourceX = 3200 + index % 50
        val sourceY = 3200 + index / 50
        val targetX = 3395 + index % 10
        val targetY = 5798 + index % 5
        return osrsRealmLink(
            id = "intermap-${index.toString().padStart(4, '0')}",
            fromRealmId = "cache-world-map:main",
            toRealmId = "cache-world-map:lms-desert-island",
            fromPosition = osrsRealmLinkPosition(plane = 0, x = sourceX, y = sourceY),
            toPosition = osrsRealmLinkPosition(plane = 0, x = targetX, y = targetY),
            direction = "client_script_1705_start_to_script_1706_jump_target",
            availability = "available",
            authoritative = true,
            confidence = 1.0,
            evidence = listOf("cache_client_script_1705_1706")
        )
    }

    private fun unavailableLink(index: Int): osrsRealmLink = osrsRealmLink(
        id = "unresolved-${index.toString().padStart(4, '0')}",
        fromRealmId = "cache-world-map:main",
        toRealmId = null,
        fromPosition = osrsRealmLinkPosition(plane = 0, x = 3200, y = 3200),
        toPosition = osrsRealmLinkPosition(plane = 0, x = 1000, y = 10000),
        direction = "client_script_1705_start_to_script_1706_jump_target",
        availability = "unavailable",
        authoritative = false,
        confidence = 0.0,
        evidence = listOf("cache_client_script_1705_1706"),
        unavailableReasons = listOf("to_endpoint_unowned")
    )
}
