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
}
