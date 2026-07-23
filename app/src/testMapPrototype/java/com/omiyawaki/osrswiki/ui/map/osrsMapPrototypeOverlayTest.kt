package com.omiyawaki.osrswiki.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.FeatureCollection

class osrsMapPrototypeOverlayTest {

    @Test
    fun `reference stops expose five in game QA anchors with continuous zoom spacing`() {
        val stops = osrsMapPrototypeOverlay.referenceStops

        assertEquals(listOf(37, 50, 75, 100, 200), stops.map { it.percent })
        assertEquals(4.0, stops.first { it.percent == 100 }.scalePxPerSquare, 0.001)
        assertEquals(8.0, stops.first { it.percent == 200 }.scalePxPerSquare, 0.001)
        assertTrue(stops.zipWithNext().all { (left, right) -> left.mapLibreZoom < right.mapLibreZoom })
    }

    @Test
    fun `manifest keeps labels pois and map links as independent semantic groups`() {
        val grouped = osrsMapPrototypeOverlay.features.groupBy { it.kind }

        assertTrue(grouped.getValue(osrsMapFeatureKind.LABEL).size >= 5)
        assertTrue(grouped.getValue(osrsMapFeatureKind.POI).size >= 5)
        assertTrue(grouped.getValue(osrsMapFeatureKind.MAP_LINK).size >= 2)
        assertTrue(grouped.getValue(osrsMapFeatureKind.MAP_LINK).any { it.action == osrsMapAction.RECENTER })
        assertTrue(grouped.getValue(osrsMapFeatureKind.MAP_LINK).any { it.action == osrsMapAction.UNKNOWN_PENDING_EVIDENCE })
        assertNotNull(osrsMapPrototypeOverlay.features.firstOrNull { it.destinationSurface.contains("Draynor") })
    }

    @Test
    fun `geojson carries layer styling metadata and explicit link actions`() {
        val geoJson = osrsMapPrototypeOverlay.geoJson()

        assertTrue(geoJson.startsWith("""{"type":"FeatureCollection""""))
        assertTrue(geoJson.contains(""""kind":"label""""))
        assertTrue(geoJson.contains(""""kind":"poi""""))
        assertTrue(geoJson.contains(""""kind":"map_link""""))
        assertTrue(geoJson.contains(""""label_tier":"regional""""))
        assertTrue(geoJson.contains(""""category":"transportation""""))
        assertTrue(geoJson.contains(""""action":"recenter""""))
        assertTrue(geoJson.contains(""""action":"unknown_pending_evidence""""))
        assertTrue(geoJson.contains(""""destination_surface_id":"kharidian-desert-underground""""))
        assertTrue(geoJson.contains(""""destination_game_x":3093.0"""))

        val parsed = FeatureCollection.fromJson(geoJson)
        val parsedFeatures = parsed.features().orEmpty()
        assertEquals(osrsMapPrototypeOverlay.features.size, parsedFeatures.size)
        assertEquals("label", parsedFeatures.first().getStringProperty("kind"))
    }

    @Test
    fun `layer ids are stable for toggles and rendered feature queries`() {
        assertEquals(
            listOf(
                osrsMapPrototypeOverlay.osrsRegionalLabelLayerId,
                osrsMapPrototypeOverlay.osrsLocalLabelLayerId,
                osrsMapPrototypeOverlay.osrsLabelBadgeLayerId,
                osrsMapPrototypeOverlay.osrsPoiLayerId,
                osrsMapPrototypeOverlay.osrsPoiHaloLayerId,
                osrsMapPrototypeOverlay.osrsMapLinkLayerId,
                osrsMapPrototypeOverlay.osrsKeyHighlightLayerId
            ).toSet(),
            osrsMapPrototypeOverlay.semanticLayerIds.toSet()
        )
        assertEquals(
            listOf(
                osrsMapPrototypeOverlay.osrsLabelBadgeLayerId,
                osrsMapPrototypeOverlay.osrsRegionalLabelLayerId,
                osrsMapPrototypeOverlay.osrsLocalLabelLayerId
            ),
            osrsMapPrototypeOverlay.labelLayerIds
        )
        assertTrue(osrsMapPrototypeOverlay.hitTestLayerIds.contains(osrsMapPrototypeOverlay.osrsMapLinkLayerId))
    }

    @Test
    fun `prototype projection matches stamped map default view`() {
        val prototype = osrsMapPrototypeOverlay.gameToLatLng(3222.0, 3218.0)

        assertEquals(osrsMapDefaultView.LATITUDE, prototype.latitude, 0.000001)
        assertEquals(osrsMapDefaultView.LONGITUDE, prototype.longitude, 0.000001)
        val roundTrip = osrsMapPrototypeOverlay.latLngToGame(prototype)
        assertEquals(3222.0, roundTrip.first, 0.0001)
        assertEquals(3218.0, roundTrip.second, 0.0001)
    }

    @Test
    fun `key and surface manifests carry explicit availability and searchable targets`() {
        assertEquals(
            setOf(
                osrsMapCategory.BANK,
                osrsMapCategory.TRANSPORTATION,
                osrsMapCategory.DUNGEON,
                osrsMapCategory.GENERAL
            ),
            osrsMapPrototypeOverlay.categoryManifest.map { it.category }.toSet()
        )
        assertEquals("gielinor-surface", osrsMapPrototypeOverlay.surfaceManifest.single { it.available }.id)
        assertTrue(osrsMapPrototypeOverlay.surfaceManifest.filterNot { it.available }.all { "unavailable" in it.availability })
        assertEquals("label-falador", osrsMapPrototypeOverlay.search("Falador")?.id)
        assertEquals("label-karamja", osrsMapPrototypeOverlay.search("karam")?.id)
        assertEquals(null, osrsMapPrototypeOverlay.search("not-a-real-surface-place"))
    }

    @Test
    fun `prototype runtime surfaces are disabled unless explicitly opted in`() {
        val disabled = osrsMapPrototypeOverlay.runtimePolicy(explicitlyEnabled = false)

        assertEquals(false, disabled.showControls)
        assertEquals(false, disabled.installOverlay)
        assertEquals(false, disabled.installHitTesting)

        val enabled = osrsMapPrototypeOverlay.runtimePolicy(explicitlyEnabled = true)

        assertEquals(true, enabled.showControls)
        assertEquals(true, enabled.installOverlay)
        assertEquals(true, enabled.installHitTesting)
    }

    @Test
    fun `accessible targets expand to 48 pixels without leaving measured content`() {
        val target = requireNotNull(
            osrsMapPrototypeAccessibleTargetBounds(
                visualBounds = osrsMapPrototypeScreenBounds(6f, 40f, 26f, 60f),
                safeContent = osrsMapPrototypeScreenBounds(0f, 0f, 200f, 200f),
                obstacles = emptyList(),
                minimumSizePx = 48f
            )
        )

        assertEquals(48f, target.right - target.left, 0.001f)
        assertEquals(48f, target.bottom - target.top, 0.001f)
        assertTrue(target.left >= 0f)
        assertTrue(target.top >= 0f)
        assertTrue(target.right <= 200f)
        assertTrue(target.bottom <= 200f)
        assertTrue(target.left <= 6f && target.right >= 26f)
    }

    @Test
    fun `offscreen or obstructed visuals never become clamped virtual targets`() {
        val safe = osrsMapPrototypeScreenBounds(0f, 0f, 200f, 200f)

        assertNull(
            osrsMapPrototypeAccessibleTargetBounds(
                visualBounds = osrsMapPrototypeScreenBounds(205f, 60f, 235f, 90f),
                safeContent = safe,
                obstacles = emptyList(),
                minimumSizePx = 48f
            )
        )
        assertNull(
            osrsMapPrototypeAccessibleTargetBounds(
                visualBounds = osrsMapPrototypeScreenBounds(80f, 80f, 100f, 100f),
                safeContent = safe,
                obstacles = listOf(osrsMapPrototypeScreenBounds(70f, 70f, 115f, 115f)),
                minimumSizePx = 48f
            )
        )
    }
}
