package com.omiyawaki.osrswiki.page

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.omiyawaki.osrswiki.search.SearchActivity
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class osrsFirstViewGloryTimingInstrumentedTest {
    @Test
    fun gloryFirstViewTimingProtocol() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Set OSRS_GLORY_TIMING=1 to run live Amulet of Glory first-view timing.",
            args.getString("OSRS_GLORY_TIMING") == "1" ||
                System.getenv("OSRS_GLORY_TIMING") == "1"
        )

        openGlory(disablePaintPrewarm = true, dwellMs = 0L)
        openGlory(disablePaintPrewarm = false, dwellMs = 0L)
        openGlory(disablePaintPrewarm = false, dwellMs = 15_000L)
    }

    private fun openGlory(disablePaintPrewarm: Boolean, dwellMs: Long) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext
        val pkg = context.packageName
        Log.d("PageLoadTrace", "GloryTiming start disable=$disablePaintPrewarm dwellMs=$dwellMs")

        val intent = SearchActivity.newIntent(
            context = context,
            query = "Amulet of glory",
            disableFirstViewPaintPrewarm = disablePaintPrewarm
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.waitForIdle()
        device.wait(Until.findObject(By.res(pkg, "search_edit_text")), 30_000)
            ?: error("Search field should appear")
        device.pressEnter()
        SystemClock.sleep(2_000)
        val glorySelector = By.res(pkg, "search_item_title").text("Amulet of glory")
        device.wait(Until.findObject(glorySelector), 20_000)
            ?: error("Exact Amulet of glory result should become tappable")
        device.wait(
            Until.findObject(By.res(pkg, "search_item_title").textContains("Amulet of Glory (")),
            12_000
        ) ?: error("Search results list should include Glory disambiguation neighbors")
        if (dwellMs > 0L) {
            SystemClock.sleep(dwellMs)
        } else {
            SystemClock.sleep(500)
        }
        Log.d("PageLoadTrace", "GloryTiming tap disable=$disablePaintPrewarm dwellMs=$dwellMs")
        clickFresh(device, glorySelector)
        SystemClock.sleep(10_000)
        Log.d("PageLoadTrace", "GloryTiming done disable=$disablePaintPrewarm dwellMs=$dwellMs")
    }

    private fun clickFresh(device: UiDevice, selector: androidx.test.uiautomator.BySelector) {
        var lastError: Exception? = null
        repeat(4) {
            val target = device.wait(Until.findObject(selector), 8_000)
                ?: error("Exact Amulet of glory result should remain tappable")
            try {
                val row = target.parent ?: target
                row.click()
                return
            } catch (error: StaleObjectException) {
                lastError = error
                SystemClock.sleep(300)
            }
        }
        throw lastError ?: IllegalStateException("Glory row click failed")
    }
}
