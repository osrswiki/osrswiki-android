package com.omiyawaki.osrswiki.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface FeedbackWorkerApiService {

    /**
     * Creates a GitHub issue via the Cloudflare feedback Worker.
     * The Worker handles GitHub authentication securely server-side.
     */
    @POST("createGithubIssue")
    suspend fun createGithubIssue(
        @Body request: FeedbackWorkerIssueRequest
    ): FeedbackWorkerResponse
}

@Serializable
data class FeedbackWorkerIssueRequest(
    val title: String,
    val body: String,
    val labels: List<String>? = null,
    val platform: String? = null,
    val appVersion: String? = null,
    val distribution: String? = null,
)

@Serializable
data class FeedbackWorkerResponse(
    val message: String
)
