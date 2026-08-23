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

object FeedbackWorkerRetrofitClient {

    // Cloudflare Worker base URL (path: createGithubIssue)
    private const val FEEDBACK_WORKER_URL = "https://osrswiki-feedback.omiyawaki.workers.dev/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private class FeedbackWorkerInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val requestWithHeaders = originalRequest.newBuilder()
                .header("Content-Type", "application/json")
                .header("User-Agent", "OSRSWikiApp/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})")
                .build()
            return chain.proceed(requestWithHeaders)
        }
    }

    private val httpClient by lazy {
        OkHttpClientFactory.offlineClient.newBuilder()
            .addInterceptor(FeedbackWorkerInterceptor())
            .build()
    }

    private val retrofitInstance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(FEEDBACK_WORKER_URL)
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val apiService: FeedbackWorkerApiService by lazy {
        retrofitInstance.create(FeedbackWorkerApiService::class.java)
    }
}
