package com.omiyawaki.osrswiki.news.repository

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsRequestRetryPolicyTest {
    @Test
    fun retriesOneTransientIoFailureThenReturnsSuccess() = runTest {
        val attempts = mutableListOf<Int>()

        val result = osrsFetchWithRetry(maxAttempts = 2, retryDelayMillis = 0) { attempt ->
            attempts += attempt
            if (attempt == 1) throw IOException("transient")
            "loaded"
        }

        assertEquals("loaded", result)
        assertEquals(listOf(1, 2), attempts)
    }

    @Test
    fun stopsAfterTheConfiguredNumberOfIoAttempts() = runTest {
        val attempts = mutableListOf<Int>()

        var failure: Throwable? = null
        try {
            osrsFetchWithRetry<Unit>(maxAttempts = 2, retryDelayMillis = 0) { attempt ->
                attempts += attempt
                throw IOException("still unavailable")
            }
        } catch (caught: Throwable) {
            failure = caught
        }

        assertTrue(failure is IOException)
        assertEquals("still unavailable", failure?.message)
        assertEquals(listOf(1, 2), attempts)
    }

    @Test
    fun doesNotRetryProgrammingFailures() = runTest {
        val attempts = mutableListOf<Int>()

        var failure: Throwable? = null
        try {
            osrsFetchWithRetry<Unit>(maxAttempts = 2, retryDelayMillis = 0) { attempt ->
                attempts += attempt
                error("parser contract")
            }
        } catch (caught: Throwable) {
            failure = caught
        }

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf(1), attempts)
    }
}
