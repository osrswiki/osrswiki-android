package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTableOfContentsExtractorTest {

    @Test
    fun extractIncludesCurrentMwHeadingMarkupAndLegacyMwHeadlineMarkup() {
        val html = """
            <div class="mw-parser-output">
                <div class="mw-heading mw-heading2"><h2 id="Recurring_methods">Recurring methods</h2></div>
                <p>Methods that reset over time.</p>
                <div class="mw-heading mw-heading3"><h3 id="Hourly_methods">Hourly methods</h3></div>
                <p>Examples.</p>
                <h2><span class="mw-headline" id="Legacy_section">Legacy section</span></h2>
                <h4 id="Ignored_subsection">Ignored subsection</h4>
            </div>
        """.trimIndent()

        val sections = PageTableOfContentsExtractor.extract(
            displayTitle = """<span class="mw-page-title-main">Money making guide</span>""",
            html = html
        )

        assertEquals(
            listOf("Money making guide", "Recurring methods", "Hourly methods", "Legacy section"),
            sections.map { it.title }
        )
        assertEquals(listOf(1, 2, 3, 2), sections.map { it.level })
        assertEquals(listOf("", "Recurring_methods", "Hourly_methods", "Legacy_section"), sections.map { it.anchor })
        assertTrue(sections.first().isLead)
    }

    @Test
    fun extractFallsBackToLeadOnlyWhenNoHeadingsExist() {
        val sections = PageTableOfContentsExtractor.extract(
            displayTitle = "Dragon",
            html = "<p>Dragon may refer to several articles.</p>"
        )

        assertEquals(listOf("Dragon"), sections.map { it.title })
    }

    @Test
    fun extractUsesASingleFloorDialectAndKeepsTheAuthoredHeadingId() {
        val html = """
            <div class="mw-heading mw-heading2">
                <h2 id="1st_floor2nd_floor">
                    <span class="floornumber">
                        <span class="floornumber-gb">1<sup>st</sup> floor<sup class="floornumber-help">[UK]</sup></span>
                        <span class="floornumber-us noexcerpt">2<sup>nd</sup> floor<sup class="floornumber-help">[US]</sup></span>
                    </span>
                </h2>
            </div>
            <div class="mw-heading mw-heading2">
                <h2 id="Basement">Basement</h2>
            </div>
        """.trimIndent()

        val sections = PageTableOfContentsExtractor.extract(
            "Heroes' Guild",
            html,
            osrsArticleFloorConvention.GB
        )

        assertEquals(listOf("Heroes' Guild", "1st floor", "Basement"), sections.map { it.title })
        assertEquals(listOf("", "1st_floor2nd_floor", "Basement"), sections.map { it.anchor })
        assertTrue(sections.none { it.title.contains("2nd") })
        assertTrue(sections.none { it.title.contains("[UK]") || it.title.contains("[US]") })
    }

    @Test
    fun extractUsesUsFloorDialectWhenTheDeviceLocaleUsesUsFloors() {
        val html = """
            <div class="mw-heading mw-heading2">
                <h2 id="1st_floor2nd_floor">
                    <span class="floornumber">
                        <span class="floornumber-gb">1<sup>st</sup> floor<sup class="floornumber-help">[UK]</sup></span>
                        <span class="floornumber-us noexcerpt">2<sup>nd</sup> floor<sup class="floornumber-help">[US]</sup></span>
                    </span>
                </h2>
            </div>
        """.trimIndent()

        val sections = PageTableOfContentsExtractor.extract(
            "Heroes' Guild",
            html,
            osrsArticleFloorConvention.US
        )

        assertEquals(listOf("Heroes' Guild", "2nd floor"), sections.map { it.title })
        assertTrue(sections.none { it.title.contains("1st") })
    }
}
