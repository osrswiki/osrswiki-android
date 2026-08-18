package com.omiyawaki.osrswiki.page

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class ArticleHorizontalGestureSnapshot(
    val sequence: Long,
    val owned: Boolean
)

internal fun decodeArticleHorizontalGestureSnapshot(rawResult: String?): ArticleHorizontalGestureSnapshot? {
    if (rawResult.isNullOrBlank() || rawResult == "null") return null
    return runCatching {
        val encodedSnapshot = Json.decodeFromString<String>(rawResult)
        Json.decodeFromString<ArticleHorizontalGestureSnapshot>(encodedSnapshot)
    }.getOrNull()
}

internal fun articleHorizontalGestureSnapshotQuery(sequence: Long): String =
    "(function(){" +
        "var ownership=window.OSRSHorizontalGestureOwnership;" +
        "var snapshot=ownership&&ownership.snapshotForSequence($sequence);" +
        "return snapshot?JSON.stringify(snapshot):null;" +
        "})()"

/**
 * Keeps horizontal WebView content in control of the complete pointer sequence that began on it.
 *
 * The DOM bridge can report ownership after Android has already delivered ACTION_DOWN to the
 * GestureDetector. A monotonically increasing generation lets that late claim cancel and veto only
 * the matching article-navigation gesture, including a claim that arrives just after ACTION_UP.
 */
internal class ArticleHorizontalGestureOwnership {
    enum class NavigationDecision {
        WAITING_FOR_CLASSIFICATION,
        ALLOW_NAVIGATION,
        BLOCK_NAVIGATION,
        STALE
    }

    private var nextGeneration = 0L
    private var acceptingClaimsGeneration: Long? = null
    private var claimedGeneration: Long? = null
    private var navigationCandidateGeneration: Long? = null
    private var finalClassification: Boolean? = null
    private var associatedDomSequence: Long? = null
    private val generationsAwaitingDomSequence = ArrayDeque<Long>()

    val currentGeneration: Long?
        get() = acceptingClaimsGeneration

    fun beginPointer(): Long {
        // A native pointer that retired without a DOM touchstart must not remain at the head of
        // the association queue and steal the next pointer's sequence.
        acceptingClaimsGeneration?.let(generationsAwaitingDomSequence::remove)
        nextGeneration += 1
        acceptingClaimsGeneration = nextGeneration
        claimedGeneration = null
        navigationCandidateGeneration = null
        finalClassification = null
        associatedDomSequence = null
        generationsAwaitingDomSequence.addLast(nextGeneration)
        return nextGeneration
    }

    /**
     * Associates the next primary DOM touch with the native ACTION_DOWN that preceded it.
     *
     * Only an active native generation may receive a DOM begin. DOM touchstart normally follows
     * ACTION_DOWN synchronously; once native UP/CANCEL retires an unbound generation, keeping it in
     * FIFO would poison the next ordinary pointer when the first pointer produced no DOM event.
     */
    fun bindNextDomTouchSequence(sequence: Long): Long? {
        if (sequence <= 0L) return null
        val generation = generationsAwaitingDomSequence.removeFirstOrNull() ?: return null
        if (acceptingClaimsGeneration == generation) {
            associatedDomSequence = sequence
        }
        return generation
    }

    fun domSequenceFor(generation: Long): Long? =
        if (acceptingClaimsGeneration == generation) associatedDomSequence else null

    /** Returns true only when this call newly claims the current pointer sequence. */
    fun claimCurrentPointer(): Boolean {
        val generation = acceptingClaimsGeneration ?: return false
        if (claimedGeneration == generation) return false
        claimedGeneration = generation
        return true
    }

    fun owns(generation: Long): Boolean = claimedGeneration == generation

    fun ownsCurrentPointer(): Boolean =
        acceptingClaimsGeneration?.let(::owns) == true

    /**
     * Registers a native back/sidebar swipe candidate without guessing how long the WebView bridge
     * needs to classify the DOM target. The caller must wait for [recordFinalClassification] when
     * this returns [NavigationDecision.WAITING_FOR_CLASSIFICATION].
     */
    fun registerNavigationCandidate(generation: Long): NavigationDecision {
        if (acceptingClaimsGeneration != generation) return NavigationDecision.STALE
        navigationCandidateGeneration = generation
        if (owns(generation)) return NavigationDecision.BLOCK_NAVIGATION
        return finalClassification?.let { owned ->
            if (owned) NavigationDecision.BLOCK_NAVIGATION else NavigationDecision.ALLOW_NAVIGATION
        } ?: NavigationDecision.WAITING_FOR_CLASSIFICATION
    }

    /** Completes the exact native generation captured by the asynchronous JavaScript callback. */
    fun recordFinalClassification(
        generation: Long,
        snapshot: ArticleHorizontalGestureSnapshot?
    ): NavigationDecision {
        if (acceptingClaimsGeneration != generation) return NavigationDecision.STALE
        val expectedSequence = associatedDomSequence
        if (expectedSequence == null || snapshot == null || snapshot.sequence != expectedSequence) {
            // A missing or mutable-latest answer must never authorize article navigation.
            return NavigationDecision.BLOCK_NAVIGATION
        }
        finalClassification = snapshot.owned
        if (navigationCandidateGeneration != generation) {
            return NavigationDecision.STALE
        }
        return if (snapshot.owned || owns(generation)) {
            NavigationDecision.BLOCK_NAVIGATION
        } else {
            NavigationDecision.ALLOW_NAVIGATION
        }
    }

    fun isAwaitingNavigationDecision(generation: Long): Boolean =
        acceptingClaimsGeneration == generation && navigationCandidateGeneration == generation

    fun finishPointer(generation: Long) {
        generationsAwaitingDomSequence.remove(generation)
        if (acceptingClaimsGeneration == generation) {
            acceptingClaimsGeneration = null
            navigationCandidateGeneration = null
            finalClassification = null
            associatedDomSequence = null
        }
    }

    fun reset() {
        acceptingClaimsGeneration = null
        claimedGeneration = null
        navigationCandidateGeneration = null
        finalClassification = null
        associatedDomSequence = null
        generationsAwaitingDomSequence.clear()
    }
}
