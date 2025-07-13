package com.omiyawaki.osrswiki.bridge

import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.util.TypedValue
import androidx.core.content.ContextCompat
import java.io.IOException

object JavaScriptActionHandler {
    private const val TAG = "JActionHandler"

    // Third-party libraries
    private const val LEAFLET_JS_PATH = "web/lib/leaflet.js"
    private const val LEAFLET_CSS_PATH = "web/lib/leaflet.css"
    private const val MAP_INITIALIZER_JS_PATH = "web/lib/map_initializer.js"

    // App-specific scripts
    private const val CSS_TABLES_PATH = "web/collapsible_tables.css"
    private const val UNIFIED_JS_PATH = "web/collapsible_content.js"
    private const val MAP_NATIVE_FINDER_JS_PATH = "web/map_native_finder.js"

    // --- Library Loaders ---
    fun getLeafletJs(context: Context): String = getFileFromAssets(context, LEAFLET_JS_PATH)
    fun getMapInitializerJs(context: Context): String = getFileFromAssets(context, MAP_INITIALIZER_JS_PATH)
    fun getMapNativeFinderJs(context: Context): String = getFileFromAssets(context, MAP_NATIVE_FINDER_JS_PATH)

    fun getLeafletCss(context: Context): String = "<style>${getFileFromAssets(context, LEAFLET_CSS_PATH)}</style>"

    // --- App Content Loaders ---
    fun getCollapsibleContentCss(context: Context): String {
        // Fetch all the required theme colors and create CSS variables for them.
        val colorSurface = getThemeColor(context, com.google.android.material.R.attr.colorSurface, "#FFFFFF")
        val colorOnSurface = getThemeColor(context, com.google.android.material.R.attr.colorOnSurface, "#000000")
        val colorSurfaceVariant = getThemeColor(context, com.google.android.material.R.attr.colorSurfaceVariant, "#f0f0f0")
        val colorOnSurfaceVariant = getThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, "#444746")
        val colorPrimaryContainer = getThemeColor(context, com.google.android.material.R.attr.colorPrimaryContainer, "#b8a282")
        val colorOnPrimaryContainer = getThemeColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer, "#000000")


        val cssVariables = """
            :root {
                --colorsurface: $colorSurface;
                --coloronsurface: $colorOnSurface;
                --colorsurfacevariant: $colorSurfaceVariant;
                --coloronsurfacevariant: $colorOnSurfaceVariant;
                --colorprimarycontainer: $colorPrimaryContainer;
                --coloronprimarycontainer: $colorOnPrimaryContainer;
            }
        """.trimIndent()

        val baseCss = getFileFromAssets(context, CSS_TABLES_PATH)
        return "<style>$cssVariables\n$baseCss</style>"
    }

    fun getCollapsibleContentJs(context: Context): String = getFileFromAssets(context, UNIFIED_JS_PATH)

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
