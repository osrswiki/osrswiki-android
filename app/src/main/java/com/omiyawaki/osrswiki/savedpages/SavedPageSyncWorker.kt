package com.omiyawaki.osrswiki.savedpages

import android.content.Context
import android.text.Html
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.database.OfflinePageFtsDao
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.network.OkHttpClientFactory
import com.omiyawaki.osrswiki.page.PageTitle
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.util.extractTextFromHtmlString // <<< UPDATED import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject // <<< ADDED for JSON parsing
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset // For reading file content

class SavedPageSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val readingListPageDao: ReadingListPageDao by lazy { AppDatabase.instance.readingListPageDao() }
    private val offlineObjectDao: OfflineObjectDao by lazy { AppDatabase.instance.offlineObjectDao() }
    private val ftsDao: OfflinePageFtsDao by lazy { AppDatabase.instance.offlinePageFtsDao() }
    private val okHttpClient by lazy { OkHttpClientFactory.offlineClient }

    private val baseApiUrl = "https://oldschool.runescape.wiki/api.php"
    private val loggerTag = "OSRSWIKI_WORKER"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.e(loggerTag, "!!! SavedPageSyncWorker: doWork() method CALLED !!!") // <<< ADD THIS VERY EARLY LOG (use Log.e to make it stand out)

        Log.d(loggerTag, "SavedPageSyncWorker started.") // Existing log
        var overallSuccess = true

        try {
            // ... rest of your existing doWork() method ...
            val pagesToSave = readingListPageDao.getPagesToProcessForSaving()
            if (pagesToSave.isNotEmpty()) {
                Log.i(loggerTag, "Found ${pagesToSave.size} page(s) to save.")
                if (!processPagesToSave(pagesToSave)) {
                    overallSuccess = false
                }
            } else {
                Log.d(loggerTag, "No pages found to save.")
            }

            val pagesToDelete = readingListPageDao.getPagesToProcessForDeleting()
            if (pagesToDelete.isNotEmpty()) {
                Log.i(loggerTag, "Found ${pagesToDelete.size} page(s) to delete offline data for.")
                if (!processPagesToDelete(pagesToDelete)) {
                    overallSuccess = false
                }
            } else {
                Log.d(loggerTag, "No pages found to delete.")
            }

        } catch (e: Exception) {
            Log.e(loggerTag, "Critical error during SavedPageSyncWorker execution.", e)
            overallSuccess = false
        }

        Log.d(loggerTag, "SavedPageSyncWorker finished. Overall success: $overallSuccess")
        return@withContext if (overallSuccess) Result.success() else Result.failure()
    }


    private suspend fun processPagesToSave(pagesToSave: List<ReadingListPage>): Boolean {
        var allItemsInThisBatchProcessedSuccessfully = true
        for (page in pagesToSave) {
            var pageSuccessfullyFetched = false
            var pageSuccessfullyIndexed = false

            val pageTitleHelper = ReadingListPage.toPageTitle(page)
            val canonicalPageUrl = pageTitleHelper.uri // Used as the key for FTS

            Log.d(loggerTag, "----------------------------------------------------") // Log separator for each page
            Log.i(loggerTag, "Attempting to save and index page: '${page.displayTitle}' (URL: $canonicalPageUrl, ReadingListPgID: ${page.id})")

            try {
                val encodedApiTitle = URLEncoder.encode(page.apiTitle, "UTF-8")
                val apiRequestUrl = "$baseApiUrl?action=parse&format=json&formatversion=2&prop=text|revid|displaytitle&redirects=true&disableeditsection=true&disablelimitreport=true&page=$encodedApiTitle"

                val request = Request.Builder()
                    .url(apiRequestUrl)
                    .header("X-Offline-Save", "readinglist")
                    .header("X-Offline-Save-PageLibIds", "|${page.id}|")
                    .build()

                Log.d(loggerTag, "Requesting API URL for page ID ${page.id}: $apiRequestUrl")

                var response: Response? = null
                try {
                    response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        pageSuccessfullyFetched = true
                        Log.i(loggerTag, "Successfully fetched API response for: ${page.displayTitle}")

                        val offlineObjectForHtml = offlineObjectDao.getOfflineObjectByUrl(apiRequestUrl)
                        Log.d(loggerTag, "OfflineObject lookup by apiRequestUrl ('$apiRequestUrl'): ${if (offlineObjectForHtml != null) "FOUND" else "NOT FOUND"}")

                        if (offlineObjectForHtml != null) {
                            Log.d(loggerTag, "Found OfflineObject: ID=${offlineObjectForHtml.id}, Path='${offlineObjectForHtml.path}', SaveType='${offlineObjectForHtml.saveType}', URL='${offlineObjectForHtml.url}'")
                            if (offlineObjectForHtml.saveType == OfflineObject.SAVE_TYPE_READING_LIST) {
                                val baseDir = File(applicationContext.filesDir, "offline_pages_rl")
                                val contentFile = File(baseDir, offlineObjectForHtml.path + ".1")

                                Log.d(loggerTag, "Expected content file path: ${contentFile.absolutePath}")
                                Log.d(loggerTag, "Content file exists: ${contentFile.exists()}")

                                if (contentFile.exists()) {
                                    var htmlContentFromJSon: String? = null
                                    try {
                                        val jsonFileContent = contentFile.readText(Charsets.UTF_8)
                                        Log.d(loggerTag, "Read content file (JSON), length: ${jsonFileContent.length}. Snippet: ${jsonFileContent.take(200)}")
                                        val jsonResponse = JSONObject(jsonFileContent)
                                        htmlContentFromJSon = jsonResponse.optJSONObject("parse")
                                            ?.optString("text") // Get the string value of "text"
                                        Log.d(loggerTag, "Extracted HTML from JSON. Is HTML null? ${htmlContentFromJSon == null}. HTML snippet: ${htmlContentFromJSon?.take(200)}")
                                    } catch (jsonEx: Exception) {
                                        Log.e(loggerTag, "Failed to parse JSON or extract HTML from ${contentFile.absolutePath}", jsonEx)
                                    }

                                    if (htmlContentFromJSon != null) {
                                        val extractedText = extractTextFromHtmlString(htmlContentFromJSon)
                                        Log.d(loggerTag, "Text extracted by Jsoup. Is text null? ${extractedText == null}. Extracted text snippet: ${extractedText?.take(200)}")

                                        if (extractedText != null && extractedText.isNotBlank()) {
                                            val cleanDisplayTitle = Html.fromHtml(page.displayTitle, Html.FROM_HTML_MODE_LEGACY).toString()
                                            Log.d(loggerTag, "Cleaned display title for FTS: '$cleanDisplayTitle'")

                                            val ftsEntry = OfflinePageFts(
                                                url = canonicalPageUrl,
                                                title = cleanDisplayTitle,
                                                body = extractedText
                                            )
                                            try {
                                                Log.d(loggerTag, "Preparing to delete/insert FTS entry for URL: $canonicalPageUrl")
                                                ftsDao.deletePageContentByUrl(canonicalPageUrl)
                                                ftsDao.insertPageContent(ftsEntry)
                                                pageSuccessfullyIndexed = true
                                                Log.i(loggerTag, "Successfully indexed page: $canonicalPageUrl (ID: ${page.id})")
                                            } catch (e: Exception) {
                                                Log.e(loggerTag, "Error during FTS DB operation for page $canonicalPageUrl (ID: ${page.id}): ${e.message}", e)
                                            }
                                        } else {
                                            Log.w(loggerTag, "Extracted text by Jsoup is null or blank for: $canonicalPageUrl (ID: ${page.id}). Skipping FTS insert.")
                                        }
                                    } else {
                                        Log.w(loggerTag, "Extracted HTML content from JSON was null for: $canonicalPageUrl (ID: ${page.id}). Cannot index.")
                                    }
                                } else {
                                    Log.w(loggerTag, "Offline content file not found for $canonicalPageUrl (ID: ${page.id}) at expected path: ${contentFile.absolutePath}. Cannot index.")
                                }
                            } else {
                                Log.w(loggerTag, "OfflineObject found for $apiRequestUrl but has unexpected saveType: ${offlineObjectForHtml.saveType}. Skipping FTS indexing for this object.")
                            }
                        } else {
                            Log.w(loggerTag, "OfflineObject not found for apiRequestUrl: $apiRequestUrl. Cannot locate file to index.")
                        }

                        val totalSize = offlineObjectDao.getTotalBytesForPageId(page.id, applicationContext)
                        readingListPageDao.updatePageSizeBytes(page.id, totalSize)
                        Log.d(loggerTag, "Updated size for page ID ${page.id} to $totalSize bytes.")

                    } else {
                        Log.w(loggerTag, "Failed to fetch page ${page.displayTitle} (ID: ${page.id}). HTTP Code: ${response.code}")
                    }
                } finally {
                    response?.body?.close()
                }
            } catch (ioe: IOException) {
                Log.e(loggerTag, "IOException while processing page ${page.displayTitle} (ID: ${page.id}) for saving/indexing", ioe)
            } catch (e: Exception) {
                Log.e(loggerTag, "Unexpected error processing page ${page.displayTitle} (ID: ${page.id}) for saving/indexing", e)
            }

            Log.d(loggerTag, "Page processing finished. Fetched: $pageSuccessfullyFetched, Indexed: $pageSuccessfullyIndexed for page ID ${page.id}")
            if (!(pageSuccessfullyFetched && pageSuccessfullyIndexed)) {
                allItemsInThisBatchProcessedSuccessfully = false
            }
            Log.d(loggerTag, "----------------------------------------------------") // Log separator
        }
        Log.d(loggerTag, "Page saving/indexing batch finished. Overall success for this batch: $allItemsInThisBatchProcessedSuccessfully")
        return allItemsInThisBatchProcessedSuccessfully
    }

    private suspend fun processPagesToDelete(pagesToDelete: List<ReadingListPage>): Boolean {
        var allItemsInThisBatchProcessedSuccessfully = true
        Log.i(loggerTag, "Starting deletion process for ${pagesToDelete.size} pages.")
        for (page in pagesToDelete) {
            val pageTitleHelper = ReadingListPage.toPageTitle(page)
            val canonicalPageUrl = pageTitleHelper.uri
            try {
                Log.i(loggerTag, "Processing page for deletion: Title='${page.displayTitle}', URL='${canonicalPageUrl}', ReadingListPage.ID=${page.id}")

                offlineObjectDao.deleteObjectsForPageIds(listOf(page.id), applicationContext)

                try {
                    ftsDao.deletePageContentByUrl(canonicalPageUrl)
                    Log.i(loggerTag, "Successfully deleted FTS entry for page: $canonicalPageUrl (ID: ${page.id})")
                } catch (e: Exception) {
                    Log.e(loggerTag, "Error deleting FTS entry for page $canonicalPageUrl (ID: ${page.id}): ${e.message}", e)
                    allItemsInThisBatchProcessedSuccessfully = false
                }

                val newStatusAfterDeletion = ReadingListPage.STATUS_SAVED
                readingListPageDao.updatePageAfterOfflineDeletion(page.id, newStatusAfterDeletion, System.currentTimeMillis())
                Log.i(loggerTag, "Successfully processed file deletion and DB update for page: ${page.displayTitle} (ID: ${page.id}). Status set to $newStatusAfterDeletion, offline=false.")

            } catch (e: Exception) {
                Log.e(loggerTag, "Error processing page ${page.displayTitle} (ID: ${page.id}) for deletion", e)
                allItemsInThisBatchProcessedSuccessfully = false
            }
        }
        Log.d(loggerTag, "Page deletion batch finished. Success status for this batch: $allItemsInThisBatchProcessedSuccessfully")
        return allItemsInThisBatchProcessedSuccessfully
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "SavedPageSyncWorker"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SavedPageSyncWorker>()
                .setConstraints(constraints)
                .addTag(UNIQUE_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d("OSRSWIKI_WORKER_ENQUEUE", "SavedPageSyncWorker enqueued with policy REPLACE.")
        }
    }
}