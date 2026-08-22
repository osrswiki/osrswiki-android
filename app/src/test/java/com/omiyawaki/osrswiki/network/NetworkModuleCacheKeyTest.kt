package com.omiyawaki.osrswiki.network

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NetworkModuleCacheKeyTest {

    private lateinit var cache: NetworkModuleCache

    @Before
    fun setUp() {
        cache = NetworkModuleCache.getInstance(ApplicationProvider.getApplicationContext())
        cache.clearCacheSync()
    }

    @Test
    fun versionAndParamOrderDoNotChangeCacheKey() {
        val calculator = "https://oldschool.runescape.wiki/load.php?modules=jquery&only=scripts"
        val reorderedVersioned =
            "https://oldschool.runescape.wiki/load.php?version=abc123&only=scripts&modules=jquery"
        assertEquals(
            NetworkModuleCache.canonicalQuery(calculator),
            NetworkModuleCache.canonicalQuery(reorderedVersioned)
        )
        assertEquals(
            NetworkModuleCache.cacheFileName(calculator),
            NetworkModuleCache.cacheFileName(reorderedVersioned)
        )
    }

    @Test
    fun batchedModuleUrlDoesNotCollideWithPerModuleCalculatorUrl() {
        val single = "https://oldschool.runescape.wiki/load.php?modules=jquery&only=scripts"
        val batched =
            "https://oldschool.runescape.wiki/load.php?modules=jquery%7Coojs%7Coojs-ui-core&only=scripts"
        assertNotEquals(
            NetworkModuleCache.canonicalQuery(single),
            NetworkModuleCache.canonicalQuery(batched)
        )
    }

    @Test
    fun secondLookupHitsAfterCalculatorShapedWarmWrite() {
        val calculator = "https://oldschool.runescape.wiki/load.php?modules=oojs-ui-core&only=scripts"
        val secondOpen =
            "https://oldschool.runescape.wiki/load.php?modules=oojs-ui-core&only=scripts&version=rlhash"
        val batched =
            "https://oldschool.runescape.wiki/load.php?modules=jquery|oojs|oojs-ui-core&only=scripts"

        cache.putResponseSync(calculator, "/* oojs-ui-core */")

        assertTrue(cache.isCached(calculator))
        assertTrue(cache.isCached(secondOpen))
        assertEquals("/* oojs-ui-core */", cache.getCachedResponseIfPresent(secondOpen))
        assertFalse(cache.isCached(batched))
    }
}
