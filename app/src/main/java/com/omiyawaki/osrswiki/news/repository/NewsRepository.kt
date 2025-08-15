package com.omiyawaki.osrswiki.news.repository

import com.omiyawaki.osrswiki.news.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException

/**
 * Repository for fetching and parsing news feed content from the OSRS Wiki.
 * This class handles network operations and HTML parsing, providing structured
 * data to the ViewModel. It is implemented as a singleton object.
 */
object NewsRepository {
    private const val BASE_URL = "https://oldschool.runescape.wiki"
    private const val WIKI_URL = "$BASE_URL/"

    /**
     * Fetches the wiki main page and parses it into a structured WikiFeed object.
     * This is a suspending function and should be called from a coroutine.
     *
     * @return A Result object containing the WikiFeed on success, or an exception on failure.
     */
    suspend fun getWikiFeed(): Result<WikiFeed> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(WIKI_URL).get()
            val feed = WikiFeed(
                recentUpdates = parseRecentUpdates(doc),
                announcements = parseAnnouncements(doc),
                onThisDay = parseOnThisDay(doc),
                popularPages = parsePopularPages(doc)
            )
            Result.success(feed)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private fun parseRecentUpdates(doc: Element): List<UpdateItem> {
        val updatesContainer = doc.selectFirst("div.mainpage-recent-updates") ?: return emptyList()
        return updatesContainer.select("div.tile-halves").mapNotNull { tile ->
            // Correctly select the link from the bottom part of the card for text content.
            val textLinkElement = tile.selectFirst("div.tile-bottom a") ?: return@mapNotNull null
            val imageElement = tile.selectFirst("div.tile-top img")

            // The title is in an <h2> tag within the text link.
            val title = textLinkElement.selectFirst("h2")?.text() ?: "No title"

            // The snippet is the LAST <p> tag within the text link.
            val snippet = textLinkElement.select("p").last()?.text() ?: ""

            UpdateItem(
                title = title,
                snippet = snippet,
                imageUrl = imageElement?.attr("src")?.let { "$BASE_URL$it" } ?: "",
                articleUrl = textLinkElement.attr("href").let { "$BASE_URL$it" }
            )
        }
    }

    private fun parseAnnouncements(doc: Element): List<AnnouncementItem> {
        val announcementsContainer = doc.selectFirst("div.mainpage-wikinews dl") ?: return emptyList()
        val dates = announcementsContainer.select("dt")
        val contents = announcementsContainer.select("dd")
        return dates.zip(contents).map { (date, content) ->
            AnnouncementItem(date = date.text(), content = content.html())
        }
    }

    private fun parseOnThisDay(doc: Element): OnThisDayItem? {
        val onThisDayContainer = doc.selectFirst("div.mainpage-onthisday") ?: return null
        val title = onThisDayContainer.selectFirst("h2")?.text() ?: "On this day..."
        val events = onThisDayContainer.select("ul li").map { it.html() }
        return OnThisDayItem(title = title, events = events)
    }

    private fun parsePopularPages(doc: Element): List<PopularPageItem> {
        val popularContainer = doc.selectFirst("div.mainpage-popular") ?: return emptyList()
        return popularContainer.select("li a").map { link ->
            PopularPageItem(
                title = link.text(),
                pageUrl = link.attr("href").let { "$BASE_URL$it" }
            )
        }
    }
}
