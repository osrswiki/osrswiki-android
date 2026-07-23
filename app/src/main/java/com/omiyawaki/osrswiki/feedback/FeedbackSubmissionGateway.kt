package com.omiyawaki.osrswiki.feedback

import android.content.Context

interface FeedbackSubmissionGateway {
    suspend fun reportIssue(
        context: Context,
        title: String,
        description: String
    ): Result<String>

    suspend fun requestFeature(
        context: Context,
        title: String,
        description: String
    ): Result<String>
}

object FeedbackSubmissionGatewayRegistry {
    @Volatile
    var gateway: FeedbackSubmissionGateway = SecureFeedbackRepository()

    fun reset() {
        gateway = SecureFeedbackRepository()
    }
}
