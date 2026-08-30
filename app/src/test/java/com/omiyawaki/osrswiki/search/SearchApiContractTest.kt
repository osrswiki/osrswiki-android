package com.omiyawaki.osrswiki.search

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchApiContractTest {
    @Test
    fun fulltextSearchSkipsExtractsWhilePrefixSearchFillsSnippetGaps() {
        val api = File("src/main/java/com/omiyawaki/osrswiki/network/WikiApiService.kt").readText()
        val fulltext = api.substringAfter("&generator=search").substringBefore("&generator=prefixsearch")
        val prefix = api.substringAfter("&generator=prefixsearch").substringBefore("suspend fun searchPages")

        assertFalse(
            "Fulltext already has Cirrus snippets; extracts on that request cap later rows and add latency",
            fulltext.contains("extracts")
        )
        assertTrue(prefix.contains("pageimages|extracts"))
        assertTrue(prefix.contains("exlimit=max"))
    }

    @Test
    fun debugHttpLoggingDoesNotBufferResponseBodies() {
        val factory = File("src/main/java/com/omiyawaki/osrswiki/network/OkHttpClientFactory.kt").readText()
        assertFalse(
            "BODY logging serializes every parse HTML payload on debug installs",
            factory.contains("Level.BODY")
        )
        assertTrue(factory.contains("Level.BASIC"))
    }

    @Test
    fun defaultSearchLocksMainAndCalculatorNamespacesWithoutACalculatorsTab() {
        val api = File("src/main/java/com/omiyawaki/osrswiki/network/WikiApiService.kt").readText()
        val fulltext = api.substringAfter("&generator=search").substringBefore("&generator=prefixsearch")
        val prefix = api.substringAfter("&generator=prefixsearch").substringBefore("suspend fun generatedNamespacedSearch")
        val openSearch = api.substringAfter("action=opensearch").substringBefore("suspend fun getPageExtract")
        val scope = File("src/main/java/com/omiyawaki/osrswiki/search/osrsSearchScope.kt").readText()

        assertEquals("0|116", osrsMediaWikiNamespace.DEFAULT_SEARCH)
        assertEquals(116, osrsMediaWikiNamespace.CALCULATOR)
        assertTrue(fulltext.contains("gsrnamespace=0|116"))
        assertTrue(prefix.contains("gpsnamespace=0|116"))
        assertTrue(openSearch.contains("namespace=0|116"))
        assertFalse(fulltext.contains("gsrnamespace=*"))
        assertFalse(prefix.contains("gpsnamespace=*"))
        assertFalse(scope.contains("CALCULATORS"))
        assertFalse(scope.contains("Calculators"))
        assertEquals(null, osrsSearchScope.ALL.namespace)
    }

    @Test
    fun openSearchParserReadsWebsiteTypeaheadTitlesAndDescriptions() {
        val payload = """
            ["glory",["Glory","Horn of glory"],["A quest item","A horn"],["https://oldschool.runescape.wiki/w/Glory","https://oldschool.runescape.wiki/w/Horn_of_glory"]]
        """.trimIndent().toByteArray()
        val results = osrsOpenSearchParser.parse(payload)
        assertEquals(listOf("Glory", "Horn of glory"), results.map { it.title })
        assertEquals("A quest item", results.first().snippet)
        assertEquals(listOf(0, 0), results.map { it.ns })
    }

    @Test
    fun openSearchParserMarksCalculatorNamespaceFromTitle() {
        val payload = """
            ["coordinates",["Treasure Trails/Guide/Coordinates","Calculator:Coordinates"],["",""],["https://oldschool.runescape.wiki/w/Treasure_Trails/Guide/Coordinates","https://oldschool.runescape.wiki/w/Calculator:Coordinates"]]
        """.trimIndent().toByteArray()
        val results = osrsOpenSearchParser.parse(payload)
        assertEquals(listOf(0, 116), results.map { it.ns })
        assertEquals("Calculator:Coordinates", results[1].title)
    }
}
