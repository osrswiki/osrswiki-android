package com.omiyawaki.osrswiki.util

import android.os.Build
import android.text.Html
import android.text.Spanned

object StringUtil {
    /**
     * Get HTML-decoded string.
     *
     * @param input HTML-encoded string.
     * @return HTML-decoded string.
     */
    @Suppress("DEPRECATION")
    fun fromHtml(input: String?): Spanned {
        if (input.isNullOrEmpty()) {
            return Html.fromHtml("")
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(input)
        }
    }

    /**
     * Extracts the main title from a MediaWiki displayTitle, removing namespace prefixes.
     * Handles both HTML-formatted titles (with mw-page-title-main spans) and plain text titles.
     *
     * @param displayTitle The display title which may contain HTML or plain text
     * @return The cleaned main title without namespace prefix
     */
    fun extractMainTitle(displayTitle: String): String {
        // Check if it contains MediaWiki title HTML structure
        if (displayTitle.contains("mw-page-title-main")) {
            // Extract content between <span class="mw-page-title-main"> and </span>
            val regex = Regex("""<span[^>]*class="mw-page-title-main"[^>]*>([^<]+)</span>""")
            val match = regex.find(displayTitle)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        // Fallback to regular HTML cleaning and Update: prefix removal
        val cleanTitle = fromHtml(displayTitle).toString()
        return when {
            cleanTitle.startsWith("Update: ") -> cleanTitle.removePrefix("Update: ")
            cleanTitle.startsWith("Update:") -> cleanTitle.removePrefix("Update:")
            else -> cleanTitle
        }
    }

    // Add other string utilities from Wikipedia's StringUtil as needed.
}
