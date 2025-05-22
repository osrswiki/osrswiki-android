package com.omiyawaki.osrswiki.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object RetrofitClient {

    private const val BASE_URL = "https://oldschool.runescape.wiki/"

    // Configure JsonSerializer to be lenient about unknown keys
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true // Helpful if API sometimes returns non-standard JSON
    }

    // Configure OkHttpClient with a logging interceptor for debugging
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Log request and response bodies
        }
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    // Public Retrofit instance (can be kept if needed elsewhere, or made private
    // if only apiService is intended for external use)
    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // Use the custom OkHttpClient
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    // Directly expose the configured WikiApiService instance
    val apiService: WikiApiService by lazy {
        instance.create(WikiApiService::class.java)
    }
}
