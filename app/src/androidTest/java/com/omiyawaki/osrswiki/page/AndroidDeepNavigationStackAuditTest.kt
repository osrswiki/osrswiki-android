package com.omiyawaki.osrswiki.page

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.omiyawaki.osrswiki.MainActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileWriter
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

@LargeTest
@RunWith(AndroidJUnit4::class)
class AndroidDeepNavigationStackAuditTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val args = InstrumentationRegistry.getArguments()
    private val currentActivity = AtomicReference<Activity?>()
    private lateinit var lifecycleCallbacks: Application.ActivityLifecycleCallbacks
    private lateinit var outputDir: File
    private lateinit var manifestWriter: FileWriter
    private val mismatches = mutableListOf<JSONObject>()
    private var forwardPages = 0
    private var backChecks = 0
    private var completedSamples = 0
    private var renderTimeouts = 0
    private var sampleAborts = 0

    @Before
    fun setUp() {
        outputDir = File(
            targetContext.getExternalFilesDir(null),
            args.getString(ARG_OUTPUT_DIR) ?: DEFAULT_OUTPUT_DIR
        )
        outputDir.mkdirs()
        manifestWriter = FileWriter(File(outputDir, "stack-manifest.jsonl"), false)
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
        val summary = JSONObject()
            .put("completed_samples", completedSamples)
            .put("forward_pages", forwardPages)
            .put("back_checks", backChecks)
            .put("mismatch_count", mismatches.size)
            .put("render_timeouts", renderTimeouts)
            .put("sample_aborts", sampleAborts)
            .put("output_dir", outputDir.absolutePath)
        File(outputDir, "summary.json").writeText(summary.toString(2))
        File(outputDir, "mismatches.jsonl").writeText(
            mismatches.joinToString(separator = "\n", postfix = if (mismatches.isEmpty()) "" else "\n") {
                it.toString()
            }
        )
        manifestWriter.flush()
        manifestWriter.close()
        (targetContext.applicationContext as Application).unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    @Test
    fun auditDeepArticleActivityBackStackReverseOrder() {
        val seed = args.getString(ARG_SEED)?.toLongOrNull() ?: DEFAULT_SEED
        val startCount = args.getString(ARG_START_COUNT)?.toIntOrNull() ?: DEFAULT_START_COUNT
        val startIndex = args.getString(ARG_START_INDEX)?.toIntOrNull() ?: 0
        val depth = args.getString(ARG_DEPTH)?.toIntOrNull() ?: DEFAULT_DEPTH
        val renderTimeoutMs = args.getString(ARG_RENDER_TIMEOUT_MS)?.toLongOrNull() ?: DEFAULT_RENDER_TIMEOUT_MS
        val waitForRender = args.getString(ARG_WAIT_FOR_RENDER)?.toBooleanStrictOrNull() ?: true
        val maxRuntimeMs = args.getString(ARG_MAX_RUNTIME_MS)?.toLongOrNull()
        val deadline = maxRuntimeMs?.let { SystemClock.elapsedRealtime() + it } ?: Long.MAX_VALUE

        for (sampleOrdinal in startIndex until startIndex + startCount) {
            if (SystemClock.elapsedRealtime() >= deadline) {
                writeEvent("run_limit", sampleOrdinal, -1, null, null, "max_runtime_ms reached before next sample")
                break
            }

            val stack = buildStack(seed, sampleOrdinal, depth)
            val sampleId = "android-deep-stack-${sampleOrdinal.toString().padStart(5, '0')}"
            writeSamplePlan(sampleId, sampleOrdinal, seed, stack)

            try {
                launchFreshMain(sampleOrdinal)
                val forwardObserved = mutableListOf<String>()
                var forwardMismatch = false
                for ((depthIndex, title) in stack.withIndex()) {
                    launchPageFromCurrentActivity(title)
                    val resumed = waitForPageActivityTitle(title, ACTIVITY_TIMEOUT_MS)
                    val rendered = if (waitForRender) waitForArticleSurface(renderTimeoutMs) else true
                    if (!rendered) {
                        renderTimeouts += 1
                    }
                    forwardPages += 1
                    forwardObserved += resumed ?: "(missing)"
                    writeEvent(
                        event = "forward",
                        sampleOrdinal = sampleOrdinal,
                        depth = depthIndex,
                        expectedTitle = title,
                        actualTitle = resumed,
                        note = if (rendered) "render_observed" else "render_timeout"
                    )
                    if (resumed != title) {
                        recordMismatch(sampleId, sampleOrdinal, depthIndex, title, resumed, "forward_resume")
                        sampleAborts += 1
                        forwardMismatch = true
                        break
                    }
                }

                if (forwardMismatch || forwardObserved.size != stack.size || forwardObserved.lastOrNull() != stack.last()) {
                    clearTaskToLauncher()
                    continue
                }

                verifyReverseBackOrder(sampleId, sampleOrdinal, stack)
                completedSamples += 1
            } catch (throwable: Throwable) {
                sampleAborts += 1
                writeEvent("sample_exception", sampleOrdinal, -1, null, null, throwable.stackTraceToString())
                clearTaskToLauncher()
            }
        }
    }

    private fun buildStack(seed: Long, sampleOrdinal: Int, depth: Int): List<String> {
        val forced = forcedPrefixFor(sampleOrdinal)
        val random = Random(seed xor sampleOrdinal.toLong())
        val stack = ArrayList<String>(depth)
        stack.addAll(forced.take(depth))
        while (stack.size < depth) {
            val title = TITLE_CORPUS[random.nextInt(TITLE_CORPUS.size)]
            val suffix = if (random.nextInt(6) == 0) "/Strategies" else ""
            stack += title + suffix
        }
        return stack
    }

    private fun forcedPrefixFor(sampleOrdinal: Int): List<String> {
        return when (sampleOrdinal) {
            0 -> listOf(
                "Update:The Blood Moon Rises",
                "The Blood Moon Rises",
                "The Blood Moon Rises/Quick guide",
                "Perilous Moons",
                "Blood moon",
            )
            1 -> listOf("Barrows", "Barrows#Rewards", "Barrows/Strategies", "Ahrim the Blighted")
            2 -> listOf("Dragon scimitar", "Dragon%20scimitar", "Monkey Madness I", "Monkey Madness I/Quick guide")
            3 -> listOf("Vorkath", "Vorkath/Strategies", "Dragon Slayer II", "Dragon Slayer II/Quick guide")
            4 -> listOf("Zulrah", "Zulrah/Strategies", "Regicide", "Regicide/Quick guide")
            else -> emptyList()
        }
    }

    private fun launchFreshMain(sampleOrdinal: Int) {
        val intent = Intent(targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        targetContext.startActivity(intent)
        val mainReady = waitUntil(ACTIVITY_TIMEOUT_MS) {
            currentActivity.get() is MainActivity
        }
        writeEvent("sample_start", sampleOrdinal, 0, "MainActivity", currentActivityName(), "main_ready=$mainReady")
    }

    private fun launchPageFromCurrentActivity(title: String) {
        val launcher = currentActivity.get()
        instrumentation.runOnMainSync {
            val context = launcher ?: targetContext
            val intent = PageActivity.newIntent(
                context,
                title,
                null,
                HistoryEntry.SOURCE_INTERNAL_LINK
            )
            if (launcher == null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun verifyReverseBackOrder(sampleId: String, sampleOrdinal: Int, stack: List<String>) {
        val expectedAfterBack = ArrayDeque(stack.dropLast(1).asReversed())
        var backDepth = stack.lastIndex
        while (expectedAfterBack.isNotEmpty()) {
            val expected = expectedAfterBack.removeFirst()
            device.pressBack()
            val actual = waitForPageActivityTitle(expected, ACTIVITY_TIMEOUT_MS)
            backChecks += 1
            writeEvent("back", sampleOrdinal, backDepth, expected, actual, "expect_previous_page")
            if (actual != expected) {
                recordMismatch(sampleId, sampleOrdinal, backDepth, expected, actual, "back_reverse_order")
                break
            }
            backDepth -= 1
        }

        device.pressBack()
        val returnedToMain = waitUntil(ACTIVITY_TIMEOUT_MS) {
            currentActivity.get() is MainActivity
        }
        backChecks += 1
        writeEvent("back_to_root", sampleOrdinal, 0, "MainActivity", currentActivityName(), "returned=$returnedToMain")
        if (!returnedToMain) {
            recordMismatch(sampleId, sampleOrdinal, 0, "MainActivity", currentActivityName(), "back_to_root")
        }
    }

    private fun waitForPageActivityTitle(expectedTitle: String, timeoutMs: Long): String? {
        var observed: String? = null
        waitUntil(timeoutMs) {
            val activity = currentActivity.get() as? PageActivity ?: return@waitUntil false
            observed = activity.intent.getStringExtra(PageActivity.EXTRA_PAGE_TITLE)
            observed == expectedTitle
        }
        return observed
    }

    private fun waitForArticleSurface(timeoutMs: Long): Boolean {
        val webViewVisible = waitUntil(timeoutMs) {
            val activity = currentActivity.get() ?: return@waitUntil false
            val webView = activity.findViewById<View?>(R.id.page_web_view)
            webView != null && webView.visibility == View.VISIBLE && webView.width > 0 && webView.height > 0
        }
        if (webViewVisible) {
            return true
        }
        return device.wait(Until.hasObject(By.res(targetContext.packageName, "page_web_view")), timeoutMs)
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

    private fun writeSamplePlan(sampleId: String, sampleOrdinal: Int, seed: Long, stack: List<String>) {
        val record = JSONObject()
            .put("event", "sample_plan")
            .put("sample_id", sampleId)
            .put("sample_ordinal", sampleOrdinal)
            .put("seed", seed)
            .put("depth", stack.size)
            .put("start_title", stack.first())
            .put("stack_titles", stack)
        manifestWriter.write(record.toString())
        manifestWriter.write("\n")
        manifestWriter.flush()
    }

    private fun writeEvent(
        event: String,
        sampleOrdinal: Int,
        depth: Int,
        expectedTitle: String?,
        actualTitle: String?,
        note: String?
    ) {
        val record = JSONObject()
            .put("event", event)
            .put("sample_ordinal", sampleOrdinal)
            .put("depth", depth)
            .put("expected_title", expectedTitle)
            .put("actual_title", actualTitle)
            .put("activity", currentActivityName())
            .put("elapsed_realtime_ms", SystemClock.elapsedRealtime())
            .put("note", note)
        manifestWriter.write(record.toString())
        manifestWriter.write("\n")
        manifestWriter.flush()
    }

    private fun recordMismatch(
        sampleId: String,
        sampleOrdinal: Int,
        depth: Int,
        expectedTitle: String?,
        actualTitle: String?,
        phase: String
    ) {
        mismatches += JSONObject()
            .put("sample_id", sampleId)
            .put("sample_ordinal", sampleOrdinal)
            .put("depth", depth)
            .put("phase", phase)
            .put("expected_title", expectedTitle)
            .put("actual_title", actualTitle)
            .put("activity", currentActivityName())
    }

    private fun currentActivityName(): String? {
        return currentActivity.get()?.javaClass?.name
    }

    private fun argumentsJson(): JSONObject {
        val json = JSONObject()
        for (key in args.keySet()) {
            json.put(key, args.getString(key))
        }
        return json
    }

    private companion object {
        const val ARG_OUTPUT_DIR = "auditOutputDir"
        const val ARG_SEED = "auditSeed"
        const val ARG_START_INDEX = "auditStartIndex"
        const val ARG_START_COUNT = "auditStartCount"
        const val ARG_DEPTH = "auditDepth"
        const val ARG_RENDER_TIMEOUT_MS = "auditRenderTimeoutMs"
        const val ARG_WAIT_FOR_RENDER = "auditWaitForRender"
        const val ARG_MAX_RUNTIME_MS = "auditMaxRuntimeMs"
        const val DEFAULT_OUTPUT_DIR = "android-deep-navigation-stack-audit-2026-07-09"
        const val DEFAULT_SEED = 20260709L
        const val DEFAULT_START_COUNT = 10_000
        const val DEFAULT_DEPTH = 100
        const val DEFAULT_RENDER_TIMEOUT_MS = 30_000L
        const val ACTIVITY_TIMEOUT_MS = 10_000L
        const val POLL_MS = 100L

        val TITLE_CORPUS = listOf(
            "Abyssal whip",
            "Agility",
            "Amulet of glory",
            "Barrows",
            "Blood moon",
            "Chambers of Xeric",
            "Construction",
            "Cooking",
            "Desert Treasure II",
            "Dragon scimitar",
            "Dragon Slayer II",
            "Farming",
            "Firemaking",
            "Fishing",
            "Grand Exchange",
            "Herblore",
            "Hunter",
            "Inferno",
            "Kalphite Queen",
            "Monkey Madness I",
            "Perilous Moons",
            "Prayer",
            "Recipe for Disaster",
            "Slayer",
            "Smithing",
            "Tempoross",
            "The Blood Moon Rises",
            "The Blood Moon Rises/Quick guide",
            "Theatre of Blood",
            "Trailblazer Reloaded League",
            "Vorkath",
            "Wintertodt",
            "Woodcutting",
            "Zulrah",
        )
    }
}
