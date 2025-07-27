package com.omiyawaki.osrswiki.network

import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.dataclient.okhttp.OfflineCacheInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Factory object for creating OkHttpClient instances, particularly for offline-enabled operations.
 */
object OkHttpClientFactory {

    private const val DEFAULT_TIMEOUT_SECONDS = 30L

    // Lazy initialization for the offline-enabled OkHttpClient
    val offlineClient: OkHttpClient by lazy {
        buildOfflineCapableClient()
    }

    private fun buildOfflineCapableClient(): OkHttpClient {
        val context = OSRSWikiApp.instance.applicationContext
        val appDatabase = AppDatabase.instance

        // Dependencies for OfflineCacheInterceptor
        val offlineObjectDao = appDatabase.offlineObjectDao()
        val readingListPageDao = appDatabase.readingListPageDao()
        val offlineCacheInterceptor = OfflineCacheInterceptor(
            context = context,
            offlineObjectDao = offlineObjectDao,
            readingListPageDao = readingListPageDao,
            appDatabase = appDatabase
        )

        val builder = OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(offlineCacheInterceptor) // Unified offline content caching

        // Add other common interceptors, e.g., HttpLoggingInterceptor for debugging
        // Assuming your BuildConfig is accessible, e.g., com.omiyawaki.osrswiki.BuildConfig
        if (com.omiyawaki.osrswiki.BuildConfig.DEBUG) { 
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        builder.addInterceptor(loggingInterceptor)
        }
    
        return builder.build()
    }
}
