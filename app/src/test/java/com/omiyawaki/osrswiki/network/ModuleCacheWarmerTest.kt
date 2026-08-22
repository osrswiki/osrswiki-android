package com.omiyawaki.osrswiki.network

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ModuleCacheWarmerTest {

    private lateinit var moduleCache: NetworkModuleCache

    @Before
    fun setUp() {
        moduleCache = NetworkModuleCache.getInstance(ApplicationProvider.getApplicationContext())
        moduleCache.clearCacheSync()
    }

    @Test
    fun essentialsIncludeCalculatorOouiAndCommonRlModules() {
        val names = ModuleCacheWarmer.ESSENTIAL_MODULES
        assertTrue(names.contains("oojs"))
        assertTrue(names.contains("jquery"))
        assertTrue(names.contains("oojs-ui-core"))
        assertTrue(names.contains("oojs-ui-widgets"))
        assertTrue(names.contains("mediawiki.widgets"))
        assertTrue(names.contains("ext.gadget.rsw-util"))
        assertTrue(names.contains("ext.gadget.calc-core"))
        assertTrue(names.contains("mediawiki.base"))
        assertTrue(names.contains("ext.gadget.GECharts"))
        assertTrue(names.contains("ext.gadget.tooltips"))
    }

    @Test
    fun essentialUrlsMatchCalculatorInjectAndDropVersionOnLookup() {
        val urls = ModuleCacheWarmer.essentialLoadUrls()
        assertTrue(urls.contains(ModuleCacheWarmer.calculatorShapedUrl("jquery", onlyScripts = true)))
        assertTrue(urls.contains(ModuleCacheWarmer.calculatorShapedUrl("oojs", onlyScripts = true)))
        assertTrue(urls.contains(ModuleCacheWarmer.calculatorShapedUrl("mediawiki.widgets", onlyScripts = false)))
        assertTrue(urls.contains(ModuleCacheWarmer.calculatorShapedUrl("ext.gadget.calc-core", onlyScripts = true)))
        assertTrue(urls.any { it.contains("modules=ext.gadget.calc-core") && it.contains("skin=minerva") })
        assertFalse(urls.any { it.contains("|") })
    }

    @Test
    fun warmingCalculatorShapedUrlHitsOnSecondVersionedOpen() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("/* ${chain.request().url.query} */".toResponseBody("application/javascript".toMediaType()))
                    .build()
            }
            .build()
        val warmer = ModuleCacheWarmer.createForTest(
            ApplicationProvider.getApplicationContext(),
            client
        )

        warmer.warmEssentialsNow()

        val firstOpen = ModuleCacheWarmer.calculatorShapedUrl("oojs-ui-widgets", onlyScripts = true)
        val secondOpen = "$firstOpen&version=second-open"
        assertTrue(moduleCache.isCached(firstOpen))
        assertTrue(moduleCache.isCached(secondOpen))
        assertTrue(
            moduleCache.getCachedResponseIfPresent(secondOpen)!!.contains("modules=oojs-ui-widgets")
        )
        assertFalse(
            moduleCache.isCached(
                "https://oldschool.runescape.wiki/load.php?modules=jquery|oojs|oojs-ui-core&only=scripts"
            )
        )
    }
}
