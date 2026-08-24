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
        assertEquals("enabled", definition.ui.autosubmit)
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
    fun nativeChromeIsAgilityOnlyAndFallsBackOnUnknownTypes() {
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
        assertTrue(osrsNativeCalcDefinition.isNativeChromeEligible(agility))
        assertFalse(osrsNativeCalcDefinition.isNativeChromeEligible(cooking))
        assertFalse(osrsNativeCalcDefinition.isNativeChromeEligible(unknown))
        assertFalse(osrsNativeCalcDefinition.isNativeChromeEligible(null))
        assertFalse(osrsNativeCalcDefinition.isNativeChromeEligible(osrsNativeCalcDefinition.parse("no config here")))
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
    fun introCopyAndDarkResultWrapperAvoidBlackOnDark() {
        val copy = osrsNativeCalcSession.introCopy(
            """
            ===Assumptions===
            * The bonus experience gained at the Agility Pyramid is only calculated for the current level.
            ===Calculator===
            """.trimIndent()
        )
        assertTrue(copy.contains("live wiki calculator"))
        assertTrue(copy.contains("Assumptions"))
        assertTrue(copy.contains("Agility Pyramid"))
        val dark = osrsNativeCalcSession.wrapResultHtml("<table><tr><td>Plank</td></tr></table>", true)
        assertTrue(dark.contains("#28221d"))
        assertTrue(dark.contains("#f4eaea"))
        assertFalse(dark.contains("background: #000"))
        assertFalse(dark.contains("color: #000"))
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
        val copy = osrsNativeCalcSession.introCopy(config, "Calculator:Combat level")
        assertTrue(copy.lowercase().contains("combat"))
        assertFalse(copy.contains("Agility"))
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
        assertFalse(osrsNativeCalcSession.hidesArticleShell(osrsNativeCalcSession.Phase.IDLE))
        assertFalse(osrsNativeCalcSession.hidesArticleShell(osrsNativeCalcSession.Phase.LOADING))
        assertFalse(osrsNativeCalcSession.hidesArticleShell(osrsNativeCalcSession.Phase.NATIVE))
        assertFalse(osrsNativeCalcSession.hidesArticleShell(osrsNativeCalcSession.Phase.SUBMITTING))
        assertFalse(osrsNativeCalcSession.hidesArticleShell(osrsNativeCalcSession.Phase.FALLBACK))
        val fragment = java.io.File("src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt")
        val source = fragment.readText()
        assertTrue(source.contains("osrsNativeCalcSession.hidesArticleShell"))
        assertFalse(
            source.contains("binding.articleSwipeRefresh.visibility = if (showNative) View.INVISIBLE else View.VISIBLE")
        )
        val runtimeCandidates = listOf(
            java.io.File("src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("app/src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("../../../shared/js/osrs_calculator_runtime.js")
        )
        val runtime = runtimeCandidates.first { it.exists() }.readText()
        assertTrue(runtime.contains("osrsInstallNativeCalcSlot"))
        assertTrue(runtime.contains("osrs-native-calc-slot"))
        assertTrue(runtime.contains("osrsNativeCalcSetResult"))
        assertTrue(runtime.contains("osrs-native-calc-slot-active"))
        assertTrue(runtime.contains("osrs-native-calc-slot-style"))
        assertTrue(runtime.contains(".osrs-calculator-panel"))
        assertTrue(runtime.contains(".oo-ui-textInputWidget"))
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
        val uninstall = runtime.substringAfter("window.osrsUninstallNativeCalcSlot = function")
            .substringBefore("function osrsInstallCalculatorKeyboardGuards")
        assertTrue(uninstall.contains("osrs-native-calc-slot-active"))
        assertTrue(uninstall.contains("osrs-native-calc-slot-style"))
    }

    @Test
    fun nativeCalcSelectUsesExposedDropdownMenu() {
        val candidates = listOf(
            java.io.File("src/main/java/com/omiyawaki/osrswiki/page/osrsNativeCalcView.kt"),
            java.io.File("app/src/main/java/com/omiyawaki/osrswiki/page/osrsNativeCalcView.kt")
        )
        val source = candidates.first { it.exists() }.readText()
        assertTrue(source.contains("END_ICON_DROPDOWN_MENU"))
        assertTrue(source.contains("MaterialAutoCompleteTextView"))
        assertTrue(source.contains("menu"))
    }
}
