package com.omiyawaki.osrswiki.network

import android.os.Build
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.omiyawaki.osrswiki.BuildConfig // For User-Agent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
// HttpLoggingInterceptor is no longer instantiated here; OkHttpClientFactory.offlineClient includes one.
import retrofit2.Retrofit
import java.io.IOException

object RetrofitClient {

    private const val BASE_URL = "https://oldschool.runescape.wiki/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // UserAgentInterceptor remains specific to this RetrofitClient
    private class UserAgentInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", "OSRSWikiApp/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MANUFACTURER} ${Build.MODEL})")
                .build()
            return chain.proceed(requestWithUserAgent)
        }
    }

    // OkHttpClient is now derived from OkHttpClientFactory.offlineClient
    // and then customized with the UserAgentInterceptor.
    private val customHttpClientForRetrofit by lazy {
        OkHttpClientFactory.offlineClient.newBuilder()
            .addInterceptor(UserAgentInterceptor()) // Add User-Agent interceptor specific to Retrofit
            // The HttpLoggingInterceptor is already part of OkHttpClientFactory.offlineClient
            // The OfflineCacheInterceptor is also already part of OkHttpClientFactory.offlineClient
            .build()
    }

    private val retrofitInstance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(customHttpClientForRetrofit) // Use the customized client
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val apiService: WikiApiService by lazy {
        retrofitInstance.create(WikiApiService::class.java)
    }
}