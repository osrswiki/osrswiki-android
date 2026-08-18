package com.omiyawaki.osrswiki.savedpages

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.omiyawaki.osrswiki.R

internal data class SavedPageSyncForegroundPlan(val totalItems: Int) {
    init {
        require(totalItems > 0)
    }
}

internal object SavedPageSyncForegroundPolicy {
    fun plan(pagesToSave: Int, pagesToDelete: Int): SavedPageSyncForegroundPlan? {
        val total = pagesToSave.coerceAtLeast(0) + pagesToDelete.coerceAtLeast(0)
        return total.takeIf { it > 0 }?.let(::SavedPageSyncForegroundPlan)
    }
}

/** Builds the quiet, cancellable notification required only for explicit offline settlement. */
internal class SavedPageSyncForegroundInfoFactory(private val context: Context) {
    fun create(
        completedItems: Int,
        totalItems: Int,
        cancelIntent: PendingIntent?
    ): ForegroundInfo {
        require(totalItems > 0)
        createChannelIfNeeded()
        val completed = completedItems.coerceIn(0, totalItems)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_saved_page_sync_24)
            .setContentTitle(context.getString(R.string.saved_page_sync_notification_title))
            .setContentText(
                context.getString(
                    R.string.saved_page_sync_notification_progress,
                    completed,
                    totalItems
                )
            )
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(totalItems, completed, false)
        cancelIntent?.let {
            builder.addAction(
                0,
                context.getString(R.string.saved_page_sync_notification_cancel),
                it
            )
        }
        val notification = builder.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.saved_page_sync_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.saved_page_sync_notification_channel_summary)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    internal companion object {
        const val CHANNEL_ID = "saved_page_sync"
        const val NOTIFICATION_ID = 0x535057
    }
}
