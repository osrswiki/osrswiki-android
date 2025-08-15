package com.omiyawaki.osrswiki.util

object WikiUrlUtil {
    private const val WIKI_BASE = "https://oldschool.runescape.wiki"
    
    /**
     * Constructs a canonical wiki URL from a page title.
     * Example: "Falador" -> "https://oldschool.runescape.wiki/w/Falador"
     */
    fun fromPageTitle(pageTitle: String): String {
        return "$WIKI_BASE/w/${pageTitle.replace(" ", "_")}"
    }
    
    /**
     * Constructs a page ID-based URL.
     * Example: 11657 -> "https://oldschool.runescape.wiki/?curid=11657"
     * Note: This format should be avoided for history entries to prevent duplicates.
     */
    fun fromPageId(pageId: Int): String {
        return "$WIKI_BASE/?curid=$pageId"
    }
    
    /**
     * Normalizes any wiki URL to the canonical format.
     * Converts ?curid=XXX URLs to /w/PageTitle format if possible.
     * If the title is not available, returns the original URL.
     */
    fun normalize(url: String, pageTitle: String?): String {
        return if (url.contains("?curid=") && !pageTitle.isNullOrBlank()) {
            fromPageTitle(pageTitle)
        } else {
            url
        }
    }
}