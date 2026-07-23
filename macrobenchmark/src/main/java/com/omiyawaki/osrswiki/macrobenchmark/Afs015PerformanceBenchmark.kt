package com.omiyawaki.osrswiki.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@OptIn(ExperimentalMacrobenchmarkApi::class)
@RunWith(AndroidJUnit4::class)
class Afs015PerformanceBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val iterations: Int
        get() = InstrumentationRegistry.getArguments()
            .getString("afs015.iterations")
            ?.toIntOrNull()
            ?.coerceAtLeast(1)
            ?: 3

    @Test
    fun coldLaunchHome() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = iterations,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Ignore(),
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait()
        waitForHome()
    }

    @Test
    fun warmLaunchHome() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = iterations,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Ignore(),
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait()
        waitForHome()
    }

    @Test
    fun bottomTabSwitches() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = iterations,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Ignore(),
        setupBlock = {
            launchHomeAndWait()
        }
    ) {
        tapRes("nav_saved")
        waitForText("Saved")
        tapRes("nav_search")
        waitForText("Search History")
        tapRes("nav_map")
        waitForRes("map_view")
        tapRes("nav_more")
        waitForText("Appearance")
        tapRes("nav_news")
        waitForHome()
    }

    @Test
    fun searchOpenAndLocalQueryEntry() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = iterations,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Ignore(),
        setupBlock = {
            launchHomeAndWait()
        }
    ) {
        tapRes("search_container")
        val field = waitForRes("search_edit_text")
        field.setText("zulrah")
        waitForRes("clear_search_button")
    }

    @Test
    fun savedSearchEmptyNoResults() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = iterations,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Ignore(),
        setupBlock = {
            launchHomeAndWait()
            tapRes("nav_saved")
            waitForText("Saved")
        }
    ) {
        tapRes("search_container")
        val field = waitForRes("search_edit_text")
        field.setText("afs015-empty")
        waitForTextContains("No saved pages found")
    }

    @Test
    fun articleOpenReadiness() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = iterations,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Ignore(),
        setupBlock = {
            launchHomeAndWait()
            tapRes("search_container")
            val field = waitForRes("search_edit_text")
            field.setText("Lumbridge")
            waitForSearchResultTitle("Lumbridge", ARTICLE_TIMEOUT_MS)
        }
    ) {
        waitForSearchResultTitle("Lumbridge", ARTICLE_TIMEOUT_MS).click()
        waitForRes("page_web_view", ARTICLE_TIMEOUT_MS)
        waitForRes("page_action_contents", DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun mapPanZoomFloor() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = iterations,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Ignore(),
        setupBlock = {
            launchHomeAndWait()
        }
    ) {
        tapRes("nav_map")
        val map = waitForRes("map_view", MAP_TIMEOUT_MS)
        waitForRes("floor_controls", MAP_TIMEOUT_MS)
        val bounds = map.visibleBounds
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        device.swipe(centerX, centerY, centerX - bounds.width() / 4, centerY, 12)
        device.click(centerX, centerY)
        device.click(centerX, centerY)
        tapRes("floor_control_up")
        waitForText("1")
        tapRes("floor_control_down")
        waitForText("0")
    }

    private fun MacrobenchmarkScope.launchHomeAndWait() {
        pressHome()
        startActivityAndWait()
        waitForHome()
    }

    private fun waitForHome() {
        waitForRes("nav_news")
        waitForRes("search_container")
    }

    private fun tapRes(resourceName: String) {
        waitForRes(resourceName).click()
        device.waitForIdle()
    }

    private fun waitForRes(
        resourceName: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): UiObject2 = waitForObject(By.res(TARGET_PACKAGE, resourceName), "resource $resourceName", timeoutMs)

    private fun waitForText(
        text: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): UiObject2 = waitForObject(By.text(text), "text $text", timeoutMs)

    private fun waitForTextContains(
        text: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): UiObject2 = waitForObject(By.textContains(text), "text containing $text", timeoutMs)

    private fun waitForSearchResultTitle(
        text: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): UiObject2 = waitForObject(
        By.res(TARGET_PACKAGE, "search_item_title").text(text),
        "search result title $text",
        timeoutMs
    )

    private fun waitForObject(
        selector: BySelector,
        label: String,
        timeoutMs: Long
    ): UiObject2 {
        return device.wait(Until.findObject(selector), timeoutMs)
            ?: throw AssertionError("Timed out waiting for $label after ${timeoutMs}ms")
    }

    private companion object {
        const val TARGET_PACKAGE = "com.omiyawaki.osrswiki"
        const val DEFAULT_TIMEOUT_MS = 10_000L
        const val ARTICLE_TIMEOUT_MS = 30_000L
        const val MAP_TIMEOUT_MS = 20_000L
    }
}
