package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmRequest
import com.omiyawaki.osrswiki.theme.Theme

internal data class osrsPreparedArticleRenderKey(
    val pageId: Int?,
    val normalizedTitle: String?,
    val dark: Boolean,
    val collapseTables: Boolean,
    val wrapTableCells: Boolean,
    val readerTextScale: Float
) {
    fun matchesPage(request: ArticlePrewarmRequest): Boolean {
        val requestId = request.pageId?.takeIf { it > 0 }
        val requestTitle = request.key.normalizedTitle
        if (pageId != null && requestId != null) {
            return pageId == requestId
        }
        return normalizedTitle != null &&
            requestTitle != null &&
            normalizedTitle == requestTitle
    }

    companion object {
        fun from(
            request: ArticlePrewarmRequest,
            theme: Theme,
            collapseTables: Boolean,
            wrapTableCells: Boolean,
            readerTextScale: Float
        ): osrsPreparedArticleRenderKey {
            return osrsPreparedArticleRenderKey(
                pageId = request.pageId?.takeIf { it > 0 },
                normalizedTitle = request.key.normalizedTitle,
                dark = theme.isDark(),
                collapseTables = collapseTables,
                wrapTableCells = wrapTableCells,
                readerTextScale = readerTextScale
            )
        }
    }
}
