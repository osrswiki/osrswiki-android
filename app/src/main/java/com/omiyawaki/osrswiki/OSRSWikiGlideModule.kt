package com.omiyawaki.osrswiki

import android.content.Context
import android.util.Log
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.module.AppGlideModule
import com.omiyawaki.osrswiki.util.log.L

@GlideModule
class OSRSWikiGlideModule : AppGlideModule() {
    init {
        L.d("GLIDE_CONFIG: OSRSWikiGlideModule constructor called.")
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        L.d("GLIDE_CONFIG: OSRSWikiGlideModule.applyOptions called.")

        // Enable verbose logging only in debug builds to avoid performance overhead in production
        if (com.omiyawaki.osrswiki.BuildConfig.DEBUG) {
            // This is an evidence-gathering experiment.
            // We are enabling Glide's most verbose logging to see cache key
            // generation and the reasons for cache misses.
            builder.setLogLevel(Log.VERBOSE)
        } else {
            // Use minimal logging in production builds for performance
            builder.setLogLevel(Log.ERROR)
        }

        val calculator = MemorySizeCalculator.Builder(context)
            .setMemoryCacheScreens(2f)
            .build()
        val memoryCacheSizeBytes = calculator.memoryCacheSize

        L.d("GLIDE_CONFIG: Setting custom memory cache size: $memoryCacheSizeBytes bytes")
        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes.toLong()))

        val diskCacheSizeBytes = 1024 * 1024 * 100 // 100 MB
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSizeBytes.toLong()))
    }
}
