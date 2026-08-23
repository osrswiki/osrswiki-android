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
     * Extracts a human-readable title from a MediaWiki displayTitle.
     * Handles HTML-formatted titles (mw-page-title-* spans) and plain text.
     *
     * Preserves non-main namespaces (e.g. Calculator:) so Calculator subpages
     * like Calculator:Agility/Agility arena tickets remain loadable when this
     * string is reused for navigation/API. Still strips the Update: feed prefix.
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

        if (displayTitle.contains("mw-page-title-main")) {
            val mainRegex = Regex("""<span[^>]*class="mw-page-title-main"[^>]*>([^<]+)</span>""")
            val nsRegex = Regex("""<span[^>]*class="mw-page-title-namespace"[^>]*>([^<]+)</span>""")
            val mainMatch = mainRegex.find(displayTitle)
            if (mainMatch != null) {
                val main = cleanTitle(mainMatch.groupValues[1])
                val ns = nsRegex.find(displayTitle)?.let { cleanTitle(it.groupValues[1]) }
                return if (!ns.isNullOrBlank()) "$ns:$main" else main
            }
        }

        return cleanTitle(displayTitle)
    }

    // Add other string utilities from Wikipedia's StringUtil as needed.
}
