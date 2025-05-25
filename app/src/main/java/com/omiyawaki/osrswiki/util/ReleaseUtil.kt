package com.omiyawaki.osrswiki.util

import com.omiyawaki.osrswiki.BuildConfig

/**
 * Utility class for determining release type and channel information.
 * Simplified to differentiate primarily between debug and release builds.
 */
object ReleaseUtil {

    /**
     * Indicates if the current build is a production (release) build.
     * This is determined by checking if `BuildConfig.DEBUG` is false.
     */
    val isProdRelease: Boolean
        get() = !BuildConfig.DEBUG

    /**
     * Indicates if the current build is a development (debug) build.
     * This is determined by checking if `BuildConfig.DEBUG` is true.
     * This flag is typically used by L.kt and other debugging utilities.
     */
    val isDevRelease: Boolean
        get() = BuildConfig.DEBUG

    /**
     * Gets a string representation of the current build channel.
     * Returns "debug" for debug builds and "release" for release builds.
     * This can be used by L.kt for tagging logs.
     */
    fun getChannel(): String {
        return if (BuildConfig.DEBUG) {
            "debug"
        } else {
            "release"
        }
    }
}
