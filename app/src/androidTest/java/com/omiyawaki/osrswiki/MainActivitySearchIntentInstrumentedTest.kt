package com.omiyawaki.osrswiki

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.omiyawaki.osrswiki.search.SearchActivity
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySearchIntentInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun coldExplicitSearchActionUsesSearchActivityAndIsRecreationSafe() {
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .setAction(MainActivity.ACTION_NAVIGATE_TO_SEARCH)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        instrumentation.targetContext.startActivity(intent)
        val search = awaitExactlyOneResumed<SearchActivity>()
        assertEquals(1, liveActivities<SearchActivity>().size)
        assertEquals(1, liveActivities<MainActivity>().size)
        instrumentation.runOnMainSync { search.finish() }
        val originalMain = awaitExactlyOneResumed<MainActivity>()

        instrumentation.runOnMainSync { originalMain.recreate() }
        val recreatedMain = awaitExactlyOneResumed<MainActivity> { it !== originalMain }
        SystemClock.sleep(500)
        assertTrue("consumed action relaunched SearchActivity on recreation", liveActivities<SearchActivity>().isEmpty())
        instrumentation.runOnMainSync { recreatedMain.finish() }
    }

    @Test
    fun warmSingleTopSearchActionUsesOnNewIntentWithoutDuplicatingMainOrSearch() {
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitExactlyOneResumed<MainActivity>()
            scenario.onActivity { main ->
                main.startActivity(
                    Intent(main, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_NAVIGATE_TO_SEARCH)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }

            val search = awaitExactlyOneResumed<SearchActivity>()
            assertEquals(1, liveActivities<SearchActivity>().size)
            assertEquals(1, liveActivities<MainActivity>().size)
            instrumentation.runOnMainSync { search.finish() }
            awaitExactlyOneResumed<MainActivity>()
        }
    }

    private inline fun <reified T : Activity> awaitExactlyOneResumed(
        crossinline predicate: (T) -> Boolean = { true }
    ): T {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val resumed = activitiesIn(Stage.RESUMED).filterIsInstance<T>().filter(predicate)
            if (resumed.size == 1) return resumed.single()
            SystemClock.sleep(100)
        }
        throw AssertionError("Expected exactly one resumed ${T::class.java.simpleName}")
    }

    private inline fun <reified T : Activity> liveActivities(): Set<T> =
        listOf(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)
            .flatMap(::activitiesIn)
            .filterIsInstance<T>()
            .toSet()

    private fun activitiesIn(stage: Stage): Collection<Activity> {
        val result = AtomicReference<Collection<Activity>>(emptyList())
        instrumentation.runOnMainSync {
            result.set(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage))
        }
        return result.get()
    }
}
