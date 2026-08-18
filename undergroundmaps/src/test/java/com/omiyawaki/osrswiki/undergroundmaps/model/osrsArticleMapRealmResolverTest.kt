package com.omiyawaki.osrswiki.undergroundmaps.model

import com.omiyawaki.osrswiki.undergroundmaps.osrsTestCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class osrsArticleMapRealmResolverTest {

    @Test
    fun wikiMapIdSelectsTheMatchingRealmInsteadOfAlwaysUsingSurface() {
        val catalog = osrsTestCatalog()
        val projection = requireNotNull(catalog.manifest.rasterProjectionOrNull())
        val mapper = osrsRealmEndpointMapper(projection)

        val resolved = osrsArticleMapRealmResolver.resolve(
            catalog = catalog,
            mapper = mapper,
            mapId = 36,
            plane = 0,
            gameX = 3400,
            gameY = 5800
        )

        assertEquals("cache-world-map:lms-desert-island", resolved.realm.id)
        assertNotEquals(catalog.surface.id, resolved.realm.id)
        assertNotNull(resolved.destination)
        assertEquals(3400, resolved.destination!!.gameX)
        assertEquals(5800, resolved.destination!!.gameY)
    }

    @Test
    fun missingMapIdStillPrefersARealmWhoseLayoutContainsThePoint() {
        val catalog = osrsTestCatalog()
        val projection = requireNotNull(catalog.manifest.rasterProjectionOrNull())
        val mapper = osrsRealmEndpointMapper(projection)

        val resolved = osrsArticleMapRealmResolver.resolve(
            catalog = catalog,
            mapper = mapper,
            mapId = null,
            plane = 0,
            gameX = 3400,
            gameY = 5800
        )

        assertEquals("cache-world-map:lms-desert-island", resolved.realm.id)
    }

    @Test
    fun surfaceMapIdKeepsGielinorSurface() {
        val catalog = osrsTestCatalog()
        val projection = requireNotNull(catalog.manifest.rasterProjectionOrNull())
        val mapper = osrsRealmEndpointMapper(projection)

        val resolved = osrsArticleMapRealmResolver.resolve(
            catalog = catalog,
            mapper = mapper,
            mapId = 0,
            plane = 0,
            gameX = 3222,
            gameY = 3218
        )

        assertEquals(catalog.surface.id, resolved.realm.id)
        assertNotNull(resolved.destination)
    }

    @Test
    fun productionCatalogMapsHeroesGuildMineOntoTaverleyUnderground() {
        val production = File(
            "${System.getProperty("user.home")}/Developer/osrswiki-local-artifacts/cache/binary-assets/underground-realms/underground-realms.json"
        )
        assumeTrue("Production realm catalog is present on this machine", production.isFile)

        val catalog = osrsRealmManifestParser().parse(production.readText())
        val projection = requireNotNull(catalog.manifest.rasterProjectionOrNull())
        val mapper = osrsRealmEndpointMapper(projection)
        val resolved = osrsArticleMapRealmResolver.resolve(
            catalog = catalog,
            mapper = mapper,
            mapId = 20,
            plane = 0,
            gameX = 2915,
            gameY = 9901
        )

        assertEquals("cache-world-map:taverley-underground", resolved.realm.id)
        assertEquals(20, resolved.realm.mapId)
        assertNotNull(resolved.destination)
    }
}
