package com.omiyawaki.osrswiki.page.preemptive

import com.omiyawaki.osrswiki.network.model.ParseResult
import com.omiyawaki.osrswiki.page.DownloadResult
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ArticlePreparationCoordinatorTest {
    @Test
    fun runningTitlePrewarmAndIdForegroundShareOneFullPreparationThenRecreationHitsCache() = runTest {
        val scope = processScope()
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fullResult = result(123, "Amulet of glory", "<article>complete text</article>")
        val coordinator = coordinator(scope) { _, _, _ ->
            calls.incrementAndGet()
            started.complete(Unit)
            release.await()
            fullResult
        }
        val lease = coordinator.requestPrewarm(ArticlePrewarmRequest(title = "Amulet_of_glory"))
        runCurrent()
        started.await()

        val firstArticle = async {
            coordinator.awaitForeground(
                ArticlePrewarmRequest(pageId = 123, title = "Amulet of glory"),
                forceNetwork = false
            ) {}
        }
        runCurrent()
        assertEquals(1, calls.get())
        release.complete(Unit)
        advanceUntilIdle()
        assertSame(fullResult, firstArticle.await())

        // A newly created PageFragment consumer uses the same process cache, not a consuming slot.
        val recreatedArticle = coordinator.awaitForeground(
            ArticlePrewarmRequest(pageId = 123, title = "Amulet of glory"),
            forceNetwork = false
        ) {}
        assertSame(fullResult, recreatedArticle)
        assertEquals(1, calls.get())
        lease.cancel()
        scope.cancel()
    }

    @Test
    fun olderPrewarmFinishingAfterForceRefreshCannotOverwriteNewerCacheGeneration() = runTest {
        val scope = processScope()
        val olderStarted = CompletableDeferred<Unit>()
        val releaseOlder = CompletableDeferred<Unit>()
        val forceStarted = CompletableDeferred<Unit>()
        val releaseForce = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val request = ArticlePrewarmRequest(pageId = 124, title = "Refresh ordering")
        val coordinator = coordinator(scope) { _, forceNetwork, _ ->
            calls.incrementAndGet()
            if (forceNetwork) {
                forceStarted.complete(Unit)
                releaseForce.await()
                result(124, "New forced result")
            } else {
                olderStarted.complete(Unit)
                releaseOlder.await()
                result(124, "Older speculative result")
            }
        }
        val lease = coordinator.requestPrewarm(request)
        runCurrent()
        olderStarted.await()
        val forced = async { coordinator.awaitForeground(request, forceNetwork = true) {} }
        runCurrent()
        forceStarted.await()

        releaseForce.complete(Unit)
        runCurrent()
        assertEquals("New forced result", forced.await().parseResult.title)
        releaseOlder.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            "New forced result",
            coordinator.awaitForeground(request, forceNetwork = false) {}.parseResult.title
        )
        assertEquals("cache hit must avoid a third preparation", 2, calls.get())
        lease.cancel()
        scope.cancel()
    }

    @Test
    fun foregroundCancellationStopsForegroundOnlySharedWork() = runTest {
        val scope = processScope()
        val started = CompletableDeferred<Unit>()
        val canceled = CompletableDeferred<Unit>()
        val coordinator = coordinator(scope) { _, _, _ ->
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                canceled.complete(Unit)
            }
        }

        val foreground = launch {
            coordinator.awaitForeground(ArticlePrewarmRequest(pageId = 7), false) {}
        }
        runCurrent()
        started.await()
        foreground.cancel()
        runCurrent()

        assertTrue(canceled.isCompleted)
        scope.cancel()
    }

    @Test
    fun cancelingPromotedForegroundDemotesToRemainingPrewarmLease() = runTest {
        val scope = processScope()
        val started = CompletableDeferred<Unit>()
        val canceled = CompletableDeferred<Unit>()
        val coordinator = coordinator(scope) { _, _, _ ->
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                canceled.complete(Unit)
            }
        }
        val request = ArticlePrewarmRequest(pageId = 9, title = "Nine")
        val lease = coordinator.requestPrewarm(request)
        runCurrent()
        started.await()
        val foreground = launch { coordinator.awaitForeground(request, false) {} }
        runCurrent()

        foreground.cancel()
        runCurrent()
        assertFalse(canceled.isCompleted)
        lease.cancel()
        runCurrent()
        assertTrue(canceled.isCompleted)
        scope.cancel()
    }

    @Test
    fun environmentChangeCancelsSpeculationButNotForegroundOwner() = runTest {
        val scope = processScope()
        var decision = ArticlePrewarmDecision(ArticlePrewarmSuppression.NONE, 2)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val canceled = CompletableDeferred<Unit>()
        val coordinator = coordinator(scope, decision = { decision }) { request, _, _ ->
            started.complete(Unit)
            try {
                release.await()
                result(request.pageId ?: 10, request.title ?: "Ten")
            } finally {
                if (!release.isCompleted) canceled.complete(Unit)
            }
        }
        val request = ArticlePrewarmRequest(pageId = 10, title = "Ten")
        val lease = coordinator.requestPrewarm(request)
        runCurrent()
        started.await()
        val foreground = async { coordinator.awaitForeground(request, false) {} }
        runCurrent()

        decision = ArticlePrewarmDecision(ArticlePrewarmSuppression.APP_BACKGROUND, 2)
        coordinator.environmentChanged()
        runCurrent()
        assertFalse(canceled.isCompleted)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(10, foreground.await().parseResult.pageid)
        lease.cancel()
        scope.cancel()
    }

    @Test
    fun environmentChangePromptlyCancelsRunningSpeculationWithoutForeground() = runTest {
        val scope = processScope()
        var decision = ArticlePrewarmDecision(ArticlePrewarmSuppression.NONE, 2)
        val started = CompletableDeferred<Unit>()
        val canceled = CompletableDeferred<Unit>()
        val coordinator = coordinator(scope, decision = { decision }) { _, _, _ ->
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                canceled.complete(Unit)
            }
        }
        coordinator.requestPrewarm(ArticlePrewarmRequest(pageId = 11))
        runCurrent()
        started.await()

        decision = ArticlePrewarmDecision(ArticlePrewarmSuppression.POWER_SAVE, 1)
        coordinator.environmentChanged()
        runCurrent()

        assertTrue(canceled.isCompleted)
        scope.cancel()
    }

    @Test
    fun suppressedNonCooperativeSpeculationCannotDelayForegroundTap() = runTest {
        val scope = processScope()
        var decision = ArticlePrewarmDecision(ArticlePrewarmSuppression.NONE, 1)
        val speculationStarted = CompletableDeferred<Unit>()
        val releaseSpeculation = CompletableDeferred<Unit>()
        val foregroundStarted = CompletableDeferred<Unit>()
        var physicallyActive = 0
        var maximumPhysical = 0
        val coordinator = coordinator(scope, decision = { decision }) { request, _, _ ->
            physicallyActive += 1
            maximumPhysical = maxOf(maximumPhysical, physicallyActive)
            try {
                if (request.pageId == 12) {
                    speculationStarted.complete(Unit)
                    withContext(NonCancellable) { releaseSpeculation.await() }
                } else {
                    foregroundStarted.complete(Unit)
                }
                result(requireNotNull(request.pageId), "Page ${request.pageId}")
            } finally {
                physicallyActive -= 1
            }
        }
        coordinator.requestPrewarm(ArticlePrewarmRequest(pageId = 12))
        runCurrent()
        speculationStarted.await()

        decision = ArticlePrewarmDecision(ArticlePrewarmSuppression.APP_BACKGROUND, 1)
        coordinator.environmentChanged()
        val foreground = async {
            coordinator.awaitForeground(ArticlePrewarmRequest(pageId = 13), false) {}
        }
        runCurrent()

        assertTrue("background cancellation must still yield the foreground lane", foregroundStarted.isCompleted)
        assertEquals(13, foreground.await().parseResult.pageid)
        assertEquals(2, maximumPhysical)
        releaseSpeculation.complete(Unit)
        advanceUntilIdle()
        scope.cancel()
    }

    @Test
    fun unmeteredToConstrainedDownshiftCancelsExcessSpeculationDeterministically() = runTest {
        val scope = processScope()
        var decision = ArticlePrewarmDecision(ArticlePrewarmSuppression.NONE, 2)
        val started = mutableListOf<Int>()
        val canceled = mutableListOf<Int>()
        val coordinator = coordinator(scope, decision = { decision }) { request, _, _ ->
            val id = requireNotNull(request.pageId)
            started += id
            try {
                awaitCancellation()
            } finally {
                canceled += id
            }
        }
        coordinator.requestPrewarm(ArticlePrewarmRequest(pageId = 1))
        coordinator.requestPrewarm(ArticlePrewarmRequest(pageId = 2))
        runCurrent()
        assertEquals(listOf(1, 2), started)

        decision = ArticlePrewarmDecision(ArticlePrewarmSuppression.NONE, 1)
        coordinator.environmentChanged()
        runCurrent()

        assertEquals(listOf(2), canceled)
        scope.cancel()
    }

    @Test
    fun downshiftedNonCooperativeSpeculationAllowsOneSerializedForegroundAndNoReplacement() = runTest {
        val scope = processScope()
        var decision = ArticlePrewarmDecision(ArticlePrewarmSuppression.NONE, 2)
        val speculationStarted = Channel<Int>(Channel.UNLIMITED)
        val releaseSpeculation = mapOf(21 to CompletableDeferred<Unit>(), 22 to CompletableDeferred())
        val firstForegroundStarted = CompletableDeferred<Unit>()
        val releaseFirstForeground = CompletableDeferred<Unit>()
        val secondForegroundStarted = CompletableDeferred<Unit>()
        val replacementSpeculationStarted = CompletableDeferred<Unit>()
        var physicallyActive = 0
        var maximumPhysical = 0
        val coordinator = coordinator(scope, decision = { decision }) { request, _, _ ->
            physicallyActive += 1
            maximumPhysical = maxOf(maximumPhysical, physicallyActive)
            try {
                val pageId = requireNotNull(request.pageId)
                when (pageId) {
                    21, 22 -> {
                        speculationStarted.send(pageId)
                        withContext(NonCancellable) { requireNotNull(releaseSpeculation[pageId]).await() }
                    }
                    23 -> {
                        firstForegroundStarted.complete(Unit)
                        releaseFirstForeground.await()
                    }
                    24 -> secondForegroundStarted.complete(Unit)
                    25 -> replacementSpeculationStarted.complete(Unit)
                }
                result(pageId, "Page $pageId")
            } finally {
                physicallyActive -= 1
            }
        }
        coordinator.requestPrewarm(ArticlePrewarmRequest(pageId = 21))
        coordinator.requestPrewarm(ArticlePrewarmRequest(pageId = 22))
        runCurrent()
        assertEquals(setOf(21, 22), setOf(speculationStarted.receive(), speculationStarted.receive()))

        decision = ArticlePrewarmDecision(ArticlePrewarmSuppression.NONE, 1)
        coordinator.environmentChanged()
        val firstForeground = async {
            coordinator.awaitForeground(ArticlePrewarmRequest(pageId = 23), false) {}
        }
        runCurrent()
        assertTrue(firstForegroundStarted.isCompleted)
        assertEquals("two old physical lanes plus one user lane", 3, maximumPhysical)

        val secondForeground = async {
            coordinator.awaitForeground(ArticlePrewarmRequest(pageId = 24), false) {}
        }
        val replacementLease = coordinator.requestPrewarm(ArticlePrewarmRequest(pageId = 25))
        runCurrent()
        assertFalse("foreground overlap must be serialized", secondForegroundStarted.isCompleted)
        assertFalse("speculation cannot refill during overlap", replacementSpeculationStarted.isCompleted)

        releaseFirstForeground.complete(Unit)
        runCurrent()
        assertEquals(23, firstForeground.await().parseResult.pageid)
        assertTrue(secondForegroundStarted.isCompleted)
        assertEquals(24, secondForeground.await().parseResult.pageid)
        assertEquals(3, maximumPhysical)
        assertFalse(replacementSpeculationStarted.isCompleted)
        replacementLease.cancel()
        releaseSpeculation.values.forEach { it.complete(Unit) }
        advanceUntilIdle()
        scope.cancel()
    }

    @Test
    fun unrelatedForegroundPreemptsNewestSpeculationInsteadOfWaitingBehindIt() = runTest {
        val scope = processScope()
        val prewarmCanceled = mutableListOf<Int>()
        val foregroundStarted = CompletableDeferred<Unit>()
        val coordinator = coordinator(scope) { request, _, _ ->
            val id = requireNotNull(request.pageId)
            if (id <= 2) {
                try {
                    awaitCancellation()
                } finally {
                    prewarmCanceled += id
                }
            } else {
                foregroundStarted.complete(Unit)
                result(id, "Foreground")
            }
        }
        coordinator.requestPrewarm(ArticlePrewarmRequest(pageId = 1))
        coordinator.requestPrewarm(ArticlePrewarmRequest(pageId = 2))
        runCurrent()

        val foreground = async {
            coordinator.awaitForeground(ArticlePrewarmRequest(pageId = 3), false) {}
        }
        runCurrent()

        assertTrue(foregroundStarted.isCompleted)
        assertEquals(listOf(2), prewarmCanceled)
        assertEquals(3, foreground.await().parseResult.pageid)
        scope.cancel()
    }

    @Test
    fun detachedRowThenImmediateSameArticleTapStartsFreshInsteadOfJoiningCanceledDeferred() = runTest {
        val scope = processScope()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseCanceledPreparation = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val request = ArticlePrewarmRequest(pageId = 31, title = "Thirty one")
        val coordinator = coordinator(
            scope,
            decision = { ArticlePrewarmDecision(ArticlePrewarmSuppression.NONE, 1) }
        ) { _, _, _ ->
            if (calls.incrementAndGet() == 1) {
                firstStarted.complete(Unit)
                withContext(NonCancellable) { releaseCanceledPreparation.await() }
                result(31, "Canceled stale result")
            } else {
                secondStarted.complete(Unit)
                result(31, "Fresh foreground result")
            }
        }

        val lease = coordinator.requestPrewarm(request)
        runCurrent()
        firstStarted.await()
        lease.cancel()
        val foreground = async { coordinator.awaitForeground(request, false) {} }
        runCurrent()

        assertTrue("fresh foreground must start while canceled Deferred is unwinding", secondStarted.isCompleted)
        assertEquals("Fresh foreground result", foreground.await().parseResult.title)
        assertEquals(2, calls.get())
        releaseCanceledPreparation.complete(Unit)
        advanceUntilIdle()
        assertEquals(
            "Fresh foreground result",
            coordinator.awaitForeground(request, false) {}.parseResult.title
        )
        assertEquals("stale canceled preparation must not overwrite cache", 2, calls.get())
        scope.cancel()
    }

    @Test
    fun foregroundStartsImmediatelyWhenPreemptedSpeculationIgnoresCancellationTemporarily() = runTest {
        val scope = processScope()
        val speculationStarted = CompletableDeferred<Unit>()
        val releaseCanceledPreparation = CompletableDeferred<Unit>()
        val foregroundStarted = CompletableDeferred<Unit>()
        val coordinator = coordinator(
            scope,
            decision = { ArticlePrewarmDecision(ArticlePrewarmSuppression.NONE, 1) }
        ) { request, _, _ ->
            if (request.pageId == 40) {
                speculationStarted.complete(Unit)
                withContext(NonCancellable) { releaseCanceledPreparation.await() }
                result(40, "Stale speculation")
            } else {
                foregroundStarted.complete(Unit)
                result(41, "Tapped article")
            }
        }
        coordinator.requestPrewarm(ArticlePrewarmRequest(pageId = 40))
        runCurrent()
        speculationStarted.await()

        val foreground = async {
            coordinator.awaitForeground(ArticlePrewarmRequest(pageId = 41), false) {}
        }
        runCurrent()

        assertTrue("tap must not wait for non-cooperative speculative finally", foregroundStarted.isCompleted)
        assertEquals(41, foreground.await().parseResult.pageid)
        releaseCanceledPreparation.complete(Unit)
        advanceUntilIdle()
        scope.cancel()
    }

    @Test
    fun repeatedNonCooperativeForegroundCancellationNeverExceedsSingleOverlap() = runTest {
        val scope = processScope()
        val speculationStarted = CompletableDeferred<Unit>()
        val firstForegroundStarted = CompletableDeferred<Unit>()
        val secondForegroundStarted = CompletableDeferred<Unit>()
        val releaseSpeculation = CompletableDeferred<Unit>()
        val releaseFirstForeground = CompletableDeferred<Unit>()
        var physicallyActive = 0
        var maximumPhysical = 0
        val coordinator = coordinator(
            scope,
            decision = { ArticlePrewarmDecision(ArticlePrewarmSuppression.NONE, 1) }
        ) { request, _, _ ->
            physicallyActive += 1
            maximumPhysical = maxOf(maximumPhysical, physicallyActive)
            try {
                when (request.pageId) {
                    50 -> {
                        speculationStarted.complete(Unit)
                        withContext(NonCancellable) { releaseSpeculation.await() }
                    }
                    51 -> {
                        firstForegroundStarted.complete(Unit)
                        withContext(NonCancellable) { releaseFirstForeground.await() }
                    }
                    52 -> secondForegroundStarted.complete(Unit)
                }
                result(requireNotNull(request.pageId), "Page ${request.pageId}")
            } finally {
                physicallyActive -= 1
            }
        }
        coordinator.requestPrewarm(ArticlePrewarmRequest(pageId = 50))
        runCurrent()
        speculationStarted.await()

        val firstForeground = launch {
            coordinator.awaitForeground(ArticlePrewarmRequest(pageId = 51), false) {}
        }
        runCurrent()
        assertTrue(firstForegroundStarted.isCompleted)
        assertEquals(2, maximumPhysical)
        firstForeground.cancel()
        val secondForeground = async {
            coordinator.awaitForeground(ArticlePrewarmRequest(pageId = 52), false) {}
        }
        runCurrent()

        assertFalse(secondForegroundStarted.isCompleted)
        assertEquals(2, maximumPhysical)
        releaseSpeculation.complete(Unit)
        runCurrent()
        assertFalse("canceled foreground still consumes the sole physical slot", secondForegroundStarted.isCompleted)
        releaseFirstForeground.complete(Unit)
        runCurrent()
        assertTrue(secondForegroundStarted.isCompleted)
        assertEquals(52, secondForeground.await().parseResult.pageid)
        assertEquals(2, maximumPhysical)
        scope.cancel()
    }

    @Test
    fun constrainedSchedulerRunsAtMostOnePreparation() = runTest {
        verifyConcurrencyLimit(1)
    }

    @Test
    fun unmeteredSchedulerRunsAtMostTwoPreparations() = runTest {
        verifyConcurrencyLimit(2)
    }

    @Test
    fun invalidatingSavedIdentityMakesNextForegroundPrepareAgain() = runTest {
        val scope = processScope()
        val calls = AtomicInteger()
        val coordinator = coordinator(scope) { request, _, _ ->
            val revision = calls.incrementAndGet()
            result(requireNotNull(request.pageId), "Revision $revision")
        }
        val request = ArticlePrewarmRequest(pageId = 73, title = "Saved article")
        assertEquals("Revision 1", coordinator.awaitForeground(request, false) {}.parseResult.title)
        runCurrent()
        coordinator.invalidate(ArticlePrewarmRequest(title = "Saved article"))
        assertEquals("Revision 2", coordinator.awaitForeground(request, false) {}.parseResult.title)
        assertEquals(2, calls.get())
        scope.cancel()
    }

    @Test
    fun conflictingAuthoritativeIdsWithSameTitleNeverCoalesceInFlight() = runTest {
        val scope = processScope()
        val started = Channel<Int>(Channel.UNLIMITED)
        val releases = mapOf(80 to CompletableDeferred<Unit>(), 81 to CompletableDeferred())
        val calls = AtomicInteger()
        val coordinator = coordinator(scope) { request, _, _ ->
            val pageId = requireNotNull(request.pageId)
            calls.incrementAndGet()
            started.send(pageId)
            requireNotNull(releases[pageId]).await()
            result(pageId, "Shared display title")
        }
        val lease = coordinator.requestPrewarm(
            ArticlePrewarmRequest(pageId = 80, title = "Shared display title")
        )
        runCurrent()
        assertEquals(80, started.receive())

        val foreground = async {
            coordinator.awaitForeground(
                ArticlePrewarmRequest(pageId = 81, title = "Shared display title"),
                false
            ) {}
        }
        runCurrent()

        assertEquals(81, started.receive())
        assertEquals(2, calls.get())
        releases.getValue(81).complete(Unit)
        runCurrent()
        assertEquals(81, foreground.await().parseResult.pageid)
        releases.getValue(80).complete(Unit)
        advanceUntilIdle()
        lease.cancel()
        scope.cancel()
    }

    private suspend fun TestScope.verifyConcurrencyLimit(limit: Int) {
        val scope = processScope()
        val release = Channel<Unit>(Channel.UNLIMITED)
        var active = 0
        var maximum = 0
        val coordinator = coordinator(
            scope,
            decision = { ArticlePrewarmDecision(ArticlePrewarmSuppression.NONE, limit) }
        ) { request, _, _ ->
            active += 1
            maximum = maxOf(maximum, active)
            release.receive()
            active -= 1
            result(requireNotNull(request.pageId), "Page ${request.pageId}")
        }
        val leases = (1..4).map { id ->
            coordinator.requestPrewarm(ArticlePrewarmRequest(pageId = id))
        }
        runCurrent()
        assertEquals(limit, maximum)
        repeat(4) {
            release.send(Unit)
            runCurrent()
        }
        advanceUntilIdle()
        assertEquals(limit, maximum)
        leases.forEach(ArticlePrewarmLease::cancel)
        scope.cancel()
    }

    private fun TestScope.processScope() =
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    private fun coordinator(
        scope: CoroutineScope,
        decision: () -> ArticlePrewarmDecision = {
            ArticlePrewarmDecision(ArticlePrewarmSuppression.NONE, 2)
        },
        prepare: suspend (ArticlePrewarmRequest, Boolean, (Int) -> Unit) -> DownloadResult
    ) = ArticlePreparationCoordinator(
        processScope = scope,
        cache = PreparedArticleCache(),
        environmentProvider = ArticlePrewarmEnvironmentProvider(decision),
        prepare = prepare,
        eventSink = ArticlePrewarmEventSink {}
    )

    private fun result(pageId: Int, title: String, html: String = "<p>$title full body</p>") =
        DownloadResult(
            processedHtml = html,
            parseResult = ParseResult(title, pageId, 1L, html, title),
            backgroundUrls = emptyList()
        )
}
