package com.omiyawaki.osrswiki.util.log // Adjusted package name

import android.util.Log
import com.omiyawaki.osrswiki.BuildConfig // Adjusted BuildConfig import
// TODO: Confirm 'com.omiyawaki.osrswiki.OsrsWikiApp' is the correct Application class name and uncomment.
// import com.omiyawaki.osrswiki.OsrsWikiApp // Placeholder for OSRSWiki Application class
// TODO: Implement the 'ReleaseUtil' class (or an equivalent) in the 'com.omiyawaki.osrswiki.util' package (or adjust path).
//       This utility is used by L.kt to control logging behavior based on build types (e.g., isProdRelease, isDevRelease).
//       Its methods (isProdRelease, isPreBetaRelease, isDevRelease) need to be defined.
//       Alternatively, modify the logic in this file if such fine-grained control is not required.
// import com.omiyawaki.osrswiki.util.ReleaseUtil // Placeholder for OSRSWiki ReleaseUtil

/** Logging utility like [Log] but with implied tags.  */
object L {
    private val LEVEL_V: LogLevel = object : LogLevel() {
        override fun logLevel(tag: String?, msg: String?, t: Throwable?) {
            Log.v(tag, msg, t)
        }
    }
    private val LEVEL_D: LogLevel = object : LogLevel() {
        override fun logLevel(tag: String?, msg: String?, t: Throwable?) {
            Log.d(tag, msg, t)
        }
    }
    private val LEVEL_I: LogLevel = object : LogLevel() {
        override fun logLevel(tag: String?, msg: String?, t: Throwable?) {
            Log.i(tag, msg, t)
        }
    }
    private val LEVEL_W: LogLevel = object : LogLevel() {
        override fun logLevel(tag: String?, msg: String?, t: Throwable?) {
            Log.w(tag, msg, t)
        }
    }
    private val LEVEL_E: LogLevel = object : LogLevel() {
        override fun logLevel(tag: String?, msg: String?, t: Throwable?) {
            Log.e(tag, msg, t)
        }
    }

    fun v(msg: String) {
        LEVEL_V.log(msg, null)
    }

    fun d(msg: String) {
        LEVEL_D.log(msg, null)
    }

    fun i(msg: String) {
        LEVEL_I.log(msg, null)
    }

    fun w(msg: String) {
        LEVEL_W.log(msg, null)
    }

    fun e(msg: String) {
        LEVEL_E.log(msg, null)
    }

    fun v(t: Throwable?) {
        LEVEL_V.log("", t)
    }

    fun d(t: Throwable?) {
        LEVEL_D.log("", t)
    }

    fun i(t: Throwable?) {
        LEVEL_I.log("", t)
    }

    fun w(t: Throwable?) {
        LEVEL_W.log("", t)
    }

    fun e(t: Throwable?) {
        LEVEL_E.log("", t)
    }

    fun v(msg: String, t: Throwable?) {
        LEVEL_V.log(msg, t)
    }

    fun d(msg: String, t: Throwable?) {
        LEVEL_D.log(msg, t)
    }

    fun i(msg: String, t: Throwable?) {
        LEVEL_I.log(msg, t)
    }

    fun w(msg: String, t: Throwable?) {
        LEVEL_W.log(msg, t)
    }

    fun e(msg: String, t: Throwable?) {
        LEVEL_E.log(msg, t)
    }

    @JvmStatic
    fun logRemoteErrorIfProd(t: Throwable) {
        // TODO: Adapt the condition 'ReleaseUtil.isProdRelease' based on the implemented 'ReleaseUtil' or chosen alternative.
        //       This requires 'ReleaseUtil' to be available and have an 'isProdRelease' property/method.
        // if (ReleaseUtil.isProdRelease) {
        //     logRemoteError(t)
        // } else {
        //     // TODO: Confirm if re-throwing a RuntimeException is the desired behavior in non-prod builds.
        //     throw RuntimeException(t)
        // }
        // Simplified version until ReleaseUtil is addressed:
        Log.e("REMOTE_ERROR_PROD_CHECK", "Unhandled exception: ${t.message}", t)
        // Consider re-throwing for non-prod or logging to a crash reporting service.
        // throw RuntimeException(t) // Example: Uncomment if crashes are preferred in debug.
    }

    // Favor logRemoteErrorIfProd(). If it's worth consuming bandwidth and developer hours, it's
    // worth crashing on everything but prod
    fun logRemoteError(t: Throwable) {
        LEVEL_E.log("", t)
        // TODO: Adapt the condition '!ReleaseUtil.isPreBetaRelease' based on the implemented 'ReleaseUtil' or chosen alternative.
        // TODO: Replace 'OsrsWikiApp.instance.logCrashManually(t)' with the correct call
        //       to the OSRSWiki Application class method if manual crash reporting is implemented.
        //       This requires 'ReleaseUtil' and the Application class method to be available.
        // if (!ReleaseUtil.isPreBetaRelease) {
        //    OsrsWikiApp.instance.logCrashManually(t)
        // }
        // Simplified version until ReleaseUtil and App class method are addressed:
        Log.e("REMOTE_ERROR", "Logged remote error: ${t.message}", t)
    }

    private abstract class LogLevel {
        abstract fun logLevel(tag: String?, msg: String?, t: Throwable?)
        fun log(msg: String, t: Throwable?) {
            // TODO: Adapt the condition 'ReleaseUtil.isDevRelease' based on the implemented 'ReleaseUtil' or chosen alternative.
            //       This determines whether to use class/method/line as tag or the application ID.
            // if (ReleaseUtil.isDevRelease) {
            //     val element = Thread.currentThread().stackTrace[STACK_INDEX]
            //     logLevel(element.className, stackTraceElementToMessagePrefix(element) + msg, t)
            // } else {
            //     // Assumes BuildConfig.APPLICATION_ID is available and appropriate for OSRSWiki.
            //     logLevel(BuildConfig.APPLICATION_ID, msg, t)
            // }
            // Simplified version until ReleaseUtil is addressed (always logs with detailed tag):
            val element = Thread.currentThread().stackTrace[STACK_INDEX]
            logLevel(element.className, stackTraceElementToMessagePrefix(element) + msg, t)
        }

        private fun stackTraceElementToMessagePrefix(element: StackTraceElement): String {
            // Extracts method name and line number for log messages.
            return element.methodName + "():" + element.lineNumber + ": "
        }

        companion object {
            // Defines the stack trace depth to find the calling method.
            // This might need adjustment if the call hierarchy changes significantly.
            private const val STACK_INDEX = 4
        }
    }
}
