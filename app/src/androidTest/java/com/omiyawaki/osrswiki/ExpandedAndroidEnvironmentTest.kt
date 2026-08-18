package com.omiyawaki.osrswiki

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isFocusable
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.lifecycle.Lifecycle
import com.omiyawaki.osrswiki.about.AboutActivity
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.donate.DonateActivity
import com.omiyawaki.osrswiki.donate.DonationBillingGateway
import com.omiyawaki.osrswiki.donate.DonationBillingGatewayFactory
import com.omiyawaki.osrswiki.donate.DonationBillingGatewayRegistry
import com.omiyawaki.osrswiki.donate.DonationBillingLaunchResult
import com.omiyawaki.osrswiki.donate.DonationBillingListener
import com.omiyawaki.osrswiki.donate.DonationProductIds
import com.omiyawaki.osrswiki.feedback.FeedbackActivity
import com.omiyawaki.osrswiki.feedback.FeedbackSubmissionGateway
import com.omiyawaki.osrswiki.feedback.FeedbackSubmissionGatewayRegistry
import com.omiyawaki.osrswiki.feedback.ReportIssueActivity
import com.omiyawaki.osrswiki.feedback.RequestFeatureActivity
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.page.PageActivity
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesSearchActivity
import com.omiyawaki.osrswiki.search.SearchActivity
import com.omiyawaki.osrswiki.search.db.RecentSearch
import com.omiyawaki.osrswiki.test.ExpandedQaMapActivity
import com.omiyawaki.osrswiki.test.ExpandedQaVoiceActivity
import com.omiyawaki.osrswiki.ui.map.MapFragment
import com.omiyawaki.osrswiki.ui.map.osrsMapDefaultView
import com.omiyawaki.osrswiki.util.SpeechRecognitionGateway
import com.omiyawaki.osrswiki.util.SpeechRecognitionGatewayRegistry
import com.omiyawaki.osrswiki.util.SpeechRecognitionHandle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@LargeTest
@RunWith(AndroidJUnit4::class)
class ExpandedAndroidEnvironmentTest {

    @get:Rule
    val recordAudioPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @After
    fun resetRegistries() {
        SpeechRecognitionGatewayRegistry.reset()
        DonationBillingGatewayRegistry.reset()
        FeedbackSubmissionGatewayRegistry.reset()
        runBlocking {
            AppDatabase.instance.historyEntryDao().deleteAllEntries()
            AppDatabase.instance.recentSearchDao().clearAll()
        }
    }

    @Test
    fun voiceSearchCanInjectSuccessfulTranscript() {
        SpeechRecognitionGatewayRegistry.gateway = FakeSpeechGateway(finalResult = "zulrah")

        ActivityScenario.launch(ExpandedQaVoiceActivity::class.java).use {
            onView(withId(R.id.voice_search_button)).perform(click())
            onView(withId(R.id.search_edit_text)).check(matches(withText("zulrah")))
        }
    }

    @Test
    fun voiceSearchExposesListeningProcessingAndFinalStates() {
        SpeechRecognitionGatewayRegistry.gateway = FakeSpeechGateway(
            finalResult = "zulrah",
            partialResult = "zul",
            endOfSpeechDelayMs = 500,
            resultDelayMs = 500
        )

        ActivityScenario.launch(ExpandedQaVoiceActivity::class.java).use {
            onView(withId(R.id.voice_search_button)).perform(click())
            waitUntil("voice listening state") {
                try {
                    onView(withId(R.id.status_text)).check(matches(withText("LISTENING")))
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            onView(withId(R.id.search_edit_text)).check(matches(withText("zul")))
            waitUntil("voice processing state") {
                try {
                    onView(withId(R.id.status_text)).check(matches(withText("PROCESSING")))
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            waitUntil("voice final result") {
                try {
                    onView(withId(R.id.search_edit_text)).check(matches(withText("zulrah")))
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            onView(withId(R.id.status_text)).check(matches(withText("IDLE")))
        }
    }

    @Test
    fun voiceSearchCanDriveUnavailableAndNoMatchStates() {
        SpeechRecognitionGatewayRegistry.gateway = FakeSpeechGateway(available = false)

        ActivityScenario.launch(ExpandedQaVoiceActivity::class.java).use {
            onView(withId(R.id.voice_search_button)).perform(click())
            onView(withId(R.id.search_edit_text)).check(matches(withText("")))
            onView(withId(R.id.status_text))
                .check(matches(withText("ERROR:${targetString(R.string.voice_search_not_available)}")))
        }

        SpeechRecognitionGatewayRegistry.gateway = FakeSpeechGateway(
            finalResult = null,
            errorCode = SpeechRecognizer.ERROR_NO_MATCH
        )

        ActivityScenario.launch(ExpandedQaVoiceActivity::class.java).use {
            onView(withId(R.id.voice_search_button)).perform(click())
            onView(withId(R.id.search_edit_text)).check(matches(withText("")))
            onView(withId(R.id.status_text))
                .check(matches(withText("ERROR:${targetString(R.string.voice_search_no_results)}")))
        }
    }

    @Test
    fun productionSearchScreensCanInsertFakeVoiceTranscript() {
        SpeechRecognitionGatewayRegistry.gateway = FakeSpeechGateway(finalResult = "abyssal whip")
        ActivityScenario.launch(SearchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.voice_search_button).performClick()
            }
            waitUntil("SearchActivity voice transcript") {
                try {
                    onView(withId(R.id.search_edit_text)).check(matches(withText("abyssal whip")))
                    true
                } catch (_: Throwable) {
                    false
                }
            }
        }

        SpeechRecognitionGatewayRegistry.gateway = FakeSpeechGateway(finalResult = "dragon boots")
        ActivityScenario.launch(SavedPagesSearchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.voice_search_button).performClick()
            }
            waitUntil("SavedPagesSearchActivity voice transcript") {
                try {
                    onView(withId(R.id.search_edit_text)).check(matches(withText("dragon boots")))
                    true
                } catch (_: Throwable) {
                    false
                }
            }
        }
    }

    @Test
    fun homeVoiceEntryPointStartsSearchWithFakeTranscript() {
        assertVoiceIntentFromMainTab(
            selectedTabId = R.id.nav_news,
            destination = SearchActivity::class.java,
            transcript = "home transcript"
        )
    }

    @Test
    fun historyVoiceEntryPointStartsSearchWithFakeTranscript() {
        assertVoiceIntentFromMainTab(
            selectedTabId = R.id.nav_search,
            destination = SearchActivity::class.java,
            transcript = "history transcript"
        )
    }

    @Test
    fun savedPagesVoiceEntryPointStartsSavedSearchWithFakeTranscript() {
        assertVoiceIntentFromMainTab(
            selectedTabId = R.id.nav_saved,
            destination = SavedPagesSearchActivity::class.java,
            transcript = "saved transcript"
        )
    }

    @Test
    fun pageToolbarVoiceEntryPointStartsSearchWithFakeTranscript() {
        val transcript = "toolbar transcript"
        SpeechRecognitionGatewayRegistry.gateway = FakeSpeechGateway(finalResult = transcript)
        Intents.init()
        try {
            stubActivity(SearchActivity::class.java)

            ActivityScenario.launch<PageActivity>(
                PageActivity.newIntent(
                    context = InstrumentationRegistry.getInstrumentation().targetContext,
                    pageTitle = "Varrock",
                    pageId = null,
                    source = HistoryEntry.SOURCE_INTERNAL_LINK
                )
            ).use {
                onView(withId(R.id.toolbar_voice_search_button)).perform(click())
                assertVoiceIntent(SearchActivity::class.java, transcript)
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun billingGatewayCanDriveSuccessfulDonation() {
        val fakeFactory = FakeDonationBillingFactory(
            products = DonationProductIds.all.toSet(),
            outcome = FakeDonationOutcome.SUCCESS
        )
        DonationBillingGatewayRegistry.factory = fakeFactory

        ActivityScenario.launch(DonateActivity::class.java).use {
            onView(withId(R.id.chip_amount_1)).perform(scrollTo(), click())
            onView(withId(R.id.donate_button)).check(matches(isEnabled()))
            onView(withId(R.id.donate_button)).perform(scrollTo(), click())

            waitUntil("fake billing launch") {
                fakeFactory.gateway?.launchedProductId == DonationProductIds.DONATE_1
            }
            onView(withText(R.string.donate_success_title)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun billingGatewayCanDriveCancellationAndErrorStates() {
        val cancelFactory = FakeDonationBillingFactory(
            products = DonationProductIds.all.toSet(),
            outcome = FakeDonationOutcome.CANCEL
        )
        DonationBillingGatewayRegistry.factory = cancelFactory

        ActivityScenario.launch(DonateActivity::class.java).use {
            onView(withId(R.id.chip_amount_5)).perform(scrollTo(), click())
            onView(withId(R.id.donate_button)).perform(scrollTo(), click())
            waitUntil("fake billing cancel") {
                cancelFactory.gateway?.launchedProductId == DonationProductIds.DONATE_5
            }
            onView(withId(R.id.donate_button)).check(matches(isEnabled()))
        }

        val errorFactory = FakeDonationBillingFactory(
            products = DonationProductIds.all.toSet(),
            outcome = FakeDonationOutcome.ERROR
        )
        DonationBillingGatewayRegistry.factory = errorFactory

        ActivityScenario.launch(DonateActivity::class.java).use {
            onView(withId(R.id.chip_amount_10)).perform(scrollTo(), click())
            onView(withId(R.id.donate_button)).perform(scrollTo(), click())
            onView(withText(containsString("Test purchase failure")))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun billingGatewayCanDriveNoProductsAndPendingStates() {
        DonationBillingGatewayRegistry.factory = FakeDonationBillingFactory(
            products = emptySet(),
            outcome = FakeDonationOutcome.SUCCESS
        )

        ActivityScenario.launch(DonateActivity::class.java).use {
            onView(withId(R.id.status_text))
                .check(matches(withText("No donation options available")))
            assertDisabledDonationControl(R.id.chip_amount_1)
            assertDisabledDonationControl(R.id.chip_amount_5)
            assertDisabledDonationControl(R.id.chip_amount_10)
            assertDisabledDonationControl(R.id.chip_amount_25)
            assertDisabledDonationControl(R.id.donate_button)
        }

        DonationBillingGatewayRegistry.factory = FakeDonationBillingFactory(
            products = DonationProductIds.all.toSet(),
            outcome = FakeDonationOutcome.PENDING
        )

        ActivityScenario.launch(DonateActivity::class.java).use {
            onView(withId(R.id.chip_amount_25)).perform(scrollTo(), click())
            onView(withId(R.id.donate_button)).perform(scrollTo(), click())
            onView(withId(R.id.status_text))
                .check(matches(withText("Purchase is pending...")))
        }
    }

    @Test
    fun donationCallbacksAfterDestroyedViewAreIgnored() {
        val fakeFactory = DeferredDonationBillingFactory()
        DonationBillingGatewayRegistry.factory = fakeFactory

        ActivityScenario.launch(DonateActivity::class.java).use { scenario ->
            waitUntil("deferred billing gateway created") {
                fakeFactory.gateway != null
            }

            scenario.moveToState(Lifecycle.State.DESTROYED)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                fakeFactory.gateway?.listener?.onBillingReady(DonationProductIds.all.toSet())
                fakeFactory.gateway?.listener?.onPurchasePending()
                fakeFactory.gateway?.listener?.onPurchaseSuccess(DonationProductIds.DONATE_1)
                fakeFactory.gateway?.listener?.onPurchaseError("late failure")
                fakeFactory.gateway?.listener?.onBillingDisconnected()
            }
        }
    }

    private fun assertDisabledDonationControl(viewId: Int) {
        onView(withId(viewId)).perform(scrollTo())
            .check(matches(not(isEnabled())))
            .check(matches(not(isClickable())))
            .check(matches(not(isFocusable())))
    }

    @Test
    fun feedbackSubmissionGatewayCanDriveSafeSuccessAndErrorStates() {
        val successGateway = FakeFeedbackSubmissionGateway(
            reportResult = Result.success("Captured bug report")
        )
        FeedbackSubmissionGatewayRegistry.gateway = successGateway

        ActivityScenario.launch(ReportIssueActivity::class.java).use {
            onView(withId(R.id.title_input)).perform(scrollTo(), replaceText("Fake bug report"))
            onView(withId(R.id.description_input)).perform(scrollTo(), replaceText("Steps to reproduce without network side effects."))
            closeSoftKeyboard()
            onView(withId(R.id.submit_button)).perform(scrollTo(), click())

            waitUntil("fake report submission captured") {
                successGateway.submissions.singleOrNull()?.let {
                    it.kind == FeedbackSubmissionKind.REPORT &&
                        it.title == "Fake bug report" &&
                        it.description == "Steps to reproduce without network side effects."
                } == true
            }
            onView(withText(R.string.feedback_success_title)).check(matches(isDisplayed()))
            onView(withText("Captured bug report")).check(matches(isDisplayed()))
        }

        val errorGateway = FakeFeedbackSubmissionGateway(
            featureResult = Result.failure(Exception("Test feature gateway failure"))
        )
        FeedbackSubmissionGatewayRegistry.gateway = errorGateway

        ActivityScenario.launch(RequestFeatureActivity::class.java).use {
            onView(withId(R.id.title_input)).perform(scrollTo(), replaceText("Fake feature request"))
            onView(withId(R.id.description_input)).perform(scrollTo(), replaceText("Feature details for fake gateway coverage."))
            onView(withId(R.id.use_case_input)).perform(scrollTo(), replaceText("Optional fake use case."))
            closeSoftKeyboard()
            onView(withId(R.id.submit_button)).perform(scrollTo(), click())

            waitUntil("fake feature submission captured") {
                errorGateway.submissions.singleOrNull()?.let {
                    it.kind == FeedbackSubmissionKind.FEATURE &&
                        it.title == "Fake feature request" &&
                        it.description.contains("Feature details for fake gateway coverage.") &&
                        it.description.contains("**Use Case:**") &&
                        it.description.contains("Optional fake use case.")
                } == true
            }
            onView(withText(R.string.feedback_error_title)).check(matches(isDisplayed()))
            onView(withText("Test feature gateway failure")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun patreonButtonCanBeVerifiedAsOutboundIntentWithoutOpeningBrowser() {
        DonationBillingGatewayRegistry.factory = FakeDonationBillingFactory(
            products = emptySet(),
            outcome = FakeDonationOutcome.SUCCESS
        )
        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_VIEW))
                .respondWith(android.app.Instrumentation.ActivityResult(Activity.RESULT_OK, null))

            ActivityScenario.launch(DonateActivity::class.java).use {
                onView(withId(R.id.wiki_donate_button)).perform(scrollTo(), click())
                intended(
                    allOf(
                        hasAction(Intent.ACTION_VIEW),
                        hasData(Uri.parse("https://www.patreon.com/runescapewiki"))
                    )
                )
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun feedbackRateAppUsesExactPlayStoreIntentWithoutOpeningStore() {
        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_VIEW))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

            ActivityScenario.launch(FeedbackActivity::class.java).use {
                onView(withId(R.id.rate_app_button)).perform(scrollTo(), click())
                intended(
                    allOf(
                        hasAction(Intent.ACTION_VIEW),
                        hasData(Uri.parse("market://details?id=com.omiyawaki.osrswiki"))
                    )
                )
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun pageOverflowExternalActionsUseExactWikiUrlsWithoutOpeningBrowser() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_VIEW))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

            ActivityScenario.launch<PageActivity>(
                PageActivity.newIntent(
                    context = context,
                    pageTitle = "Grand Exchange",
                    pageId = null,
                    source = HistoryEntry.SOURCE_SEARCH
                )
            ).use {
                onView(withId(R.id.toolbar_overflow_menu_button)).perform(click())
                onView(withText("Open in browser")).perform(click())
                intended(
                    allOf(
                        hasAction(Intent.ACTION_VIEW),
                        hasData(Uri.parse("https://oldschool.runescape.wiki/w/Grand_Exchange"))
                    )
                )
            }

            ActivityScenario.launch<PageActivity>(
                PageActivity.newIntent(
                    context = context,
                    pageTitle = "Grand Exchange",
                    pageId = null,
                    source = HistoryEntry.SOURCE_SEARCH
                )
            ).use {
                onView(withId(R.id.toolbar_overflow_menu_button)).perform(click())
                onView(withText("View page history")).perform(click())
                intended(
                    allOf(
                        hasAction(Intent.ACTION_VIEW),
                        hasData(Uri.parse("https://oldschool.runescape.wiki/w/Special:History/Grand_Exchange"))
                    )
                )
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun aboutExternalCreditLinksUseExactUrlsWithoutOpeningBrowser() {
        val links = listOf(
            R.id.osrs_button to "https://oldschool.runescape.com/",
            R.id.wiki_button to "https://oldschool.runescape.wiki/",
            R.id.openrs2_button to "https://archive.openrs2.org/",
            R.id.maplibre_button to "https://maplibre.org/",
            R.id.wikipedia_button to "https://www.wikipedia.org/"
        )

        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_VIEW))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

            ActivityScenario.launch(AboutActivity::class.java).use {
                links.forEach { (buttonId, url) ->
                    onView(withId(buttonId)).perform(scrollTo(), click())
                    intended(
                        allOf(
                            hasAction(Intent.ACTION_VIEW),
                            hasData(Uri.parse(url))
                        )
                    )
                }
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun pageToolbarNavigateUpAndSystemBackFinishArticleActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        ActivityScenario.launch<PageActivity>(
            PageActivity.newIntent(
                context = context,
                pageTitle = "Lumbridge",
                pageId = null,
                source = HistoryEntry.SOURCE_SEARCH
            )
        ).use { scenario ->
            onView(withContentDescription("Navigate up")).perform(click())
            waitUntil("page activity finished after toolbar Navigate up") {
                scenario.state == Lifecycle.State.DESTROYED
            }
        }

        ActivityScenario.launch<PageActivity>(
            PageActivity.newIntent(
                context = context,
                pageTitle = "Varrock",
                pageId = null,
                source = HistoryEntry.SOURCE_SEARCH
            )
        ).use { scenario ->
            pressBackUnconditionally()
            waitUntil("page activity finished after system Back") {
                scenario.state == Lifecycle.State.DESTROYED
            }
        }
    }

    @Test
    fun pageContentsDrawerBackClosesDrawerBeforeFinishingActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        ActivityScenario.launch<PageActivity>(
            PageActivity.newIntent(
                context = context,
                pageTitle = "Lumbridge",
                pageId = null,
                source = HistoryEntry.SOURCE_SEARCH
            )
        ).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                activity.showContents()
            }

            waitUntil("contents drawer open") {
                var isOpen = false
                scenario.onActivity { activity ->
                    isOpen = activity.isContentsDrawerOpen()
                }
                isOpen
            }

            pressBackUnconditionally()

            waitUntil("contents drawer closed without finishing page") {
                var isClosed = false
                scenario.onActivity { activity ->
                    isClosed = !activity.isContentsDrawerOpen()
                }
                isClosed && scenario.state.isAtLeast(Lifecycle.State.STARTED)
            }
        }
    }

    @Test
    fun clearRecentSearchesDeletesSeededDataThroughUi() {
        runBlocking {
            AppDatabase.instance.recentSearchDao().clearAll()
            AppDatabase.instance.recentSearchDao().insert(
                RecentSearch(query = "abyssal whip", timestamp = System.currentTimeMillis())
            )
        }

        ActivityScenario.launch(SearchActivity::class.java).use {
            closeSoftKeyboard()
            waitUntil("recent search visible") {
                try {
                    onView(withText("abyssal whip")).check(matches(isDisplayed()))
                    true
                } catch (_: Throwable) {
                    false
                }
            }

            onView(withId(R.id.buttonClearAll)).perform(click())
            onView(withText(R.string.dialog_option_clear)).perform(click())

            waitUntil("recent searches cleared") {
                runBlocking { AppDatabase.instance.recentSearchDao().getAll().first().isEmpty() }
            }
            onView(withText(R.string.no_recent_searches)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun clearHistoryDeletesSeededDataThroughUi() {
        runBlocking {
            AppDatabase.instance.historyEntryDao().deleteAllEntries()
            AppDatabase.instance.historyEntryDao().insertEntry(
                HistoryEntry(
                    wikiUrl = "https://oldschool.runescape.wiki/w/Lumbridge",
                    displayText = "Lumbridge",
                    pageId = 1,
                    apiPath = "Lumbridge",
                    source = HistoryEntry.SOURCE_SEARCH
                )
            )
            AppDatabase.instance.historyEntryDao().insertEntry(
                HistoryEntry(
                    wikiUrl = "https://oldschool.runescape.wiki/w/Varrock",
                    displayText = "Varrock",
                    pageId = 2,
                    apiPath = "Varrock",
                    source = HistoryEntry.SOURCE_INTERNAL_LINK
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(allOf(withId(R.id.nav_search), isDisplayed())).perform(click())
            waitUntil("seeded history visible") {
                try {
                    onView(withText("Lumbridge")).check(matches(isDisplayed()))
                    true
                } catch (_: Throwable) {
                    false
                }
            }

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val clearHistoryButton = device.findObjects(By.res(BuildConfig.APPLICATION_ID, "clear_all_button"))
                .firstOrNull { it.visibleBounds.width() > 0 && it.visibleBounds.height() > 0 }
                ?: error("Clear history button should be available to accessibility")
            val clearHistoryBounds = clearHistoryButton.visibleBounds
            device.click(clearHistoryBounds.centerX(), (clearHistoryBounds.bottom - 1).coerceAtLeast(0))
            device.waitForIdle()
            val clearAllDialogButton = device.wait(Until.findObject(By.text("Clear All")), 5_000)
                ?: error("Clear All confirmation button should be visible")
            clearAllDialogButton.click()
            device.waitForIdle()

            waitUntil("history entries cleared") {
                runBlocking { AppDatabase.instance.historyEntryDao().getAllEntries().first().isEmpty() }
            }
            onView(allOf(withId(R.id.empty_state_container), isDisplayed())).check(matches(isDisplayed()))
            onView(withText(R.string.history_empty_message))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }

    @Test
    fun mapEnvironmentSupportsMultiPointerGestureAndCameraAssertion() {
        ActivityScenario.launch(ExpandedQaMapActivity::class.java).use { scenario ->
            val initialZoom = AtomicReference<Double?>()
            waitUntil("map ready") {
                scenario.onActivity { activity ->
                    initialZoom.set(activity.mapFragment()?.debugStateForTesting()?.zoom)
                }
                initialZoom.get() != null
            }

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val mapObject = device.findObject(By.res(BuildConfig.APPLICATION_ID, "map_view"))
            assertNotNull("map_view should be visible for gesture targeting", mapObject)
            waitUntil("nonblank map screenshot") {
                try {
                    assertMapScreenshotHasVisibleContent(device, mapObject.visibleBounds)
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            mapObject.pinchOpen(0.6f, 20)
            device.waitForIdle()

            val before = initialZoom.get() ?: error("missing initial zoom")
            scenario.onActivity { activity ->
                assertTrue(activity.mapFragment()?.zoomByForTesting(1.0) == true)
            }
            waitUntil("map camera zoom changed") {
                val currentZoom = AtomicReference<Double?>()
                scenario.onActivity { activity ->
                    currentZoom.set(activity.mapFragment()?.debugStateForTesting()?.zoom)
                }
                (currentZoom.get() ?: before) > before
            }
        }
    }

    @Test
    fun mapAssetUpdateDefaultViewGraphicallyRendersCenteredOnLumbridge() {
        ActivityScenario.launch(ExpandedQaMapActivity::class.java).use { scenario ->
            val renderedState = AtomicReference<MapFragment.DebugState?>()
            waitUntil("map ready at Lumbridge default center") {
                scenario.onActivity { activity ->
                    renderedState.set(activity.mapFragment()?.debugStateForTesting())
                }
                renderedState.get()?.latitude != null && renderedState.get()?.longitude != null
            }

            val state = renderedState.get() ?: error("missing rendered map state")
            assertEquals(osrsMapDefaultView.LATITUDE, state.latitude ?: Double.NaN, 0.000000000001)
            assertEquals(osrsMapDefaultView.LONGITUDE, state.longitude ?: Double.NaN, 0.000000000001)
            assertEquals(osrsMapDefaultView.ZOOM, state.zoom ?: Double.NaN, 0.000000000001)

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val mapObject = device.findObject(By.res(BuildConfig.APPLICATION_ID, "map_view"))
            assertNotNull("map_view should be visible for graphical verification", mapObject)
            assertMapScreenshotHasVisibleContent(device, mapObject.visibleBounds)
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

    private fun assertVoiceIntentFromMainTab(
        selectedTabId: Int,
        destination: Class<out Activity>,
        transcript: String
    ) {
        SpeechRecognitionGatewayRegistry.gateway = FakeSpeechGateway(finalResult = transcript)
        Intents.init()
        try {
            stubActivity(destination)

            ActivityScenario.launch(MainActivity::class.java).use {
                if (selectedTabId != R.id.nav_news) {
                    onView(allOf(withId(selectedTabId), isDisplayed())).perform(click())
                }
                waitUntil("visible voice button for ${destination.simpleName}") {
                    try {
                        onView(allOf(withId(R.id.voice_search_button), isDisplayed()))
                            .check(matches(isDisplayed()))
                        true
                    } catch (_: Throwable) {
                        false
                    }
                }
                onView(allOf(withId(R.id.voice_search_button), isDisplayed())).perform(click())
                assertVoiceIntent(destination, transcript)
            }
        } finally {
            Intents.release()
        }
    }

    private fun stubActivity(activityClass: Class<out Activity>) {
        intending(hasComponent(activityClass.name))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    private fun assertVoiceIntent(destination: Class<out Activity>, transcript: String) {
        waitUntil("voice intent for ${destination.simpleName}") {
            intended(
                allOf(
                    hasComponent(destination.name),
                    hasExtra("query", transcript)
                )
            )
            true
        }
    }

    private fun assertMapScreenshotHasVisibleContent(device: UiDevice, mapBounds: Rect) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshotFile = File(context.cacheDir, "expanded-map-screenshot.png")
        assertTrue("map screenshot should be captured", device.takeScreenshot(screenshotFile))
        val bitmap = BitmapFactory.decodeFile(screenshotFile.absolutePath)
        assertNotNull("map screenshot should decode", bitmap)

        val left = mapBounds.left.coerceIn(0, bitmap.width - 1)
        val top = mapBounds.top.coerceIn(0, bitmap.height - 1)
        val right = mapBounds.right.coerceIn(left + 1, bitmap.width)
        val bottom = mapBounds.bottom.coerceIn(top + 1, bitmap.height)
        assertTrue("map bounds should overlap screenshot", right > left && bottom > top)

        val stepX = ((right - left) / 24).coerceAtLeast(1)
        val stepY = ((bottom - top) / 24).coerceAtLeast(1)
        val quantizedColors = mutableSetOf<Int>()
        var visiblePixels = 0
        var samples = 0

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val color = bitmap.getPixel(x, y)
                val alpha = color ushr 24 and 0xff
                val red = color ushr 16 and 0xff
                val green = color ushr 8 and 0xff
                val blue = color and 0xff
                if (alpha > 0 && (red > 8 || green > 8 || blue > 8)) {
                    visiblePixels++
                }
                quantizedColors += ((red / 32) shl 10) or ((green / 32) shl 5) or (blue / 32)
                samples++
                x += stepX
            }
            y += stepY
        }

        assertTrue("map screenshot should contain visible map pixels", visiblePixels > samples / 20)
        assertTrue("map screenshot should contain varied map pixels", quantizedColors.size > 1)

        device.executeShellCommand("screencap -p /sdcard/Download/osrswiki-expanded-map-screenshot.png")
    }

    private fun targetString(resId: Int): String {
        return InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
    }

    private class FakeSpeechGateway(
        private val finalResult: String? = null,
        private val partialResult: String? = null,
        private val errorCode: Int? = null,
        private val available: Boolean = true,
        private val endOfSpeechDelayMs: Long = 0,
        private val resultDelayMs: Long = 0
    ) : SpeechRecognitionGateway {
        override fun isRecognitionAvailable(context: Context): Boolean = available

        override fun createRecognizer(
            context: Context,
            listener: RecognitionListener
        ): SpeechRecognitionHandle {
            return object : SpeechRecognitionHandle {
                override fun startListening(intent: Intent) {
                    val handler = Handler(Looper.getMainLooper())
                    handler.post {
                        listener.onReadyForSpeech(Bundle())
                        if (partialResult != null) {
                            val partialResults = Bundle().apply {
                                putStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION,
                                    arrayListOf(partialResult)
                                )
                            }
                            listener.onPartialResults(partialResults)
                        }

                        val finishRecognition = {
                            listener.onEndOfSpeech()
                            val completeRecognition = {
                                if (errorCode != null) {
                                    listener.onError(errorCode)
                                } else {
                                    val results = Bundle().apply {
                                        putStringArrayList(
                                            SpeechRecognizer.RESULTS_RECOGNITION,
                                            finalResult?.let { arrayListOf(it) } ?: arrayListOf()
                                        )
                                    }
                                    listener.onResults(results)
                                }
                            }

                            if (resultDelayMs > 0) {
                                handler.postDelayed(completeRecognition, resultDelayMs)
                            } else {
                                completeRecognition()
                            }
                            Unit
                        }

                        if (endOfSpeechDelayMs > 0) {
                            handler.postDelayed(finishRecognition, endOfSpeechDelayMs)
                        } else {
                            finishRecognition()
                        }
                    }
                }

                override fun stopListening() = Unit
                override fun destroy() = Unit
            }
        }
    }

    private enum class FakeDonationOutcome {
        SUCCESS,
        CANCEL,
        PENDING,
        ERROR
    }

    private class FakeDonationBillingFactory(
        private val products: Set<String>,
        private val outcome: FakeDonationOutcome
    ) : DonationBillingGatewayFactory {
        var gateway: FakeDonationBillingGateway? = null
            private set

        override fun create(
            context: Context,
            listener: DonationBillingListener
        ): DonationBillingGateway {
            return FakeDonationBillingGateway(products, outcome, listener).also {
                gateway = it
            }
        }
    }

    private class FakeDonationBillingGateway(
        private val products: Set<String>,
        private val outcome: FakeDonationOutcome,
        private val listener: DonationBillingListener
    ) : DonationBillingGateway {
        var launchedProductId: String? = null
            private set

        override fun start() {
            Handler(Looper.getMainLooper()).post {
                listener.onBillingReady(products)
            }
        }

        override fun launchPurchase(activity: Activity, productId: String): DonationBillingLaunchResult {
            launchedProductId = productId
            Handler(Looper.getMainLooper()).post {
                when (outcome) {
                    FakeDonationOutcome.SUCCESS -> listener.onPurchaseSuccess(productId)
                    FakeDonationOutcome.CANCEL -> listener.onPurchaseCancelled()
                    FakeDonationOutcome.PENDING -> listener.onPurchasePending()
                    FakeDonationOutcome.ERROR -> listener.onPurchaseError("Test purchase failure")
                }
            }
            return DonationBillingLaunchResult(isSuccess = true)
        }

        override fun disconnect() = Unit
    }

    private class DeferredDonationBillingFactory : DonationBillingGatewayFactory {
        var gateway: DeferredDonationBillingGateway? = null
            private set

        override fun create(
            context: Context,
            listener: DonationBillingListener
        ): DonationBillingGateway {
            return DeferredDonationBillingGateway(listener).also {
                gateway = it
            }
        }
    }

    private class DeferredDonationBillingGateway(
        val listener: DonationBillingListener
    ) : DonationBillingGateway {
        override fun start() = Unit

        override fun launchPurchase(activity: Activity, productId: String): DonationBillingLaunchResult {
            return DonationBillingLaunchResult(isSuccess = false, message = "Deferred")
        }

        override fun disconnect() = Unit
    }

    private enum class FeedbackSubmissionKind {
        REPORT,
        FEATURE
    }

    private data class CapturedFeedbackSubmission(
        val kind: FeedbackSubmissionKind,
        val title: String,
        val description: String
    )

    private class FakeFeedbackSubmissionGateway(
        private val reportResult: Result<String> = Result.success("Fake report submitted"),
        private val featureResult: Result<String> = Result.success("Fake feature submitted")
    ) : FeedbackSubmissionGateway {
        val submissions = mutableListOf<CapturedFeedbackSubmission>()

        override suspend fun reportIssue(
            context: Context,
            title: String,
            description: String
        ): Result<String> {
            submissions += CapturedFeedbackSubmission(FeedbackSubmissionKind.REPORT, title, description)
            return reportResult
        }

        override suspend fun requestFeature(
            context: Context,
            title: String,
            description: String
        ): Result<String> {
            submissions += CapturedFeedbackSubmission(FeedbackSubmissionKind.FEATURE, title, description)
            return featureResult
        }
    }
}
