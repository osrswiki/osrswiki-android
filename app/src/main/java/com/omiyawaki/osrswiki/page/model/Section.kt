package com.omiyawaki.osrswiki.page.model

import kotlinx.serialization.Serializable

@Serializable
data class Section(
    val id: Int,
    val level: Int,
    val anchor: String,
    val title: String
) {
    val isLead: Boolean
        get() = id == 0
}
