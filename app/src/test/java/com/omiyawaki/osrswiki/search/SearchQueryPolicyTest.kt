package com.omiyawaki.osrswiki.search

import com.omiyawaki.osrswiki.network.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryPolicyTest {
    private val noisyServerOrder = listOf(
        result("Locations", 1),
        result("Herblore", 2),
        result("Demonic Pacts", 3),
        result("Font", 4),
        result("Barbarian Village", 5),
        result("Barbarian", 6)
    )

    @Test
    fun partialMultiTokenQueryPromotesTheExpectedTitle() {
        assertEquals("Barbarian Village", SearchQueryPolicy.rank("barbarian v", noisyServerOrder).first().title)
    }

    @Test
    fun typoAndOverspecifiedQueriesStillPromoteTheExpectedTitle() {
        assertEquals("Barbarian Village", SearchQueryPolicy.rank("barbarian vilage", noisyServerOrder).first().title)
        assertEquals("Barbarian Village", SearchQueryPolicy.rank("barbarian village osrs", noisyServerOrder).first().title)
    }

    @Test
    fun officialArticleUrlSearchesForItsReadableTitle() {
        val url = "https://oldschool.runescape.wiki/w/Amulet_of_glory"
        assertEquals("Amulet of glory", SearchQueryPolicy.apiQuery(url))
        assertEquals("Amulet of glory", SearchQueryPolicy.rank(url, listOf(result("Minigames", 1), result("Amulet of glory", 9))).first().title)
        assertEquals(
            "Barbarian Village",
            SearchQueryPolicy.apiQuery("https://oldschool.runescape.wiki/index.php?title=Barbarian_Village&oldid=1")
        )
    }

    @Test
    fun commonOverspecificationIsRemovedBeforeTheNetworkRequest() {
        assertEquals("barbarian village", SearchQueryPolicy.apiQuery("barbarian village osrs"))
        assertEquals("Amulet of glory", SearchQueryPolicy.apiQuery("how to get Amulet of glory"))
        assertEquals("Ancient Cavern", SearchQueryPolicy.apiQuery("where is Ancient Cavern wiki page"))
        assertEquals("falador* gen* store*", SearchQueryPolicy.networkQuery("falador gen store"))
        assertEquals("Amulet of glory", SearchQueryPolicy.apiQuery("Amulet_of_glory"))
        assertEquals("Amulet* of glory*", SearchQueryPolicy.networkQuery("Amulet_of_glory"))
    }

    @Test
    fun punctuationOnlyServerTitlesCannotOutrankRealMatches() {
        val ranked = SearchQueryPolicy.rank(
            "recipe disaster",
            listOf(result("? ? ? ?", 1), result("Recipe for Disaster", 2))
        )
        assertEquals("Recipe for Disaster", ranked.first().title)
    }

    @Test
    fun titlePrefixPagesSurfaceEvenWhenFulltextRanksUnrelatedRunes() {
        val prefix = listOf(result("Earth rune", 1), result("Earth rune pack", 2))
        val fulltext = listOf(result("Nature rune", 1), result("Law rune", 2), result("Death rune", 3))
        assertEquals(
            "Earth rune",
            SearchQueryPolicy.merge("earth ru", prefix, fulltext).first().title
        )
        assertEquals(
            "Earth rune",
            SearchQueryPolicy.rank("earth rune", prefix + fulltext).first().title
        )
    }

    @Test
    fun agilitySkillOutranksCalculatorAgility() {
        val ranked = SearchQueryPolicy.rank(
            "agility",
            listOf(
                result("Calculator:Agility", 2),
                result("Agility", 1),
                result("Agility training", 3)
            )
        )
        assertEquals("Agility", ranked.first().title)
        assertTrue(ranked.any { it.title == "Calculator:Agility" })
    }

    @Test
    fun fullTokenCoverageOutranksAShortTitlePrefix() {
        val results = listOf(result("Amulet", 1), result("Amulet of glory", 2))
        assertEquals("Amulet of glory", SearchQueryPolicy.rank("amulet glo", results).first().title)
        assertEquals("Sailing", SearchQueryPolicy.rank("sailing guide", listOf(result("Ironman Guide/Sailing", 1), result("Sailing", 2))).first().title)
    }

    @Test
    fun unrelatedResultsKeepStableServerOrder() {
        val ranked = SearchQueryPolicy.rank("unmatched phrase", noisyServerOrder)
        assertEquals(noisyServerOrder.map { it.pageid }, ranked.map { it.pageid })
    }

    @Test
    fun highlightingUsesMeaningfulTermsAcrossTitleAndSnippet() {
        assertEquals(listOf("barbarian"), SearchQueryPolicy.highlightTerms("Barbarian V"))
        assertTrue(SearchQueryPolicy.highlightTerms("a").isEmpty())
        assertEquals(
            listOf(SearchQueryPolicy.HighlightRange(0, 11)),
            SearchQueryPolicy.titleHighlightRanges("Barbarian Village", "barbarian v")
        )
        assertEquals(
            listOf(SearchQueryPolicy.HighlightRange(4, 13)),
            SearchQueryPolicy.snippetHighlightRanges("The barbarian village has anvils", "barbarian v")
        )
    }

    @Test
    fun titleHighlightingUsesDecodedDisplayText() {
        assertEquals(
            listOf(SearchQueryPolicy.HighlightRange(0, 12)),
            SearchQueryPolicy.titleHighlightRanges("Wyrmscraig &amp; Sailing Changes", "wyrmscraig &")
        )
    }

    @Test
    fun prefixHitsKeepFulltextSnippetsWhenTheTitleMatchHasNoPreview() {
        val prefix = listOf(
            result("Glory", 10, snippet = null),
            result("Amulet of glory", 20, snippet = null)
        )
        val fulltext = listOf(
            result("Glory", 10, snippet = "A quest item used in..."),
            result("Amulet of glory", 20, snippet = "A dragonstone amulet...")
        )
        val merged = SearchQueryPolicy.merge("glory", prefix, fulltext)
        assertEquals("A quest item used in...", merged.first { it.title == "Glory" }.snippet)
        assertEquals("A dragonstone amulet...", merged.first { it.title == "Amulet of glory" }.snippet)
    }

    @Test
    fun representativeQueryCorpusPromotesNaturalTitleMatches() {
        val cases = listOf(
            "barbarian v" to "Barbarian Village",
            "barbarian vilage" to "Barbarian Village",
            "BARBARIAN-VILLAGE" to "Barbarian Village",
            "where to find Barbarian Village" to "Barbarian Village",
            "amulet glo" to "Amulet of glory",
            "amulet glory" to "Amulet of glory",
            "amulet of glroy" to "Amulet of glory",
            "Amulet_of_glory" to "Amulet of glory",
            "amulet of glory (4)" to "Amulet of glory",
            "low level alch" to "Low Level Alchemy",
            "dragon scim" to "Dragon scimitar",
            "recipe disaster" to "Recipe for Disaster",
            "varrok teleport" to "Varrock Teleport",
            "ancient cav" to "Ancient Cavern",
            "falador gen store" to "Falador General Store",
            "zulrah" to "Zulrah",
            "agility training guide" to "Agility training",
            "ironman money making" to "Ironman money making guide",
            "sailing guide" to "Sailing",
            "https://oldschool.runescape.wiki/w/Barbarian_Village" to "Barbarian Village"
        )
        val distractors = listOf("Locations", "Herblore", "Demonic Pacts", "Font")
        cases.forEachIndexed { index, (query, expected) ->
            val results = (distractors + expected).mapIndexed { resultIndex, title -> result(title, resultIndex + 1) }
            assertEquals("case $index: $query", expected, SearchQueryPolicy.rank(query, results).first().title)
        }
    }

    @Test
    fun emptyQueryBrowseIsReverseChronologicalByGeneratorIndexThenTimestampThenPageId() {
        val shuffled = listOf(
            SearchResult(
                ns = 112,
                title = "Update:Oldest",
                pageid = 10,
                index = 3,
                timestamp = "2024-01-01T00:00:00Z"
            ),
            SearchResult(
                ns = 112,
                title = "Update:Newest",
                pageid = 30,
                index = 1,
                timestamp = "2026-08-01T00:00:00Z"
            ),
            SearchResult(
                ns = 112,
                title = "Update:Middle",
                pageid = 20,
                index = 2,
                timestamp = "2025-06-01T00:00:00Z"
            )
        )
        assertEquals(
            listOf("Update:Newest", "Update:Middle", "Update:Oldest"),
            osrsUpdatesBrowseOrder.sort(shuffled).map { it.title }
        )
        assertEquals(
            listOf("Update:Newest", "Update:Middle", "Update:Oldest"),
            SearchQueryPolicy.rank("", shuffled).map { it.title }
        )

        val missingIndex = listOf(
            SearchResult(ns = 112, title = "Update:Later", pageid = 2, timestamp = "2026-08-02T00:00:00Z"),
            SearchResult(ns = 112, title = "Update:Earlier", pageid = 9, timestamp = "2026-08-01T00:00:00Z")
        )
        assertEquals(
            listOf("Update:Later", "Update:Earlier"),
            osrsUpdatesBrowseOrder.sort(missingIndex).map { it.title }
        )
    }

    private fun result(title: String, index: Int, snippet: String? = null) = SearchResult(
        ns = 0,
        title = title,
        pageid = index,
        index = index,
        snippet = snippet
    )
}
