package com.omiyawaki.osrswiki.random

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLDecoder

/**
 * Repository for fetching random pages from the OSRS Wiki.
 */
object RandomPageRepository {
    private const val RANDOM_URL = "https://oldschool.runescape.wiki/w/Special:RandomRootpage/main"

    /**
     * Fetches a random page from the OSRS Wiki.
     * @return A Result containing the page title on success, or an exception on failure.
     */
    suspend fun getRandomPage(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Make a request to the random page endpoint
            // This will redirect to a random page, so we get the final URL
            val connection = Jsoup.connect(RANDOM_URL)
                .followRedirects(true)
                .execute()
            
            val finalUrl = connection.url().toString()
            
            // Extract the page title from the final URL
            val pageTitle = extractPageTitleFromUrl(finalUrl)
            
            if (pageTitle.isBlank()) {
                Result.failure(Exception("Failed to extract page title from random page URL: $finalUrl"))
            } else {
                Result.success(pageTitle)
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractPageTitleFromUrl(url: String): String {
        return try {
            // Extract the path segment after /w/
            val wikiPath = url.substringAfter("/w/")
            
            // Skip Special: pages and other non-article pages
            if (wikiPath.startsWith("Special:") || 
                wikiPath.startsWith("Category:") || 
                wikiPath.startsWith("File:") ||
                wikiPath.startsWith("Template:")) {
                return ""
            }
            
            // Replace underscores with spaces and decode URL encoding
            val withSpaces = wikiPath.replace('_', ' ')
            URLDecoder.decode(withSpaces, "UTF-8")
        } catch (e: Exception) {
            ""
        }
    }
}