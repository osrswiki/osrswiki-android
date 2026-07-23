package com.omiyawaki.osrswiki.undergroundmaps.model

import com.omiyawaki.osrswiki.undergroundmaps.OSRS_TEST_MANIFEST
import com.omiyawaki.osrswiki.undergroundmaps.osrsTestCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsRealmManifestParserTest {
    @Test
    fun `manifest creates a selector entry for every published record`() {
        val catalog = osrsTestCatalog()

        assertEquals(3, catalog.realmCount)
        assertEquals(catalog.realmCount, catalog.selectorCount)
        assertEquals("cache-world-map:main", catalog.surface.id)
        assertEquals(1, catalog.sections.getValue(OSRS_REALM_GROUP_SURFACE).size)
        assertEquals(1, catalog.sections.getValue(OSRS_REALM_GROUP_REALMS).size)
        assertEquals(1, catalog.sections.getValue(OSRS_REALM_GROUP_OTHER_MAPS).size)
    }

    @Test
    fun `instanced realm without underground words remains primary and searchable`() {
        val realm = osrsTestCatalog().byId.getValue("cache-world-map:lms-desert-island")

        assertEquals(OSRS_REALM_GROUP_REALMS, realm.group)
        assertFalse(realm.canonicalName.contains("underground", ignoreCase = true))
        assertTrue(osrsRealmSearch.matches(realm, "desert island"))
        assertTrue(osrsRealmSearch.matches(realm, "LMS"))
    }

    @Test
    fun `search includes aliases and article while requiring every term`() {
        val other = osrsTestCatalog().byId.getValue("other-map-10042")

        assertTrue(osrsRealmSearch.matches(other, "poh"))
        assertTrue(osrsRealmSearch.matches(other, "player house"))
        assertFalse(osrsRealmSearch.matches(other, "player island"))
    }

    @Test
    fun `unsafe asset traversal fails closed`() {
        val unsafe = OSRS_TEST_MANIFEST.replace("realms/surface.mbtiles", "../surface.mbtiles")

        assertThrows(IllegalArgumentException::class.java) {
            osrsRealmManifestParser().parse(unsafe)
        }
    }

    @Test
    fun `duplicate stable IDs fail closed`() {
        val duplicate = OSRS_TEST_MANIFEST.replace("other-map-10042", "cache-world-map:main")

        assertThrows(IllegalArgumentException::class.java) {
            osrsRealmManifestParser().parse(duplicate)
        }
    }

    @Test
    fun `plane list and asset list must be a bijection`() {
        val missingPlane = OSRS_TEST_MANIFEST.replaceFirst("\"planes\": [0, 1]", "\"planes\": [0, 1, 2]")

        assertThrows(IllegalArgumentException::class.java) {
            osrsRealmManifestParser().parse(missingPlane)
        }
    }

    @Test
    fun `asset path validation rejects absolute empty and backslash paths`() {
        assertTrue(osrsRealmManifestParser.isSafeRelativeAssetPath("realms/a.mbtiles"))
        assertFalse(osrsRealmManifestParser.isSafeRelativeAssetPath("/realms/a.mbtiles"))
        assertFalse(osrsRealmManifestParser.isSafeRelativeAssetPath("realms\\a.mbtiles"))
        assertFalse(osrsRealmManifestParser.isSafeRelativeAssetPath("realms//a.mbtiles"))
        assertFalse(osrsRealmManifestParser.isSafeRelativeAssetPath("realms/../a.mbtiles"))
    }

    @Test
    fun `only authoritative available link resolves to a published target`() {
        val linked = OSRS_TEST_MANIFEST.replaceFirst(
            "\"assets\": [",
            """
                "links": [{
                  "id": "intermap-0001",
                  "from_realm_id": "cache-world-map:lms-desert-island",
                  "to_realm_id": "cache-world-map:main",
                  "from_position": {"plane": 1, "x": 3400, "y": 5800},
                  "to_position": {"plane": 0, "x": 3222, "y": 3218},
                  "direction": "cache-script",
                  "availability": "available",
                  "authoritative": true,
                  "confidence": 1.0,
                  "evidence": ["cache_client_script_1705_1706"],
                  "unavailable_reasons": []
                }],
                "assets": [
            """.trimIndent()
        )
        val link = osrsRealmManifestParser().parse(linked)
            .byId.getValue("cache-world-map:lms-desert-island").links.single()

        assertEquals("cache-world-map:main", link.targetRealmId("cache-world-map:lms-desert-island"))
        assertEquals(0, link.targetPlane("cache-world-map:lms-desert-island"))
        assertEquals("cache-world-map:lms-desert-island", link.targetRealmId("cache-world-map:main"))
        assertEquals(1, link.targetPlane("cache-world-map:main"))
    }

    @Test
    fun `available link to unpublished realm fails closed`() {
        val linked = OSRS_TEST_MANIFEST.replaceFirst(
            "\"assets\": [",
            """
                "links": [{
                  "id": "intermap-0002",
                  "from_realm_id": "cache-world-map:lms-desert-island",
                  "to_realm_id": "removed-realm",
                  "from_position": {"plane": 0, "x": 1, "y": 1},
                  "to_position": {"plane": 0, "x": 2, "y": 2},
                  "direction": "cache-script",
                  "availability": "available",
                  "authoritative": true,
                  "confidence": 1.0
                }],
                "assets": [
            """.trimIndent()
        )

        assertThrows(IllegalArgumentException::class.java) {
            osrsRealmManifestParser().parse(linked)
        }
    }

    @Test
    fun `available link with endpoint outside exact realm layout fails closed`() {
        val linked = OSRS_TEST_MANIFEST.replaceFirst(
            "\"assets\": [",
            """
                "links": [{
                  "id": "intermap-unmapped",
                  "from_realm_id": "cache-world-map:lms-desert-island",
                  "to_realm_id": "cache-world-map:main",
                  "from_position": {"plane": 0, "x": 4000, "y": 5800},
                  "to_position": {"plane": 0, "x": 3222, "y": 3218},
                  "direction": "cache-script",
                  "availability": "available",
                  "authoritative": true,
                  "confidence": 1.0
                }],
                "assets": [
            """.trimIndent()
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            osrsRealmManifestParser().parse(linked)
        }
        assertTrue(failure.message.orEmpty().contains("endpoint cannot be mapped"))
    }

    @Test
    fun `unavailable link without a recorded reason fails closed`() {
        val linked = OSRS_TEST_MANIFEST.replaceFirst(
            "\"assets\": [",
            """
                "links": [{
                  "id": "intermap-unavailable-without-reason",
                  "from_realm_id": "cache-world-map:lms-desert-island",
                  "to_realm_id": null,
                  "from_position": {"plane": 0, "x": 3400, "y": 5800},
                  "to_position": {"plane": 0, "x": 1000, "y": 10000},
                  "direction": "cache-script",
                  "availability": "unavailable",
                  "authoritative": false,
                  "confidence": 0.0,
                  "unavailable_reasons": []
                }],
                "assets": [
            """.trimIndent()
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            osrsRealmManifestParser().parse(linked)
        }
        assertTrue(failure.message.orEmpty().contains("recorded reason"))
    }
}
