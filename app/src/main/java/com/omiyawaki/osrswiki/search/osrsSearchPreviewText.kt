package com.omiyawaki.osrswiki.search

/**
 * Preview text for Search rows when Cirrus snippets and intro extracts are empty.
 * Update pages often start with templates or a verbatim copyright line, so
 * `exintro` is blank even though the parse HTML always has readable copy.
 */
object osrsSearchPreviewText {
    const val MAX_CHARS = 160
    private val CHUNK_SPLIT = Regex("(?<=[.!?])\\s+|\\n+|<(?:br|div|p)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val FILENAME_PREFIX = Regex("^[0-9]+px-", RegexOption.IGNORE_CASE)
    private val NUMBERED_TOC = Regex("""^\d+\s+\S+.+\d+\s+\S+""")
    private val SUBSECTION_TOC = Regex("""\d+\.\d+\s+\S+.+\d+\.\d+\s+\S+""")

    fun fromPlainExtract(extract: String?): String? = firstUsableChunk(extract.orEmpty())

    fun fromCandidates(vararg values: String?): String? {
        for (value in values) {
            fromPlainExtract(value)?.let { return it }
        }
        return null
    }

    fun fromHtml(html: String?): String? {
        if (html.isNullOrEmpty()) return null
        val withoutChrome = stripDocumentChrome(html)
        var fallback: String? = null
        withoutChrome.split(Regex("</(?:p|div|h[1-6]|li|section)>", RegexOption.IGNORE_CASE)).forEach { paragraph ->
            val candidate = preview(stripTags(paragraph)) ?: return@forEach
            if (looksLikeSentence(candidate)) return candidate
            if (fallback == null) fallback = candidate
        }
        return fallback ?: firstUsableChunk(stripTags(withoutChrome))
    }

    fun preview(raw: String): String? {
        var text = decodeEntities(stripTags(raw))
        text = text.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty() || isBoilerplate(text)) return null
        if (text.length <= MAX_CHARS) return text
        var clipped = text.take(MAX_CHARS)
        val lastSpace = clipped.lastIndexOf(' ')
        if (lastSpace > 0) {
            clipped = clipped.substring(0, lastSpace)
        }
        return clipped.trim().ifEmpty { null }
    }

    private fun firstUsableChunk(raw: String): String? {
        preview(raw)?.let { return it }
        raw.split(CHUNK_SPLIT).forEach { chunk ->
            preview(chunk)?.let { return it }
        }
        return null
    }

    private fun isBoilerplate(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("copied verbatim") ||
            lower.contains("this official news post") ||
            lower.contains("if you can't see the") ||
            lower.contains("if you can’t see the") ||
            lower.contains("click here to show") ||
            lower.contains("it was added on") ||
            lower.contains("snapshots of the web page") ||
            lower.contains("horizontal_line") ||
            lower == "contents" ||
            lower == "changelog" ||
            lower.startsWith("file:") ||
            lower.startsWith("category:") ||
            FILENAME_PREFIX.containsMatchIn(lower) ||
            NUMBERED_TOC.containsMatchIn(lower) ||
            SUBSECTION_TOC.containsMatchIn(lower) ||
            ('_' in text && ' ' !in text)
    }

    private fun looksLikeSentence(text: String): Boolean =
        text.length >= 40 && Regex("[A-Za-z][.!?](?:\\s|$)").containsMatchIn(text)

    private fun stripDocumentChrome(html: String): String = html
        .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        .replace(
            Regex(
                "<(?:div|nav|ol|ul)[^>]*(?:id|class)=['\"][^'\"]*\\btoc\\b[^'\"]*['\"][^>]*>[\\s\\S]*?</(?:div|nav|ol|ul)>",
                RegexOption.IGNORE_CASE
            ),
            " "
        )
        .replace(
            Regex("<div[^>]*id=['\"]mw-panel-toc['\"][^>]*>[\\s\\S]*?</div>", RegexOption.IGNORE_CASE),
            " "
        )

    private fun stripTags(html: String): String =
        html.replace(Regex("<[^>]+>"), " ")

    private fun decodeEntities(text: String): String {
        var decoded = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        val numeric = Regex("&#(\\d+);")
        decoded = numeric.replace(decoded) { match ->
            match.groupValues[1].toIntOrNull()
                ?.takeIf { it in 1..0x10FFFF }
                ?.let { Character.toChars(it).concatToString() }
                ?: match.value
        }
        return decoded
    }
}
