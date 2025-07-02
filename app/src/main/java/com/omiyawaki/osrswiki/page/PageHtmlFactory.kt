package com.omiyawaki.osrswiki.page

class PageHtmlFactory {
    fun createPageHtml(state: PageUiState, pageTitleArg: String?): String {
        val htmlBodySnippet = state.htmlContent ?: return ""

        // Get the title and format it for display.
        val titleToDisplay = (state.plainTextTitle ?: pageTitleArg)?.replace("_", " ")

        // Create the HTML for the title header.
        val titleHtml = if (!titleToDisplay.isNullOrEmpty()) {
            // This style is copied from the h1/h2 style in wiki_content.css to ensure consistency.
            // Using single quotes for the font names is valid in a CSS style attribute.
            val fontFamily = "'PT Serif', 'Palatino', 'Georgia', serif"
            val style = """
                margin-top: 0;
                color: var(--heading-color);
                font-family: $fontFamily;
                border-bottom: 1px solid var(--sidebar-color);
                padding-bottom: 0.2em;
                margin-bottom: 0.6em;
                line-height: 1.3;
                font-size: 1.8em;
            """.trimIndent().replace(Regex("\\s+"), " ")

            "<h1 style=\"$style\">$titleToDisplay</h1>"
        } else {
            ""
        }

        // Prepend the title's HTML to the main content snippet.
        return titleHtml + htmlBodySnippet
    }
}
