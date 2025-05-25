package com.omiyawaki.osrswiki.util.log

import android.util.Log
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.OSRSWikiApplication // Corrected Application class import
import com.omiyawaki.osrswiki.util.ReleaseUtil   // ReleaseUtil import activated

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
        if (ReleaseUtil.isProdRelease) {
            logRemoteError(t)
        } else {
            // For non-production builds, re-throw the exception to make it crash loudly.
            throw RuntimeException(t)
        }
    }

    // Favor logRemoteErrorIfProd(). If it's worth consuming bandwidth and developer hours, it's
    // worth crashing on everything but prod
    fun logRemoteError(t: Throwable) {
        LEVEL_E.log("", t) // Log the error locally using our E level.
        // For production releases, also log the crash to the remote reporting service.
        if (ReleaseUtil.isProdRelease) {
            OSRSWikiApplication.instance.logCrashManually(t)
        }
    }

    private abstract class LogLevel {
        abstract fun logLevel(tag: String?, msg: String?, t: Throwable?)
        fun log(msg: String, t: Throwable?) {
            if (ReleaseUtil.isDevRelease) {
                // For development builds, use detailed tags including class, method, and line number.
                val element = Thread.currentThread().stackTrace[STACK_INDEX]
                logLevel(element.className, stackTraceElementToMessagePrefix(element) + msg, t)
            } else {
                // For release builds, use the application ID as the tag.
                logLevel(BuildConfig.APPLICATION_ID, msg, t)
            }
        }

        private fun stackTraceElementToMessagePrefix(element: StackTraceElement): String {
            // Extracts method name and line number for log messages.
            return element.methodName + "():" + element.lineNumber + ": "
        }

        companion object {
            // Defines the stack trace depth to find the calling method.
            // This might need adjustment if the call hierarchy changes significantly.
            private const val STACK_INDEX = 4 // this must be 4 for the call order: L.e -> LEVEL_E.log -> LogLevel.log
        }
    }
}
