package com.omiyawaki.osrswiki.network

import android.os.Build
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.omiyawaki.osrswiki.BuildConfig // For User-Agent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException

object RetrofitClient {

    private const val BASE_URL = "https://oldschool.runescape.wiki/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

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

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) { // Only log in debug builds
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor()) // Add User-Agent interceptor
            .addInterceptor(loggingInterceptor)   // Add logging interceptor
            // Add other configurations like timeouts if needed
            // .connectTimeout(30, TimeUnit.SECONDS)
            // .readTimeout(30, TimeUnit.SECONDS)
            // .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofitInstance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val apiService: WikiApiService by lazy {
        retrofitInstance.create(WikiApiService::class.java)
    }
}
