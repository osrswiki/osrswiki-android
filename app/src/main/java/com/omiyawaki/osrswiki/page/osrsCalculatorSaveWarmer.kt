package com.omiyawaki.osrswiki.page

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Warms default calculator parse responses while a page is being saved so the
 * saved copy can submit its default inputs offline.
 */
object osrsCalculatorSaveWarmer {
    private const val TAG = "osrsCalcWarm"

    fun warmDefaultParse(context: Context, html: String, pageTitle: String? = null) {
        if (!html.contains("jcConfig") && !html.contains("template=") && !html.contains("module=")) {
            return
        }
        val wikitext = defaultTemplateCall(html) ?: return
        try {
            val wikiTitle = osrsWikiWebViewUrl.mediaWikiPageConfig(
                pageTitle ?: "Calculator",
                pageTitle ?: "Calculator"
            ).pageName
            val data = JSONObject()
                .put("action", "parse")
                .put("text", wikitext)
                .put("prop", "text|limitreportdata")
                .put("title", wikiTitle)
                .put("disablelimitreport", "true")
                .put("contentmodel", "wikitext")
                .put("format", "json")
            osrsWikiWebViewProxy.request(
                context,
                "GET",
                "/api.php",
                data
            )
        } catch (error: Exception) {
            Log.w(TAG, "Failed to warm default calculator parse: ${error.message}")
        }
    }

    fun defaultTemplateCall(html: String): String? {
        return osrsNativeCalcDefinition.invokeWikitext(osrsNativeCalcDefinition.parse(html))
    }
}
