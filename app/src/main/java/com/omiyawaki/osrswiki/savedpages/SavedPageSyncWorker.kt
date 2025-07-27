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
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.database.OfflinePageFtsDao
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.network.OkHttpClientFactory
import com.omiyawaki.osrswiki.page.PageRepository
import com.omiyawaki.osrswiki.page.PageTitle
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.util.Result as AppResult
import com.omiyawaki.osrswiki.util.extractTextFromHtmlString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset

class SavedPageSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val readingListPageDao: ReadingListPageDao by lazy { AppDatabase.instance.readingListPageDao() }
    private val offlineObjectDao: OfflineObjectDao by lazy { AppDatabase.instance.offlineObjectDao() }
    private val ftsDao: OfflinePageFtsDao by lazy { AppDatabase.instance.offlinePageFtsDao() }
    private val okHttpClient by lazy { OkHttpClientFactory.offlineClient }
    private val pageRepository: PageRepository by lazy {
        (applicationContext as OSRSWikiApp).pageRepository
    }

    private val baseApiUrl = "https://oldschool.runescape.wiki/api.php"
    private val loggerTag = "OSRSWIKI_WORKER"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.e(loggerTag, "!!! SavedPageSyncWorker: doWork() method CALLED !!!")
        Log.d(loggerTag, "SavedPageSyncWorker started. (Phase 3: Enhanced with incremental sync and queue management)")
        var overallSuccess = true

        try {
            val pagesToSave = readingListPageDao.getPagesToProcessForSaving()
            val pagesToDelete = readingListPageDao.getPagesToProcessForDeleting()
            Log.i(loggerTag, "Phase 3 Queue Status: ${pagesToSave.size} pages to save, ${pagesToDelete.size} pages to delete")
            
            if (pagesToSave.isNotEmpty()) {
                Log.i(loggerTag, "Found ${pagesToSave.size} page(s) to save.")
                if (!processPagesToSave(pagesToSave)) {
                    overallSuccess = false
                }
            } else {
                Log.d(loggerTag, "No pages found to save.")
            }
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
            var pageSuccessfullyFetchedForMech2 = false
            var pageSuccessfullyIndexedForMech2 = false
            var pageSuccessfullySavedToArticleMeta = false
            var contentFile: File? = null

            // Declare variables for extracted data at the top of the loop for this page iteration
            var M1_pageId: Int? = null
            var M1_canonicalTitle: String? = null
            var htmlContentFromJSon: String? = null

            val pageTitleHelper = ReadingListPage.toPageTitle(page)
            val canonicalPageUrlForFts = pageTitleHelper.uri

            Log.d(loggerTag, "----------------------------------------------------")
            Log.i(loggerTag, "Attempting to save and index page: '${page.displayTitle}' (URL for FTS: $canonicalPageUrlForFts, ReadingListPgID: ${page.id})")
            
            // Phase 3: Incremental sync - check if page has changed before downloading
            val currentRevId = getCurrentRevisionId(page.apiTitle)
            if (currentRevId != null && page.revId > 0 && currentRevId == page.revId) {
                Log.i(loggerTag, "Page '${page.displayTitle}' unchanged (revId: $currentRevId). Skipping download.")
                readingListPageDao.updatePageStatusToSavedAndMtime(page.id, currentTimeMs = System.currentTimeMillis())
                continue
            }
            
            if (currentRevId != null) {
                Log.i(loggerTag, "Page '${page.displayTitle}' revision changed: stored=${page.revId}, current=$currentRevId. Proceeding with download.")
            } else {
                Log.w(loggerTag, "Could not determine current revision for '${page.displayTitle}'. Proceeding with download.")
            }

            try {
                val encodedApiTitle = URLEncoder.encode(page.apiTitle, "UTF-8")
                val apiRequestUrl = "$baseApiUrl?action=parse&format=json&formatversion=2&prop=text|revid|displaytitle|pageid|title&redirects=true&disableeditsection=true&disablelimitreport=true&page=$encodedApiTitle"
                val request = Request.Builder().url(apiRequestUrl)
                    .header("X-Offline-Save", "readinglist")
                    .header("X-Offline-Save-PageLibIds", "|${page.id}|")
                    .build()
                Log.d(loggerTag, "Requesting API URL for page ID ${page.id}: $apiRequestUrl")

                var response: Response? = null
                try {
                    response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        pageSuccessfullyFetchedForMech2 = true
                        Log.i(loggerTag, "Successfully fetched API response for: ${page.displayTitle}")

                        val offlineObjectForHtml = offlineObjectDao.getOfflineObjectByUrl(apiRequestUrl)
                        if (offlineObjectForHtml != null && offlineObjectForHtml.saveType == OfflineObject.SAVE_TYPE_READING_LIST) {
                            val baseDir = File(applicationContext.filesDir, "offline_pages_rl")
                            contentFile = File(baseDir, offlineObjectForHtml.path + ".1")
                            Log.d(loggerTag, "Expected content file for JSON: ${contentFile.absolutePath}, Exists: ${contentFile.exists()}")
                        } else {
                            Log.w(loggerTag, "OfflineObject not found for reading list type or is null for URL: $apiRequestUrl")
                        }

                        if (contentFile?.exists() == true) {
                            try {
                                val jsonFileContent = contentFile.readText(Charsets.UTF_8)
                                val jsonResponse = JSONObject(jsonFileContent)
                                val parseObject = jsonResponse.optJSONObject("parse") // parseObject is local to this try block
                                if (parseObject != null) {
                                    M1_pageId = parseObject.optInt("pageid", 0).takeIf { it != 0 }
                                    M1_canonicalTitle = if (parseObject.has("title") && !parseObject.isNull("title")) parseObject.getString("title") else null
                                    htmlContentFromJSon = if (parseObject.has("text") && !parseObject.isNull("text")) parseObject.getString("text") else null
                                    val newRevId = parseObject.optLong("revid", 0L).takeIf { it != 0L }
                                    Log.d(loggerTag, "Extracted from JSON: pageId=$M1_pageId, canonicalTitle='$M1_canonicalTitle', revId=$newRevId. HTML isNull: ${htmlContentFromJSon == null}")

                                    if (M1_pageId != null) {
                                        readingListPageDao.updateMediaWikiPageId(page.id, M1_pageId)
                                        Log.i(loggerTag, "Updated ReadingListPage (ID: ${page.id}) with mediaWikiPageId: $M1_pageId")
                                    }
                                    
                                    // Phase 3: Update the stored revision ID
                                    if (newRevId != null) {
                                        readingListPageDao.updatePageRevisionId(page.id, newRevId)
                                        Log.i(loggerTag, "Updated ReadingListPage (ID: ${page.id}) with revisionId: $newRevId")
                                    }
                                } else { Log.w(loggerTag, "No 'parse' object in JSON response for ${page.displayTitle}") }
                            } catch (jsonEx: Exception) { Log.e(loggerTag, "Failed to parse JSON from ${contentFile.absolutePath}", jsonEx) }
                        } else { Log.w(loggerTag, "Content file for JSON response not found for ${page.displayTitle}") }

                        // Now use the loop-scoped M1_canonicalTitle and htmlContentFromJSon
                        if (htmlContentFromJSon != null) {
                            val extractedText = extractTextFromHtmlString(htmlContentFromJSon)
                            if (extractedText?.isNotBlank() == true) {
                                val cleanDisplayTitle = Html.fromHtml(page.displayTitle, Html.FROM_HTML_MODE_LEGACY).toString()
                                val ftsEntry = OfflinePageFts(url = canonicalPageUrlForFts, title = cleanDisplayTitle, body = extractedText)
                                try {
                                    ftsDao.deletePageContentByUrl(canonicalPageUrlForFts)
                                    ftsDao.insertPageContent(ftsEntry)
                                    pageSuccessfullyIndexedForMech2 = true
                                    Log.i(loggerTag, "Successfully FTS-indexed page: $canonicalPageUrlForFts")
                                } catch (e: Exception) { Log.e(loggerTag, "Error FTS DB op for $canonicalPageUrlForFts", e) }
                            } else { Log.w(loggerTag, "Extracted text for FTS is blank for $canonicalPageUrlForFts") }
                        } else { Log.w(loggerTag, "HTML from JSON was null for FTS for ${page.displayTitle}") }

                        if (M1_pageId != null) {
                            Log.d(loggerTag, "Attempting ArticleMeta save (PageRepository) for pageId: $M1_pageId ('${page.displayTitle}', API Canonical: '$M1_canonicalTitle')")
                            try {
                                val repoSaveResult = pageRepository.saveArticleOffline(pageId = M1_pageId, displayTitleFromUi = page.displayTitle)
                                if (repoSaveResult is AppResult.Success) {
                                    pageSuccessfullySavedToArticleMeta = true
                                    Log.i(loggerTag, "Successfully saved pageId $M1_pageId to ArticleMeta.")
                                } else if (repoSaveResult is AppResult.Error) {
                                    Log.e(loggerTag, "Failed to save pageId $M1_pageId to ArticleMeta: ${repoSaveResult.message}")
                                }
                            } catch (e: Exception) { Log.e(loggerTag, "Exception PageRepository.saveArticleOffline for $M1_pageId", e) }
                        } else { Log.w(loggerTag, "Cannot save to ArticleMeta: Valid pageId not extracted for '${page.displayTitle}'.") }

                        val totalSize = offlineObjectDao.getTotalBytesForPageId(page.id, applicationContext)
                        readingListPageDao.updatePageSizeBytes(page.id, totalSize)
                        readingListPageDao.updatePageStatusToSavedAndMtime(page.id, currentTimeMs = System.currentTimeMillis())
                        Log.d(loggerTag, "Updated ReadingListPage status to SAVED & size for page ID ${page.id} to $totalSize bytes.")
                    } else { Log.w(loggerTag, "Failed to fetch page ${page.displayTitle}. HTTP: ${response.code}") }
                } finally { response?.body?.close() }
            } catch (ioe: IOException) { Log.e(loggerTag, "IOException for ${page.displayTitle}", ioe)
            } catch (e: Exception) { Log.e(loggerTag, "Unexpected error for ${page.displayTitle}", e) }

            Log.d(loggerTag, "Page processing fin. Mech2Fetch: $pageSuccessfullyFetchedForMech2, Mech2Index: $pageSuccessfullyIndexedForMech2, ArtMetaSave: $pageSuccessfullySavedToArticleMeta for page ID ${page.id}")
            if (!(pageSuccessfullyFetchedForMech2 && pageSuccessfullyIndexedForMech2 && pageSuccessfullySavedToArticleMeta )) {
                allItemsInThisBatchProcessedSuccessfully = false
            }
            Log.d(loggerTag, "----------------------------------------------------")
        }
        Log.d(loggerTag, "Page saving batch finished. Overall success: $allItemsInThisBatchProcessedSuccessfully")
        return allItemsInThisBatchProcessedSuccessfully
    }

    private suspend fun processPagesToDelete(pagesToDelete: List<ReadingListPage>): Boolean {
        var allItemsInThisBatchProcessedSuccessfully = true
        Log.i(loggerTag, "Starting deletion process for ${pagesToDelete.size} pages.")
        for (page in pagesToDelete) {
            val pageTitleHelper = ReadingListPage.toPageTitle(page)
            val canonicalPageUrlForFts = pageTitleHelper.uri

            val M1_pageId_for_delete: Int? = page.mediaWikiPageId // Use the stored Int? field directly

            try {
                Log.i(loggerTag, "Processing page for deletion: Title='${page.displayTitle}', URL for FTS='${canonicalPageUrlForFts}', ReadingListPg.ID=${page.id}, MediaWiki PageID for ArticleMeta: $M1_pageId_for_delete")

                offlineObjectDao.deleteObjectsForPageIds(listOf(page.id), applicationContext)
                Log.i(loggerTag, "Successfully deleted offline objects (Mech2) for page: ${page.displayTitle}")

                try {
                    ftsDao.deletePageContentByUrl(canonicalPageUrlForFts)
                    Log.i(loggerTag, "Successfully deleted FTS entry for: $canonicalPageUrlForFts")
                } catch (e: Exception) { Log.e(loggerTag, "Error deleting FTS for $canonicalPageUrlForFts", e) }

                if (M1_pageId_for_delete != null && M1_pageId_for_delete != 0) { // Check for valid non-zero ID
                    Log.d(loggerTag, "Attempting ArticleMeta deletion (PageRepository) for MediaWiki pageId: $M1_pageId_for_delete")
                    try {
                        val removeResult = pageRepository.removeArticleOffline(pageId = M1_pageId_for_delete)
                        if (removeResult is AppResult.Success) {
                            Log.i(loggerTag, "Successfully removed MediaWiki pageId $M1_pageId_for_delete from ArticleMeta.")
                        } else if (removeResult is AppResult.Error) {
                            Log.w(loggerTag, "Failed to remove MediaWiki pageId $M1_pageId_for_delete from ArticleMeta: ${removeResult.message}")
                        }
                    } catch (e: Exception) { Log.e(loggerTag, "Exception PageRepository.removeArticleOffline for $M1_pageId_for_delete", e) }
                } else { Log.w(loggerTag, "Cannot remove from ArticleMeta: MediaWiki pageId not available/valid in ReadingListPage for '${page.displayTitle}'.") }

                val newStatusAfterDeletion = ReadingListPage.STATUS_SAVED
                readingListPageDao.updatePageAfterOfflineDeletion(page.id, newStatusAfterDeletion, System.currentTimeMillis())
                Log.i(loggerTag, "Updated ReadingListPage status for ${page.displayTitle} (ID: ${page.id}). Status: $newStatusAfterDeletion, offline=false.")
            } catch (e: Exception) {
                Log.e(loggerTag, "Error processing deletion for ${page.displayTitle} (ID: ${page.id})", e)
                allItemsInThisBatchProcessedSuccessfully = false
            }
        }
        Log.d(loggerTag, "Page deletion batch finished. Overall success: $allItemsInThisBatchProcessedSuccessfully")
        return allItemsInThisBatchProcessedSuccessfully
    }

    /**
     * Phase 3: Get current revision ID from API without downloading full content
     */
    private suspend fun getCurrentRevisionId(apiTitle: String): Long? {
        try {
            val encodedApiTitle = URLEncoder.encode(apiTitle, "UTF-8")
            val infoUrl = "$baseApiUrl?action=query&format=json&formatversion=2&prop=info&titles=$encodedApiTitle"
            val request = Request.Builder().url(infoUrl).build()
            
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(loggerTag, "Failed to get revision info for '$apiTitle'. HTTP: ${response.code}")
                return null
            }
            
            response.body?.use { body ->
                val jsonResponse = JSONObject(body.string())
                val queryObject = jsonResponse.optJSONObject("query")
                val pagesObject = queryObject?.optJSONObject("pages")
                
                if (pagesObject != null) {
                    val pageKeys = pagesObject.keys()
                    if (pageKeys.hasNext()) {
                        val pageId = pageKeys.next()
                        val pageInfo = pagesObject.getJSONObject(pageId)
                        val revId = pageInfo.optLong("lastrevid", 0L)
                        if (revId > 0) {
                            Log.d(loggerTag, "Current revision ID for '$apiTitle': $revId")
                            return revId
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(loggerTag, "Error getting revision ID for '$apiTitle'", e)
        }
        return null
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "SavedPageSyncWorker"
        
        /**
         * Phase 3: Enhanced enqueue with proper queue management
         * Uses APPEND_OR_REPLACE to ensure requests are processed sequentially
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<SavedPageSyncWorker>()
                .setConstraints(constraints)
                .addTag(UNIQUE_WORK_NAME)
                .build()
                
            // Phase 3: Use APPEND_OR_REPLACE to ensure proper queuing
            // This prevents cancelling running workers while ensuring new requests are processed
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                workRequest
            )
            Log.d("OSRSWIKI_WORKER_ENQUEUE", "SavedPageSyncWorker enqueued with policy APPEND_OR_REPLACE for sequential processing.")
        }
    }
}