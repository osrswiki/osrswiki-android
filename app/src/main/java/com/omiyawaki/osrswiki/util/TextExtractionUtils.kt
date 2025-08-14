package com.omiyawaki.osrswiki.util // Or your preferred utility package

import org.jsoup.Jsoup
// Removed File, IOException, Charset imports if not used by other functions in this file.
// Add android.util.Log if you want to use it here directly for logging.

/**
 * Parses an HTML string and extracts its plain text content.
 *
 * @param htmlString The HTML content as a String.
 * @return The extracted plain text, or null if an error occurs or input is null.
 */
fun extractTextFromHtmlString(htmlString: String?): String? {
    if (htmlString == null) {
        println("Error: HTML string input is null.") // Consider using Android Log
        return null
    }
    return try {
        // Parse the HTML string
        val document = Jsoup.parse(htmlString)

        // Extract plain text. Jsoup's document.text() method does a good job of
        // getting the combined, human-readable text of the document,
        // stripping HTML, script, and style tags.
        val plainText = document.text()
        plainText
    } catch (e: Exception) {
        // Log error: Other exceptions during parsing
        println("Error parsing HTML string: ${e.message}") // Consider Android Log
        e.printStackTrace()
        null
    }
}