package com.omiyawaki.osrswiki.page

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.network.ModuleCacheWarmer
import com.omiyawaki.osrswiki.theme.Theme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class osrsWikiGadgetAbsenceTest {
    private val builder = PageHtmlBuilder(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun shippedAssetsDoNotContainWikiGadgetJavascript() {
        val forbidden = listOf(
            "src/main/assets/mediawiki/gadget_calc_core.js",
            "src/main/assets/mediawiki/page_modules.js",
            "src/main/assets/mediawiki/page_bootstrap.js",
            "../../../shared/js/mediawiki/gadget_calc_core.js",
            "../../../shared/js/mediawiki/page_modules.js",
            "../../../shared/js/mediawiki/page_bootstrap.js",
            "../../../tools/js/extract-gadget-calc-core.py"
        )
        forbidden.forEach { relative ->
            assertFalse("$relative must not ship wiki gadget source", File(relative).exists())
        }
        assertFalse(
            "Android assets must not keep a gadget JS extract",
            File("src/main/assets").walkTopDown().any { file ->
                file.isFile && file.name.contains("gadget", ignoreCase = true) && file.extension == "js"
            }
        )
        val pageModules = File("src/main/assets/mediawiki/page_modules.js")
        if (pageModules.exists()) {
            val text = pageModules.readText()
            assertFalse(text.contains("MediaWiki:Gadget-"))
        }
    }

    @Test
    fun articleHtmlDoesNotInjectOrRequestGadgetScripts() {
        val html = builder.buildFullHtmlDocument(
            title = "Calculator:Combat level",
            bodyContent = """
                <pre class="jcConfig">template = Calculator:Combat level/Template</pre>
                <div class="GEChartBox" data-item="Abyssal whip"></div>
                <div class="infobox-switch" data-switch-infobox="1"></div>
            """.trimIndent(),
            theme = Theme.OSRS_LIGHT,
            canonicalTitle = "Calculator:Combat level"
        )

        assertFalse(html.contains("gadget_calc_core.js"))
        assertFalse(html.contains("page_modules.js"))
        assertFalse(html.contains("page_bootstrap.js"))
        assertFalse(html.contains("MediaWiki:Gadget-"))
        val pageModules = Regex("""var RLPAGEMODULES = \[(.*?)\];""", RegexOption.DOT_MATCHES_ALL)
            .find(html)
            ?.groupValues
            ?.get(1)
            .orEmpty()
        assertFalse(
            "RLPAGEMODULES must not request MediaWiki gadgets: $pageModules",
            pageModules.contains("ext.gadget.")
        )
        assertTrue(html.contains("web/osrs_calculator_runtime.js"))
        assertTrue(html.contains("web/osrs_native_calc_indoc.js"))
        assertTrue(html.contains("web/ge_charts_init.js"))
        assertTrue(html.contains("web/switch_infobox.js"))
        assertTrue(html.contains("web/osrs_article_tooltips.js"))
    }

    @Test
    fun moduleRegistryNeverSelectsMediaWikiGadgets() {
        val modules = WikiModuleRegistry.generateRLPAGEMODULES(
            """
                <pre class="jcConfig">template = Calculator:Cooking/Template</pre>
                <div class="GEChartBox"></div>
                <div data-switch-infobox="1"></div>
                <span class="tooltip">hint</span>
            """.trimIndent(),
            "Calculator:Cooking"
        )
        assertFalse(modules.any { it.startsWith("ext.gadget.") })
        assertTrue(modules.contains("oojs-ui-core"))
        assertTrue(modules.contains("mediawiki.page.ready"))
    }

    @Test
    fun calculatorRuntimeAndCacheWarmerDoNotLoadGadgets() {
        val runtime = File("../../../shared/js/osrs_calculator_runtime.js").readText()
        assertFalse(runtime.contains("ext.gadget."))
        assertFalse(runtime.contains("mw.loader.load('ext.gadget.calc-core')"))
        assertFalse(runtime.contains("/load.php?modules=ext.gadget."))

        val urls = ModuleCacheWarmer.essentialLoadUrls()
        assertFalse(urls.any { it.contains("ext.gadget.") })
        assertFalse(ModuleCacheWarmer.ESSENTIAL_MODULES.any { it.startsWith("ext.gadget.") })
        assertFalse(ModuleCacheWarmer.CALCULATOR_INJECT_MODULES.any { it.name.startsWith("ext.gadget.") })
    }

    @Test
    fun loadPhpProxyStripsOrRejectsGadgetModules() {
        val gadgetOnly = "https://oldschool.runescape.wiki/load.php?modules=ext.gadget.rsw-util&only=scripts"
        val mixed = "https://oldschool.runescape.wiki/load.php?modules=jquery|ext.gadget.calc-core|oojs&only=scripts"
        val floss = "https://oldschool.runescape.wiki/load.php?modules=oojs-ui-core&only=scripts"

        assertTrue(osrsWikiWebViewUrl.isMediaWikiGadgetModule("ext.gadget.calc-core"))
        assertFalse(osrsWikiWebViewUrl.isMediaWikiGadgetModule("oojs-ui-core"))
        assertTrue(osrsWikiWebViewUrl.isRejectedMediaWikiGadgetLoad(gadgetOnly))
        assertFalse(osrsWikiWebViewUrl.isRejectedMediaWikiGadgetLoad(floss))

        val stripped = osrsWikiWebViewUrl.withoutMediaWikiGadgetLoadModules(mixed)
        assertTrue(stripped != null && stripped.contains("modules=jquery"))
        assertTrue(stripped!!.contains("oojs"))
        assertFalse(stripped.contains("ext.gadget."))
        assertTrue(osrsWikiWebViewUrl.withoutMediaWikiGadgetLoadModules(gadgetOnly) == null)
        assertTrue(osrsWikiWebViewUrl.withoutMediaWikiGadgetLoadModules(floss) == floss)
    }

    @Test
    fun gadgetOnlyLoadPhpIsRejectedWithoutWikiFetch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = osrsWikiWebViewProxy.request(
            context,
            "GET",
            "/load.php?modules=ext.gadget.rsw-util&only=scripts",
            null
        )
        assertTrue(result.getBoolean("ok"))
        assertTrue(result.getString("body").contains("MediaWiki gadgets are not loaded"))
    }
}
