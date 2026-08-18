package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.undergroundmaps.data.osrsRealmRepository
import com.omiyawaki.osrswiki.undergroundmaps.model.OSRS_REALM_GROUPS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.ceil
import kotlin.system.measureNanoTime

/**
 * Retains a host-side profile of the exact synchronous selector work exercised on API 34.
 * Device p95 remains the release gate; this profile makes accidental O(items x normalization)
 * regressions cheap to detect before an emulator run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class osrsRealmSelectorPerformanceTest {
    @Test
    fun `profile full selector filtering and kiss row projection`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val catalog = osrsRealmRepository(context).loadCatalog()
        assumeTrue(
            "Full generated realm assets are required for the selector profile",
            catalog.realmCount == OSRS_EXPECTED_REALM_COUNT
        )
        val presentations = osrsRealmPresentationCatalog(catalog.manifest.realms)
        val selectorIndex = osrsRealmSelectorIndex(catalog.sections, presentations)
        val samples = buildList {
            repeat(10) {
                add("a")
                add("an")
                add("anc")
                add("m")
                add("map id")
                add("")
            }
        }.map { query ->
            measureNanoTime {
                val result = selectorIndex.filter(query)
                buildList {
                    OSRS_REALM_GROUPS.forEach { group ->
                        result.sections.getValue(group).forEach { add(it.id) }
                    }
                }
            }
        }
        val sorted = samples.sorted()
        val p95 = sorted[(ceil(sorted.size * 0.95).toInt() - 1).coerceIn(sorted.indices)]
        println(
            "osrs_selector_profile realms=${catalog.realmCount} samples=${samples.size} " +
                "p95Nanos=$p95 maxNanos=${samples.max()}"
        )
        assertTrue("Host-side selector p95 was $p95 ns", p95 < OSRS_HOST_P95_BUDGET_NANOS)
    }

    private companion object {
        const val OSRS_EXPECTED_REALM_COUNT = 50
        const val OSRS_HOST_P95_BUDGET_NANOS = 50_000_000L
    }
}
