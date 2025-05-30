package com.omiyawaki.osrswiki.util.log

import android.util.Log
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.OSRSWikiApp // Ensure this import is correct
import com.omiyawaki.osrswiki.util.ReleaseUtil  // Ensure this import is correct

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
        if (ReleaseUtil.isProdRelease) { // Make sure ReleaseUtil.isProdRelease is accessible
            logRemoteError(t)
        } else {
            throw RuntimeException(t)
        }
    }

    fun logRemoteError(t: Throwable) {
        LEVEL_E.log("", t)
        if (ReleaseUtil.isProdRelease) { // Make sure ReleaseUtil.isProdRelease is accessible
            OSRSWikiApp.logCrashManually(t) // Make sure OSRSWikiApp.logCrashManually(t) exists
        }
    }

    private abstract class LogLevel {
        abstract fun logLevel(tag: String?, msg: String?, t: Throwable?)
        fun log(msg: String, t: Throwable?) {
            if (ReleaseUtil.isDevRelease) { // Make sure ReleaseUtil.isDevRelease is accessible
                val element = Thread.currentThread().stackTrace[STACK_INDEX]
                logLevel(element.className, stackTraceElementToMessagePrefix(element) + msg, t)
            } else {
                logLevel(BuildConfig.APPLICATION_ID, msg, t)
            }
        }

        private fun stackTraceElementToMessagePrefix(element: StackTraceElement): String {
            return element.methodName + "():" + element.lineNumber + ": "
        }

        companion object {
            private const val STACK_INDEX = 4
        }
    }
}