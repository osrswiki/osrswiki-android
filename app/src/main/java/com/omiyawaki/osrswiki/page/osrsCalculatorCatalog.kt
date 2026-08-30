package com.omiyawaki.osrswiki.page

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

data class osrsCalculatorCatalogEntry(
    val title: String,
    val pageid: Long?,
    val url: String
)

data class osrsCalculatorCatalogSnapshot(
    val fetchedAt: String?,
    val calculators: List<osrsCalculatorCatalogEntry>,
    val excludedCount: Int
)

object osrsCalculatorCatalog {
    const val ASSET_PATH = "manifests/osrs-wiki-calculators.json"
    const val LIVE_API =
        "https://oldschool.runescape.wiki/api.php?action=query&list=allpages&apnamespace=116&aplimit=500&apfilterredir=nonredirects&format=json"

    fun loadSnapshot(json: String): osrsCalculatorCatalogSnapshot {
        val root = JSONObject(json)
        val calculators = mutableListOf<osrsCalculatorCatalogEntry>()
        val array: JSONArray = root.optJSONArray("calculators") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val title = item.optString("title")
            if (!osrsWikiWebViewUrl.isUserFacingCalculator(title)) {
                continue
            }
            calculators.add(
                osrsCalculatorCatalogEntry(
                    title = title,
                    pageid = if (item.has("pageid")) item.optLong("pageid") else null,
                    url = item.optString("url")
                )
            )
        }
        return osrsCalculatorCatalogSnapshot(
            fetchedAt = root.optString("fetched_at", null),
            calculators = calculators,
            excludedCount = root.optInt("excluded_count", 0)
        )
    }

    fun loadBundled(context: Context): osrsCalculatorCatalogSnapshot {
        val json = context.assets.open(ASSET_PATH).use { stream ->
            stream.readBytes().toString(StandardCharsets.UTF_8)
        }
        return loadSnapshot(json)
    }

    fun mergeLivePages(snapshot: osrsCalculatorCatalogSnapshot, livePages: JSONArray): List<osrsCalculatorCatalogEntry> {
        val byTitle = linkedMapOf<String, osrsCalculatorCatalogEntry>()
        snapshot.calculators.forEach { byTitle[it.title] = it }
        for (index in 0 until livePages.length()) {
            val page = livePages.optJSONObject(index) ?: continue
            val title = page.optString("title")
            if (!osrsWikiWebViewUrl.isUserFacingCalculator(title)) {
                continue
            }
            byTitle[title] = osrsCalculatorCatalogEntry(
                title = title,
                pageid = if (page.has("pageid")) page.optLong("pageid") else null,
                url = "https://oldschool.runescape.wiki/w/" + title.replace(" ", "_")
            )
        }
        return byTitle.values.sortedBy { it.title.lowercase() }
    }
}
