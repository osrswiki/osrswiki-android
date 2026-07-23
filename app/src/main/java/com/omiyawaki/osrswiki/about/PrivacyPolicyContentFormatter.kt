package com.omiyawaki.osrswiki.about

object PrivacyPolicyContentFormatter {
    data class Section(
        val text: String,
        val isHeading: Boolean
    )

    private val numberedHeading = Regex("""^\d+\.\s+[A-Z0-9'’\-\s]+$""")

    fun sections(rawContent: String): List<Section> {
        return rawContent
            .split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, text ->
                Section(
                    text = text,
                    isHeading = index == 0 || numberedHeading.matches(text)
                )
            }
    }
}
