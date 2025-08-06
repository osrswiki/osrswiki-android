package com.omiyawaki.osrswiki.network

import com.omiyawaki.osrswiki.network.model.github.CreateIssueRequest
import com.omiyawaki.osrswiki.network.model.github.IssueResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface GitHubApiService {
    
    /**
     * Creates a new issue in the specified GitHub repository.
     * Requires authentication via Authorization header.
     */
    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateIssueRequest
    ): IssueResponse
}