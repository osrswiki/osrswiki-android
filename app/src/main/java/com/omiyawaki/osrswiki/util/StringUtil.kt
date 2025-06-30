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

    // Add other string utilities from Wikipedia's StringUtil as needed.
}
