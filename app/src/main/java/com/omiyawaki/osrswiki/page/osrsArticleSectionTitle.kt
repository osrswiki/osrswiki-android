package com.omiyawaki.osrswiki.page

import org.jsoup.nodes.Element

/**
 * Visible heading text for the native table of contents. Wiki floor-number
 * markup always contains both GB and US variants plus [UK]/[US] help marks;
 * CSS hides one dialect, but Jsoup `.text()` would still concatenate both.
 */
object osrsArticleSectionTitle {
    fun visible(
        element: Element,
        convention: osrsArticleFloorConvention = osrsArticleFloorConvention.current()
    ): String {
        val clone = element.clone()
        clone.select(convention.hiddenDialectSelector).remove()
        return clone.text().replace(Regex("\\s+"), " ").trim()
    }
}
