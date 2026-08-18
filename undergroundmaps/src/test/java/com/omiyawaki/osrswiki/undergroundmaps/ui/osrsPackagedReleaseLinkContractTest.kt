package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.undergroundmaps.data.osrsRealmRepository
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCameraEnvelope
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLinkTraversalDirection
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRelativeLinkZoomForAssets
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRasterCompositionFor
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
        val authoritativeAvailableLinkCount = catalog.manifest.realms
            .flatMap { it.links }
            .filter { it.authoritative && it.availability == "available" }
            .distinctBy { it.id }
            .size
        assumeTrue(
            "Full generated realm assets are required for the packaged release contract",
            catalog.realmCount == OSRS_EXPECTED_REALM_COUNT &&
                authoritativeAvailableLinkCount == OSRS_EXPECTED_AVAILABLE_LINK_COUNT
        )
        val presentations = osrsRealmPresentationCatalog(catalog.manifest.realms)
        val surfaceLinks = osrsRealmLinkCatalog(catalog.surface, catalog, presentations)
        val cache = osrsRealmLinkCatalogCache(catalog, presentations)
        val cachedSurfaceLinks = cache.get(catalog.surface)
        val repeatedSurfaceLinks = cache.get(catalog.surface)

        val allFloorCompositions = catalog.manifest.realms.flatMap { realm ->
            realm.planes.map { plane -> realm to osrsRasterCompositionFor(realm, plane) }
        }
        assertEquals(122, allFloorCompositions.size)
        assertEquals(
            1,
            catalog.manifest.realms.count { realm -> realm.assetForPlane(0) == null }
        )
        assertEquals(
            72,
            allFloorCompositions.count { (realm, composition) ->
                composition.selectedPlane != 0 && realm.assetForPlane(0) != null
            }
        )
        assertEquals(
            1,
            allFloorCompositions.count { (realm, composition) ->
                composition.selectedPlane != 0 && realm.assetForPlane(0) == null
            }
        )
        allFloorCompositions.forEach { (realm, composition) ->
            assertEquals(composition.selectedPlane, composition.layersBottomToTop.last().plane)
            assertEquals(1.0f, composition.layersBottomToTop.last().opacity)
            if (composition.selectedPlane != 0 && realm.assetForPlane(0) != null) {
                assertEquals(
                    listOf(0, composition.selectedPlane),
                    composition.layersBottomToTop.map { it.plane }
                )
                assertEquals(0.5f, composition.layersBottomToTop.first().opacity)
            } else {
                assertEquals(
                    listOf(composition.selectedPlane),
                    composition.layersBottomToTop.map { it.plane }
                )
            }
        }

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

        allAvailableRows.forEach { row ->
            val sourceRealm = catalog.byId.getValue(requireNotNull(row.side.sourceRealmId))
            val sourceAsset = requireNotNull(sourceRealm.assetForPlane(row.sourcePosition.plane))
            val asset = row.targetRealm.assetForPlane(row.targetPosition.plane)!!
            val zoom = osrsRelativeLinkZoomForAssets(
                currentZoom = sourceAsset.maxZoom + 1.25,
                sourceAsset = sourceAsset,
                targetAsset = asset
            ).finalTargetZoom
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

    private companion object {
        const val OSRS_EXPECTED_REALM_COUNT = 50
        const val OSRS_EXPECTED_AVAILABLE_LINK_COUNT = 350
    }
}
