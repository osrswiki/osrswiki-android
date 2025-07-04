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
    private const val CSS_PATH = "www/css/collapsible_tables.css"
    private const val JS_PATH = "www/js/collapsible_tables.js"

    fun getCollapsibleTablesCss(context: Context): String {
        val containerBgColor = getThemeColor(context, com.google.android.material.R.attr.colorSurfaceVariant, "#f8f9fa")
        val textColor = getThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, "#202122")
        val borderColor = getThemeColor(context, com.google.android.material.R.attr.colorOutline, "#c8ccd1")

        val cssVariables = """
            :root {
                --container-bg-color: $containerBgColor;
                --primary-text-color: $textColor;
                --border-color: $borderColor;
            }
        """.trimIndent()

        val baseCss = getFileFromAssets(context, CSS_PATH)
        return "<style>$cssVariables\n$baseCss</style>"
    }

    fun getCollapsibleTablesJs(context: Context): String {
        return getFileFromAssets(context, JS_PATH)
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
                // The attribute is a direct color value (e.g., #FFFFFF).
                typedValue.data
            } else {
                // The attribute is a reference to a resource (e.g., @color/my_color).
                ContextCompat.getColor(context, typedValue.resourceId)
            }
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "Color resource not found for attribute ID #$attrId", e)
            return fallback
        }

        return String.format("#%06X", (0xFFFFFF and color))
    }
}
