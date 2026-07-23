package com.omiyawaki.osrswiki.ui.map

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProcessOwnedPreloadCoordinatorTest {
    @Test
    fun concurrentWaitersShareExactlyOneProcessOwnedGeneration() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val coordinator = ProcessOwnedPreloadCoordinator(scope)
        val operationCount = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        val waiters = List(12) {
            async {
                coordinator.awaitOrStart { generation ->
                    operationCount.incrementAndGet()
                    assertTrue(coordinator.isCurrent(generation))
                    release.await()
                    Result.success(Unit)
                }
            }
        }

        runCurrent()
        assertEquals(1, operationCount.get())
        release.complete(Unit)
        advanceUntilIdle()
        assertTrue(waiters.all { it.await().isSuccess })
        scope.cancel()
    }

    @Test
    fun cancellingOneCallerDoesNotCancelTheSharedOperationOrSecondWaiter() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val coordinator = ProcessOwnedPreloadCoordinator(scope)
        val operationCount = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = launch {
            coordinator.awaitOrStart {
                operationCount.incrementAndGet()
                started.complete(Unit)
                release.await()
                Result.success(Unit)
            }
        }
        runCurrent()
        started.await()
        first.cancel()
        val second = async {
            coordinator.awaitOrStart {
                operationCount.incrementAndGet()
                Result.failure(AssertionError("must join existing operation"))
            }
        }
        runCurrent()

        assertEquals(1, operationCount.get())
        release.complete(Unit)
        advanceUntilIdle()
        assertTrue(second.await().isSuccess)
        scope.cancel()
    }

    @Test
    fun functionalFailureAndTimeoutCanRetryWithNewGenerations() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val coordinator = ProcessOwnedPreloadCoordinator(scope)
        val generations = mutableListOf<Long>()

        val failed = coordinator.awaitOrStart { generation ->
            generations += generation
            Result.failure(IllegalStateException("style failure"))
        }
        val timedOut = coordinator.awaitOrStart { generation ->
            generations += generation
            try {
                withTimeout(10) { awaitCancellation() }
                Result.success(Unit)
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }
        advanceTimeBy(10)
        advanceUntilIdle()
        val recovered = coordinator.awaitOrStart { generation ->
            generations += generation
            Result.success(Unit)
        }

        assertTrue(failed.isFailure)
        assertTrue(timedOut.isFailure)
        assertTrue(recovered.isSuccess)
        assertEquals(3, generations.distinct().size)
        scope.cancel()
    }

    @Test
    fun invalidationMakesLateGenerationCallbacksObsolete() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val coordinator = ProcessOwnedPreloadCoordinator(scope)
        var oldGeneration = -1L
        val release = CompletableDeferred<Unit>()
        val old = async {
            coordinator.awaitOrStart { generation ->
                oldGeneration = generation
                release.await()
                Result.success(Unit)
            }
        }
        runCurrent()

        coordinator.invalidate(kotlinx.coroutines.CancellationException("rotation boundary"))
        assertFalse(coordinator.isCurrent(oldGeneration))
        release.complete(Unit)
        advanceUntilIdle()
        assertTrue(old.isCancelled)
        val replacement = coordinator.awaitOrStart { generation ->
            assertTrue(generation > oldGeneration)
            Result.success(Unit)
        }
        assertTrue(replacement.isSuccess)
        scope.cancel()
    }
}
