package com.omiyawaki.osrswiki.network

import android.os.Build
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.omiyawaki.osrswiki.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import retrofit2.Retrofit
import java.io.IOException

object GitHubRetrofitClient {

    private const val BASE_URL = "https://api.github.com/"
    
    // TODO: Move this to secure storage or build config for production
    // For now, this is a placeholder - will need actual GitHub token
    private const val GITHUB_TOKEN = "github_pat_placeholder"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private class GitHubAuthInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val requestWithAuth = originalRequest.newBuilder()
                .header("Authorization", "Bearer $GITHUB_TOKEN")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "OSRSWikiApp/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MANUFACTURER} ${Build.MODEL})")
                .build()
            return chain.proceed(requestWithAuth)
        }
    }

    private val httpClient by lazy {
        OkHttpClientFactory.offlineClient.newBuilder()
            .addInterceptor(GitHubAuthInterceptor())
            .build()
    }

    private val retrofitInstance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val apiService: GitHubApiService by lazy {
        retrofitInstance.create(GitHubApiService::class.java)
    }
}