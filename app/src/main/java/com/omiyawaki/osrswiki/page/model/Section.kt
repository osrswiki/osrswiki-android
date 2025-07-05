package com.omiyawaki.osrswiki.page.model

import kotlinx.serialization.Serializable

@Serializable
data class Section(
    val id: Int,
    val level: Int,
    val anchor: String,
    val title: String,
    val isItalic: Boolean,
    val isBold: Boolean
) {
    val isLead: Boolean
        get() = id == 0
}

@Serializable
data class LeadSectionDetails(
    val title: String,
    val isItalic: Boolean,
    val isBold: Boolean
)

@Serializable
data class TocData(
    // Making this nullable prevents crashes if the JS cannot find the lead section.
    val leadSectionDetails: LeadSectionDetails? = null,
    val sections: List<Section>
)
