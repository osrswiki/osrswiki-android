package com.omiyawaki.osrswiki.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

// Assumes the pre-existing service interface was indeed 'WikiApiService'.
// If it was also 'MediaWikiApiService' or something else, this import might need adjustment
// or the existing service might need to be merged with the new one.
// For now, we proceed assuming 'WikiApiService' is a distinct, existing service.

object RetrofitClient {

    private const val BASE_URL = "https://oldschool.runescape.wiki/" // Corrected BASE_URL

    // Configure JsonSerializer to be lenient about unknown keys
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true // Helpful if API sometimes returns non-standard JSON
    }

    // Configure OkHttpClient with a logging interceptor for debugging
   @Suppress("unused")
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Log request and response bodies
        }
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

   @Suppress("unused")
    private val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    // Assumes this was the existing service instance.
    // If the existing service was different, adjust 'WikiApiService' accordingly.
   @Suppress("unused")
    val apiService: WikiApiService by lazy {
        instance.create(WikiApiService::class.java)
    }

}
