package com.omiyawaki.osrswiki.news.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsAccessibilityPolicyTest {

    @Test
    fun updateCardDescriptionCombinesTitleAndSnippetAsOneAction() {
        val description = NewsAccessibilityPolicy.updateCardDescription(
            title = "Summer Sweep Up: Combat",
            snippet = "This week brings balance changes."
        )

        assertEquals(
            "Summer Sweep Up: Combat. This week brings balance changes. Opens update article.",
            description
        )
    }

    @Test
    fun popularPageDescriptionIdentifiesIndividualAction() {
        assertEquals(
            "Money making guide. Opens popular page.",
            NewsAccessibilityPolicy.popularPageDescription("Money making guide")
        )
    }

    @Test
    fun carouselChildMustBeFullyInsideViewportToRemainAccessible() {
        assertTrue(
            NewsAccessibilityPolicy.isCarouselChildFullyVisible(
                viewportStart = 16,
                viewportEnd = 1064,
                childStart = 16,
                childEnd = 296
            )
        )
        assertFalse(
            NewsAccessibilityPolicy.isCarouselChildFullyVisible(
                viewportStart = 16,
                viewportEnd = 1064,
                childStart = 1000,
                childEnd = 1280
            )
        )
        assertFalse(
            NewsAccessibilityPolicy.isCarouselChildFullyVisible(
                viewportStart = 16,
                viewportEnd = 1064,
                childStart = -20,
                childEnd = 260
            )
        )
    }
}
