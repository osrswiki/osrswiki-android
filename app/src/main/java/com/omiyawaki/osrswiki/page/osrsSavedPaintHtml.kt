package com.omiyawaki.osrswiki.page

/**
 * Cheap transforms for a persisted ready-to-paint article document.
 * These must not rebuild the article through [PageHtmlBuilder].
 */
object osrsSavedPaintHtml {
    private val pageHeaderRegex = Regex(
        """<h1\s+class=["']page-header["'][^>]*>.*?</h1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val androidStylesheetLinkRegex = Regex(
        """<link\s+rel=["']stylesheet["']\s+href=["']https://appassets\.androidplatform\.net/assets/([^"']+)["']\s*/?>""",
        RegexOption.IGNORE_CASE
    )
    private val htmlClassRegex = Regex("""<html\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val bodyClassRegex = Regex("""<body\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val classAttrRegex = Regex("""\bclass=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val liveChromeStyleRegex = Regex(
        """<style id=["']osrs-article-live-chrome["']>[\s\S]*?</style>""",
        RegexOption.IGNORE_CASE
    )
    private val readerScaleRegex = Regex(
        """--osrs-article-user-text-scale:\s*[0-9.]+""",
        RegexOption.IGNORE_CASE
    )

    fun isFullDocument(html: String): Boolean {
        val start = html.trimStart()
        return start.startsWith("<!DOCTYPE", ignoreCase = true) ||
            start.startsWith("<html", ignoreCase = true)
    }

    fun extractBodyForToc(html: String): String {
        if (!isFullDocument(html)) {
            return html
        }
        val bodyOpen = html.indexOf("<body", ignoreCase = true)
        val bodyClose = html.lastIndexOf("</body>", ignoreCase = true)
        if (bodyOpen < 0 || bodyClose < 0 || bodyClose <= bodyOpen) {
            return html
        }
        val contentStart = html.indexOf('>', bodyOpen).takeIf { it >= 0 }?.plus(1) ?: return html
        return pageHeaderRegex.replace(html.substring(contentStart, bodyClose), "")
    }

    fun applyingLiveTheme(html: String, isDark: Boolean): String {
        return toggleThemeClass(toggleThemeClass(html, htmlClassRegex, isDark), bodyClassRegex, isDark)
    }

    fun applyingWrapClass(html: String, wrapEnabled: Boolean): String {
        return toggleNamedClass(
            toggleNamedClass(html, htmlClassRegex, "osrs-table-cells-wrap", wrapEnabled),
            bodyClassRegex,
            "osrs-table-cells-wrap",
            wrapEnabled
        )
    }

    fun applyingReaderScale(html: String, scaleCssValue: String): String {
        return readerScaleRegex.replace(html, "--osrs-article-user-text-scale: $scaleCssValue")
    }

    fun withLiveChrome(
        html: String,
        chromeClearancePx: Int = 0,
        bottomChromePx: Int = 0
    ): String {
        val style = """
            <style id="osrs-article-live-chrome">
            html:root {
                --osrs-article-chrome-clearance: ${chromeClearancePx}px;
                --osrs-article-bottom-chrome: ${bottomChromePx}px;
            }
            html {
                padding-top: calc(env(safe-area-inset-top, 0px) + ${chromeClearancePx}px) !important;
                padding-bottom: calc(env(safe-area-inset-bottom, 0px) + ${bottomChromePx}px) !important;
            }
            </style>
        """.trimIndent()
        return if (liveChromeStyleRegex.containsMatchIn(html)) {
            liveChromeStyleRegex.replace(html, style)
        } else {
            html.replaceFirst(Regex("""</head>""", RegexOption.IGNORE_CASE), "$style</head>")
        }
    }

    fun inlineLinkedFirstPaintCss(html: String, loadCss: (assetPath: String) -> String?): String {
        return androidStylesheetLinkRegex.replace(html) { match ->
            val assetPath = match.groupValues[1]
            val css = loadCss(assetPath)
            if (css.isNullOrEmpty()) {
                match.value
            } else {
                """<style data-osrs-inline-css="$assetPath">$css</style>"""
            }
        }
    }

    fun applyingLivePreferences(
        html: String,
        isDark: Boolean,
        wrapEnabled: Boolean,
        scaleCssValue: String,
        bottomChromePx: Int = 0
    ): String {
        return withLiveChrome(
            applyingReaderScale(
                applyingWrapClass(applyingLiveTheme(html, isDark), wrapEnabled),
                scaleCssValue
            ),
            bottomChromePx = bottomChromePx
        )
    }

    private fun toggleThemeClass(html: String, tagRegex: Regex, isDark: Boolean): String {
        return toggleNamedClass(html, tagRegex, "theme-osrs-dark", isDark)
    }

    private fun toggleNamedClass(
        html: String,
        tagRegex: Regex,
        className: String,
        enabled: Boolean
    ): String {
        return tagRegex.replace(html) { match ->
            val attrs = match.groupValues[1]
            val classMatch = classAttrRegex.find(attrs)
            val classes = classMatch?.groupValues?.get(1)
                ?.split(Regex("""\s+"""))
                ?.filter { it.isNotBlank() }
                ?.toMutableList()
                ?: mutableListOf()
            if (enabled) {
                if (className !in classes) classes += className
            } else {
                classes.removeAll { it == className }
            }
            val withoutClass = classAttrRegex.replace(attrs, "").trim()
            val classAttr = if (classes.isEmpty()) "" else """ class="${classes.joinToString(" ")}""""
            val spacer = if (withoutClass.isEmpty()) "" else " "
            "<${tagName(match.value)}$spacer$withoutClass$classAttr>"
        }
    }

    private fun tagName(openTag: String): String {
        return openTag.trim().removePrefix("<").substringBefore(' ').substringBefore('>')
    }
}
