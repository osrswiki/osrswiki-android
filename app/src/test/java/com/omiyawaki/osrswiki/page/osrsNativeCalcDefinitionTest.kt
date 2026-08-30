package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsNativeCalcDefinitionTest {
    private val agilityConfig = """
        <pre class="jcConfig">
        template=Calculator:Skill calc/Template
        form=AgilityCalc
        result=AgilityResults
        name =
        param = name|Name||hs|XPInput,17,2;lvlInput,17,1
        param = currentToggle|Current: Level or Experience|Level|select|Level,Experience|Level=lvlInput;Experience=XPInput
        param = lvlInput|Current (per choice above)|1|int|1-126|
        param = XPInput|Current (per choice above)|1|int|1-200000000|
        param = goalToggle|Goal: Level or Experience?|Level|select|Level,Experience
        param = goal|Goal (per choice above)|0|int|0-200000000
        param = method|Method|All|select|All,Agility Course,Brimhaven Agility Arena,Rooftop Agility Course,Hallowed Sepulchre,Barbarian Fishing
        param = dataCriteria|Hide inaccessible methods|Show All|buttonselect|Show All,Hide,Greyed out
        param = leagueGroup|League multiplier?||toggleswitch|false|leagueMultiplier
        param = leagueMultiplier|League multiplier value?|5|int|5-32
        param = skill|Skill|Agility|hidden
        autosubmit = enabled
        </pre>
    """.trimIndent()

    @Test
    fun extractIncludesHiddenSkillAndLiveLabels() {
        val definition = osrsNativeCalcDefinition.parse(agilityConfig, "Calculator:Agility")
        assertNotNull(definition)
        requireNotNull(definition)
        assertEquals("Calculator:Agility", definition.id)
        assertEquals(osrsNativeCalcDefinition.InvokeKind.TEMPLATE, definition.invoke.kind)
        assertEquals("Calculator:Skill calc/Template", definition.invoke.template)
        assertEquals("AgilityCalc", definition.ui.formId)
        assertEquals("AgilityResults", definition.ui.resultId)
        assertEquals("on", definition.ui.autosubmit)
        assertEquals(
            listOf(
                "name", "currentToggle", "lvlInput", "XPInput", "goalToggle",
                "goal", "method", "dataCriteria", "leagueGroup", "leagueMultiplier", "skill"
            ),
            definition.inputs.map { it.name }
        )
        val skill = definition.inputs.first { it.name == "skill" }
        assertEquals(osrsNativeCalcDefinition.ParamType.HIDDEN, skill.type)
        assertEquals("Agility", skill.defaultValue)
        val current = definition.inputs.first { it.name == "currentToggle" }
        assertEquals("Current: Level or Experience", current.label)
        assertEquals(listOf("Level", "Experience"), current.options)
        assertEquals(listOf("lvlInput"), current.toggles["Level"])
        val method = definition.inputs.first { it.name == "method" }
        assertTrue(method.options.contains("Hallowed Sepulchre"))
        val data = definition.inputs.first { it.name == "dataCriteria" }
        assertEquals(osrsNativeCalcDefinition.ParamType.BUTTON_SELECT, data.type)
        assertEquals("Hide inaccessible methods", data.label)
    }

    @Test
    fun defaultInvokeIncludesHiddenSkillAndOmitsDisabledFields() {
        val definition = osrsNativeCalcDefinition.parse(agilityConfig, "Calculator:Agility")
        val wikitext = osrsNativeCalcDefinition.invokeWikitext(definition)
        assertNotNull(wikitext)
        requireNotNull(wikitext)
        assertTrue(wikitext.startsWith("{{Calculator:Skill calc/Template|"))
        assertTrue(wikitext.contains("|skill=Agility"))
        assertTrue(wikitext.contains("|currentToggle=Level"))
        assertTrue(wikitext.contains("|lvlInput=1"))
        assertFalse(wikitext.contains("|XPInput="))
        assertTrue(wikitext.contains("|goal=0"))
        assertTrue(wikitext.contains("|method=All"))
        assertTrue(wikitext.contains("|dataCriteria=Show All"))
        assertTrue(wikitext.contains("|leagueGroup=false"))
        assertFalse(wikitext.contains("|leagueMultiplier="))
        assertFalse(wikitext.contains("|name="))
        assertEquals(wikitext, osrsCalculatorSaveWarmer.defaultTemplateCall(agilityConfig))
    }

    @Test
    fun experienceToggleAndLeagueSwitchChangeSubmittedFields() {
        val definition = osrsNativeCalcDefinition.parse(agilityConfig, "Calculator:Agility")
        val wikitext = osrsNativeCalcDefinition.invokeWikitext(
            definition,
            mapOf(
                "currentToggle" to "Experience",
                "XPInput" to "200",
                "goalToggle" to "Level",
                "goal" to "99",
                "leagueGroup" to "true",
                "leagueMultiplier" to "8"
            )
        )
        requireNotNull(wikitext)
        assertTrue(wikitext.contains("|currentToggle=Experience"))
        assertTrue(wikitext.contains("|XPInput=200"))
        assertFalse(wikitext.contains("|lvlInput="))
        assertTrue(wikitext.contains("|goal=99"))
        assertTrue(wikitext.contains("|leagueGroup=true"))
        assertTrue(wikitext.contains("|leagueMultiplier=8"))
        assertTrue(wikitext.contains("|skill=Agility"))
    }

    @Test
    fun nativeChromeIsKitNotTitleAndFallsBackOnUnknownTypes() {
        val agility = osrsNativeCalcDefinition.parse(agilityConfig, "Calculator:Agility")
        val cooking = osrsNativeCalcDefinition.parse(
            agilityConfig.replace("Agility", "Cooking"),
            "Calculator:Cooking"
        )
        val unknown = osrsNativeCalcDefinition.parse(
            """
            <pre class="jcConfig">
            template = Calculator:Agility/Template
            param = voice|Voice of Seren|Amlodd|voiceofseren|
            param = skill|Skill|Agility|hidden
            </pre>
            """.trimIndent(),
            "Calculator:Agility"
        )
        val barrowsHtml = """
            <pre class="jcConfig">
            template=Calculator:Barrows/Template
            form=BarrowsForm
            result=BarrowsResult
            param = Ahrim|Ahrim?|yes|check|yes,no
            param = unitKill|Barrows crypt units||group|bloodworm,cryptRat
            </pre>
            """.trimIndent()
        val barrows = osrsNativeCalcDefinition.parse(barrowsHtml, "Calculator:Barrows")
        val coordinates = """
            <pre class="jcConfig">
            template = Calculator:Coordinates/PlanarToGeo
            param = x || 2441 | int | 1024-3967
            </pre>
            <pre class="jcConfig">
            template = Calculator:Coordinates/GeoToPlanar
            param = ndeg | Degrees N/S | 0 | int | 0-180
            </pre>
        """.trimIndent()
        val dry = """
            <pre class="jcConfig">
            module = Dry calc
            form = dryin
            result = dryout
            param = chance|Chance of drop|1/128|string|
            param = kills|Number of kills|128|int|1-inf
            param = dropped|Number of drops obtained thus far|0|int|0-inf
            </pre>
        """.trimIndent()
        assertTrue(osrsNativeCalcDefinition.isNativeChromeEligible(agility))
        assertTrue(osrsNativeCalcDefinition.isNativeChromeEligible(cooking))
        assertFalse(osrsNativeCalcDefinition.isNativeChromeEligible(unknown))
        assertTrue(osrsNativeCalcDefinition.isNativeChromeEligible(barrows))
        assertFalse(osrsNativeCalcDefinition.isNativeChromeEligible(null))
        assertFalse(osrsNativeCalcDefinition.isNativeChromeEligible(osrsNativeCalcDefinition.parse("no config here")))
        assertTrue(osrsNativeCalcDefinition.isPageNativeChromeEligible(agilityConfig, "Calculator:Agility"))
        assertTrue(osrsNativeCalcDefinition.isPageNativeChromeEligible(dry, "Calculator:Dry calc"))
        assertTrue(osrsNativeCalcDefinition.isPageNativeChromeEligible(barrowsHtml, "Calculator:Barrows"))
        assertEquals(2, osrsNativeCalcDefinition.countJcConfigs(coordinates))
        assertFalse(osrsNativeCalcDefinition.isPageNativeChromeEligible(coordinates, "Calculator:Coordinates"))
        assertTrue(osrsNativeCalcDefinition.isNativeChromeEligible(osrsNativeCalcDefinition.parse(coordinates, "Calculator:Coordinates")))
        assertEquals("on", osrsNativeCalcDefinition.normalizeAutosubmit("enabled"))
        assertEquals("on", osrsNativeCalcDefinition.normalizeAutosubmit("true"))
        assertEquals("off", osrsNativeCalcDefinition.normalizeAutosubmit("disabled"))
    }

    @Test
    fun leftoverSingleConfigPagesTakeNativeChrome() {
        val barrows = """
            <pre class="jcConfig">
            template=Calculator:Barrows/Template
            param = Ahrim|Ahrim?|yes|check|yes,no
            param = toggleUnitKill|Select units killed instead of combat level sum|false|toggleswitch||unitKill
            param = unitKill|Barrows crypt units||group|bloodworm,cryptRat
            param = bloodworm|Bloodworms killed|0|int|0-
            param = cryptRat|Crypt rats killed|0|int|0-
            </pre>
        """.trimIndent()
        val wrench = """
            <div class="jcConfig" style="display: none;">
            <p>template = Calculator:Prayer/Holy wrench/Template
            form = HWForm
            result = HWResult
            param = PrayerLevel|Prayer level|99|int|1-99|
            autosubmit = enabled
            </p>
            </div>
        """.trimIndent()
        val quests = """
            <div class="jcConfig">
            <p>template = Template:Recursive_Questreq
            param = 1|Quest name|While Guthix Sleeps|combobox|,A Kingdom Divided,A Night at the Theatre
            </p>
            </div>
        """.trimIndent()
        val rumours = """
            <pre class="jcConfig">
            template=Calculator:Hunter/Rumours/Template
            param = leaguesRegions|Select Leagues Regions?|false|toggleswitch||regionOptions|This will assume the Karamja and Varlamore regions are unlocked by default.
            param = regionOptions|Regions:||togglebuttongroup|Karamja,Desert,Fremennik
            </pre>
        """.trimIndent()
        val barrowsDef = osrsNativeCalcDefinition.parse(barrows, "Calculator:Barrows")!!
        val toggle = barrowsDef.inputs.first { it.name == "toggleUnitKill" }
        assertTrue(toggle.toggles.containsKey("true"))
        assertFalse(toggle.toggles.containsKey("false"))
        val off = osrsNativeCalcDefinition.invokeWikitext(barrowsDef)!!
        val on = osrsNativeCalcDefinition.invokeWikitext(barrowsDef, mapOf("toggleUnitKill" to "true"))!!
        assertFalse(off.contains("|bloodworm="))
        assertFalse(off.contains("|unitKill="))
        assertTrue(on.contains("|bloodworm="))
        assertFalse(on.contains("|unitKill="))
        assertTrue(osrsNativeCalcDefinition.isPageNativeChromeEligible(wrench, "Calculator:Prayer/Holy wrench"))
        val questDef = osrsNativeCalcDefinition.parse(quests, "Calculator:Recursive Quest Requirements")!!
        assertEquals(osrsNativeCalcDefinition.ParamType.COMBOBOX, questDef.inputs.first().type)
        assertTrue(questDef.inputs.first().options.contains("A Kingdom Divided"))
        val rumoursDef = osrsNativeCalcDefinition.parse(rumours, "Calculator:Hunter/Rumours")!!
        assertEquals("This will assume the Karamja and Varlamore regions are unlocked by default.", rumoursDef.inputs.first().help)
        assertEquals(osrsNativeCalcDefinition.ParamType.TOGGLE_BUTTON_GROUP, rumoursDef.inputs[1].type)
    }

    @Test
    fun parseResultDetectsScribuntoError() {
        assertTrue(
            osrsNativeCalcDefinition.parseResultIsError(
                "<div class=\"scribunto-error\">Lua error in Module:Skill_calc</div>"
            )
        )
        assertTrue(osrsNativeCalcDefinition.parseResultIsError(""))
        assertFalse(
            osrsNativeCalcDefinition.parseResultIsError(
                "<table class=\"wikitable\"><tr><td>Plank</td><td>1</td></tr></table>"
            )
        )
    }

    @Test
    fun fallbackReasons() {
        assertEquals(
            osrsNativeCalcDefinition.FallbackReason.MISSING_CONFIG,
            osrsNativeCalcDefinition.fallbackReason("Calculator:Agility", null, null)
        )
        assertEquals(
            osrsNativeCalcDefinition.FallbackReason.UNKNOWN_PARAM_TYPE,
            osrsNativeCalcDefinition.fallbackReason(
                "Calculator:Agility",
                osrsNativeCalcDefinition.parse(
                    """
                    <pre class="jcConfig">
                    template = Calculator:Agility/Template
                    param = voice|Voice of Seren|Amlodd|voiceofseren|
                    </pre>
                    """.trimIndent(),
                    "Calculator:Agility"
                ),
                null
            )
        )
        assertEquals(
            osrsNativeCalcDefinition.FallbackReason.PARSE_ERROR,
            osrsNativeCalcDefinition.fallbackReason(
                null,
                null,
                "<p class=\"scribunto-error\">Lua error</p>"
            )
        )
    }

    @Test
    fun combatLevelExtractAndDefaultInvokeReuseTheKit() {
        val config = """
            <pre class="jcConfig">
             template = Calculator:Combat level/Template
             form = combatCalcForm
             result = combatCalcResult
             param  = playername|Player name||hs|attack,1,1;strength,3,1;ranged,5,1;magic,7,1;defence,2,1;hitpoints,4,1;prayer,6,1
             param = attack|Attack|1|int|1-99
             param = strength|Strength|1|int|1-99
             param = ranged|Ranged|1|int|1-99
             param = magic|Magic|1|int|1-99
             param = defence|Defence|1|int|1-99
             param = hitpoints|Hitpoints|10|int|9-99
             param = prayer|Prayer|1|int|1-99
             autosubmit = enabled
            </pre>
        """.trimIndent()
        val definition = osrsNativeCalcDefinition.parse(config, "Calculator:Combat level")
        assertNotNull(definition)
        requireNotNull(definition)
        assertTrue(osrsNativeCalcDefinition.isNativeChromeEligible(definition))
        assertEquals("Combat level calculator", osrsNativeCalcDefinition.chromeTitle(definition.id))
        assertEquals("Calculator:Combat level/Template", definition.invoke.template)
        assertEquals(
            listOf("playername", "attack", "strength", "ranged", "magic", "defence", "hitpoints", "prayer"),
            definition.inputs.map { it.name }
        )
        assertTrue(definition.inputs.none { it.type == osrsNativeCalcDefinition.ParamType.HIDDEN })
        val hp = definition.inputs.first { it.name == "hitpoints" }
        assertEquals("10", hp.defaultValue)
        assertEquals(9, hp.minValue)
        assertEquals(99, hp.maxValue)
        val wikitext = osrsNativeCalcDefinition.invokeWikitext(definition)
        assertEquals(
            "{{Calculator:Combat level/Template|attack=1|strength=1|ranged=1|magic=1|defence=1|hitpoints=10|prayer=1}}",
            wikitext
        )
        assertFalse(wikitext!!.contains("|skill="))
        assertFalse(osrsNativeCalcDefinition.parseResultIsError("<p>Your combat level is 3, balanced.</p>"))
    }

    @Test
    fun hiscoresUnavailableMessageMatchesWikiGadget() {
        assertEquals(
            "The player \"zzzznotaplayer\" does not exist, is banned or unranked, or we couldn't fetch your hiscores. Please enter the data manually.",
            osrsNativeCalcDefinition.hiscoresUnavailableMessage("zzzznotaplayer")
        )
        assertEquals(
            osrsNativeCalcDefinition.hiscoresUnavailableMessage("Lynx Titan"),
            osrsNativeCalcDefinition.hiscoresUnavailableMessage("  Lynx Titan  ")
        )
    }

    @Test
    fun nameFieldEditsDoNotAutosubmit() {
        assertFalse(osrsNativeCalcDefinition.shouldAutosubmitOnEdit(osrsNativeCalcDefinition.ParamType.HS))
        assertFalse(osrsNativeCalcDefinition.shouldAutosubmitOnEdit(osrsNativeCalcDefinition.ParamType.RSN))
        assertFalse(osrsNativeCalcDefinition.shouldAutosubmitOnEdit(osrsNativeCalcDefinition.ParamType.STRING))
        assertTrue(osrsNativeCalcDefinition.shouldAutosubmitOnEdit(osrsNativeCalcDefinition.ParamType.SELECT))
        assertTrue(osrsNativeCalcDefinition.shouldAutosubmitOnEdit(osrsNativeCalcDefinition.ParamType.INT))
        assertTrue(osrsNativeCalcDefinition.shouldAutosubmitOnEdit(osrsNativeCalcDefinition.ParamType.TOGGLE_SWITCH))
    }

    @Test
    fun applyHiscoresMapsAgilityLevelAndXp() {
        val lines = MutableList(24) { "-1,-1,-1" }
        lines[17] = "100,60,273742"
        val applied = osrsNativeCalcDefinition.applyHiscores(
            lines.joinToString("\n"),
            "XPInput,17,2;lvlInput,17,1"
        )
        assertEquals("60", applied?.get("lvlInput"))
        assertEquals("273742", applied?.get("XPInput"))
    }

    @Test
    fun applyHiscoresRejectsMissingPlayerPayloads() {
        assertEquals(null, osrsNativeCalcDefinition.applyHiscores("", "lvlInput,17,1"))
        assertEquals(null, osrsNativeCalcDefinition.applyHiscores("404", "lvlInput,17,1"))
        assertEquals(null, osrsNativeCalcDefinition.applyHiscores("<html>not found</html>", "lvlInput,17,1"))
        val lookup = osrsNativeCalcDefinition.interpretHiscoresLookup(
            false,
            "",
            "zzzznotaplayer",
            "XPInput,17,2;lvlInput,17,1"
        )
        assertTrue(lookup is osrsNativeCalcDefinition.HiscoresLookup.Failed)
        val message = (lookup as osrsNativeCalcDefinition.HiscoresLookup.Failed).message
        assertTrue(message.contains("zzzznotaplayer"))
        assertTrue(message.contains("does not exist"))
    }

    @Test
    fun parseFailureStaysAsNativeBannerCopy() {
        val message = osrsNativeCalcDefinition.parseFailureMessage(
            "<p class=\"scribunto-error\">Lua error in Module:Skill_calc</p>"
        )
        assertTrue(message.isNotBlank())
        assertFalse(message.lowercase().contains("scribunto"))
    }

    @Test
    fun nativeCalcKeepsArticleWebViewAsPageShell() {
        val fragment = java.io.File("src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt")
        val source = fragment.readText()
        assertFalse(source.contains("osrsNativeCalcSession"))
        assertFalse(source.contains("osrsNativeCalcSlotGeometry"))
        assertFalse(source.contains("native_calc_host"))
        val layout = java.io.File("src/main/res/layout/fragment_page.xml").takeIf { it.exists() }
            ?: java.io.File("app/src/main/res/layout/fragment_page.xml")
        assertFalse(layout.readText().contains("native_calc_host"))
        val runtimeCandidates = listOf(
            java.io.File("src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("app/src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("../../../shared/js/osrs_calculator_runtime.js")
        )
        val runtime = runtimeCandidates.first { it.exists() }.readText()
        assertTrue(runtime.contains("osrsInstallNativeCalcSlot"))
        assertTrue(runtime.contains("osrsBootIndocCalc"))
        assertTrue(runtime.contains("osrs-native-calc-slot"))
        assertTrue(runtime.contains("osrsNativeCalcSetResult"))
        assertTrue(runtime.contains("osrs-native-calc-slot-active"))
        assertTrue(runtime.contains("osrs-native-calc-slot-style"))
        assertTrue(runtime.contains(".osrs-calculator-panel"))
        assertTrue(runtime.contains(".oo-ui-textInputWidget"))
        assertTrue(runtime.contains("collapsible-calculator"))
        assertTrue(runtime.contains("osrsWrapNativeCalcCalculatorBox"))
        assertTrue(runtime.contains("osrsNativeCalcSetCollapsed"))
        assertTrue(runtime.contains("osrsWrapCollapsible"))
        assertTrue(runtime.contains("osrsWrapWikitablesInRoot"))
        val collapsibleCandidates = listOf(
            java.io.File("src/main/assets/web/collapsible_content.js"),
            java.io.File("app/src/main/assets/web/collapsible_content.js"),
            java.io.File("../../../shared/js/collapsible_content.js")
        )
        val collapsible = collapsibleCandidates.first { it.exists() }.readText()
        assertTrue(collapsible.contains("window.osrsWrapCollapsible"))
        assertTrue(collapsible.contains("kind === 'calculator'"))
        assertTrue(collapsible.contains("allowInsideCalculator"))
        assertTrue(collapsible.contains("window.osrsWrapWikitablesInRoot"))
        assertTrue(runtime.contains("osrsWrapCollapsible({"))
    }

    @Test
    fun nativeCalcInstallSlotHidesLeftoverGadgetChrome() {
        val runtimeCandidates = listOf(
            java.io.File("src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("app/src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("../../../shared/js/osrs_calculator_runtime.js")
        )
        val runtime = runtimeCandidates.first { it.exists() }.readText()
        val install = runtime.substringAfter("window.osrsInstallNativeCalcSlot = function")
            .substringBefore("window.osrsNativeCalcSetSlotHeight")
        assertTrue(install.contains("osrs-native-calc-slot-active"))
        assertTrue(install.contains("osrs-native-calc-slot-style"))
        assertTrue(install.contains(".osrs-calculator-panel"))
        assertTrue(install.contains(".oo-ui-textInputWidget"))
        assertTrue(install.contains(".jsCalc-field"))
        assertTrue(runtime.contains("html.osrs-native-calc-slot-active"))
        val hideRootAt = install.indexOf("var hideRoot")
        val selectQueryAt = install.indexOf("hideRoot.querySelectorAll('select")
        assertTrue("install must assign hideRoot", hideRootAt >= 0)
        assertTrue("install must neutralize leftover gadget <select> nodes", selectQueryAt >= 0)
        assertTrue(
            "hideRoot must be assigned before querying selects, or slot install throws and Android WebView keeps the HTML Method picker",
            hideRootAt < selectQueryAt
        )
        assertTrue(install.contains("disabled"))
        assertTrue(install.contains("removeChild") || install.contains(".remove("))
        assertTrue(install.contains("MutationObserver"))
        assertTrue(install.contains("waiting: true"))
        val uninstall = runtime.substringAfter("window.osrsUninstallNativeCalcSlot = function")
            .substringBefore("function osrsInstallCalculatorKeyboardGuards")
        assertTrue(uninstall.contains("osrs-native-calc-slot-active"))
        assertTrue(uninstall.contains("osrs-native-calc-slot-style"))
    }

    @Test
    fun agilitySelectLabelsRenderFromJcConfig() {
        val definition = osrsNativeCalcDefinition.parse(agilityConfig, "Calculator:Agility")!!
        assertEquals(
            listOf(
                "Current: Level or Experience",
                "Goal: Level or Experience?",
                "Method"
            ),
            definition.inputs.filter { it.type == osrsNativeCalcDefinition.ParamType.SELECT }.map { it.label }
        )
        val indoc = listOf(
            java.io.File("src/main/assets/web/osrs_native_calc_indoc.js"),
            java.io.File("app/src/main/assets/web/osrs_native_calc_indoc.js"),
            java.io.File("../../../shared/js/osrs_native_calc_indoc.js")
        ).first { it.exists() }.readText()
        assertTrue(indoc.contains("osrs-indoc-calc-form"))
        assertTrue(indoc.contains("role=\"form\""))
        assertTrue(indoc.contains("aria-label"))
        val runtime = listOf(
            java.io.File("src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("app/src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("../../../shared/js/osrs_calculator_runtime.js")
        ).first { it.exists() }.readText()
        assertTrue(runtime.contains("showChoicePicker"))
    }

    @Test
    fun indocOwnsTheSlotWithoutNativeOverlay() {
        val fragment = java.io.File("src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt")
        val source = fragment.readText()
        assertFalse(source.contains("osrsNativeCalcView"))
        assertFalse(source.contains("PopupWindow"))
        val runtime = listOf(
            java.io.File("src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("app/src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("../../../shared/js/osrs_calculator_runtime.js")
        ).first { it.exists() }.readText()
        assertTrue(runtime.contains("osrsBootIndocCalc"))
        assertTrue(runtime.contains("data-osrs-indoc-calc"))
        val indoc = listOf(
            java.io.File("src/main/assets/web/osrs_native_calc_indoc.js"),
            java.io.File("app/src/main/assets/web/osrs_native_calc_indoc.js"),
            java.io.File("../../../shared/js/osrs_native_calc_indoc.js")
        ).first { it.exists() }.readText()
        assertTrue(indoc.contains("osrs-indoc-calc-form"))
        assertTrue(indoc.contains("isPageEligible"))
        assertTrue(indoc.contains("countJcConfigs"))
        assertFalse(indoc.contains("isAllowlisted"))
    }

    @Test
    fun indocEnterKeyHintIsGoOnHiscoresAndDoneOnOtherFields() {
        val indoc = listOf(
            java.io.File("src/main/assets/web/osrs_native_calc_indoc.js"),
            java.io.File("app/src/main/assets/web/osrs_native_calc_indoc.js"),
            java.io.File("../../../shared/js/osrs_native_calc_indoc.js")
        ).first { it.exists() }.readText()
        assertTrue(indoc.contains("input.type === 'hs' ? 'go' : 'done'"))
        assertTrue(indoc.contains("enterkeyhint=\"go\"" ) || indoc.contains("enterkeyhint=\"' + enterHint + '\""))
        assertTrue(indoc.contains("enterkeyhint=\"done\""))
        assertTrue(indoc.contains("data-osrs-indoc-type"))
        val runtime = listOf(
            java.io.File("src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("app/src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("../../../shared/js/osrs_calculator_runtime.js")
        ).first { it.exists() }.readText()
        val bind = runtime.substringAfter("function bind() {")
            .substringBefore("form.addEventListener('change'")
        assertTrue(bind.contains("isIndocEnterKey"))
        assertTrue(bind.contains("keydown"))
        assertTrue(bind.contains("fieldTypeFor(target.name) === 'hs'"))
        assertTrue(bind.contains("lookupHiscores()"))
        assertTrue(bind.contains("dismissIndocKeyboard"))
        assertTrue(bind.contains("preventDefault"))
        val keydown = bind.substringAfter("form.addEventListener('keydown'")
            .substringBefore("form.addEventListener('click'")
        assertTrue(keydown.contains("lookupHiscores()"))
        assertFalse(keydown.contains("data-osrs-indoc-step"))
        assertTrue(keydown.contains("isIndocTextOrNumberField"))
        val click = bind.substringAfter("form.addEventListener('click'")
        assertTrue(click.contains("data-osrs-indoc-lookup"))
        assertTrue(click.contains("lookupHiscores()"))
        assertTrue(runtime.contains("existingHint !== 'go' && existingHint !== 'search'"))
    }

    @Test
    fun indocLookupFailWritesResultErrorIconAndPublisherIgnoresStatus() {
        val indoc = listOf(
            java.io.File("src/main/assets/web/osrs_native_calc_indoc.js"),
            java.io.File("app/src/main/assets/web/osrs_native_calc_indoc.js"),
            java.io.File("../../../shared/js/osrs_native_calc_indoc.js")
        ).first { it.exists() }.readText()
        assertTrue(indoc.contains("function lookupErrorHtml"))
        assertTrue(indoc.contains("osrs-indoc-calc-error-icon"))
        assertTrue(indoc.contains("<strong class=\"error\">"))
        val runtime = listOf(
            java.io.File("src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("app/src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("../../../shared/js/osrs_calculator_runtime.js")
        ).first { it.exists() }.readText()
        val lookup = runtime.substringAfter("function lookupHiscores() {")
            .substringBefore("function fieldTypeFor(name)")
        assertTrue(lookup.contains("clearLookupOutput()"))
        assertTrue(lookup.indexOf("clearLookupOutput()") < lookup.indexOf("osrsIndocRequest("))
        assertTrue(lookup.contains("showLookupError(player)"))
        val publish = runtime.substringAfter("function osrsPublishCalculatorResult() {")
            .substringBefore("function osrsRevealCalculatorNode")
        assertFalse(publish.contains("innerText"))
        assertFalse(publish.contains("document.body"))
        assertTrue(publish.contains("osrsCalculatorResultSourceNode()"))
        assertTrue(runtime.contains("node.id === 'osrs-calculator-status'"))
    }
}
