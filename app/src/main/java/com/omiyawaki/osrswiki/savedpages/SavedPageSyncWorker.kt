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
import androidx.work.workDataOf
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.page.osrsCalculatorSaveWarmer
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.network.OkHttpClientFactory
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.settings.osrsDownloadSettings
import com.omiyawaki.osrswiki.settings.osrsSavedPageDownloadNetwork
import com.omiyawaki.osrswiki.settings.osrsSavedPageUpdateTrigger
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.extractTextFromHtmlString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

class SavedPageSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val readingListPageDao: ReadingListPageDao by lazy { AppDatabase.instance.readingListPageDao() }
    private val okHttpClient by lazy { OkHttpClientFactory.offlineClient }
    private val snapshotPublisher: ReadingListPageSnapshotPublisher by lazy {
        ReadingListPageSnapshotPublisher(applicationContext, AppDatabase.instance)
    }
    private val snapshotDeletion: ReadingListSnapshotDeletion by lazy {
        ReadingListSnapshotDeletion(
            context = applicationContext,
            database = AppDatabase.instance,
            invalidatePreparedArticle = { request ->
                (applicationContext as OSRSWikiApp).pageAssetDownloader.invalidatePreparedArticle(
                    pageId = request.pageId,
                    title = request.title
                )
            }
        )
    }

    private val baseApiUrl = "https://oldschool.runescape.wiki/api.php"
    private val loggerTag = "OSRSWIKI_WORKER"
    private val foregroundInfoFactory by lazy {
        SavedPageSyncForegroundInfoFactory(applicationContext)
    }
    private var foregroundTotalItems = 0
    private var foregroundCompletedItems = 0
    private var foregroundCancelableSavePageId: Long? = null

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.e(loggerTag, "!!! SavedPageSyncWorker: doWork() method CALLED !!!")
        Log.d(loggerTag, "SavedPageSyncWorker started. (Phase 3: Enhanced with incremental sync and queue management)")
        var overallSuccess = true
        var pagesToSave: List<ReadingListPage> = emptyList()

        try {
            queueAutomaticRevisionRefreshes()
            pagesToSave = readingListPageDao.getPagesToProcessForSaving()
            val pagesToDelete = readingListPageDao.getPagesToProcessForDeleting()
            Log.i(loggerTag, "Phase 3 Queue Status: ${pagesToSave.size} pages to save, ${pagesToDelete.size} pages to delete")
            SavedPageSyncForegroundPolicy.plan(pagesToSave.size, pagesToDelete.size)?.let { plan ->
                foregroundTotalItems = plan.totalItems
                reportForegroundProgress()
            }
            
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
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (linkageError: LinkageError) {
            // Android's runtime can reject a class initializer that the host JVM accepted (for
            // example, an ICU regex dialect difference). WorkManager records an Error as failed,
            // but it cannot repair our queued rows. Reconcile only the exact save batch already
            // claimed by this worker so the UI never remains stuck at a stale download percent.
            Log.e(loggerTag, "Fatal linkage error during SavedPageSyncWorker execution.", linkageError)
            withContext(NonCancellable) {
                pagesToSave.forEach { page ->
                    SavedPageSyncStatusRecorder.markSaveFailure(
                        readingListPageDao = readingListPageDao,
                        pageId = page.id,
                        currentTimeMs = System.currentTimeMillis()
                    )
                }
            }
            overallSuccess = false
        } catch (e: Exception) {
            Log.e(loggerTag, "Critical error during SavedPageSyncWorker execution.", e)
            overallSuccess = false
        }

        Log.d(loggerTag, "SavedPageSyncWorker finished. Overall success: $overallSuccess")
        return@withContext if (overallSuccess) Result.success() else Result.failure()
    }

    private suspend fun queueAutomaticRevisionRefreshes() {
        val settings = osrsDownloadSettings.load()
        if (!settings.shouldRefreshSnapshot(
                osrsSavedPageUpdateTrigger.AUTOMATIC_SCAN,
                isOnline = true,
                isUnmetered = true
            )
        ) {
            return
        }
        val savedPages = readingListPageDao.getPagesByStatus(ReadingListPage.STATUS_SAVED)
            .filter { it.offline }
        for (page in savedPages) {
            val remote = osrsSavedPageRevisionProbe.fetchRemoteRevision(page.apiTitle, okHttpClient)
                ?: continue
            if (!osrsSavedPageRevisionProbe.snapshotNeedsRefresh(page.revId, remote.revisionId)) {
                continue
            }
            readingListPageDao.transitionPageToForcedOfflineSave(page.id)
        }
    }

private suspend fun processPagesToSave(pagesToSave: List<ReadingListPage>): Boolean {
        var allItemsInThisBatchProcessedSuccessfully = true
        for (page in pagesToSave) {
            var pageSuccessfullyFetched = false
            var pageIndexPrepared = false
            var pageArticlePrepared = false
            var pageAssetsPersisted = false
            var mediaWikiPageId: Int? = null
            var canonicalTitle: String? = null
            var displayTitle: String? = null
            var revisionId: Long? = null
            var stagedArticleFile: File? = null
            var stagedFtsEntry: OfflinePageFts? = null

            val canonicalPageUrlForFts = ReadingListPage.toPageTitle(page).uri
            val snapshotStage = ReadingListPageSnapshotStage(
                context = applicationContext,
                client = okHttpClient,
                readingListPageId = page.id
            )

            try {
                Log.d(loggerTag, "----------------------------------------------------")
                Log.i(
                    loggerTag,
                    "Staging atomic offline snapshot for '${page.displayTitle}' " +
                        "(FTS URL: $canonicalPageUrlForFts, ReadingListPgID: ${page.id})"
                )
                foregroundCancelableSavePageId = page.id
                reportForegroundProgress()
                updateQueuedSaveProgressOrCancel(page.id, 5)
                updateQueuedSaveProgressOrCancel(page.id, 10)

                try {
                    val prepared = (applicationContext as OSRSWikiApp).pageAssetDownloader
                        .peekPreparedArticle(title = page.apiTitle, pageId = page.mediaWikiPageId?.takeIf { it > 0 })
                    val articleHtml: String?
                    if (prepared != null) {
                        pageSuccessfullyFetched = true
                        updateQueuedSaveProgressOrCancel(page.id, 40)
                        mediaWikiPageId = prepared.parseResult.pageid.takeIf { it > 0 }
                        canonicalTitle = prepared.parseResult.title.takeIf { it.isNotBlank() }
                        displayTitle = prepared.parseResult.title.takeIf { it.isNotBlank() }
                        revisionId = prepared.parseResult.revid.takeIf { it > 0L }
                        articleHtml = prepared.processedHtml.takeIf { it.isNotBlank() }
                    } else {
                        val encodedApiTitle = URLEncoder.encode(page.apiTitle, "UTF-8")
                        val apiRequestUrl = "$baseApiUrl?action=parse&format=json&formatversion=2&prop=text|revid|displaytitle|pageid|title&redirects=true&disableeditsection=true&disablelimitreport=true&page=$encodedApiTitle"
                        val stagedDocument = snapshotStage.stageDocument(apiRequestUrl)
                        val parseObject = stagedDocument?.contentFile?.readText(Charsets.UTF_8)
                            ?.let { JSONObject(it).optJSONObject("parse") }
                        if (stagedDocument != null && parseObject != null) {
                            pageSuccessfullyFetched = true
                            updateQueuedSaveProgressOrCancel(page.id, 30)
                            mediaWikiPageId = parseObject.optInt("pageid", 0).takeIf { it != 0 }
                            canonicalTitle = parseObject.optString("title").takeIf { it.isNotBlank() }
                            displayTitle = parseObject.optString("displaytitle").takeIf { it.isNotBlank() }
                            revisionId = parseObject.optLong("revid", 0L).takeIf { it != 0L }
                            articleHtml = parseObject.optString("text").takeIf { it.isNotBlank() }
                            updateQueuedSaveProgressOrCancel(page.id, 50)
                        } else {
                            articleHtml = null
                        }
                    }

                    if (articleHtml != null) {
                        val extractedText = extractTextFromHtmlString(articleHtml)
                        if (extractedText?.isNotBlank() == true) {
                            stagedFtsEntry = OfflinePageFts(
                                url = canonicalPageUrlForFts,
                                title = Html.fromHtml(
                                    page.displayTitle,
                                    Html.FROM_HTML_MODE_LEGACY
                                ).toString(),
                                body = extractedText
                            )
                            pageIndexPrepared = true
                            updateQueuedSaveProgressOrCancel(page.id, 70)
                        }

                        val parsedPageId = mediaWikiPageId
                        val parsedCanonicalTitle = canonicalTitle
                        if (parsedPageId != null && parsedCanonicalTitle != null) {
                            val fullHtml = (applicationContext as OSRSWikiApp).pageHtmlBuilder
                                .buildFullHtmlDocument(
                                    displayTitle ?: parsedCanonicalTitle,
                                    articleHtml,
                                    Theme.DEFAULT_LIGHT,
                                    Prefs.isCollapseTablesEnabled,
                                    canonicalTitle = parsedCanonicalTitle
                                )
                            stagedArticleFile = snapshotStage.stageArticleHtml(
                                parsedPageId,
                                fullHtml
                            )
                            pageArticlePrepared = true
                            osrsCalculatorSaveWarmer.warmDefaultParse(
                                applicationContext,
                                articleHtml,
                                parsedCanonicalTitle
                            )
                            updateQueuedSaveProgressOrCancel(page.id, 80)
                        }

                        val assetResult = ReadingListOfflineAssetSaver(snapshotStage)
                            .persistAll(page.id, articleHtml) { persisted, required ->
                                val spanned = 80 + ((persisted.toFloat() / required.toFloat()) * 15f).toInt()
                                updateQueuedSaveProgressOrCancel(page.id, spanned.coerceIn(80, 95))
                            }
                        pageAssetsPersisted = assetResult.isComplete
                        Log.i(
                            loggerTag,
                            "Staged explicit-save media for page ${page.id}: " +
                                "${assetResult.persistedCount}/${assetResult.requiredCount}; " +
                                "failures=${assetResult.failedUrls.size}"
                        )
                        updateQueuedSaveProgressOrCancel(page.id, 95)
                    }
                } catch (ownershipLost: ReadingListSnapshotOwnershipLostException) {
                    throw ownershipLost
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    Log.e(
                        loggerTag,
                        "Could not stage complete snapshot for ${page.displayTitle}",
                        failure
                    )
                }

                val pageSaveComplete = SavedPageSaveCompletionPolicy.isComplete(
                    htmlFetched = pageSuccessfullyFetched,
                    textIndexed = pageIndexPrepared,
                    articlePersisted = pageArticlePrepared,
                    assetsPersisted = pageAssetsPersisted
                )
                var terminalTransitionWon = false
                var publicationOwnershipReleased = false
                if (pageSaveComplete) {
                    try {
                        val publicationResult = ReadingListSnapshotPublicationHandoff.publishOrNull(
                            stage = snapshotStage,
                            publisher = snapshotPublisher,
                            publication = ReadingListSnapshotPublication(
                                page = page,
                                mediaWikiPageId = checkNotNull(mediaWikiPageId),
                                canonicalTitle = checkNotNull(canonicalTitle),
                                revisionId = revisionId,
                                articleFile = checkNotNull(stagedArticleFile),
                                ftsEntry = checkNotNull(stagedFtsEntry),
                                assets = snapshotStage.stagedResponses()
                            )
                        )
                        if (publicationResult != null) {
                            terminalTransitionWon = true
                            (applicationContext as OSRSWikiApp).pageAssetDownloader
                                .invalidatePreparedArticle(
                                    pageId = mediaWikiPageId ?: page.mediaWikiPageId,
                                    title = canonicalTitle ?: page.apiTitle
                                )
                            Log.i(
                                loggerTag,
                                "Published complete atomic snapshot for page ${page.id} " +
                                    "(${publicationResult.totalSizeBytes} bytes)."
                            )
                        } else {
                            publicationOwnershipReleased = true
                            Log.i(
                                loggerTag,
                                "Snapshot ownership was released for page ${page.id}; " +
                                    "discarding its staged generation and continuing the batch."
                            )
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Throwable) {
                        Log.e(
                            loggerTag,
                            "Atomic snapshot publication failed for ${page.id}",
                            failure
                        )
                    }
                }
                if (!terminalTransitionWon && !publicationOwnershipReleased) {
                    allItemsInThisBatchProcessedSuccessfully = false
                    terminalTransitionWon = withContext(NonCancellable) {
                        SavedPageSyncStatusRecorder.markSaveFailure(
                            readingListPageDao = readingListPageDao,
                            pageId = page.id,
                            currentTimeMs = System.currentTimeMillis()
                        )
                    }
                }
                foregroundCancelableSavePageId = null
                if (terminalTransitionWon || publicationOwnershipReleased) {
                    reportForegroundItemCompleted()
                }
                Log.d(loggerTag, "----------------------------------------------------")
            } catch (ownershipLost: ReadingListSnapshotOwnershipLostException) {
                foregroundCancelableSavePageId = null
                Log.i(
                    loggerTag,
                    "Snapshot ownership was released for page ${page.id}; " +
                        "discarding its staged generation and continuing the batch.",
                    ownershipLost
                )
                reportForegroundItemCompleted()
            } finally {
                snapshotStage.close()
            }
        }
        Log.d(
            loggerTag,
            "Page saving batch finished. Overall success: $allItemsInThisBatchProcessedSuccessfully"
        )
        return allItemsInThisBatchProcessedSuccessfully
    }

    private suspend fun updateQueuedSaveProgressOrCancel(pageId: Long, progress: Int) {
        val updated = readingListPageDao.updateQueuedSaveDownloadProgress(pageId, progress)
        if (updated == 0) {
            throw ReadingListSnapshotOwnershipLostException(pageId)
        }
    }


    private suspend fun processPagesToDelete(pagesToDelete: List<ReadingListPage>): Boolean {
        foregroundCancelableSavePageId = null
        reportForegroundProgress()
        Log.i(loggerTag, "Starting deletion process for ${pagesToDelete.size} pages.")
        return try {
            val authoritativePages = snapshotDeletion.removeOfflineSnapshots(
                pagesToDelete.map(ReadingListPage::id)
            )
            authoritativePages.forEach { page ->
                Log.i(
                    loggerTag,
                    "Removed authoritative offline snapshot for ${page.displayTitle} " +
                        "(reading-list ID ${page.id}, MediaWiki ID ${page.mediaWikiPageId})."
                )
                reportForegroundItemCompleted()
            }
            Log.d(loggerTag, "Page deletion batch finished. Overall success: true")
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            Log.e(loggerTag, "Atomic reading-list snapshot deletion failed.", failure)
            Log.d(loggerTag, "Page deletion batch finished. Overall success: false")
            false
        }
    }

    private suspend fun reportForegroundItemCompleted() {
        foregroundCompletedItems = (foregroundCompletedItems + 1).coerceAtMost(foregroundTotalItems)
        reportForegroundProgress()
    }

    private suspend fun reportForegroundProgress() {
        if (foregroundTotalItems <= 0) return
        val progress = workDataOf(
            PROGRESS_COMPLETED_ITEMS to foregroundCompletedItems,
            PROGRESS_TOTAL_ITEMS to foregroundTotalItems
        )
        setProgress(progress)
        setForeground(
            foregroundInfoFactory.create(
                completedItems = foregroundCompletedItems,
                totalItems = foregroundTotalItems,
                cancelIntent = foregroundCancelableSavePageId?.let { pageId ->
                    SavedPageSyncCancelReceiver.pendingIntent(applicationContext, pageId)
                }
            )
        )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "SavedPageSyncWorker"
        internal const val PROGRESS_COMPLETED_ITEMS = "completed_items"
        internal const val PROGRESS_TOTAL_ITEMS = "total_items"
        
        /**
         * Phase 3: Enhanced enqueue with proper queue management
         * Uses APPEND_OR_REPLACE to ensure requests are processed sequentially
         */
        fun enqueue(context: Context) {
            // Phase 3: Use APPEND_OR_REPLACE to ensure proper queuing
            // This prevents cancelling running workers while ensuring new requests are processed
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                newWorkRequest()
            )
            Log.d("OSRSWIKI_WORKER_ENQUEUE", "SavedPageSyncWorker enqueued with policy APPEND_OR_REPLACE for sequential processing.")
        }

        private fun newWorkRequest() = OneTimeWorkRequestBuilder<SavedPageSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        when (osrsDownloadSettings.load().downloadNetwork) {
                            osrsSavedPageDownloadNetwork.WIFI_ONLY -> NetworkType.UNMETERED
                            osrsSavedPageDownloadNetwork.ANY -> NetworkType.CONNECTED
                        }
                    )
                    .build()
            )
            .addTag(UNIQUE_WORK_NAME)
            .build()

        suspend fun cancelQueuedSave(
            context: Context,
            readingListPageDao: ReadingListPageDao,
            pageId: Long
        ) = withContext(Dispatchers.IO) {
            // One unique chain owns explicit-save work. Stop it first so a worker snapshot cannot
            // mark the cancelled page SAVED after the in-app cancel action, then resume any other
            // pending entries in a fresh chain.
            WorkManager.getInstance(context)
                .cancelUniqueWork(UNIQUE_WORK_NAME)
                .result
                .get()

            val pageIdentity = readingListPageDao.getPageById(pageId)
            val outcome = SavedPageSyncCancellationReconciler.reconcile(
                readingListPageDao = readingListPageDao,
                pageId = pageId,
                currentTimeMs = System.currentTimeMillis()
            )
            if (outcome.terminalTransitionWon && pageIdentity != null) {
                (context.applicationContext as? OSRSWikiApp)?.pageAssetDownloader
                    ?.invalidatePreparedArticle(
                        pageId = pageIdentity.mediaWikiPageId,
                        title = pageIdentity.apiTitle
                    )
            }
            if (outcome.shouldResumeQueue) {
                enqueue(context)
            }
        }
    }
}

internal data class SavedPageSyncCancellationOutcome(
    val terminalTransitionWon: Boolean,
    val shouldResumeQueue: Boolean
)

internal object SavedPageSyncCancellationReconciler {
    suspend fun reconcile(
        readingListPageDao: ReadingListPageDao,
        pageId: Long,
        currentTimeMs: Long
    ): SavedPageSyncCancellationOutcome {
        val transitionWon = SavedPageSyncStatusRecorder.markSaveFailure(
            readingListPageDao = readingListPageDao,
            pageId = pageId,
            currentTimeMs = currentTimeMs
        )
        return SavedPageSyncCancellationOutcome(
            terminalTransitionWon = transitionWon,
            shouldResumeQueue = readingListPageDao.getPagesToProcessForSaving().isNotEmpty() ||
                readingListPageDao.getPagesToProcessForDeleting().isNotEmpty()
        )
    }
}
