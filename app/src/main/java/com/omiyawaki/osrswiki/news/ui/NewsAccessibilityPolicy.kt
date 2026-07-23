package com.omiyawaki.osrswiki.news.ui

object NewsAccessibilityPolicy {
    fun updateCardDescription(title: String, snippet: String): String {
        return listOf(title.asSentence(), snippet.asSentence(), "Opens update article.")
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    fun popularPageDescription(title: String): String {
        return "${title.trim()}. Opens popular page."
    }

    fun isCarouselChildFullyVisible(
        viewportStart: Int,
        viewportEnd: Int,
        childStart: Int,
        childEnd: Int
    ): Boolean {
        return childStart >= viewportStart && childEnd <= viewportEnd
    }

    private fun String.asSentence(): String {
        val clean = trim()
        if (clean.isEmpty()) {
            return ""
        }
        return if (clean.last() in setOf('.', '!', '?')) clean else "$clean."
    }
}
