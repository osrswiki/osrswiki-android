package com.omiyawaki.osrswiki.bridge

import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.util.TypedValue
import androidx.core.content.ContextCompat
import java.io.IOException

/**
 * Creates JavaScript snippets and CSS content to be injected into the WebView.
 */
object JavaScriptActionHandler {
    private const val TAG = "JActionHandler"

    // Define paths for all collapsible content assets.
    private const val CSS_TABLES_PATH = "web/collapsible_tables.css"
    private const val UNIFIED_JS_PATH = "web/collapsible_content.js"

    /**
     * Gets all necessary CSS for collapsible content.
     * Currently, only tables require special, theme-aware CSS.
     */
    fun getCollapsibleContentCss(context: Context): String {
        val containerBgColor = getThemeColor(context, com.google.android.material.R.attr.colorSurfaceVariant, "#f0f0f0")
        val onSurfaceVariantColor = getThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, "#444746")

        val cssVariables = """
            :root {
                --container-bg-color: $containerBgColor;
                --onsurfacevariant-color: $onSurfaceVariantColor;
            }
        """.trimIndent()

        // The styles for the container are now used by both tables and sections.
        val baseCss = getFileFromAssets(context, CSS_TABLES_PATH)
        return "<style>$cssVariables\n$baseCss</style>"
    }

    /**
     * Gets the unified JavaScript for all collapsible content.
     */
    fun getCollapsibleContentJs(context: Context): String {
        return getFileFromAssets(context, UNIFIED_JS_PATH)
    }

    private fun getFileFromAssets(context: Context, path: String): String {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
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

        val color: Int
        try {
            color = if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                typedValue.data
            } else {
                ContextCompat.getColor(context, typedValue.resourceId)
            }
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "Color resource not found for attribute ID #$attrId", e)
            return fallback
        }

        return String.format("#%06X", (0xFFFFFF and color))
    }
}
