package com.omiyawaki.osrswiki.network.model.github

import kotlinx.serialization.Serializable

@Serializable
data class CreateIssueRequest(
    val title: String,
    val body: String,
    val labels: List<String> = emptyList()
)