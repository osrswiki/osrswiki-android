package com.omiyawaki.osrswiki.page

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Warms default calculator parse responses while a page is being saved so the
 * saved copy can submit its default inputs offline.
 */
object osrsCalculatorSaveWarmer {
    private const val TAG = "osrsCalcWarm"
    private val paramPattern = Pattern.compile(
        """(?i)\bparam\s*=\s*([^|\n]+)\|([^|\n]*)\|([^|\n]*)\|([^|\n]*)"""
    )
    private val templatePattern = Pattern.compile(
        """(?i)\btemplate\s*=\s*(.+?)(?=\s+(?:form|result|param|name|autosubmit|module|modulefunc)\b|$)"""
    )
    private val modulePattern = Pattern.compile(
        """(?i)\bmodule\s*=\s*(.+?)(?=\s+(?:form|result|param|name|autosubmit|modulefunc|template)\b|$)"""
    )
    private val moduleFuncPattern = Pattern.compile(
        """(?i)\bmodulefunc\s*=\s*(\S+)"""
    )
    private val prePattern = Pattern.compile(
        """(?is)<pre[^>]*class="[^"]*jcConfig[^"]*"[^>]*>(.*?)</pre>"""
    )
    private val loosePattern = Pattern.compile(
        """(?is)(?:^|\n)\s*(?:template|module)\s*=.+?(?=\n\s*(?:\{\||----|<pre|$))"""
    )

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
        val config = firstConfig(html) ?: return null
        val moduleMatcher = modulePattern.matcher(config)
        val templateMatcher = templatePattern.matcher(config)
        val builder = StringBuilder()
        if (moduleMatcher.find()) {
            val module = moduleMatcher.group(1)?.trim().orEmpty()
            if (module.isEmpty()) {
                return null
            }
            var func = "main"
            val funcMatcher = moduleFuncPattern.matcher(config)
            if (funcMatcher.find()) {
                func = funcMatcher.group(1)?.trim().orEmpty().ifEmpty { "main" }
            }
            builder.append("{{#invoke:").append(module).append('|').append(func)
        } else if (templateMatcher.find()) {
            val template = templateMatcher.group(1)?.trim().orEmpty()
            if (template.isEmpty()) {
                return null
            }
            builder.append("{{").append(template)
        } else {
            return null
        }
        val paramMatcher = paramPattern.matcher(config)
        while (paramMatcher.find()) {
            val name = paramMatcher.group(1)?.trim().orEmpty()
            val initial = paramMatcher.group(3)?.trim().orEmpty()
            val type = paramMatcher.group(4)?.trim()?.lowercase().orEmpty()
            if (name.isEmpty() || type == "hidden" || type == "hs" || type == "rsn") {
                continue
            }
            builder.append('|').append(name).append('=').append(initial)
        }
        builder.append("}}")
        return builder.toString()
    }

    private fun firstConfig(html: String): String? {
        val pre = prePattern.matcher(html)
        if (pre.find()) {
            return pre.group(1)
        }
        val loose = loosePattern.matcher(html)
        if (loose.find()) {
            return loose.group(0)
        }
        return null
    }
}
