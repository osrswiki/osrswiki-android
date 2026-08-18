package com.omiyawaki.osrswiki

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.lifecycle.Lifecycle
import com.omiyawaki.osrswiki.about.AboutActivity
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.donate.DonateActivity
import com.omiyawaki.osrswiki.feedback.FeedbackActivity
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesSearchActivity
import com.omiyawaki.osrswiki.search.SearchActivity
import com.omiyawaki.osrswiki.settings.AppearanceSettingsActivity
import com.omiyawaki.osrswiki.views.CustomBottomNavBar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@LargeTest
@RunWith(AndroidJUnit4::class)
class AndroidRcSmokeTest {

    @Before
    fun clearStateBeforeTest() {
        clearSeededState()
    }

    @After
    fun clearStateAfterTest() {
        clearSeededState()
    }

    @Test
    fun topLevelTabsExposeStableSurfacesAndBackReturnsHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForDisplayed(allOf(withId(R.id.page_title), withText(R.string.nav_news)))

            onView(allOf(withId(R.id.nav_saved), isDisplayed())).perform(click())
            waitForDisplayed(allOf(withId(R.id.page_title), withText(R.string.nav_saved)))
            waitForDisplayed(allOf(withId(R.id.search_text), withText(R.string.search_hint_saved_pages)))

            onView(allOf(withId(R.id.nav_search), isDisplayed())).perform(click())
            waitForDisplayed(allOf(withId(R.id.page_title), withText(R.string.search_history_title)))

            onView(allOf(withId(R.id.nav_map), isDisplayed())).perform(click())
            waitForDisplayed(
                withId(com.omiyawaki.osrswiki.undergroundmaps.R.id.osrs_floor_controls)
            )
            waitForDisplayed(
                withId(com.omiyawaki.osrswiki.undergroundmaps.R.id.osrs_underground_map)
            )
            waitUntil("integrated realm map activity is foreground") {
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .wait(
                        Until.hasObject(
                            By.pkg(ApplicationProvider.getApplicationContext<android.content.Context>().packageName)
                                .depth(0)
                        ),
                        100
                    )
            }

            androidx.test.espresso.Espresso.pressBack()
            waitForDisplayed(allOf(withId(R.id.page_title), withText(R.string.nav_news)))

            onView(allOf(withId(R.id.nav_more), isDisplayed())).perform(click())
            waitForDisplayed(withText(R.string.settings_category_appearance))
            waitForDisplayed(withText(R.string.menu_title_feedback))

            androidx.test.espresso.Espresso.pressBack()
            waitUntil("back selected Home tab") {
                var selectedItemId = 0
                it.onActivity { activity ->
                    selectedItemId = activity.findViewById<CustomBottomNavBar>(R.id.bottom_nav)
                        .selectedItemId
                }
                selectedItemId == R.id.nav_news
            }
        }
    }

    @Test
    fun topLevelTabsSurviveRecreateAndBackgroundForeground() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(allOf(withId(R.id.nav_more), isDisplayed())).perform(click())
            waitForDisplayed(withText(R.string.settings_category_appearance))

            scenario.recreate()
            waitForDisplayed(withText(R.string.settings_category_appearance))

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForDisplayed(withText(R.string.settings_category_appearance))
            waitUntil("restored activity window has input focus") {
                var hasFocus = false
                scenario.onActivity { activity -> hasFocus = activity.hasWindowFocus() }
                hasFocus
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            // Espresso can inject while ActivityScenario's synthetic CREATED -> RESUMED
            // transition still owns the input dispatcher even after Window.hasFocus() flips.
            // Invoke the restored view's click listener on the main thread: this test is about
            // listener/state restoration, while physical input routing is covered separately.
            scenario.onActivity { activity ->
                val search = activity.findViewById<android.view.View>(R.id.nav_search)
                check(search.isShown && search.isEnabled)
                check(search.performClick())
            }
            waitForDisplayed(allOf(withId(R.id.page_title), withText(R.string.search_history_title)))

            scenario.recreate()
            waitForDisplayed(allOf(withId(R.id.page_title), withText(R.string.search_history_title)))
        }
    }

    @Test
    fun searchEntryPointsRouteToExpectedActivities() {
        Intents.init()
        try {
            stubActivity(SearchActivity::class.java)
            stubActivity(SavedPagesSearchActivity::class.java)

            ActivityScenario.launch(MainActivity::class.java).use {
                onView(allOf(withId(R.id.search_container), isDisplayed())).perform(click())
                intended(hasComponent(SearchActivity::class.java.name))

                onView(allOf(withId(R.id.nav_saved), isDisplayed())).perform(click())
                waitForDisplayed(allOf(withId(R.id.page_title), withText(R.string.nav_saved)))
                onView(allOf(withId(R.id.search_container), isDisplayed())).perform(click())
                intended(hasComponent(SavedPagesSearchActivity::class.java.name))

                onView(allOf(withId(R.id.nav_search), isDisplayed())).perform(click())
                waitForDisplayed(allOf(withId(R.id.page_title), withText(R.string.search_history_title)))
                onView(allOf(withId(R.id.nav_search), isDisplayed())).perform(click())
                intended(hasComponent(SearchActivity::class.java.name))
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun moreMenuRoutesToAllSecondaryScreens() {
        Intents.init()
        try {
            stubActivity(AppearanceSettingsActivity::class.java)
            stubActivity(DonateActivity::class.java)
            stubActivity(AboutActivity::class.java)
            stubActivity(FeedbackActivity::class.java)

            ActivityScenario.launch(MainActivity::class.java).use {
                onView(allOf(withId(R.id.nav_more), isDisplayed())).perform(click())
                waitForDisplayed(withText(R.string.settings_category_appearance))

                onView(allOf(withText(R.string.settings_category_appearance), isDisplayed())).perform(click())
                intended(hasComponent(AppearanceSettingsActivity::class.java.name))

                onView(allOf(withText(R.string.menu_title_donate), isDisplayed())).perform(click())
                intended(hasComponent(DonateActivity::class.java.name))

                onView(allOf(withText(R.string.menu_title_about), isDisplayed())).perform(click())
                intended(hasComponent(AboutActivity::class.java.name))

                onView(allOf(withText(R.string.menu_title_feedback), isDisplayed())).perform(click())
                intended(hasComponent(FeedbackActivity::class.java.name))
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun seededBrowsingHistoryCanBeClearedThroughSearchTabUi() {
        runBlocking {
            AppDatabase.instance.historyEntryDao().insertEntry(
                HistoryEntry(
                    wikiUrl = "https://oldschool.runescape.wiki/w/Lumbridge",
                    displayText = "Lumbridge",
                    pageId = 123,
                    apiPath = "Lumbridge",
                    timestamp = Date(),
                    source = HistoryEntry.SOURCE_SEARCH,
                    snippet = "A city in Misthalin.",
                    thumbnailUrl = null
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(allOf(withId(R.id.nav_search), isDisplayed())).perform(click())
            waitForDisplayed(withText("Lumbridge"))

            clickVisibleViewWithDescription(it, R.string.action_clear_history)
            clickObjectByText("Clear All")

            waitUntil("history table empty") {
                runBlocking { AppDatabase.instance.historyEntryDao().getAllEntries().first().isEmpty() }
            }
            waitForObjectText(R.string.history_empty_message)
        }
    }

    private fun clearSeededState() {
        runBlocking {
            AppDatabase.instance.historyEntryDao().deleteAllEntries()
            AppDatabase.instance.recentSearchDao().clearAll()
        }
    }

    private fun stubActivity(activityClass: Class<out Activity>) {
        intending(hasComponent(activityClass.name))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()))
    }

    private fun clickVisibleViewWithDescription(
        scenario: ActivityScenario<MainActivity>,
        descriptionResId: Int
    ) {
        scenario.onActivity { activity ->
            val description = activity.getString(descriptionResId)
            val target = findVisibleViewWithDescription(activity.window.decorView, description)
                ?: throw AssertionError("No visible view found with description $description")
            assertEquals(true, target.performClick())
        }
    }

    private fun findVisibleViewWithDescription(
        view: android.view.View,
        description: String
    ): android.view.View? {
        if (view.isShown && view.contentDescription?.toString() == description) {
            return view
        }
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                val match = findVisibleViewWithDescription(view.getChildAt(index), description)
                if (match != null) return match
            }
        }
        return null
    }

    private fun clickObjectByText(text: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val target = device.wait(Until.findObject(By.text(text)), 5_000)
            ?: throw AssertionError("Timed out waiting for object with text $text")
        target.click()
    }

    private fun waitForObjectText(textResId: Int) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val text = context.getString(textResId)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val target = device.wait(Until.findObject(By.text(text)), 5_000)
        if (target == null) {
            throw AssertionError("Timed out waiting for object with text $text")
        }
    }

    private fun waitForDisplayed(matcher: org.hamcrest.Matcher<android.view.View>) {
        waitUntil("displayed view matching $matcher") {
            try {
                onView(matcher).check(matches(isDisplayed()))
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun waitUntil(label: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition()) return
            } catch (error: Throwable) {
                lastError = error
            }
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for $label", lastError)
    }
}
