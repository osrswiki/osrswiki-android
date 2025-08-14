package com.omiyawaki.osrswiki.savedpages

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.settings.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker that manages offline cache size by removing oldest saved pages
 * when the cache exceeds the user-configured size limit.
 */
class CacheCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val readingListPageDao: ReadingListPageDao by lazy { AppDatabase.instance.readingListPageDao() }
    private val offlineObjectDao: OfflineObjectDao by lazy { AppDatabase.instance.offlineObjectDao() }

    private val loggerTag = "CACHE_CLEANUP_WORKER"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(loggerTag, "CacheCleanupWorker started.")

        try {
            val cacheLimitMB = Prefs.offlineCacheSizeLimitMB
            val cacheLimitBytes = cacheLimitMB * 1024L * 1024L // Convert MB to bytes
            
            Log.d(loggerTag, "Cache size limit: $cacheLimitMB MB ($cacheLimitBytes bytes)")

            val currentCacheSize = readingListPageDao.getTotalCacheSizeBytes() ?: 0L
            Log.d(loggerTag, "Current cache size: ${currentCacheSize / (1024 * 1024)} MB ($currentCacheSize bytes)")

            if (currentCacheSize <= cacheLimitBytes) {
                Log.d(loggerTag, "Cache size is within limit. No cleanup needed.")
                return@withContext Result.success()
            }

            val bytesToFree = currentCacheSize - cacheLimitBytes
            Log.i(loggerTag, "Cache exceeds limit. Need to free ${bytesToFree / (1024 * 1024)} MB ($bytesToFree bytes)")

            // Get oldest saved pages, ordered by last access time
            val oldestPages = readingListPageDao.getOldestSavedPages()
            Log.d(loggerTag, "Found ${oldestPages.size} saved pages to consider for cleanup")

            var bytesFreed = 0L
            val pagesToDelete = mutableListOf<ReadingListPage>()

            for (page in oldestPages) {
                if (bytesFreed >= bytesToFree) {
                    break
                }
                
                pagesToDelete.add(page)
                bytesFreed += page.sizeBytes
                Log.d(loggerTag, "Marking page '${page.displayTitle}' for deletion (${page.sizeBytes} bytes)")
            }

            if (pagesToDelete.isNotEmpty()) {
                Log.i(loggerTag, "Cleaning up ${pagesToDelete.size} pages to free ${bytesFreed / (1024 * 1024)} MB")
                
                // Delete offline objects and FTS entries for these pages
                for (page in pagesToDelete) {
                    try {
                        // Delete offline objects (cached files)
                        offlineObjectDao.deleteObjectsForPageIds(listOf(page.id), applicationContext)
                        
                        // Delete FTS entry
                        val pageTitleHelper = ReadingListPage.toPageTitle(page)
                        val canonicalPageUrlForFts = pageTitleHelper.uri
                        AppDatabase.instance.offlinePageFtsDao().deletePageContentByUrl(canonicalPageUrlForFts)
                        
                        Log.d(loggerTag, "Cleaned up offline data for page: ${page.displayTitle}")
                    } catch (e: Exception) {
                        Log.e(loggerTag, "Error cleaning up offline data for page: ${page.displayTitle}", e)
                    }
                }
                
                // Remove pages from reading list
                val pageIds = pagesToDelete.map { it.id }
                readingListPageDao.deletePagesByIds(pageIds)
                
                Log.i(loggerTag, "Cache cleanup completed. Freed approximately ${bytesFreed / (1024 * 1024)} MB")
            } else {
                Log.w(loggerTag, "No pages available for cleanup, but cache exceeds limit")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(loggerTag, "Error during cache cleanup", e)
            Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "CacheCleanupWorker"

        /**
         * Enqueues cache cleanup to run when needed.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .build()
                
            val workRequest = OneTimeWorkRequestBuilder<CacheCleanupWorker>()
                .setConstraints(constraints)
                .addTag(UNIQUE_WORK_NAME)
                .build()
                
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            Log.d("CACHE_CLEANUP_ENQUEUE", "CacheCleanupWorker enqueued with policy REPLACE.")
        }
    }
}