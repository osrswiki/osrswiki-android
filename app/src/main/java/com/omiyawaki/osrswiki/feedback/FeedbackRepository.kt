package com.omiyawaki.osrswiki.feedback

import android.content.Context
import android.os.Build
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.network.GitHubRetrofitClient
import com.omiyawaki.osrswiki.network.model.github.CreateIssueRequest
import com.omiyawaki.osrswiki.network.model.github.IssueResponse
import com.omiyawaki.osrswiki.util.log.L

class FeedbackRepository {

    private val gitHubApi = GitHubRetrofitClient.apiService
    
    companion object {
        private const val REPO_OWNER = "omiyawaki"
        private const val REPO_NAME = "osrswiki-android"
        private const val LABEL_BUG = "bug"
        private const val LABEL_ENHANCEMENT = "enhancement"
    }

    /**
     * Creates a bug report issue on GitHub
     */
    suspend fun reportIssue(
        context: Context,
        title: String,
        description: String
    ): Result<IssueResponse> {
        return try {
            val deviceInfo = getDeviceInfo()
            val fullBody = buildString {
                appendLine(description)
                appendLine()
                appendLine("---")
                appendLine("**Device Information:**")
                appendLine(deviceInfo)
            }
            
            val request = CreateIssueRequest(
                title = title,
                body = fullBody,
                labels = listOf(LABEL_BUG)
            )
            
            L.d("FeedbackRepository: Creating bug report issue")
            val response = gitHubApi.createIssue(REPO_OWNER, REPO_NAME, request)
            L.d("FeedbackRepository: Bug report created successfully: ${response.htmlUrl}")
            
            Result.success(response)
        } catch (e: Exception) {
            L.e("FeedbackRepository: Error creating bug report", e)
            when (e) {
                is retrofit2.HttpException -> {
                    when (e.code()) {
                        401 -> Result.failure(Exception("GitHub API authentication failed. Please set up a valid GitHub token."))
                        403 -> Result.failure(Exception("GitHub API access forbidden. Check repository permissions."))
                        404 -> Result.failure(Exception("GitHub repository not found. Check repository settings."))
                        else -> Result.failure(Exception("GitHub API error: ${e.message()}"))
                    }
                }
                is java.net.UnknownHostException -> Result.failure(Exception("No internet connection. Please check your network."))
                is java.net.SocketTimeoutException -> Result.failure(Exception("Request timed out. Please try again."))
                else -> Result.failure(Exception("Unexpected error: ${e.message ?: "Unknown error"}"))
            }
        }
    }

    /**
     * Creates a feature request issue on GitHub
     */
    suspend fun requestFeature(
        context: Context,
        title: String,
        description: String
    ): Result<IssueResponse> {
        return try {
            val deviceInfo = getDeviceInfo()
            val fullBody = buildString {
                appendLine(description)
                appendLine()
                appendLine("---")
                appendLine("**Device Information:**")
                appendLine(deviceInfo)
            }
            
            val request = CreateIssueRequest(
                title = title,
                body = fullBody,
                labels = listOf(LABEL_ENHANCEMENT)
            )
            
            L.d("FeedbackRepository: Creating feature request issue")
            val response = gitHubApi.createIssue(REPO_OWNER, REPO_NAME, request)
            L.d("FeedbackRepository: Feature request created successfully: ${response.htmlUrl}")
            
            Result.success(response)
        } catch (e: Exception) {
            L.e("FeedbackRepository: Error creating feature request", e)
            when (e) {
                is retrofit2.HttpException -> {
                    when (e.code()) {
                        401 -> Result.failure(Exception("GitHub API authentication failed. Please set up a valid GitHub token."))
                        403 -> Result.failure(Exception("GitHub API access forbidden. Check repository permissions."))
                        404 -> Result.failure(Exception("GitHub repository not found. Check repository settings."))
                        else -> Result.failure(Exception("GitHub API error: ${e.message()}"))
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