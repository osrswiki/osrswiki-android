package com.omiyawaki.osrswiki.page

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.omiyawaki.osrswiki.MainActivity
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@LargeTest
@RunWith(AndroidJUnit4::class)
class AndroidDeepNavigationFixtureAuditTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val args = InstrumentationRegistry.getArguments()
    private val currentActivity = AtomicReference<Activity?>()
    private lateinit var lifecycleCallbacks: Application.ActivityLifecycleCallbacks
    private lateinit var outputDir: File

    @Before
    fun setUp() {
        outputDir = File(
            targetContext.getExternalFilesDir(null),
            args.getString(ARG_OUTPUT_DIR) ?: DEFAULT_OUTPUT_DIR
        )
        outputDir.deleteRecursively()
        outputDir.mkdirs()
        File(outputDir, "audit-arguments.json").writeText(argumentsJson().toString(2))

        lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity.set(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity.get() === activity) {
                    currentActivity.compareAndSet(activity, null)
                }
            }
        }
        (targetContext.applicationContext as Application).registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    @After
    fun tearDown() {
        clearTaskToLauncher()
        (targetContext.applicationContext as Application).unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    @Test
    fun auditDeterministicNativeStackFixtureReverseOrder() {
        require(device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1") {
            "Android deep navigation fixture audit must run on an emulator."
        }

        val seed = args.getString(ARG_SEED)?.toIntOrNull() ?: osrsDeepNavigationFixtureAudit.DEFAULT_SEED
        val startOffset = args.getString(ARG_START_OFFSET)?.toIntOrNull() ?: osrsDeepNavigationFixtureAudit.DEFAULT_START_OFFSET
        val startCount = args.getString(ARG_START_COUNT)?.toIntOrNull() ?: DEFAULT_START_COUNT
        val depth = args.getString(ARG_DEPTH)?.toIntOrNull() ?: osrsDeepNavigationFixtureAudit.DEFAULT_DEPTH
        val batchSize = args.getString(ARG_BATCH_SIZE)?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_BATCH_SIZE
        val testStartedAt = SystemClock.elapsedRealtime()

        launchFixtureProbe(seed, startOffset)
        val activity = waitForPageActivity(ACTIVITY_TIMEOUT_MS)
        assertTrue("PageActivity did not start for fixture probe", activity != null)

        var processedStarts = 0
        var completedStarts = 0
        var forwardTransitions = 0
        var backTransitions = 0
        var mismatchCount = 0
        var firstMismatch: String? = null
        var appElapsedMilliseconds = 0L
        var finalActiveTitle: String? = null
        var finalActiveUrl: String? = null

        while (processedStarts < startCount) {
            val batchStartOffset = startOffset + processedStarts
            val batchCount = minOf(batchSize, startCount - processedStarts)
            var batchResult: osrsDeepNavigationFixtureAuditResult? = null
            instrumentation.runOnMainSync {
                batchResult = activity!!.runDeepNavigationFixtureAuditForDebugTests(
                    seed = seed,
                    startOffset = batchStartOffset,
                    startCount = batchCount,
                    targetDepth = depth
                )
            }
            val batch = requireNotNull(batchResult)
            completedStarts += batch.completedStarts
            forwardTransitions += batch.forwardTransitions
            backTransitions += batch.backTransitions
            mismatchCount += batch.mismatchCount
            firstMismatch = firstMismatch ?: batch.firstMismatch
            appElapsedMilliseconds += batch.elapsedMilliseconds
            finalActiveTitle = batch.finalActiveTitle
            finalActiveUrl = batch.finalActiveUrl
            processedStarts += batchCount
            instrumentation.waitForIdleSync()

            if (!batch.passed) {
                break
            }
        }

        val result = osrsDeepNavigationFixtureAuditResult(
            status = if (
                completedStarts == startCount &&
                forwardTransitions == startCount * depth &&
                backTransitions == startCount * depth &&
                mismatchCount == 0
            ) {
                "pass"
            } else {
                "mismatch"
            },
            seed = seed,
            startOffset = startOffset,
            startCount = startCount,
            targetDepth = depth,
            completedStarts = completedStarts,
            forwardTransitions = forwardTransitions,
            backTransitions = backTransitions,
            mismatchCount = mismatchCount,
            firstMismatch = firstMismatch,
            elapsedMilliseconds = appElapsedMilliseconds,
            finalActiveTitle = finalActiveTitle,
            finalActiveUrl = finalActiveUrl
        )
        writeResult(result, testElapsedMilliseconds = SystemClock.elapsedRealtime() - testStartedAt)

        assertEquals("pass", result.status)
        assertEquals(seed, result.seed)
        assertEquals(startOffset, result.startOffset)
        assertEquals(startCount, result.startCount)
        assertEquals(depth, result.targetDepth)
        assertEquals(startCount, result.completedStarts)
        assertEquals(startCount * depth, result.forwardTransitions)
        assertEquals(startCount * depth, result.backTransitions)
        assertEquals(0, result.mismatchCount)
        assertTrue(result.passed)
    }

    private fun launchFixtureProbe(seed: Int, startOffset: Int) {
        val sampleOrdinal = osrsDeepNavigationFixtureAudit.sampleOrdinal(seed, startOffset)
        val intent = PageActivity.newIntent(
            context = targetContext,
            pageTitle = osrsDeepNavigationFixtureAudit.articleTitle(sampleOrdinal, depth = 0),
            pageId = null,
            source = HistoryEntry.SOURCE_INTERNAL_LINK
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(PageActivity.EXTRA_DEEP_NAVIGATION_FIXTURE_PROBE_FOR_DEBUG_TESTS, true)
        }
        targetContext.startActivity(intent)
    }

    private fun waitForPageActivity(timeoutMs: Long): PageActivity? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            instrumentation.waitForIdleSync()
            val activity = currentActivity.get() as? PageActivity
            if (activity != null) {
                return activity
            }
            SystemClock.sleep(POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return currentActivity.get() as? PageActivity
    }

    private fun writeResult(result: osrsDeepNavigationFixtureAuditResult, testElapsedMilliseconds: Long) {
        val json = resultJson(result, testElapsedMilliseconds)
        File(outputDir, "fixture-stack-summary.json").writeText(json.toString(2))
        File(outputDir, "summary.json").writeText(json.toString(2))
        File(outputDir, "fixture-stack-observations.jsonl").writeText(json.toString() + "\n")
    }

    private fun resultJson(result: osrsDeepNavigationFixtureAuditResult, testElapsedMilliseconds: Long): JSONObject {
        return JSONObject()
            .put("event", "fixture_audit")
            .put("result", result.status)
            .put("seed", result.seed)
            .put("start_offset", result.startOffset)
            .put("start_count", result.startCount)
            .put("target_depth", result.targetDepth)
            .put("completed_starts", result.completedStarts)
            .put("forward_transitions", result.forwardTransitions)
            .put("back_transitions", result.backTransitions)
            .put("mismatch_count", result.mismatchCount)
            .put("first_mismatch", result.firstMismatch ?: JSONObject.NULL)
            .put("elapsed_ms", result.elapsedMilliseconds)
            .put("test_elapsed_ms", testElapsedMilliseconds)
            .put("final_active_title", result.finalActiveTitle ?: JSONObject.NULL)
            .put("final_active_url", result.finalActiveUrl ?: JSONObject.NULL)
            .put("raw_state", result.accessibilityLabel)
            .put("completed_samples", result.completedStarts)
            .put("forward_pages", result.forwardTransitions)
            .put("back_checks", result.backTransitions)
            .put("sample_aborts", if (result.passed) 0 else 1)
            .put("render_timeouts", 0)
            .put("output_dir", outputDir.absolutePath)
    }

    private fun clearTaskToLauncher() {
        val intent = Intent(targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        targetContext.startActivity(intent)
        waitUntil(ACTIVITY_TIMEOUT_MS) { currentActivity.get() is MainActivity }
    }

    private fun waitUntil(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            instrumentation.waitForIdleSync()
            if (predicate()) {
                return true
            }
            SystemClock.sleep(POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return predicate()
    }

    private fun argumentsJson(): JSONObject {
        val json = JSONObject()
        for (key in args.keySet()) {
            json.put(key, args.getString(key))
        }
        return json
    }

    private companion object {
        const val ARG_OUTPUT_DIR = "fixtureOutputDir"
        const val ARG_SEED = "fixtureSeed"
        const val ARG_START_OFFSET = "fixtureStartOffset"
        const val ARG_START_COUNT = "fixtureStartCount"
        const val ARG_DEPTH = "fixtureDepth"
        const val ARG_BATCH_SIZE = "fixtureBatchSize"
        const val DEFAULT_OUTPUT_DIR = "android-deep-navigation-harness-parity-2026-07-09"
        const val DEFAULT_START_COUNT = 10_000
        const val DEFAULT_BATCH_SIZE = 100
        const val ACTIVITY_TIMEOUT_MS = 10_000L
        const val POLL_MS = 25L
    }
}
