package com.omiyawaki.osrswiki.feedback

import android.content.Context
import android.os.Build
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.network.CloudFunctionApiService
import com.omiyawaki.osrswiki.network.CloudFunctionIssueRequest
import com.omiyawaki.osrswiki.network.CloudFunctionRetrofitClient
import com.omiyawaki.osrswiki.util.log.L

/**
 * Repository for securely submitting feedback via Google Cloud Function.
 * This approach keeps the GitHub API token secure on the server side.
 */
class SecureFeedbackRepository {

    private val cloudFunctionApi: CloudFunctionApiService = CloudFunctionRetrofitClient.apiService
    
    companion object {
        private const val LABEL_BUG = "bug"
        private const val LABEL_ENHANCEMENT = "enhancement"
    }

    /**
     * Creates a bug report issue via secure Cloud Function
     */
    suspend fun reportIssue(
        context: Context,
        title: String,
        description: String
    ): Result<String> {
        return submitFeedback(title, description, LABEL_BUG)
    }

    /**
     * Creates a feature request issue via secure Cloud Function
     */
    suspend fun requestFeature(
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
            
            val request = CloudFunctionIssueRequest(
                title = title,
                body = fullBody,
                labels = listOf(label)
            )
            
            L.d("SecureFeedbackRepository: Submitting feedback via Cloud Function")
            val response = cloudFunctionApi.createGithubIssue(request)
            L.d("SecureFeedbackRepository: Feedback submitted successfully: ${response.message}")
            
            // Return a success message since we don't get the issue URL from Cloud Function
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
            appendLine("- Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("- Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("- Device Brand: ${Build.BRAND}")
            appendLine("- Device Product: ${Build.PRODUCT}")
        }
    }
}