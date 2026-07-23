package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.page.model.Section
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object PageTableOfContentsExtractor {
    fun extract(displayTitle: String?, html: String): List<Section> {
        val document = Jsoup.parseBodyFragment(html)
        val sections = mutableListOf(
            Section(
                id = 0,
                level = 1,
                anchor = "",
                title = cleanTitle(displayTitle).ifBlank { "Top of page" },
                isItalic = false,
                isBold = true
            )
        )
        val seenAnchors = mutableSetOf<String>()

        document.select("h2[id], h3[id], h2 .mw-headline[id], h3 .mw-headline[id]").forEach { element ->
            val heading = element.headingElement() ?: return@forEach
            val anchor = element.id().ifBlank { heading.id() }
            val title = if (element.hasClass("mw-headline")) element.text() else heading.text()
            if (anchor.isBlank() || title.isBlank() || !seenAnchors.add(anchor)) {
                return@forEach
            }

            sections += Section(
                id = sections.size,
                level = heading.tagName().removePrefix("h").toIntOrNull() ?: 2,
                anchor = anchor,
                title = title,
                isItalic = heading.selectFirst("i, em") != null,
                isBold = heading.selectFirst("b, strong") != null
            )
        }

        return sections
    }

    private fun Element.headingElement(): Element? {
        return when (tagName()) {
            "h2", "h3" -> this
            else -> parent()?.takeIf { it.tagName() == "h2" || it.tagName() == "h3" }
        }
    }

    private fun cleanTitle(displayTitle: String?): String {
        return Jsoup.parseBodyFragment(displayTitle.orEmpty()).text()
    }
}
