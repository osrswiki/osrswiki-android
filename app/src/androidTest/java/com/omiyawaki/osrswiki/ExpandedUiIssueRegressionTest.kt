package com.omiyawaki.osrswiki

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.omiyawaki.osrswiki.about.AboutActivity
import com.omiyawaki.osrswiki.about.PrivacyPolicyActivity
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.donate.DonateActivity
import com.omiyawaki.osrswiki.feedback.FeedbackActivity
import com.omiyawaki.osrswiki.feedback.ReportIssueActivity
import com.omiyawaki.osrswiki.feedback.RequestFeatureActivity
import com.omiyawaki.osrswiki.search.SearchActivity
import com.omiyawaki.osrswiki.search.db.RecentSearch
import com.omiyawaki.osrswiki.settings.AppearanceSettingsActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ExpandedUiIssueRegressionTest {

    @After
    fun resetState() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        runBlocking {
            AppDatabase.instance.recentSearchDao().clearAll()
        }
    }

    @Test
    fun bottomSearchTabShowsSearchHistoryHubInsteadOfGenericHistoryTitle() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.nav_search)).perform(click())

            waitUntil("visible search history title") {
                try {
                    onView(
                        allOf(
                            withId(R.id.page_title),
                            withText(R.string.search_history_title),
                            isDisplayed()
                        )
                    ).check(matches(isDisplayed()))
                    true
                } catch (_: Throwable) {
                    false
                }
            }
        }
    }

    @Test
    fun searchToolbarHintClearDescriptionAndQuerySurviveRecreate() {
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            onView(withId(R.id.search_edit_text))
                .check(matches(withHint(R.string.page_toolbar_search_hint)))

            scenario.onActivity { activity ->
                activity.findViewById<TextInputEditText>(R.id.search_edit_text)
                    .setText("abyssal whip")
            }

            onView(withId(R.id.clear_search_button))
                .check(
                    matches(
                        allOf(
                            isDisplayed(),
                            withContentDescription(R.string.search_clear_content_description)
                        )
                    )
                )
            onView(withId(R.id.search_edit_text)).check(matches(withText("abyssal whip")))

            scenario.recreate()

            onView(withId(R.id.search_edit_text)).check(matches(withText("abyssal whip")))
            onView(withId(R.id.clear_search_button)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun aboutPrivacyPolicyLaunchesInAppActivity() {
        Intents.init()
        try {
            ActivityScenario.launch(AboutActivity::class.java).use {
                onView(withId(R.id.privacy_button)).perform(scrollTo(), click())

                intended(
                    allOf(
                        hasComponent(PrivacyPolicyActivity::class.java.name),
                        not(hasAction(Intent.ACTION_VIEW))
                    )
                )
                onView(withId(R.id.privacy_container)).check(matches(isDisplayed()))
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun feedbackFormsHideInvalidToolbarSubmitAndApplyNavigationInsets() {
        assertFeedbackFormRuntimeBehavior<ReportIssueActivity>()
        assertFeedbackFormRuntimeBehavior<RequestFeatureActivity>()
    }

    @Test
    fun moreSecondaryActivitiesExposeRuntimeUpNavigation() {
        assertToolbarNavigationIcon<AboutActivity>()
        assertToolbarNavigationIcon<DonateActivity>()
        assertToolbarNavigationIcon<FeedbackActivity>()
        assertToolbarNavigationIcon<PrivacyPolicyActivity>()
        assertToolbarNavigationIcon<AppearanceSettingsActivity>()
    }

    @Test
    fun darkThemeClearRecentSearchesDialogUsesSurfaceTextColor() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        runBlocking {
            AppDatabase.instance.recentSearchDao().clearAll()
            AppDatabase.instance.recentSearchDao().insert(
                RecentSearch(query = "zulrah", timestamp = System.currentTimeMillis())
            )
        }

        ActivityScenario.launch(SearchActivity::class.java).use {
            closeSoftKeyboard()
            waitUntil("seeded recent search") {
                try {
                    onView(withText("zulrah")).check(matches(isDisplayed()))
                    true
                } catch (_: Throwable) {
                    false
                }
            }

            onView(withId(R.id.buttonClearAll)).perform(click())

            assertDialogActionUsesSurfaceTextColor(R.string.dialog_option_clear)
            assertDialogActionUsesSurfaceTextColor(R.string.dialog_option_cancel)
        }
    }

    private inline fun <reified A : Activity> assertToolbarNavigationIcon() {
        ActivityScenario.launch(A::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                assertTrue("${A::class.java.simpleName} toolbar should be visible", toolbar.isShown)
                assertNotNull(
                    "${A::class.java.simpleName} should expose an up navigation icon",
                    toolbar.navigationIcon
                )
            }
        }
    }

    private inline fun <reified A : Activity> assertFeedbackFormRuntimeBehavior() {
        ActivityScenario.launch(A::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(
                    "${A::class.java.simpleName} bottom submit starts disabled",
                    activity.findViewById<View>(R.id.submit_button).isEnabled
                )
                assertFalse(
                    "${A::class.java.simpleName} scroll view must allow inset padding",
                    activity.findViewById<ScrollView>(R.id.content_scroll_view).clipToPadding
                )
                assertTrue(
                    "${A::class.java.simpleName} toolbar submit starts absent or hidden",
                    activity.findViewById<View>(R.id.action_submit)?.isShown != true
                )
            }

            dispatchNavigationInset<A>(scenario)

            scenario.onActivity { activity ->
                activity.findViewById<TextInputEditText>(R.id.title_input)
                    .setText("Search button overlaps")
                activity.findViewById<TextInputEditText>(R.id.description_input)
                    .setText("The submit button should be reachable above gesture navigation.")
            }

            waitUntil("${A::class.java.simpleName} submit controls enabled") {
                var enabled = false
                scenario.onActivity { activity ->
                    enabled = activity.findViewById<View>(R.id.submit_button).isEnabled &&
                        activity.findViewById<View>(R.id.action_submit)?.isShown == true &&
                        activity.findViewById<View>(R.id.action_submit)?.isEnabled == true
                }
                enabled
            }
        }
    }

    private inline fun <reified A : Activity> dispatchNavigationInset(
        scenario: ActivityScenario<A>
    ) {
        scenario.onActivity { activity ->
            val scrollView = activity.findViewById<ScrollView>(R.id.content_scroll_view)
            val container = activity.findViewById<View>(R.id.form_content_container)
            val baseFormPadding = activity.resources.getDimensionPixelSize(
                R.dimen.feedback_form_content_padding
            )
            val navigationInset = activity.resources.displayMetrics.density.let { density ->
                (48 * density).toInt()
            }
            val syntheticInsets = WindowInsetsCompat.Builder()
                .setInsets(
                    WindowInsetsCompat.Type.navigationBars(),
                    Insets.of(0, 0, 0, navigationInset)
                )
                .build()

            ViewCompat.dispatchApplyWindowInsets(scrollView, syntheticInsets)

            assertEquals(
                "${A::class.java.simpleName} should preserve base form padding plus navigation inset",
                baseFormPadding + navigationInset,
                container.paddingBottom
            )
        }
    }

    private fun assertDialogActionUsesSurfaceTextColor(textResId: Int) {
        onView(withText(textResId)).check { view, noViewFoundException ->
            if (noViewFoundException != null) throw noViewFoundException
            val textView = view as TextView
            val expectedColor = MaterialColors.getColor(
                textView,
                com.google.android.material.R.attr.colorOnSurface
            )
            assertEquals(expectedColor, textView.currentTextColor)
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
