package com.omiyawaki.osrswiki.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPolicyContentFormatterTest {

    @Test
    fun splitsPolicyIntoParagraphSizedSections() {
        val sections = PrivacyPolicyContentFormatter.sections(
            """
            OSRS Wiki App Privacy Policy

            The app respects privacy.

            1. INFORMATION WE COLLECT

            Audio Data:
            - Voice recordings for search
            """.trimIndent()
        )

        assertEquals(4, sections.size)
        assertEquals("OSRS Wiki App Privacy Policy", sections[0].text)
        assertEquals("The app respects privacy.", sections[1].text)
        assertEquals("1. INFORMATION WE COLLECT", sections[2].text)
        assertEquals("Audio Data:\n- Voice recordings for search", sections[3].text)
    }

    @Test
    fun marksDocumentAndNumberedSectionTitlesAsHeadings() {
        val sections = PrivacyPolicyContentFormatter.sections(
            """
            OSRS Wiki App Privacy Policy

            Body paragraph.

            2. HOW WE USE YOUR INFORMATION
            """.trimIndent()
        )

        assertTrue(sections[0].isHeading)
        assertFalse(sections[1].isHeading)
        assertTrue(sections[2].isHeading)
    }
}
