package com.omiyawaki.osrswiki.page

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageHtmlPreprocessTocTest {
    private val vectorTocHtml = """
        <div class="mw-parser-output">
        <p>Lead with an unclosed image <img src="/images/clockwork.png" alt="Clockwork"></p>
        <div id="toc" class="toc" role="navigation" aria-labelledby="mw-toc-heading">
        <input type="checkbox" role="button" id="toctogglecheckbox" class="toctogglecheckbox" style="display:none">
        <div class="toctitle" lang="en" dir="ltr"><h2 id="mw-toc-heading">Contents</h2>
        <span class="toctogglespan"><label class="toctogglelabel" for="toctogglecheckbox"></label></span></div>
        <ul>
        <li class="toclevel-1 tocsection-1"><a href="#Skills"><span class="tocnumber">1</span> <span class="toctext">Skills</span></a></li>
        </ul>
        </div>
        <div class="mw-heading mw-heading2"><h2 id="Skills">Skills</h2></div>
        <p>Body</p>
        </div>
    """.trimIndent()

    @Test
    fun htmlBodyFragmentKeepsVectorContentsAfterUnclosedImages() {
        val document = Jsoup.parseBodyFragment(vectorTocHtml)
        val toc = document.selectFirst("#toc")
        assertNotNull(toc)
        assertEquals("Contents", document.selectFirst("#mw-toc-heading")?.text())
        assertTrue(document.selectFirst("#toc a[href=#Skills]") != null)
        assertEquals("Skills", document.selectFirst("#Skills")?.text())
    }

    @Test
    fun xmlParserIsUnsafeForWikiParseHtmlBecauseVoidTagsDoNotClose() {
        val xml = Jsoup.parse(vectorTocHtml, "", Parser.xmlParser())
        val headingInsideInput = xml.selectFirst("input #mw-toc-heading")
        assertTrue(
            "The XML parser must not be used for wiki HTML: void <input>/<img> tags swallow the Contents list.",
            headingInsideInput != null || xml.selectFirst("#toc") == null
        )
    }
}
