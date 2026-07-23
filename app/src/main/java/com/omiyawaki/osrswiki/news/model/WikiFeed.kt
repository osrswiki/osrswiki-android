package com.omiyawaki.osrswiki.news.model

/**
 * A container class holding all the parsed content from the main wiki page,
 * structured for use in the news feed.
 */
data class WikiFeed(
    val recentUpdates: List<UpdateItem>,
    val announcements: List<AnnouncementItem>,
    val onThisDay: OnThisDayItem?,
    val popularPages: List<PopularPageItem>
)

/**
 * Represents a single news article from the "Recent Updates" section.
 */
data class UpdateItem(
    val title: String,
    val snippet: String,
    val imageUrl: String,
    val articleUrl: String
)

/**
 * Represents a single item from the "Announcements" section.
 */
data class AnnouncementItem(
    val date: String,
    val content: String
)

/**
 * Represents the "On this day..." section, containing a title and a list of events.
 */
data class OnThisDayItem(
    val title: String,
    val events: List<String>
)

/**
 * Represents a single link from the "Popular pages" section.
 */
data class PopularPageItem(
    val title: String,
    val pageUrl: String
)
