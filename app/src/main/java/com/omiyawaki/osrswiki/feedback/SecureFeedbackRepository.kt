package com.omiyawaki.osrswiki.feedback

import android.content.Context
import android.os.Build
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.network.FeedbackWorkerApiService
import com.omiyawaki.osrswiki.network.FeedbackWorkerIssueRequest
import com.omiyawaki.osrswiki.network.FeedbackWorkerRetrofitClient
import com.omiyawaki.osrswiki.util.log.L

/**
 * Repository for securely submitting feedback via the Cloudflare feedback Worker.
 * This approach keeps the GitHub API token secure on the server side.
 */
class SecureFeedbackRepository : FeedbackSubmissionGateway {

    private val feedbackWorkerApi: FeedbackWorkerApiService = FeedbackWorkerRetrofitClient.apiService
    
    companion object {
        private const val LABEL_BUG = "bug"
        private const val LABEL_ENHANCEMENT = "enhancement"
    }

    /**
     * Creates a bug report issue via the feedback Worker
     */
    override suspend fun reportIssue(
        context: Context,
        title: String,
        description: String
    ): Result<String> {
        return submitFeedback(title, description, LABEL_BUG)
    }

    /**
     * Creates a feature request issue via the feedback Worker
     */
    override suspend fun requestFeature(
        context: Context,
        title: String,
        description: String
    ): Result<String> {
        return submitFeedback(title, description, LABEL_ENHANCEMENT)
    }
    
    private suspend fun submitFeedback(
        title: String,
        description: String,
        label: String
    ): Result<String> {
        return try {
            val deviceInfo = getDeviceInfo()
            val fullBody = buildString {
                appendLine(description)
                appendLine()
                appendLine("---")
                appendLine("**Device Information:**")
                appendLine(deviceInfo)
            }
            
            val request = FeedbackWorkerIssueRequest(
                title = title,
                body = fullBody,
                labels = listOf(label),
                platform = "android",
                appVersion = BuildConfig.VERSION_NAME,
                distribution = BuildConfig.FLAVOR,
            )
            
            L.d("SecureFeedbackRepository: Submitting feedback via feedback Worker")
            val response = feedbackWorkerApi.createGithubIssue(request)
            L.d("SecureFeedbackRepository: Feedback submitted successfully: ${response.message}")
            
            // Return a success message since we don't get the issue URL from the Worker
            Result.success("Your feedback has been submitted successfully!")
        } catch (e: Exception) {
            L.e("SecureFeedbackRepository: Error submitting feedback", e)
            when (e) {
                is retrofit2.HttpException -> {
                    when (e.code()) {
                        400 -> Result.failure(Exception("Invalid request. Please check your input."))
                        500 -> Result.failure(Exception("Server error. Please try again later."))
                        else -> Result.failure(Exception("Failed to submit feedback: ${e.message()}"))
                    }
                }
                is java.net.UnknownHostException -> Result.failure(Exception("No internet connection. Please check your network."))
                is java.net.SocketTimeoutException -> Result.failure(Exception("Request timed out. Please try again."))
                else -> Result.failure(Exception("Unexpected error: ${e.message ?: "Unknown error"}"))
            }
        }
    }

    private fun getDeviceInfo(): String {
        return buildString {
            appendLine("- App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("- Distribution: ${BuildConfig.FLAVOR}")
            appendLine("- Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("- Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("- Device Brand: ${Build.BRAND}")
            appendLine("- Device Product: ${Build.PRODUCT}")
        }
    }
}
