package com.omiyawaki.osrswiki.network.model.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IssueResponse(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    @SerialName("html_url")
    val htmlUrl: String,
    val labels: List<Label> = emptyList(),
    @SerialName("created_at")
    val createdAt: String
)

@Serializable
data class Label(
    val id: Long,
    val name: String,
    val color: String
)