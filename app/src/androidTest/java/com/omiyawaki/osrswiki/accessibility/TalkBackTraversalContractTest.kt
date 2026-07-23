package com.omiyawaki.osrswiki.accessibility

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.MainActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.about.PrivacyPolicyActivity
import com.omiyawaki.osrswiki.donate.DonateActivity
import com.omiyawaki.osrswiki.donate.DonationBillingGateway
import com.omiyawaki.osrswiki.donate.DonationBillingGatewayFactory
import com.omiyawaki.osrswiki.donate.DonationBillingGatewayRegistry
import com.omiyawaki.osrswiki.donate.DonationBillingLaunchResult
import com.omiyawaki.osrswiki.donate.DonationBillingListener
import com.omiyawaki.osrswiki.feedback.ReportIssueActivity
import com.omiyawaki.osrswiki.feedback.RequestFeatureActivity
import com.omiyawaki.osrswiki.test.ExpandedQaMapActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class TalkBackTraversalContractTest {

    private val harness = AccessibilityTraversalHarness(BuildConfig.APPLICATION_ID)

    @After
    fun resetRegistries() {
        DonationBillingGatewayRegistry.reset()
    }

    @Test
    fun homeTraversalExposesSearchUpdatesAndBottomNavigationInOrder() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val snapshot = harness.waitForSnapshot {
                it.firstByIdSuffix("nav_news") != null &&
                    it.traversalNodes.any { node -> node.label.contains("Updates") }
            }

            snapshot.assertLabelsContainInOrder(
                "Home",
                "Random page",
                "Search OSRS Wiki",
                "Voice search",
                "Updates"
            )

            listOf(
                "nav_news" to "Home",
                "nav_saved" to "Saved",
                "nav_search" to "Search",
                "nav_map" to "Map",
                "nav_more" to "More"
            ).forEach { (id, label) ->
                val target = snapshot.singleTraversalTargetByIdSuffix(id)
                assertTrue("$id should expose traversal label '$label'", target.label.contains(label))
                assertTrue("$id should be enabled", target.enabled)
                assertTrue("$id should be clickable", target.clickable)
                assertTrue("$id should be focusable", target.focusable)
            }
            snapshot.assertIdsInOrder("nav_news", "nav_saved", "nav_search", "nav_map", "nav_more")

            val partialUpdateCards = snapshot.nodes.filter { node ->
                node.label.contains("Opens update article.") &&
                    node.bounds.right > snapshot.rootBounds.right
            }
            partialUpdateCards.forEach { node ->
                assertFalse("partially visible update card must not be clickable", node.clickable)
                assertFalse("partially visible update card must not be focusable", node.focusable)
            }
        }
    }

    private fun AccessibilityTraversalHarness.Snapshot.singleTraversalTargetByIdSuffix(
        idSuffix: String
    ): AccessibilityTraversalHarness.Node {
        val targets = traversalTargetsByIdSuffix(idSuffix)
        assertTrue(
            "$idSuffix should be exactly one reachable traversal target, found ${targets.size}",
            targets.size == 1
        )
        return targets.single()
    }

    @Test
    fun donateBillingUnavailableControlsAreNotTraversalTargets() {
        DonationBillingGatewayRegistry.factory = FakeDonationBillingFactory(products = emptySet())

        ActivityScenario.launch(DonateActivity::class.java).use {
            val snapshot = harness.waitForSnapshot {
                it.firstByIdSuffix("status_text")?.label == "No donation options available"
            }

            listOf(
                "chip_amount_1",
                "chip_amount_5",
                "chip_amount_10",
                "chip_amount_25",
                "donate_button"
            ).forEach { id ->
                val node = snapshot.requiredByIdSuffix(id)
                assertFalse("$id should be disabled when billing is unavailable", node.enabled)
                assertFalse("$id should not expose click action when disabled", node.clickable)
                assertFalse("$id should not expose accessibility focus when disabled", node.focusable)
                assertTrue(
                    "$id should not be a traversal target",
                    snapshot.traversalTargetsByIdSuffix(id).isEmpty()
                )
            }

            val wikiDonate = snapshot.requiredByIdSuffix("wiki_donate_button")
            assertTrue("Patreon support handoff should remain enabled", wikiDonate.enabled)
            assertTrue("Patreon support handoff should remain clickable", wikiDonate.clickable)
        }
    }

    @Test
    fun feedbackDisabledSubmitIsNotTraversalTargetBeforeValidationPasses() {
        assertDisabledFeedbackSubmitIsNotTraversalTarget(ReportIssueActivity::class.java)
        assertDisabledFeedbackSubmitIsNotTraversalTarget(RequestFeatureActivity::class.java)
    }

    private fun <A : Activity> assertDisabledFeedbackSubmitIsNotTraversalTarget(
        activityClass: Class<A>
    ) {
        ActivityScenario.launch(activityClass).use {
            onView(withId(R.id.submit_button)).perform(scrollTo())

            val snapshot = harness.waitForSnapshot {
                it.firstByIdSuffix("submit_button") != null
            }
            val submit = snapshot.requiredByIdSuffix("submit_button")

            assertFalse("empty feedback submit should be disabled", submit.enabled)
            assertFalse("empty feedback submit should not expose click action", submit.clickable)
            assertFalse("empty feedback submit should not expose accessibility focus", submit.focusable)
            assertTrue(
                "empty feedback submit should not be a traversal target",
                snapshot.traversalTargetsByIdSuffix("submit_button").isEmpty()
            )
        }
    }

    @Test
    fun mapFloorControlsExposeOnlyActionableFloorButtons() {
        ActivityScenario.launch(ExpandedQaMapActivity::class.java).use {
            val snapshot = harness.waitForSnapshot {
                it.firstByIdSuffix("floor_control_up") != null ||
                    it.firstByIdSuffix("floor_control_text")?.label?.contains("Current map floor: 0") == true
            }

            val up = snapshot.requiredByIdSuffix("floor_control_up")
            assertTrue("floor up should be enabled on floor 0", up.enabled)
            assertTrue("floor up should expose click action on floor 0", up.clickable)
            assertTrue("floor up should expose accessibility focus on floor 0", up.focusable)
            assertTrue(
                "floor up should be reachable in traversal",
                snapshot.traversalTargetsByIdSuffix("floor_control_up").isNotEmpty()
            )

            val down = snapshot.firstByIdSuffix("floor_control_down")
            if (down != null) {
                assertFalse("floor down should be disabled on floor 0", down.enabled)
                assertFalse("floor down should not expose click action on floor 0", down.clickable)
                assertFalse("floor down should not expose accessibility focus on floor 0", down.focusable)
            }
            assertTrue(
                "floor down should not be reachable in traversal on floor 0",
                snapshot.traversalTargetsByIdSuffix("floor_control_down").isEmpty()
            )
        }
    }

    @Test
    fun privacyPolicyTraversalSeparatesHeadingsAndBodySections() {
        ActivityScenario.launch(PrivacyPolicyActivity::class.java).use {
            val snapshot = harness.waitForSnapshot {
                it.traversalNodes.any { node -> node.label.contains("1. INFORMATION WE COLLECT") }
            }

            snapshot.assertLabelsContainInOrder(
                "Privacy Policy",
                "Last updated",
                "OSRS Wiki App Privacy Policy",
                "The OSRS Wiki App is designed to respect your privacy",
                "1. INFORMATION WE COLLECT",
                "Audio Data (Voice Search)"
            )

            assertTrue(
                "document title should be exposed as a heading",
                snapshot.traversalNodes.any {
                    it.label == "OSRS Wiki App Privacy Policy" && it.heading
                }
            )
            assertTrue(
                "numbered policy section should be exposed as a heading",
                snapshot.traversalNodes.any {
                    it.label == "1. INFORMATION WE COLLECT" && it.heading
                }
            )
        }
    }

    private class FakeDonationBillingFactory(
        private val products: Set<String>
    ) : DonationBillingGatewayFactory {
        override fun create(
            context: Context,
            listener: DonationBillingListener
        ): DonationBillingGateway {
            return FakeDonationBillingGateway(products, listener)
        }
    }

    private class FakeDonationBillingGateway(
        private val products: Set<String>,
        private val listener: DonationBillingListener
    ) : DonationBillingGateway {
        override fun start() {
            Handler(Looper.getMainLooper()).post {
                listener.onBillingReady(products)
            }
        }

        override fun launchPurchase(
            activity: Activity,
            productId: String
        ): DonationBillingLaunchResult {
            return DonationBillingLaunchResult(isSuccess = false, message = "No products")
        }

        override fun disconnect() = Unit
    }
}
