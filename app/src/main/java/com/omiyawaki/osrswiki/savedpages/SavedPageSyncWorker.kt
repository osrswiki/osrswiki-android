package com.omiyawaki.osrswiki.savedpages

import android.content.Context
import android.util.Log // Added import
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.network.OkHttpClientFactory
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage // Ensure this import is correct
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder

class SavedPageSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val readingListPageDao: ReadingListPageDao by lazy { AppDatabase.instance.readingListPageDao() }
    private val offlineObjectDao: OfflineObjectDao by lazy { AppDatabase.instance.offlineObjectDao() }
    private val okHttpClient by lazy { OkHttpClientFactory.offlineClient }

    private val baseApiUrl = "https://oldschool.runescape.wiki/api.php"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.e("OSRSWIKI_WORKER", "[D] SavedPageSyncWorker started.")
        var overallSuccess = true

        try {
            val pagesToSave = readingListPageDao.getPagesToProcessForSaving()
            if (pagesToSave.isNotEmpty()) {
                Log.e("OSRSWIKI_WORKER", "[I] Found ${pagesToSave.size} page(s) to save.")
                if (!processPagesToSave(pagesToSave)) {
                    overallSuccess = false
                }
            } else {
                Log.e("OSRSWIKI_WORKER", "[D] No pages found to save.")
            }

            val pagesToDelete = readingListPageDao.getPagesToProcessForDeleting()
            if (pagesToDelete.isNotEmpty()) {
                Log.e("OSRSWIKI_WORKER", "[I] Found ${pagesToDelete.size} page(s) to delete offline data for.")
                if (!processPagesToDelete(pagesToDelete)) {
                    overallSuccess = false
                }
            } else {
                Log.e("OSRSWIKI_WORKER", "[D] No pages found to delete.")
            }

        } catch (e: Exception) {
            Log.e("OSRSWIKI_WORKER", "[E] Critical error during SavedPageSyncWorker execution.", e)
            overallSuccess = false
        }

        Log.e("OSRSWIKI_WORKER", "[D] SavedPageSyncWorker finished. Overall success: $overallSuccess")
        return@withContext if (overallSuccess) Result.success() else Result.failure()
    }

    private suspend fun processPagesToSave(pagesToSave: List<ReadingListPage>): Boolean {
        var allItemsInThisBatchProcessedSuccessfully = true
        for (page in pagesToSave) {
            try {
                Log.e("OSRSWIKI_WORKER", "[I] Processing page for saving: Title='${page.apiTitle}', ReadingListPage.ID=${page.id}")

                val encodedApiTitle = URLEncoder.encode(page.apiTitle, "UTF-8")
                val requestUrl = "$baseApiUrl?action=parse&format=json&formatversion=2&prop=text|revid|displaytitle&redirects=true&disableeditsection=true&disablelimitreport=true&page=$encodedApiTitle"

                val request = Request.Builder()
                    .url(requestUrl)
                    .header("X-Offline-Save", "readinglist")
                    .header("X-Offline-Save-PageLibIds", "|${page.id}|")
                    .build()

                Log.e("OSRSWIKI_WORKER", "[D] Requesting URL for page ID ${page.id}: $requestUrl")

                var response: Response? = null
                try {
                    response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        Log.e("OSRSWIKI_WORKER", "[I] Successfully fetched page for saving: ${page.apiTitle} (ID: ${page.id}). Interceptor handles saving and status update to SAVED.")
                        // The OfflineCacheInterceptor updates ReadingListPage status to SAVED and mtime.
                        
                        val totalSize = offlineObjectDao.getTotalBytesForPageId(page.id, applicationContext)
                        readingListPageDao.updatePageSizeBytes(page.id, totalSize)
                        Log.e("OSRSWIKI_WORKER", "[D] Updated size for page ID ${page.id} to $totalSize bytes.")
                    } else {
                        Log.e("OSRSWIKI_WORKER", "[W] Failed to fetch page ${page.apiTitle} (ID: ${page.id}). HTTP Code: ${response.code}")
                        allItemsInThisBatchProcessedSuccessfully = false
                        // TODO: Consider specific error handling for non-successful HTTP codes (e.g., mark page as FAILED_TO_DOWNLOAD)
                    }
                } finally {
                    response?.body?.close()
                }
            } catch (ioe: IOException) {
                Log.e("OSRSWIKI_WORKER", "[E] IOException while saving page ${page.apiTitle} (ID: ${page.id})", ioe)
                allItemsInThisBatchProcessedSuccessfully = false
            } catch (e: Exception) {
                Log.e("OSRSWIKI_WORKER", "[E] Unexpected error processing page ${page.apiTitle} (ID: ${page.id}) for saving", e)
                allItemsInThisBatchProcessedSuccessfully = false
            }
        }
        Log.e("OSRSWIKI_WORKER", "[D] Page saving batch finished. Success status for this batch: $allItemsInThisBatchProcessedSuccessfully")
        return allItemsInThisBatchProcessedSuccessfully
    }

    private suspend fun processPagesToDelete(pagesToDelete: List<ReadingListPage>): Boolean {
        var allItemsInThisBatchProcessedSuccessfully = true
        Log.e("OSRSWIKI_WORKER", "[I] Starting deletion process for ${pagesToDelete.size} pages.")
        for (page in pagesToDelete) {
            try {
                Log.e("OSRSWIKI_WORKER", "[I] Processing page for deletion: Title='${page.apiTitle}', ReadingListPage.ID=${page.id}")

                offlineObjectDao.deleteObjectsForPageIds(listOf(page.id), applicationContext)

                val newStatusAfterDeletion = ReadingListPage.STATUS_SAVED
                readingListPageDao.updatePageAfterOfflineDeletion(page.id, newStatusAfterDeletion, System.currentTimeMillis())

                Log.e("OSRSWIKI_WORKER", "[I] Successfully processed deletion for page: ${page.apiTitle} (ID: ${page.id}). Status set to $newStatusAfterDeletion, offline=false.")

            } catch (e: Exception) {
                Log.e("OSRSWIKI_WORKER", "[E] Error processing page ${page.apiTitle} (ID: ${page.id}) for deletion", e)
                allItemsInThisBatchProcessedSuccessfully = false
            }
        }
        Log.e("OSRSWIKI_WORKER", "[D] Page deletion batch finished. Success status for this batch: $allItemsInThisBatchProcessedSuccessfully")
        return allItemsInThisBatchProcessedSuccessfully
    }

    companion object {
        // private val TAG = SavedPageSyncWorker::class.java.simpleName // Commented out
        private const val UNIQUE_WORK_NAME = "SavedPageSyncWorker"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SavedPageSyncWorker>()
                .setConstraints(constraints)
                .addTag(UNIQUE_WORK_NAME) // Use UNIQUE_WORK_NAME as tag or a specific string tag
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.e("OSRSWIKI_WORKER", "[D] SavedPageSyncWorker enqueued with policy REPLACE.")
        }
    }
}
