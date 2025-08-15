package com.omiyawaki.osrswiki.ui.map

import android.content.Context
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat
import com.google.android.material.R as MaterialR
import org.json.JSONObject

/**
 * A JavaScript interface for the WebView, enabling communication from JS to native Android code.
 *
 * This class provides methods that can be called from the JavaScript context of the WebView
 * to fetch data or trigger actions in the native part of the app.
 *
 * @property context The application or activity context, used to resolve theme attributes.
 */
class WebViewInterface(private val context: Context) {
    /**
     * Retrieves key theme colors from the app's theme and returns them as a JSON string.
     * This method is exposed to JavaScript and allows the WebView content to style itself
     * according to the native app's theme.
     *
     * @return A JSON string containing key-value pairs for theme colors.
     * Example: {"colorSurfaceVariant":"#45464F","colorOnSurfaceVariant":"#C5C6D0"}
     */
    @JavascriptInterface
    fun getThemeColors(): String {
        val surfaceVariantColor = resolveAttrColor(MaterialR.attr.colorSurfaceVariant)
        val onSurfaceVariantColor = resolveAttrColor(MaterialR.attr.colorOnSurfaceVariant)

        val colorsJson = JSONObject().apply {
            put("surfaceVariant", surfaceVariantColor)
            put("onSurfaceVariant", onSurfaceVariantColor)
        }
        return colorsJson.toString()
    }

    /**
     * Resolves a color attribute from the current theme.
     *
     * @param attrResId The resource ID of the color attribute (e.g., R.attr.colorPrimary).
     * @return The hex string representation of the color (e.g., "#RRGGBB").
     */
    private fun resolveAttrColor(attrResId: Int): String {
        val typedArray = context.obtainStyledAttributes(intArrayOf(attrResId))
        val color = typedArray.getColor(0, 0)
        typedArray.recycle()
        return String.format("#%06X", 0xFFFFFF and color)
    }
}
