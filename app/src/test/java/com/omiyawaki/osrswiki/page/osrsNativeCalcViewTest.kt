package com.omiyawaki.osrswiki.page

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class osrsNativeCalcViewTest {
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
    fun failedOsamosisLookupBindsWikiSentenceOntoVisibleBanner() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_OSRSWiki_OSRSLight
        )
        val view = osrsNativeCalcView(context)
        lateinit var session: osrsNativeCalcSession
        session = osrsNativeCalcSession(context) {
            view.bind(session)
        }
        val definition = osrsNativeCalcDefinition.parse(agilityConfig, "Calculator:Agility")
        assertNotNull(definition)
        session.seedNativeStateForTesting(
            definition!!,
            definition.inputs.associate { it.name to it.defaultValue },
            "Plank"
        )
        view.bind(session)
        session.setValue("name", "osamosis", submit = false)
        assertEquals("osamosis", session.values["name"])
        assertEquals(osrsNativeCalcSession.Phase.NATIVE, session.phase)
        assertEquals("Plank", session.resultHtml)
        session.applyLookupResult(
            ok = false,
            body = "",
            player = "osamosis",
            mapping = "XPInput,17,2;lvlInput,17,1"
        )
        val banner = view.findViewById<TextView>(R.id.native_calc_error)
        assertNotNull(banner)
        assertEquals(View.VISIBLE, banner.visibility)
        val text = banner.text.toString()
        assertTrue(text.contains("osamosis"))
        assertTrue(text.contains("does not exist, is banned or unranked"))
        assertEquals(
            osrsNativeCalcDefinition.hiscoresUnavailableMessage("osamosis"),
            text
        )
        assertEquals(text, banner.contentDescription)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, banner.importantForAccessibility)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, 1080, view.measuredHeight)
        assertTrue("error banner must have on-screen height", banner.height > 0)
        val visible = android.graphics.Rect()
        assertTrue(banner.getGlobalVisibleRect(visible))
        assertTrue(visible.height() > 0)
        assertFalse(osrsNativeCalcSession.hidesArticleShell(session.phase))
    }

    @Test
    fun nameEditsDoNotNotifyOrClearParseBackedResult() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_OSRSWiki_OSRSLight
        )
        var changes = 0
        val session = osrsNativeCalcSession(context) { changes++ }
        val definition = osrsNativeCalcDefinition.parse(agilityConfig, "Calculator:Agility")
        assertNotNull(definition)
        session.seedNativeStateForTesting(
            definition!!,
            definition.inputs.associate { it.name to it.defaultValue },
            "Plank"
        )
        changes = 0
        session.setValue("name", "osa", submit = false)
        session.setValue("name", "osamo", submit = false)
        assertEquals(0, changes)
        assertEquals(osrsNativeCalcSession.Phase.NATIVE, session.phase)
        assertEquals("Plank", session.resultHtml)
        assertEquals("osamo", session.values["name"])
    }

    @Test
    fun agilitySelectLabelsAndMenusExposeEveryJcConfigOption() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_OSRSWiki_OSRSLight
        )
        val view = osrsNativeCalcView(context)
        lateinit var session: osrsNativeCalcSession
        session = osrsNativeCalcSession(context) { view.bind(session) }
        val definition = osrsNativeCalcDefinition.parse(agilityConfig, "Calculator:Agility")
        assertNotNull(definition)
        session.seedNativeStateForTesting(
            definition!!,
            definition.inputs.associate { it.name to it.defaultValue },
            "Plank"
        )
        view.bind(session)
        val wanted = listOf(
            "Current: Level or Experience",
            "Goal: Level or Experience?",
            "Method"
        )
        val labels = mutableListOf<String>()
        collectText(view, labels)
        wanted.forEach { label ->
            assertTrue("missing label $label in $labels", labels.contains(label))
        }
        val dropdowns = mutableListOf<MaterialAutoCompleteTextView>()
        collectDropdowns(view, dropdowns)
        assertTrue("expected Agility select menus, found ${dropdowns.size}", dropdowns.size >= 3)
        dropdowns.forEach { dropdown ->
            dropdown.showDropDown()
            val adapter = dropdown.adapter
            assertNotNull(adapter)
            assertTrue(
                "menu must list every option, not only the first item: ${adapter!!.count}",
                adapter.count > 1
            )
        }
        val method = definition.inputs.first { it.name == "method" }
        val methodDropdown = dropdowns.first { it.contentDescription?.toString()?.contains("Method") == true }
        methodDropdown.showDropDown()
        assertEquals(method.options.size, methodDropdown.adapter.count)
        assertTrue(method.options.contains("Hallowed Sepulchre"))
        assertTrue(method.options.contains("Barbarian Fishing"))
        val menus = mutableListOf<android.view.View>()
        collectMenuButtons(view, menus)
        val methodMenu = menus.first { it.contentDescription?.toString()?.contains("Method menu") == true }
        assertEquals(method.options, methodMenu.tag)
        methodMenu.performClick()
        val opened = mutableListOf<String>()
        collectText(view, opened)
        method.options.forEach { option ->
            assertTrue(
                "Method menu must list $option in the native form, not a WebView picker: $opened",
                opened.contains(option)
            )
        }
    }

    @Test
    fun collapsibleCalculatorBoxHidesBodyWithoutInnerScroller() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_OSRSWiki_OSRSLight
        )
        val view = osrsNativeCalcView(context)
        lateinit var session: osrsNativeCalcSession
        session = osrsNativeCalcSession(context) { view.bind(session) }
        val definition = osrsNativeCalcDefinition.parse(agilityConfig, "Calculator:Agility")
        assertNotNull(definition)
        session.seedNativeStateForTesting(
            definition!!,
            definition.inputs.associate { it.name to it.defaultValue },
            "Plank"
        )
        view.bind(session)
        val overflow = view.findViewById<android.view.View>(R.id.native_calc_overflow)
        val form = view.findViewById<android.view.View>(R.id.native_calc_form)
        val header = view.findViewById<TextView>(R.id.native_calc_header)
        assertNotNull(overflow)
        assertNotNull(form)
        assertEquals("calculator", view.contentDescription)
        assertEquals(
            "article header owns toggle; native overlay must not paint a one-off header",
            null,
            header
        )
        assertFalse(
            "article owns vertical scrolling; chrome is intrinsic like an on-wiki collapsible",
            overflow is androidx.core.widget.NestedScrollView
        )
        assertEquals(android.view.View.VISIBLE, overflow.visibility)
        view.setCollapsed(true)
        assertTrue(view.collapsed)
        assertEquals(android.view.View.GONE, overflow.visibility)
        view.setCollapsed(false)
        assertEquals(android.view.View.VISIBLE, overflow.visibility)
        form.minimumHeight = 2400
        form.minimumWidth = 2400
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, 400, view.measuredHeight)
        assertEquals(
            "overflow wraps the form; it must not be a MATCH_PARENT inner scroller",
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            overflow.layoutParams.height
        )
        assertFalse(
            "Agility must not inner-pan; the article WebView scrolls through the chrome",
            overflow.canScrollVertically(1)
        )
        assertFalse(
            "wider-than-box chrome clips like article tables and must not steal vertical pans",
            overflow.canScrollHorizontally(1)
        )
        val editors = mutableListOf<android.widget.EditText>()
        fun collectEditors(v: android.view.View) {
            if (v is android.widget.EditText) editors.add(v)
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) collectEditors(v.getChildAt(i))
            }
        }
        collectEditors(view)
        assertTrue("Agility Name/level fields must exist", editors.isNotEmpty())
        editors.forEach { editor ->
            assertTrue(
                "fields are interactive controls; hitClickable must see them",
                editor.isClickable && editor.isFocusableInTouchMode
            )
        }
        assertTrue("wide form must clip to the collapsible box width", form.width <= overflow.width || overflow.width == 400)
        view.setCollapsed(true)
        view.setCollapsed(false)
        assertFalse(view.collapsed)
        assertEquals(android.view.View.VISIBLE, overflow.visibility)
    }

    @Test
    fun shippedCollapsibleAndRuntimeWrapCalcLikeArticleTables() {
        val collapsible = listOf(
            java.io.File("src/main/assets/web/collapsible_content.js"),
            java.io.File("app/src/main/assets/web/collapsible_content.js")
        ).first { it.exists() }.readText()
        val runtime = listOf(
            java.io.File("src/main/assets/web/osrs_calculator_runtime.js"),
            java.io.File("app/src/main/assets/web/osrs_calculator_runtime.js")
        ).first { it.exists() }.readText()
        assertTrue(collapsible.contains("window.osrsWrapCollapsible"))
        assertTrue(collapsible.contains("collapsible-header"))
        assertTrue(collapsible.contains("collapsible-label"))
        assertTrue(collapsible.contains("collapsible-state"))
        assertTrue(collapsible.contains("osrs-disclosure-body"))
        assertTrue(collapsible.contains("kind === 'calculator'"))
        assertTrue(collapsible.contains("allowInsideCalculator"))
        assertTrue(collapsible.contains("window.osrsWrapWikitablesInRoot"))
        assertTrue(collapsible.contains("window.osrsToggleCollapsible"))
        assertTrue(runtime.contains("osrsWrapCollapsible"))
        assertTrue(runtime.contains("osrsWrapWikitablesInRoot"))
        assertTrue(runtime.contains("osrsPlaceNativeCalcResultInBox"))
        assertFalse(
            "calc box must keep article chrome, not a transparent full-bleed override",
            runtime.contains("html.osrs-native-calc-slot-active .collapsible-calculator {") &&
                runtime.contains("background:transparent!important;background-color:transparent!important")
        )
        val fragment = java.io.File("src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt").takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt")
        val source = fragment.readText()
        assertTrue(source.contains("setCollapsed(parsed.collapsed)"))
        assertTrue(source.contains("popupMayShow"))
        assertFalse(source.contains("osrsNativeCalcSetCollapsed(\$collapsed)"))
    }

    private fun collectText(view: android.view.View, into: MutableList<String>) {
        when (view) {
            is TextView -> into.add(view.text.toString())
            is android.view.ViewGroup -> {
                for (i in 0 until view.childCount) collectText(view.getChildAt(i), into)
            }
        }
        view.contentDescription?.toString()?.let { if (it.isNotBlank()) into.add(it) }
    }

    private fun collectDropdowns(view: android.view.View, into: MutableList<MaterialAutoCompleteTextView>) {
        if (view is MaterialAutoCompleteTextView) into.add(view)
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) collectDropdowns(view.getChildAt(i), into)
        }
    }

    private fun collectMenuButtons(view: android.view.View, into: MutableList<android.view.View>) {
        if (view.contentDescription?.toString()?.endsWith(" menu") == true) into.add(view)
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) collectMenuButtons(view.getChildAt(i), into)
        }
    }
}
