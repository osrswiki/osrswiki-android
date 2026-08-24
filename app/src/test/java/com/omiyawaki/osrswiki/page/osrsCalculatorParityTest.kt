package com.omiyawaki.osrswiki.page

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class osrsCalculatorParityTest {
    @Test
    fun testUserFacingClassifierRejectsTemplatePathSegments() {
        assertTrue(osrsWikiWebViewUrl.isUserFacingCalculator("Calculator:Combat level"))
        assertTrue(osrsWikiWebViewUrl.isUserFacingCalculator("Calculator:Cooking/Fish"))
        assertFalse(osrsWikiWebViewUrl.isUserFacingCalculator("Calculator:Combat level/Template"))
        assertFalse(osrsWikiWebViewUrl.isUserFacingCalculator("Calculator:Fletching/Ammo/Template1"))
        assertFalse(osrsWikiWebViewUrl.isUserFacingCalculator("Calculator:Herblore/Potions/Template:Clean"))
    }

    @Test
    fun localApiAndCorsPathsRewriteToTheWiki() {
        val api = "https://appassets.androidplatform.net/api.php?action=parse&text=calc"
        val cors = "https://appassets.androidplatform.net/cors/m=hiscore_oldschool/index_lite.ws?player=Zezima"
        val load = "https://appassets.androidplatform.net/load.php?modules=oojs-ui-core"

        assertTrue(osrsWikiWebViewUrl.shouldProxy(Uri.parse(api)))
        assertTrue(osrsWikiWebViewUrl.shouldProxy(Uri.parse(cors)))
        assertTrue(osrsWikiWebViewUrl.shouldProxy(Uri.parse(load)))
        assertTrue(osrsWikiWebViewUrl.rewriteToWiki(api).startsWith("https://oldschool.runescape.wiki/api.php"))
        assertTrue(osrsWikiWebViewUrl.rewriteToWiki(cors).startsWith("https://oldschool.runescape.wiki/cors/"))
        assertTrue(osrsWikiWebViewUrl.rewriteToWiki(load).startsWith("https://oldschool.runescape.wiki/load.php"))
    }

    @Test
    fun bundledCatalogListsEveryUserFacingCalculator() {
        val snapshot = osrsCalculatorCatalog.loadSnapshot(catalogJson())
        assertTrue(snapshot.calculators.size >= 100)
        snapshot.calculators.forEach { entry ->
            assertTrue(entry.title, osrsWikiWebViewUrl.isUserFacingCalculator(entry.title))
            assertTrue(entry.url.startsWith("https://oldschool.runescape.wiki/w/Calculator"))
            assertFalse(entry.title, entry.title.split('/').any { it.startsWith("Template", ignoreCase = true) })
        }
        val titles = snapshot.calculators.map { it.title }.toSet()
        assertTrue(titles.contains("Calculator:Combat level"))
        assertTrue(titles.contains("Calculator:Cooking"))
        assertTrue(titles.contains("Calculator:Barrows"))
        assertFalse(osrsWikiWebViewUrl.isUserFacingCalculator("Calculator:Fletching/Ammo/Template1"))
    }

    @Test
    fun everyCataloguedCalculatorLoadsCalcCoreAndCalculatorNamespace() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val builder = PageHtmlBuilder(context)
        val snapshot = osrsCalculatorCatalog.loadSnapshot(catalogJson())
        snapshot.calculators.forEach { entry ->
            val html = builder.buildFullHtmlDocument(
                title = entry.title,
                bodyContent = """<pre class="jcConfig">template = ${entry.title}/Template</pre>""",
                theme = com.omiyawaki.osrswiki.theme.Theme.OSRS_LIGHT,
                canonicalTitle = entry.title
            )
            assertTrue(entry.title, html.contains("\"oojs-ui-core\""))
            assertTrue(entry.title, html.contains("\"oojs-ui-widgets\""))
            assertTrue(
                entry.title,
                html.contains("\"wgNamespaceNumber\": 116") || html.contains("\"wgNamespaceNumber\":116")
            )
            assertTrue(entry.title, html.contains("mediawiki/gadget_calc_core.js"))
            assertTrue(entry.title, html.contains("web/osrs_calculator_runtime.js"))
            assertTrue(entry.title, html.contains("\"wgLoadScript\": \"/load.php\""))
            assertTrue(entry.title, html.contains("\"wgScript\": \"/index.php\""))
            assertTrue(entry.title, html.contains("id=\"bodyContent\""))
            assertTrue(entry.title, html.contains("--osrs-article-bottom-chrome"))
        }
    }

    @Test
    fun liveWikiNamespaceMergesOntoTheBundledCatalog() {
        val snapshot = osrsCalculatorCatalog.loadSnapshot(catalogJson())
        val first = org.json.JSONObject()
        first.put("title", "Calculator:Brand New Tool")
        first.put("pageid", 1)
        val second = org.json.JSONObject()
        second.put("title", "Calculator:Combat level/Template")
        second.put("pageid", 2)
        val live = org.json.JSONArray()
        live.put(first)
        live.put(second)
        val merged = osrsCalculatorCatalog.mergeLivePages(snapshot, live)
        assertTrue(merged.any { it.title == "Calculator:Brand New Tool" })
        assertFalse(merged.any { it.title == "Calculator:Combat level/Template" })
        assertEquals(
            merged.map { it.title }.toSet().size,
            merged.size
        )
    }

    @Test
    fun parseCacheKeysIgnoreQueryParameterOrder() {
        val left = osrsCalculatorParseCache.key(
            "GET",
            "https://oldschool.runescape.wiki/api.php?b=2&a=1",
            ""
        )
        val right = osrsCalculatorParseCache.key(
            "GET",
            "https://oldschool.runescape.wiki/api.php?a=1&b=2",
            ""
        )
        val fromBody = osrsCalculatorParseCache.key(
            "POST",
            "https://oldschool.runescape.wiki/api.php",
            "b=2&a=1"
        )
        val fromBodyReordered = osrsCalculatorParseCache.key(
            "POST",
            "https://oldschool.runescape.wiki/api.php",
            "a=1&b=2"
        )
        assertEquals(left, right)
        assertEquals(fromBody, fromBodyReordered)
    }

    @Test
    fun defaultCombatTemplateCallMatchesWikiGadgetSubmit() {
        val html = """
            <pre class="jcConfig">
            template = Calculator:Combat level/Template
            form = combatCalcForm
            result = combatCalcResult
            param = attack|Attack|1|int|1-99
            param = strength|Strength|1|int|1-99
            param = playername|Player name||hs|attack,1,1
            </pre>
        """.trimIndent()
        val wikitext = osrsCalculatorSaveWarmer.defaultTemplateCall(html)
        assertEquals(
            "{{Calculator:Combat level/Template|attack=1|strength=1}}",
            wikitext
        )
        val moduleHtml = """
            <pre class="jcConfig">
            module = Dry calc
            param = chance|Chance of drop|1/128|string|
            param = kills|Number of kills|128|int|1-inf
            </pre>
        """.trimIndent()
        assertEquals(
            "{{#invoke:Dry calc|main|chance=1/128|kills=128}}",
            osrsCalculatorSaveWarmer.defaultTemplateCall(moduleHtml)
        )
    }

    @Test
    fun articleHtmlLoadsBundledCalcCoreAndDoesNotHijackForms() {
        val html = File("src/main/java/com/omiyawaki/osrswiki/page/PageHtmlBuilder.kt").let {
            if (it.exists()) it else File("app/src/main/java/com/omiyawaki/osrswiki/page/PageHtmlBuilder.kt")
        }.readText()
        assertTrue(html.contains("mediawiki/gadget_calc_core.js"))
        assertTrue(html.contains("web/osrs_calculator_runtime.js"))
        assertTrue(html.contains("styles/gadget_calc.css"))

        val articleTools = File("../../../../platforms/ios/osrswiki/Assets/web/article_tools.js")
        val tools = if (articleTools.exists()) {
            articleTools.readText()
        } else {
            File("../../../platforms/ios/osrswiki/Assets/web/article_tools.js").readText()
        }
        assertFalse(tools.contains("document.querySelectorAll('pre.jcConfig').forEach(setupCalculator)"))
        assertTrue(tools.contains("Calculator forms are owned by ext.gadget.calc-core"))

        val calcCoreCandidates = listOf(
            File("src/main/assets/mediawiki/gadget_calc_core.js"),
            File("../../../shared/js/mediawiki/gadget_calc_core.js")
        )
        val calcCore = calcCoreCandidates.first { it.exists() }.readText()
        assertTrue(calcCore.contains("document.getElementById('bodyContent') || document.body"))
        assertTrue(calcCore.contains("osrsEnsureOOUITheme"))
        assertTrue(calcCore.contains("OO.ui.ButtonOptionWidget"))
        assertTrue(calcCore.contains("__osrsCalculatorPatched"))
        assertFalse(calcCore.contains("\$('#bodyContent')"))
        val runtimeCandidates = listOf(
            File("src/main/assets/web/osrs_calculator_runtime.js"),
            File("../../../shared/js/osrs_calculator_runtime.js")
        )
        val runtime = runtimeCandidates.first { it.exists() }.readText()
        assertTrue(runtime.contains("setTimeout(patchAjax, 25)"))
        assertTrue(runtime.contains("oojs-ui-widgets"))
        assertTrue(runtime.contains("ButtonOptionWidget"))
        assertTrue(runtime.contains("ToggleSwitchWidget"))
        assertTrue(runtime.contains("data-osrs-ooui-loader"))
        assertTrue(runtime.contains("/load.php?modules=oojs-ui-core"))
        assertTrue(runtime.contains("only=scripts"))
        assertTrue(runtime.contains("/load.php?modules=jquery&only=scripts"))
        assertTrue(runtime.contains("osrsHideCalculatorJsPlaceholder"))
        assertTrue(runtime.contains("osrsReassertCalculatorThemeSheets"))
        assertTrue(runtime.contains("osrs-calc-skin"))
        assertTrue(runtime.contains("osrsEnsureCalculatorPageVisible"))
        assertTrue(runtime.contains("osrsInstallNativeCalcSlot"))
        assertTrue(runtime.contains("osrsNativeCalcSetResult"))
        assertTrue(runtime.contains("osrs-native-calc-slot"))
        assertTrue(runtime.contains("osrsDedupeHiscoreRows"))
        assertTrue(runtime.contains("pointerdown"))
        assertTrue(runtime.contains("touchstart"))
        assertTrue(runtime.contains("mousedown"))
        assertTrue(runtime.contains("__osrsOpenCalcDropdown"))
        assertTrue(runtime.contains(".osrs-article-scroll-region, .osrs-local-scroll-surface"))
        assertTrue(calcCore.contains("data-osrs-calc-built"))
        assertTrue(runtime.contains("dynamic calculator requires JavaScript"))
        assertTrue(calcCore.contains("already implemented"))
        assertTrue(calcCore.contains("osrsRunModuleScript"))
        assertTrue(calcCore.contains("osrsMakeModuleRequire"))
        assertTrue(calcCore.contains("osrsInstallImplementedScript"))
        assertTrue(calcCore.contains("osrsEnsureMwHelpers"))
        assertTrue(calcCore.contains("mw.html.escape"))
        assertTrue(runtime.contains("osrsEnsureJQueryAlias"))
        assertTrue(calcCore.contains("setupCalc:"))
        assertTrue(calcCore.contains("ToggleSwitchWidget"))
        assertTrue(calcCore.contains("__osrsRebuildCalcs"))
        assertTrue(runtime.contains("osrsArmSmokeSubmit"))
        assertTrue(runtime.contains("aria-live"))
        assertTrue(runtime.contains("MutationObserver"))
        assertTrue(runtime.contains("[id\$=\"Form\"]"))
        assertTrue(runtime.contains("#bodyContent"))
        assertTrue(runtime.contains("scrollIntoView"))
    }

    @Test
    fun barrowsCalcWaitsForToggleSwitchGroupAndResolvesFormOutsideBodyContent() {
        val runtime = File("src/main/assets/web/osrs_calculator_runtime.js").readText()
        val calcCore = File("src/main/assets/mediawiki/gadget_calc_core.js").readText()
        val barrowsConfig = """
            template=Calculator:Barrows/Template
            form=BarrowsForm
            result=BarrowsResult
            param = Ahrim|Ahrim?|yes|check|yes,no
            param = toggleUnitKill|Select units killed instead of combat level sum|false|toggleswitch||unitKill
            param = unitKill|Barrows crypt units||group|bloodworm,cryptRat
        """.trimIndent()

        assertTrue(barrowsConfig.contains("|check|"))
        assertTrue(barrowsConfig.contains("|toggleswitch|"))
        assertTrue(barrowsConfig.contains("|group|"))
        assertTrue(calcCore.contains("'check'"))
        assertTrue(calcCore.contains("'toggleswitch'"))
        assertTrue(calcCore.contains("'group'"))
        assertTrue(calcCore.contains("typeof OO.ui.CheckboxInputWidget === 'function'"))
        assertTrue(calcCore.contains("OO.ui.ToggleSwitchWidget"))
        assertTrue(calcCore.contains("/\\|\\s*group\\s*\\|/i"))
        assertTrue(calcCore.contains("document.getElementById(self.form)"))
        val oouiReady = runtime.substringAfter("function osrsCalcOOUIReady()").substringBefore("function osrsLoadModuleScript")
        assertTrue(oouiReady.contains("CheckboxInputWidget"))
        assertTrue(oouiReady.contains("ToggleSwitchWidget"))
        assertTrue(oouiReady.contains("HorizontalLayout"))
        val coreSkip = runtime.substringAfter("modules=oojs-ui-core")
            .substringAfter("skip: function()")
            .substringBefore("src:")
        assertTrue(coreSkip.contains("ToggleSwitchWidget"))
        assertTrue(coreSkip.contains("CheckboxInputWidget"))
        assertTrue(coreSkip.contains("HorizontalLayout"))
        assertTrue(runtime.contains("osrsSanitizeResourceLoaderScript"))
        assertTrue(runtime.contains("window.OO=module.exports"))
    }

    @Test
    fun resourceLoaderOojsTrailerDoesNotAssignModuleExportsWhenModuleMissing() {
        val payload = """
            (function(global){var OO={initClass:function(){}};global.OO=OO;}(this));
            window.OO=module.exports;
            mw.loader.state({"oojs":"ready"});
        """.trimIndent()
        val sanitized = osrsResourceLoaderScript.sanitize(payload)
        assertTrue(payload.contains("window.OO=module.exports;"))
        assertFalse(sanitized.contains("window.OO=module.exports;"))
        assertTrue(sanitized.contains("typeof module!=='undefined'&&module.exports"))
        assertEquals(sanitized, osrsResourceLoaderScript.sanitize(sanitized))
    }

    @Test
    fun parseCacheIsServedOfflineByCalculatorProxy() {
        val context = RuntimeEnvironment.getApplication()
        val url = "https://oldschool.runescape.wiki/api.php?action=parse&text=%7B%7BCalculator:Combat+level/Template%7Cattack%3D1%7D%7D"
        val payload = """{"parse":{"text":{"*":"<p>Your combat level is 3</p>"}}}""".toByteArray()
        osrsCalculatorParseCache.write(context, "GET", url, "", payload)
        val proxied = osrsWikiWebViewProxy.request(context, "GET", url, null)
        assertTrue(proxied.getBoolean("ok"))
        assertTrue(proxied.getBoolean("cached"))
        assertTrue(proxied.getString("body").contains("Your combat level is 3"))
    }

    @Test
    fun hiscoresLookupFailureReturnsOkFalseInsteadOfThrowing() {
        val context = RuntimeEnvironment.getApplication()
        val result = osrsWikiWebViewProxy.request(
            context,
            "GET",
            "/cors/m=hiscore_oldschool/index_lite.ws?player=zzzznotaplayer",
            null
        )
        assertFalse(result.optBoolean("ok"))
        assertTrue(result.optString("error").isNotBlank())
    }

    private fun catalogJson(): String {
        val candidates = listOf(
            File("../../../shared/manifests/osrs-wiki-calculators.json"),
            File("../../../../shared/manifests/osrs-wiki-calculators.json"),
            File("src/main/assets/manifests/osrs-wiki-calculators.json")
        )
        val file = candidates.first { it.exists() }
        return file.readText()
    }
}
