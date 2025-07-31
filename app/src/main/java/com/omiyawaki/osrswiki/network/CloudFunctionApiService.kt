package com.omiyawaki.osrswiki.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface CloudFunctionApiService {
    
    /**
     * Creates a GitHub issue via the secure Cloud Function endpoint.
     * The Cloud Function handles GitHub authentication securely server-side.
     */
    @POST("createGithubIssue")
    suspend fun createGithubIssue(
        @Body request: CloudFunctionIssueRequest
    ): CloudFunctionResponse
}

@Serializable
data class CloudFunctionIssueRequest(
    val title: String,
    val body: String,
    val labels: List<String>? = null
)

@Serializable
data class CloudFunctionResponse(
    val message: String
)