package com.omiyawaki.osrswiki.util // Adjusted package name

import android.content.Context
import android.content.pm.PackageManager
import com.omiyawaki.osrswiki.BuildConfig // Adjusted BuildConfig import
// TODO: Resolve dependency on 'Prefs'. This class was 'org.wikipedia.settings.Prefs'.
//       A similar class/object needs to be created in the OSRSWiki project,
//       or the 'getChannel' method below needs to be adapted/removed.
// import com.omiyawaki.osrswiki.settings.Prefs // Placeholder for OSRSWiki Prefs

object ReleaseUtil {
    private const val RELEASE_PROD = 0
    private const val RELEASE_BETA = 1
    private const val RELEASE_ALPHA = 2
    private const val RELEASE_DEV = 3

    val isProdRelease: Boolean
        get() = calculateReleaseType() == RELEASE_PROD

    val isPreProdRelease: Boolean
        get() = calculateReleaseType() != RELEASE_PROD

    val isAlphaRelease: Boolean
        get() = calculateReleaseType() == RELEASE_ALPHA

    val isPreBetaRelease: Boolean
        get() = when (calculateReleaseType()) {
            RELEASE_PROD, RELEASE_BETA -> false
            else -> true
        }

    val isDevRelease: Boolean
        get() = calculateReleaseType() == RELEASE_DEV

    /*
    // TODO: This method depends on 'Prefs'. Decide how to handle 'Prefs' for OSRSWiki.
    //       If 'Prefs' is implemented, uncomment this method and the 'Prefs' import above.
    //       Ensure 'Prefs.appChannel' and 'Prefs.appChannelKey' are available.
    fun getChannel(ctx: Context): String {
        var channel = Prefs.appChannel
        if (channel == null) {
            channel = getChannelFromManifest(ctx)
            Prefs.appChannel = channel
        }
        return channel
    }
    */

    private fun calculateReleaseType(): Int {
        // This logic assumes that the BuildConfig.APPLICATION_ID for beta, alpha, or dev
        // builds will contain the respective strings ("beta", "alpha", "dev").
        // Ensure this convention is followed in the OSRSWiki project's build configuration
        // if this behavior is desired.
        return when {
            BuildConfig.APPLICATION_ID.contains("beta") -> RELEASE_BETA
            BuildConfig.APPLICATION_ID.contains("alpha") -> RELEASE_ALPHA
            BuildConfig.APPLICATION_ID.contains("dev") -> RELEASE_DEV
            else -> RELEASE_PROD
        }
    }

    /*
    // TODO: This method is part of 'getChannel', which is currently commented out due to 'Prefs' dependency.
    private fun getChannelFromManifest(ctx: Context): String {
        return try {
            val info = ctx.packageManager
                    .getApplicationInfo(BuildConfig.APPLICATION_ID, PackageManager.GET_META_DATA)
            // Ensure 'Prefs.appChannelKey' is defined if this method is used.
            val channel = info.metaData.getString(Prefs.appChannelKey)
            channel ?: ""
        } catch (t: Throwable) {
            ""
        }
    }
    */
}
