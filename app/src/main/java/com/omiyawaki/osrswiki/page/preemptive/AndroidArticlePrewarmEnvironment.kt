package com.omiyawaki.osrswiki.page.preemptive

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.omiyawaki.osrswiki.settings.Prefs
import java.util.concurrent.atomic.AtomicInteger

internal class AppForegroundTracker(
    private val onForegroundChanged: (Boolean) -> Unit = {}
) : Application.ActivityLifecycleCallbacks {
    private val startedActivityCount = AtomicInteger(0)

    val isAppInForeground: Boolean
        get() = startedActivityCount.get() > 0

    override fun onActivityStarted(activity: Activity) {
        if (startedActivityCount.incrementAndGet() == 1) {
            onForegroundChanged(true)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (startedActivityCount.updateAndGet { count -> (count - 1).coerceAtLeast(0) } == 0) {
            onForegroundChanged(false)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

internal class AndroidArticlePrewarmEnvironmentProvider(
    context: Context,
    private val isAppInForeground: () -> Boolean,
    private val isNetworkAvailable: () -> Boolean
) : ArticlePrewarmEnvironmentProvider {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var environmentListener: (() -> Unit)? = null
    private var observing = false
    private var powerSaveReceiverRegistered = false
    private var thermalListenerRegistered = false
    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            environmentListener?.invoke()
        }
    }
    private val thermalListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PowerManager.OnThermalStatusChangedListener { environmentListener?.invoke() }
    } else {
        null
    }

    fun startObserving(listener: () -> Unit) {
        if (observing) return
        observing = true
        environmentListener = listener
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                powerSaveReceiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            powerSaveReceiverRegistered = true
        }.onFailure { error ->
            Log.w(TAG, "Unable to observe power-save changes", error)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener?.let { listener ->
                runCatching {
                    powerManager.addThermalStatusListener(appContext.mainExecutor, listener)
                    thermalListenerRegistered = true
                }.onFailure { error ->
                    Log.w(TAG, "Unable to observe thermal changes", error)
                }
            }
        }
    }

    fun stopObserving() {
        if (!observing) return
        observing = false
        if (powerSaveReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(powerSaveReceiver) }
            powerSaveReceiverRegistered = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListenerRegistered) {
            thermalListener?.let { listener ->
                runCatching { powerManager.removeThermalStatusListener(listener) }
            }
            thermalListenerRegistered = false
        }
        environmentListener = null
    }

    override fun currentDecision(): ArticlePrewarmDecision {
        val dataSaverConstrained = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.restrictBackgroundStatus !=
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
        } else {
            false
        }
        val thermalConstrained = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        } else {
            false
        }
        return ArticlePrewarmPolicy.evaluate(
            ArticlePrewarmSignals(
                appInForeground = isAppInForeground(),
                networkAvailable = isNetworkAvailable(),
                networkConstrained = connectivityManager.isActiveNetworkMetered || dataSaverConstrained,
                powerSave = powerManager.isPowerSaveMode,
                thermallyConstrained = thermalConstrained,
                debugDisabled = Prefs.disableArticlePrewarm
            )
        )
    }

    private companion object {
        const val TAG = "ArticlePrewarmEnv"
    }
}
