package com.omiyawaki.osrswiki.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
// HttpLoggingInterceptor is no longer instantiated here; OkHttpClientFactory.offlineClient includes one.
import retrofit2.Retrofit

object RetrofitClient {

    private const val BASE_URL = "https://oldschool.runescape.wiki/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Shared OkHttp client already identifies the app and honors Retry-After.
    private val retrofitInstance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(OkHttpClientFactory.offlineClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val apiService: WikiApiService by lazy {
        retrofitInstance.create(WikiApiService::class.java)
    }
}