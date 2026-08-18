package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleHorizontalGestureOwnershipTest {
    @Test
    fun lateDomClaimOwnsTheCompleteCurrentPointerSequence() {
        val ownership = ArticleHorizontalGestureOwnership()
        val generation = ownership.beginPointer()

        assertFalse(ownership.owns(generation))
        assertTrue(ownership.claimCurrentPointer())
        assertTrue(ownership.owns(generation))
        assertFalse("a repeated bridge signal must not recancel the detector", ownership.claimCurrentPointer())
    }

    @Test
    fun finishingAndStartingANewPointerCannotLeakPriorOwnership() {
        val ownership = ArticleHorizontalGestureOwnership()
        val first = ownership.beginPointer()
        ownership.claimCurrentPointer()
        ownership.finishPointer(first)

        val second = ownership.beginPointer()
        assertNotEquals(first, second)
        assertFalse(ownership.owns(second))
        assertFalse(ownership.ownsCurrentPointer())
    }

    @Test
    fun lifecycleResetRejectsClaimsWithoutAnActivePointer() {
        val ownership = ArticleHorizontalGestureOwnership()
        ownership.beginPointer()
        ownership.reset()

        assertFalse(ownership.claimCurrentPointer())
        assertFalse(ownership.ownsCurrentPointer())
    }

    @Test
    fun localClassificationBlocksNavigationWithoutAnyTimingThreshold() {
        listOf(0L, 16L, 48L, 100L).forEach { simulatedBridgeDelayMillis ->
            val ownership = ArticleHorizontalGestureOwnership()
            val generation = ownership.beginPointer()
            ownership.bindNextDomTouchSequence(41L)

            assertEquals(
                "candidate must remain pending at ${simulatedBridgeDelayMillis}ms",
                ArticleHorizontalGestureOwnership.NavigationDecision.WAITING_FOR_CLASSIFICATION,
                ownership.registerNavigationCandidate(generation)
            )
            // No timer advances this state. Whether JavaScript answers immediately or after 100ms,
            // the same exact generation is classified and cannot become a page navigation.
            assertEquals(
                ArticleHorizontalGestureOwnership.NavigationDecision.BLOCK_NAVIGATION,
                ownership.recordFinalClassification(
                    generation,
                    ArticleHorizontalGestureSnapshot(sequence = 41L, owned = true)
                )
            )
        }
    }

    @Test
    fun explicitNonLocalClassificationAllowsNavigation() {
        val ownership = ArticleHorizontalGestureOwnership()
        val generation = ownership.beginPointer()
        ownership.bindNextDomTouchSequence(7L)

        assertEquals(
            ArticleHorizontalGestureOwnership.NavigationDecision.WAITING_FOR_CLASSIFICATION,
            ownership.registerNavigationCandidate(generation)
        )
        assertEquals(
            ArticleHorizontalGestureOwnership.NavigationDecision.ALLOW_NAVIGATION,
            ownership.recordFinalClassification(
                generation,
                ArticleHorizontalGestureSnapshot(sequence = 7L, owned = false)
            )
        )
    }

    @Test
    fun classificationForAnInvalidatedGenerationCannotAffectTheNextPointer() {
        val ownership = ArticleHorizontalGestureOwnership()
        val first = ownership.beginPointer()
        ownership.bindNextDomTouchSequence(18L)
        ownership.registerNavigationCandidate(first)
        val second = ownership.beginPointer()
        ownership.bindNextDomTouchSequence(19L)

        assertEquals(
            ArticleHorizontalGestureOwnership.NavigationDecision.STALE,
            ownership.recordFinalClassification(
                first,
                ArticleHorizontalGestureSnapshot(sequence = 18L, owned = true)
            )
        )
        assertFalse(ownership.owns(second))
    }

    @Test
    fun retiredPointerWithoutDomBeginCannotPoisonTheNextPointerAssociation() {
        val ownership = ArticleHorizontalGestureOwnership()
        val first = ownership.beginPointer()
        ownership.finishPointer(first)
        val second = ownership.beginPointer()

        assertEquals(second, ownership.bindNextDomTouchSequence(102L))
        assertEquals(102L, ownership.domSequenceFor(second))
        assertEquals(
            ArticleHorizontalGestureOwnership.NavigationDecision.WAITING_FOR_CLASSIFICATION,
            ownership.registerNavigationCandidate(second)
        )
        assertEquals(
            ArticleHorizontalGestureOwnership.NavigationDecision.ALLOW_NAVIGATION,
            ownership.recordFinalClassification(
                second,
                ArticleHorizontalGestureSnapshot(sequence = 102L, owned = false)
            )
        )
    }

    @Test
    fun interleavedDelayedCallbacksCannotCrossAuthorizeTwoTouches() {
        val ownership = ArticleHorizontalGestureOwnership()
        val first = ownership.beginPointer()
        ownership.bindNextDomTouchSequence(201L)
        ownership.registerNavigationCandidate(first)

        val second = ownership.beginPointer()
        ownership.bindNextDomTouchSequence(202L)
        ownership.registerNavigationCandidate(second)

        assertEquals(
            ArticleHorizontalGestureOwnership.NavigationDecision.STALE,
            ownership.recordFinalClassification(
                first,
                ArticleHorizontalGestureSnapshot(sequence = 201L, owned = false)
            )
        )
        assertEquals(
            ArticleHorizontalGestureOwnership.NavigationDecision.BLOCK_NAVIGATION,
            ownership.recordFinalClassification(
                second,
                ArticleHorizontalGestureSnapshot(sequence = 201L, owned = false)
            )
        )
        assertEquals(
            ArticleHorizontalGestureOwnership.NavigationDecision.ALLOW_NAVIGATION,
            ownership.recordFinalClassification(
                second,
                ArticleHorizontalGestureSnapshot(sequence = 202L, owned = false)
            )
        )
    }

    @Test
    fun missingDomSequenceOrSnapshotFailsClosed() {
        val withoutAssociation = ArticleHorizontalGestureOwnership()
        val first = withoutAssociation.beginPointer()
        withoutAssociation.registerNavigationCandidate(first)
        assertEquals(
            ArticleHorizontalGestureOwnership.NavigationDecision.BLOCK_NAVIGATION,
            withoutAssociation.recordFinalClassification(first, snapshot = null)
        )

        val withoutSnapshot = ArticleHorizontalGestureOwnership()
        val second = withoutSnapshot.beginPointer()
        withoutSnapshot.bindNextDomTouchSequence(301L)
        withoutSnapshot.registerNavigationCandidate(second)
        assertEquals(
            ArticleHorizontalGestureOwnership.NavigationDecision.BLOCK_NAVIGATION,
            withoutSnapshot.recordFinalClassification(second, snapshot = null)
        )
    }

    @Test
    fun snapshotQueryAndDecoderPreserveTheRequestedSequence() {
        val query = articleHorizontalGestureSnapshotQuery(404L)
        assertTrue(query.contains("snapshotForSequence(404)"))
        assertFalse(query.contains("latestTouchIsOwned"))

        val decoded = decodeArticleHorizontalGestureSnapshot(
            "\"{\\\"sequence\\\":404,\\\"owned\\\":true}\""
        )
        assertEquals(ArticleHorizontalGestureSnapshot(sequence = 404L, owned = true), decoded)
        assertEquals(null, decodeArticleHorizontalGestureSnapshot("null"))
    }
}
