package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.undergroundmaps.data.osrsRealmRepository
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsEndpointZoomForViewport
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsMaximumDisplayExtentDp
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCameraEnvelope
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLinkTraversalDirection
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsCameraState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class osrsPackagedReleaseLinkContractTest {
    @Test
    fun `full packaged release preserves mixed links and endpoint navigation contract`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val catalog = osrsRealmRepository(context).loadCatalog()
        assumeTrue(
            "Full generated realm assets are required for the packaged release contract",
            catalog.realmCount == OSRS_EXPECTED_REALM_COUNT
        )
        val presentations = osrsRealmPresentationCatalog(catalog.manifest.realms)
        val surfaceLinks = osrsRealmLinkCatalog(catalog.surface, catalog, presentations)
        val cache = osrsRealmLinkCatalogCache(catalog, presentations)
        val cachedSurfaceLinks = cache.get(catalog.surface)
        val repeatedSurfaceLinks = cache.get(catalog.surface)

        assertFalse(cachedSurfaceLinks.cacheHit)
        assertTrue(repeatedSurfaceLinks.cacheHit)
        assertSame(cachedSurfaceLinks.catalog, repeatedSurfaceLinks.catalog)
        assertEquals(surfaceLinks.availableRows, cachedSurfaceLinks.catalog.availableRows)
        assertEquals(surfaceLinks.unavailableRows, cachedSurfaceLinks.catalog.unavailableRows)

        assertEquals(335, surfaceLinks.availableRows.size)
        assertEquals(47, surfaceLinks.unavailableRows.size)
        assertEquals(382, surfaceLinks.allRows.size)
        assertTrue(surfaceLinks.unavailableRows.none { it.actionable })
        assertTrue(surfaceLinks.unavailableRows.all { it.link.unavailableReasons.isNotEmpty() })

        val ancientEntrance = surfaceLinks.availableRows.single {
            it.link.id == "intermap-0137"
        }
        assertEquals("cache-world-map:ancient-cavern", ancientEntrance.targetRealm.id)
        assertEquals(1, ancientEntrance.targetPosition.plane)
        assertEquals(1764, ancientEntrance.targetPosition.x)
        assertEquals(5367, ancientEntrance.targetPosition.y)
        assertTrue(ancientEntrance.visibleSecondary.contains("intermap-0137"))
        assertTrue(ancientEntrance.accessibilityLabel.contains("endpoint x 1764 y 5367"))

        val ancientRealm = catalog.byId.getValue("cache-world-map:ancient-cavern")
        val ancientLinks = osrsRealmLinkCatalog(ancientRealm, catalog, presentations)
        assertEquals(2, ancientLinks.availableRows.size)
        assertEquals(1, ancientLinks.availableRows.map { it.visiblePrimary }.distinct().size)
        assertEquals(
            2,
            ancientLinks.availableRows
                .map { it.visiblePrimary to it.visibleSecondary }
                .distinct()
                .size
        )
        assertEquals(2, ancientLinks.availableRows.map { it.accessibilityLabel }.distinct().size)
        assertEquals(
            setOf("intermap-0076", "intermap-0137"),
            ancientLinks.availableRows.map { it.link.id }.toSet()
        )

        val fishingTrawlerIds = listOf(
            "other-map-10064",
            "other-map-10065",
            "other-map-10066"
        )
        val fishingLabels = fishingTrawlerIds.map { presentations[it] }
        assertEquals(3, fishingLabels.map { it.visibleName }.distinct().size)
        assertEquals(3, fishingLabels.map { it.accessibilityName }.distinct().size)

        val allAvailableRows = catalog.manifest.realms.flatMap { realm ->
            osrsRealmLinkCatalog(realm, catalog, presentations).availableRows
        }
        val distinctAvailableLinks = catalog.manifest.realms
            .flatMap { it.links }
            .filter { it.authoritative && it.availability == "available" }
            .distinctBy { it.id }
        assertEquals(350, distinctAvailableLinks.size)
        assertEquals(700, allAvailableRows.size)
        assertEquals(700, allAvailableRows.map { it.side.key }.distinct().size)
        assertEquals(
            distinctAvailableLinks.map { it.id }.sorted(),
            allAvailableRows.groupBy { it.link.id }
                .onEach { (_, rows) -> assertEquals(2, rows.size) }
                .keys
                .sorted()
        )

        val sameRealmIds = setOf(
            "intermap-0036",
            "intermap-0338",
            "intermap-0350",
            "intermap-0362"
        )
        sameRealmIds.forEach { linkId ->
            val sides = allAvailableRows.filter { it.link.id == linkId }
            assertEquals(
                setOf(
                    osrsRealmLinkTraversalDirection.FORWARD,
                    osrsRealmLinkTraversalDirection.REVERSE
                ),
                sides.map { it.side.traversalDirection }.toSet()
            )
            assertEquals(2, sides.map { it.visibleSecondary }.distinct().size)
            assertEquals(2, sides.map { it.accessibilityLabel }.distinct().size)
            sides.forEach { row ->
                assertEquals(row.side.targetPosition, row.targetPosition)
                assertEquals(row.side.sourcePosition, row.sourcePosition)
                assertEquals(row.side.targetRealmId, row.targetRealm.id)
                assertEquals(row.targetPosition.x, row.destination.gameX)
                assertEquals(row.targetPosition.y, row.destination.gameY)
            }
        }

        val displayCases = listOf(
            Triple(540, 960, 0.75),
            Triple(1200, 1200, 0.75),
            Triple(1080, 1920, 1.0),
            Triple(2400, 3200, 2.0),
            Triple(4320, 4800, 3.0)
        )
        allAvailableRows.forEach { row ->
            val asset = row.targetRealm.assetForPlane(row.targetPosition.plane)!!
            displayCases.forEach { (widthPixels, heightPixels, density) ->
                val extentDp = osrsMaximumDisplayExtentDp(
                    widthPixels,
                    heightPixels,
                    density,
                    (density * 160.0).toInt()
                )
                val zoom = osrsEndpointZoomForViewport(
                    row.targetRealm,
                    row.destination,
                    extentDp
                )
                assertTrue(osrsRealmCameraEnvelope.contains(asset, zoom))
                assertTrue(
                    osrsCameraState(
                        latitude = row.destination.latitude,
                        longitude = row.destination.longitude,
                        zoom = zoom
                    ).isWithin(asset)
                )
            }
        }
    }

    private companion object {
        const val OSRS_EXPECTED_REALM_COUNT = 1097
    }
}
