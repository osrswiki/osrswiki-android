package com.omiyawaki.osrswiki.page

/**
 * Represents the core displayable data for an article.
 * This structure groups essential article properties for better organization.
 * Assumes that when this data is present, essential fields like pageId, title,
 * and htmlContent are non-null.
 */
data class ArticleDisplayData(
    val pageId: Int,
    val title: String,
    val htmlContent: String,
    val imageUrl: String? // Image URL can be optional
)
