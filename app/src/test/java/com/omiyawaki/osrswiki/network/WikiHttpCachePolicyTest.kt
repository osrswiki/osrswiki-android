package com.omiyawaki.osrswiki.network

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiHttpCachePolicyTest {

    @Test
    fun cacheBoundsAreSixtyFourMibUnderNamedCacheDir() {
        assertEquals("okhttp_wiki_http", WikiHttpCachePolicy.CACHE_DIR_NAME)
        assertEquals(64L * 1024L * 1024L, WikiHttpCachePolicy.CACHE_MAX_BYTES)
    }

    @Test
    fun wikiParseAndLoadPhpStayUnderServerCacheControl() {
        assertNull(WikiHttpCachePolicy.cacheControlFor(get(PARSE_URL)))
        assertNull(
            WikiHttpCachePolicy.cacheControlFor(
                get("https://oldschool.runescape.wiki/load.php?modules=jquery&only=scripts")
            )
        )
        assertNull(
            WikiHttpCachePolicy.cacheControlFor(
                get("https://oldschool.runescape.wiki/images/Coins_25.png")
            )
        )
    }

    @Test
    fun searchPrefixOpenSearchAndRecentChangesMustStayFresh() {
        val fresh = listOf(
            "https://oldschool.runescape.wiki/api.php?action=query&generator=search&gsrsearch=rune",
            "https://oldschool.runescape.wiki/api.php?action=query&generator=prefixsearch&gpssearch=earth",
            "https://oldschool.runescape.wiki/api.php?action=query&generator=recentchanges&grcnamespace=0",
            "https://oldschool.runescape.wiki/api.php?action=query&list=search&srsearch=glory",
            "https://oldschool.runescape.wiki/api.php?action=query&list=prefixsearch&pssearch=abys",
            "https://oldschool.runescape.wiki/api.php?action=opensearch&search=glory"
        )
        fresh.forEach { url ->
            val control = WikiHttpCachePolicy.cacheControlFor(get(url))
            assertNotNull(url, control)
            assertTrue(url, control!!.noStore)
        }
    }

    @Test
    fun cloudFunctionsAndOtherHostsAreNotStored() {
        val control = WikiHttpCachePolicy.cacheControlFor(
            get("https://us-central1-osrs-459713.cloudfunctions.net/search")
        )
        assertNotNull(control)
        assertTrue(control!!.noStore)
    }

    @Test
    fun explicitOfflineSaveForcesNetwork() {
        val control = WikiHttpCachePolicy.cacheControlFor(
            Request.Builder()
                .url(PARSE_URL)
                .header(WikiHttpCachePolicy.HEADER_OFFLINE_SAVE, "readinglist")
                .build()
        )
        assertNotNull(control)
        assertTrue(control!!.noCache)
        assertFalse(control.noStore)
    }

    @Test
    fun wwwWikiHostIsTreatedAsWiki() {
        assertTrue(WikiHttpCachePolicy.isWikiHost("www.oldschool.runescape.wiki"))
        assertTrue(WikiHttpCachePolicy.isWikiHost("oldschool.runescape.wiki"))
        assertFalse(WikiHttpCachePolicy.isWikiHost("maps.runescape.wiki"))
    }

    private fun get(url: String): Request = Request.Builder().url(url).build()

    companion object {
        private const val PARSE_URL =
            "https://oldschool.runescape.wiki/api.php?action=parse&format=json&page=Varrock&maxage=300&smaxage=300"
    }
}
