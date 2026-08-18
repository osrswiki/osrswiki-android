package com.omiyawaki.osrswiki.util

import android.os.Build
import android.text.Html
import android.text.Spanned
import org.jsoup.Jsoup

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

    /** Decodes nested MediaWiki/feed entities without leaking raw markup into list rows. */
    fun decodeHtmlToFixedPoint(input: String?): String {
        var decoded = input.orEmpty()
        repeat(3) {
            // This normalizer is also used by pure ranking/highlighting code. Keep it independent
            // of android.text.Html so it behaves identically in local JVM tests and on-device.
            val next = Jsoup.parseBodyFragment(decoded).text()
            if (next == decoded) return decoded
            decoded = next
        }
        return decoded
    }

    /**
     * Extracts the main title from a MediaWiki displayTitle, removing namespace prefixes.
     * Handles both HTML-formatted titles (with mw-page-title-main spans) and plain text titles.
     *
     * @param displayTitle The display title which may contain HTML or plain text
     * @return The cleaned main title without namespace prefix
     */
    fun extractMainTitle(displayTitle: String): String {
        fun cleanTitle(value: String): String {
            // MediaWiki feed titles can arrive entity-encoded twice (for example
            // `&amp;amp;`). Decode to a fixed point so history, saved rows, search,
            // and update cards all expose the same human-readable title.
            val decoded = decodeHtmlToFixedPoint(value)
            return when {
                decoded.startsWith("Update: ") -> decoded.removePrefix("Update: ")
                decoded.startsWith("Update:") -> decoded.removePrefix("Update:")
                else -> decoded
            }
        }

        // Check if it contains MediaWiki title HTML structure
        if (displayTitle.contains("mw-page-title-main")) {
            // Extract content between <span class="mw-page-title-main"> and </span>
            val regex = Regex("""<span[^>]*class="mw-page-title-main"[^>]*>([^<]+)</span>""")
            val match = regex.find(displayTitle)
            if (match != null) {
                return cleanTitle(match.groupValues[1])
            }
        }
        
        // Fallback to regular HTML cleaning and Update: prefix removal
        return cleanTitle(displayTitle)
    }

    // Add other string utilities from Wikipedia's StringUtil as needed.
}
