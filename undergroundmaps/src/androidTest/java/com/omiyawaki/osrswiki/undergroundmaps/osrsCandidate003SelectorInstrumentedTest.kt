package com.omiyawaki.osrswiki.undergroundmaps

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil

/** Candidate 004 regressions for the complete packaged 1,097-realm selector. */
@RunWith(AndroidJUnit4::class)
class osrsCandidate004SelectorInstrumentedTest {
    @Test
    fun fullSelectorFilterStaysBelowSimpleControlBudget() {
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            val initial = awaitDiagnostics(scenario) {
                it.sourceId != null && it.manifestRealmCount == OSRS_EXPECTED_REALM_COUNT
            }
            assumeTrue("Exact Candidate 006 assets are required", initial.candidate == "006")
            scenario.onActivity { activity ->
                assertTrue(activity.openRealmSelectorForTesting())
            }

            val samples = mutableListOf<Long>()
            val oneCharacterSamples = mutableListOf<Long>()
            OSRS_FILTER_QUERIES.forEach { query ->
                scenario.onActivity { activity ->
                    assertTrue(activity.filterRealmSelectorForTesting(query))
                    val elapsed = activity.debugStateForTesting().lastSelectorFilterNanos
                    assertNotNull("Missing app-owned selector timing for query '$query'", elapsed)
                    samples += elapsed!!
                    if (query.length == 1) oneCharacterSamples += elapsed
                }
            }
            scenario.onActivity { it.dismissRealmSelectorForTesting() }

            assertEquals(60, samples.size)
            assertEquals(20, oneCharacterSamples.size)
            assertTrue(
                "Full selector p95 exceeded 50 ms: ${nearestRankP95(samples)} ns",
                nearestRankP95(samples) < OSRS_SIMPLE_CONTROL_BUDGET_NANOS
            )
            assertTrue(
                "One-character selector p95 exceeded 50 ms: ${nearestRankP95(oneCharacterSamples)} ns",
                nearestRankP95(oneCharacterSamples) < OSRS_SIMPLE_CONTROL_BUDGET_NANOS
            )
        }
    }

    @Test
    fun longestAndStructuredDuplicateIdentitiesStayVisiblyHonestAndAccessible() {
        ActivityScenario.launch(osrsUndergroundMapsActivity::class.java).use { scenario ->
            val initial = awaitDiagnostics(scenario) {
                it.sourceId != null && it.manifestRealmCount == OSRS_EXPECTED_REALM_COUNT
            }
            assumeTrue("Exact Candidate 006 assets are required", initial.candidate == "006")
            scenario.onActivity { activity ->
                assertTrue(activity.selectRealmForTesting(OSRS_LONGEST_IDENTITY_REALM_ID))
            }
            val longest = awaitDiagnostics(scenario) {
                it.activeRealmId == OSRS_LONGEST_IDENTITY_REALM_ID &&
                    it.switchCompletedAtNanos != null &&
                    it.selectorIdentityTextLength == OSRS_LONGEST_IDENTITY.length &&
                    it.selectorIdentityHonest == true
            }
            assertEquals(OSRS_LONGEST_IDENTITY, longest.activeRealmDisplayName)
            assertEquals(3, longest.selectorIdentityMaxLines)
            assertTrue(
                longest.selectorIdentityLastVisibleEnd == OSRS_LONGEST_IDENTITY.length ||
                    (longest.selectorIdentityEllipsisCount ?: 0) > 0
            )
            assertTrue(
                longest.selectorIdentityAccessibilityText.orEmpty().contains(OSRS_LONGEST_IDENTITY)
            )
            assertTrue(longest.selectorAndStatusSeparated != false)
            assertTrue(longest.topAndFloorControlsSeparated != false)

            val visibleIdentities = mutableListOf<String>()
            val accessibleIdentities = mutableListOf<String>()
            OSRS_STRUCTURED_DUPLICATE_IDS.forEach { realmId ->
                scenario.onActivity { activity -> assertTrue(activity.selectRealmForTesting(realmId)) }
                val diagnostics = awaitDiagnostics(scenario) {
                    it.activeRealmId == realmId &&
                        it.switchCompletedAtNanos != null &&
                        it.selectorIdentityAccessibilityText.orEmpty().contains(
                            "Map ID ${realmId.removePrefix("other-map-")}"
                        ) &&
                        it.selectorIdentityHonest == true
                }
                visibleIdentities += requireNotNull(diagnostics.activeRealmDisplayName)
                accessibleIdentities += requireNotNull(diagnostics.selectorIdentityAccessibilityText)
                assertTrue(diagnostics.selectorIdentityHonest == true)
            }
            assertEquals(3, visibleIdentities.distinct().size)
            assertEquals(3, accessibleIdentities.distinct().size)
            assertTrue(visibleIdentities[0].contains("Map ID 10064"))
            assertTrue(visibleIdentities[1].contains("Map ID 10065"))
            assertTrue(visibleIdentities[2].contains("Map ID 10066"))
        }
    }

    private fun awaitDiagnostics(
        scenario: ActivityScenario<osrsUndergroundMapsActivity>,
        predicate: (osrsMapDiagnostics) -> Boolean
    ): osrsMapDiagnostics {
        val deadline = System.nanoTime() + OSRS_TEST_TIMEOUT_NANOS
        var latest: osrsMapDiagnostics? = null
        while (System.nanoTime() < deadline) {
            scenario.onActivity { activity -> latest = activity.debugStateForTesting() }
            latest?.let { if (predicate(it)) return it }
            Thread.sleep(OSRS_TEST_POLL_MILLIS)
        }
        throw AssertionError("Timed out waiting for Candidate 004 selector diagnostics; latest=$latest")
    }

    private fun nearestRankP95(values: List<Long>): Long {
        val sorted = values.sorted()
        return sorted[(ceil(sorted.size * 0.95).toInt() - 1).coerceIn(sorted.indices)]
    }

    private companion object {
        const val OSRS_EXPECTED_REALM_COUNT = 1097
        const val OSRS_LONGEST_IDENTITY_REALM_ID = "other-map-10162"
        const val OSRS_LONGEST_IDENTITY =
            "Underground Pass - bottom level (Song of the Elves instance)"
        const val OSRS_SIMPLE_CONTROL_BUDGET_NANOS = 50_000_000L
        const val OSRS_TEST_TIMEOUT_NANOS = 30_000_000_000L
        const val OSRS_TEST_POLL_MILLIS = 100L
        val OSRS_STRUCTURED_DUPLICATE_IDS = listOf(
            "other-map-10064",
            "other-map-10065",
            "other-map-10066"
        )
        val OSRS_FILTER_QUERIES = buildList {
            repeat(10) {
                add("a")
                add("an")
                add("anc")
                add("m")
                add("map id")
                add("")
            }
        }
    }
}
