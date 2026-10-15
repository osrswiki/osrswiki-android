package com.omiyawaki.osrswiki.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsGeChartPriceCacheTest {

    @Test
    fun timeseriesUrlMatchesChartJsAndExtractsItemIds() {
        assertEquals(
            "https://prices.runescape.wiki/api/v2/osrs/timeseries?lookback=1y&id=4151",
            osrsGeChartPriceCache.timeseriesUrl("4151")
        )
        val html = """
            <div class="GEChartBox">
              <div class="GEdataprices" data-itemid="4151"></div>
            </div>
        """.trimIndent()
        assertEquals(listOf("4151"), osrsGeChartPriceCache.itemIdsIn(html))
    }

    @Test
    fun memoryPrefetchScriptInlinesMemoryHitsWithoutDisk() {
        val url = osrsGeChartPriceCache.timeseriesUrl("4151")
        osrsGeChartPriceCache.putMemory(url, """{"data":[{"timestamp":1,"avgHighPrice":2}]}""")
        val script = osrsGeChartPriceCache.memoryPrefetchScript(listOf("4151"))
        assertTrue(script.contains("__osrsGeChartPrefetch"))
        assertTrue(script.contains("4151"))
        assertTrue(script.contains("avgHighPrice"))
        assertEquals(
            """{"data":[{"timestamp":1,"avgHighPrice":2}]}""",
            osrsGeChartPriceCache.readMemory(url)
        )
        assertFalse(script.contains("Api-User-Agent"))
    }
}
