package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArticleFloorNumberCssContractTest {

    @Test
    fun bundledWikiCssDoesNotForceBothFloorDialectsVisible() {
        listOf(
            asset("styles/wiki-integration.css"),
            asset("styles/modules/other.css")
        ).forEach { css ->
            val gbHideRules = Regex(
                """[^{}]*\.floornumber-setting-gb #toc li a span\.toctext span span:nth-child\(2\)[^{]*\{[^}]*\}"""
            ).findAll(css).map { it.value }.toList()
            assertTrue(gbHideRules.isNotEmpty())
            gbHideRules.forEach { rule ->
                assertTrue(
                    "GB locale must hide the second in-page TOC floor dialect. Rule: $rule",
                    rule.contains("display: none")
                )
            }
        }
    }

    @Test
    fun laterFixesKeepOneFloorDialectAndShowConventionHelpMarks() {
        val fixes = asset("styles/fixes.css")
        assertTrue(fixes.contains(".floornumber-setting-us .floornumber .floornumber-gb"))
        assertTrue(fixes.contains(".floornumber-setting-gb .floornumber .floornumber-us"))
        assertTrue(fixes.contains("display: none !important"))
        assertTrue(fixes.contains(".floornumber-help"))
        assertTrue(fixes.contains("cursor: pointer"))
        assertFalse(fixes.contains(".mw-halign-left > figcaption"))
        assertFalse(fixes.contains(".mw-halign-right > figcaption"))
        assertTrue(fixes.contains("ul.gallery"))
        assertTrue(fixes.contains("list-style: none !important"))
    }

    private fun asset(path: String): String {
        val file = File("src/main/assets/$path").takeIf { it.exists() }
            ?: File("app/src/main/assets/$path")
        return file.readText()
    }
}
