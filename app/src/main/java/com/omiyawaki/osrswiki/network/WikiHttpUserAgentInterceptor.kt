package com.omiyawaki.osrswiki.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

internal class WikiHttpUserAgentInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.header("User-Agent") != null) {
            return chain.proceed(original)
        }
        return chain.proceed(
            original.newBuilder()
                .header("User-Agent", osrsWikiApiStewardship.appUserAgent("okhttp"))
                .build()
        )
    }
}
