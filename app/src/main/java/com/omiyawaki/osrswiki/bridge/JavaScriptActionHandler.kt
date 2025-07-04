package com.omiyawaki.osrswiki.bridge

import android.content.Context
import android.util.Log
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.omiyawaki.osrswiki.R
import java.io.IOException

/**
 * Creates JavaScript snippets and CSS content to be injected into the WebView.
 */
object JavaScriptActionHandler {
    private const val TAG = "JActionHandler"
    private const val CSS_PATH = "www/css/collapsible_tables.css"
    private const val JS_PATH = "www/js/collapsible_tables.js"

    /**
     * Reads the base collapsible_tables.css file and prepends a dynamic <style> block
     * containing the theme-aware CSS variables.
     * @param context The context used to resolve theme attributes.
     * @return A string containing the full CSS to be injected.
     */
    fun getCollapsibleTablesCss(context: Context): String {
        // 1. Fetch the actual color values from your app's theme.
        val containerBgColor = getThemeColor(context, R.attr.paper_color, "#ffffff")
        val containerBorderColor = getThemeColor(context, R.attr.border_color, "#a2a9b1")
        val headerBgColor = getThemeColor(context, R.attr.section_header_color, "#eaecf0")
        val primaryTextColor = getThemeColor(context, R.attr.primary_text_color, "#202122")
        val iconColor = getThemeColor(context, R.attr.primary_text_color, "#202122")

        // 2. Define the SVG icons using the theme color.
        val expandIconSvg = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path fill="$iconColor" d="M7 10l5 5 5-5z"/></svg>"""
        val collapseIconSvg = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path fill="$iconColor" d="M7 14l5-5 5 5z"/></svg>"""

        // 3. Construct the CSS block that defines the variables.
        val cssVariables = """
            :root {
                --container-bg-color: $containerBgColor;
                --container-border-color: $containerBorderColor;
                --header-bg-color: $headerBgColor;
                --primary-text-color: $primaryTextColor;
                --icon-expand: ${getCssUrlForSvg(expandIconSvg)};
                --icon-collapse: ${getCssUrlForSvg(collapseIconSvg)};
            }
        """.trimIndent()

        val baseCss = getFileFromAssets(context, CSS_PATH)

        // 4. Combine the dynamic variables and the base CSS into a single style block.
        return "<style>$cssVariables\n$baseCss</style>"
    }

    /**
     * Reads the collapsible_tables.js file from assets.
     * @param context The context used to access assets.
     * @return A string containing the JavaScript to be injected.
     */
    fun getCollapsibleTablesJs(context: Context): String {
        return getFileFromAssets(context, JS_PATH)
    }

    private fun getFileFromAssets(context: Context, path: String): String {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            // Log the error and return an empty string if the file cannot be read.
            Log.e(TAG, "Error reading asset: $path", e)
            ""
        }
    }

    private fun getThemeColor(context: Context, attrId: Int, fallback: String): String {
        val typedValue = TypedValue()
        if (!context.theme.resolveAttribute(attrId, typedValue, true)) {
            Log.e(TAG, "Failed to resolve theme attribute ID #$attrId")
            return fallback
        }

        val color = if (typedValue.resourceId != 0) {
            // The attribute pointed to a resource (e.g., @color/some_color)
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            // The attribute pointed to a direct value (e.g., #FFFFFF)
            typedValue.data
        }

        // Format as a hex string for CSS, ignoring the alpha channel.
        return String.format("#%06X", (0xFFFFFF and color))
    }

    private fun getCssUrlForSvg(svg: String): String {
        // URL-encode the SVG to be safely used in a data URI.
        return "url(\"data:image/svg+xml,${java.net.URLEncoder.encode(svg, "UTF-8")}\")"
    }
}
