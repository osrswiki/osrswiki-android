package com.omiyawaki.osrswiki.savedpages

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.database.AppDatabase
import kotlinx.coroutines.launch

/**
 * Routes the foreground notification action through the same state reconciliation as the
 * in-app cancel affordance. WorkManager's raw cancel PendingIntent cannot identify the active
 * reading-list row and can otherwise leave it queued indefinitely.
 */
class SavedPageSyncCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pageId = pageIdFrom(intent) ?: return
        val pendingResult = goAsync()
        OSRSWikiApp.instance.applicationScope.launch {
            try {
                SavedPageSyncWorker.cancelQueuedSave(
                    context = context.applicationContext,
                    readingListPageDao = AppDatabase.instance.readingListPageDao(),
                    pageId = pageId
                )
            } catch (failure: Throwable) {
                Log.e(TAG, "Could not reconcile cancelled saved-page sync for page $pageId", failure)
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal companion object {
        private const val ACTION_CANCEL =
            "com.omiyawaki.osrswiki.savedpages.action.CANCEL_SAVED_PAGE_SYNC"
        private const val EXTRA_READING_LIST_PAGE_ID = "reading_list_page_id"
        private const val TAG = "SavedPageSyncCancel"

        fun pendingIntent(context: Context, pageId: Long): PendingIntent = PendingIntent.getBroadcast(
            context,
            (pageId xor (pageId ushr Int.SIZE_BITS)).toInt(),
            intent(context, pageId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        internal fun intent(context: Context, pageId: Long): Intent =
            Intent(context, SavedPageSyncCancelReceiver::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_READING_LIST_PAGE_ID, pageId)

        fun pageIdFrom(intent: Intent): Long? = intent
            .takeIf { it.action == ACTION_CANCEL }
            ?.getLongExtra(EXTRA_READING_LIST_PAGE_ID, -1L)
            ?.takeIf { it > 0L }
    }
}
