package com.omiyawaki.osrswiki.savedpages

import android.content.Context
import androidx.room.withTransaction
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.dataclient.okhttp.OfflineResponseFileWriter
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Prevents an explicit snapshot refresh from falling back to the generation it is replacing. */
internal data object ReadingListSnapshotNetworkRequestMarker

internal data class ReadingListStagedResponse(
    val url: String,
    val language: String,
    val relativePath: String,
    val metadataFile: File,
    val contentFile: File
)

/**
 * Downloads a complete page generation into unreferenced files. Nothing here is visible to the
 * resolver until [ReadingListPageSnapshotPublisher] switches every database pointer atomically.
 */
internal class ReadingListPageSnapshotStage(
    context: Context,
    private val client: OkHttpClient,
    private val readingListPageId: Long,
    private val language: String = "en",
    generationId: String = UUID.randomUUID().toString()
) : ReadingListAssetFetcher, AutoCloseable {
    private val offlineRoot = File(context.filesDir, ReadingListOfflineAssetResolver.STORAGE_DIRECTORY)
    private val relativeGenerationDirectory = ".generations/$generationId"
    private val generationDirectory = File(offlineRoot, relativeGenerationDirectory)
    private val articleGenerationDirectory = File(
        File(context.filesDir, ARTICLES_DIRECTORY),
        relativeGenerationDirectory
    )
    private val stagedResponses = ConcurrentHashMap<String, ReadingListStagedResponse>()
    @Volatile
    private var published = false

    override suspend fun fetchAndPersist(url: String, readingListPageId: Long): Boolean {
        check(readingListPageId == this.readingListPageId)
        stagedResponses[url]?.let { return true }
        val staged = stageResponse(url, validateArtwork = true) ?: return false
        stagedResponses[url] = staged
        return true
    }

    suspend fun stageDocument(url: String): ReadingListStagedResponse? {
        stagedResponses[url]?.let { return it }
        val staged = stageResponse(url, validateArtwork = false) ?: return null
        stagedResponses[url] = staged
        return staged
    }

    override suspend fun readPersistedCss(url: String): ReadingListPersistedCss? {
        val staged = stagedResponses[url] ?: return null
        val contentType = ReadingListAssetResponseValidator.contentTypeFromMetadata(staged.metadataFile)
            .orEmpty()
        val isCss = contentType.substringBefore(';').trim().equals("text/css", ignoreCase = true) ||
            runCatching { URI(url).path.endsWith(".css", ignoreCase = true) }.getOrDefault(false)
        if (!isCss) return null
        if (staged.contentFile.length() > ReadingListOfflineAssetSaver.MAX_CSS_CHARACTERS_PER_STYLESHEET) {
            return ReadingListPersistedCss.TooLarge
        }
        return ReadingListPersistedCss.Content(staged.contentFile.readText())
    }

    fun stagedResponses(): List<ReadingListStagedResponse> =
        stagedResponses.values.sortedBy(ReadingListStagedResponse::url)

    fun stageArticleHtml(pageId: Int, fullHtml: String): File {
        articleGenerationDirectory.mkdirs()
        val destination = File(articleGenerationDirectory, "$pageId.html")
        val temporary = File(articleGenerationDirectory, "$pageId.html.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(fullHtml.toByteArray(StandardCharsets.UTF_8))
                output.fd.sync()
            }
            check(temporary.renameTo(destination)) { "Could not stage offline article HTML" }
            return destination
        } catch (failure: Throwable) {
            temporary.delete()
            destination.delete()
            throw failure
        }
    }

    fun markPublished() {
        published = true
    }

    override fun close() {
        if (!published) {
            generationDirectory.deleteRecursively()
            articleGenerationDirectory.deleteRecursively()
        }
    }

    private suspend fun stageResponse(
        url: String,
        validateArtwork: Boolean
    ): ReadingListStagedResponse? = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(url)
            .header("Accept-Language", language)
            .tag(
                ReadingListSnapshotNetworkRequestMarker::class.java,
                ReadingListSnapshotNetworkRequestMarker
            )
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: java.io.IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful || response.body == null) {
                        if (continuation.isActive) continuation.resume(null)
                        return
                    }
                    var staged: OfflineResponseFileWriter.StagedResponse? = null
                    try {
                        val metadata = response.headers.toMultimap().entries.joinToString("\n") {
                            "${it.key}: ${it.value.joinToString(", ")}"
                        }.toByteArray(StandardCharsets.UTF_8)
                        staged = OfflineResponseFileWriter.stageResponse(
                            storageDir = generationDirectory,
                            hashedBaseName = hashUrl(url, language),
                            metadata = metadata,
                            body = response.body!!.byteStream()
                        )
                        if (validateArtwork) {
                            ReadingListAssetResponseValidator.invalidReason(
                                url = url,
                                contentType = response.header("Content-Type"),
                                contentFile = staged.contentFile
                            )?.let { reason ->
                                throw ReadingListAssetValidationException(url, reason)
                            }
                        }
                        val result = ReadingListStagedResponse(
                            url = url,
                            language = language,
                            relativePath = "$relativeGenerationDirectory/${staged.path}",
                            metadataFile = staged.metadataFile,
                            contentFile = staged.contentFile
                        )
                        if (continuation.isActive) {
                            continuation.resume(result)
                        } else {
                            staged.discard()
                        }
                    } catch (failure: Throwable) {
                        staged?.discard()
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            }
        })
    }

    private fun hashUrl(url: String, language: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest("$url-$language".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ARTICLES_DIRECTORY = "osrs_wiki_articles"
    }
}

internal data class ReadingListSnapshotPublication(
    val page: ReadingListPage,
    val mediaWikiPageId: Int,
    val canonicalTitle: String,
    val revisionId: Long?,
    val articleFile: File,
    val ftsEntry: OfflinePageFts,
    val assets: List<ReadingListStagedResponse>
)

internal data class ReadingListSnapshotPublicationResult(
    val totalSizeBytes: Long
)

internal class ReadingListSnapshotOwnershipLostException(pageId: Long) :
    IllegalStateException("Reading-list page $pageId no longer belongs to this snapshot publication")

/** Makes the database commit-to-file-ownership handoff indivisible with respect to cancellation. */
internal object ReadingListSnapshotPublicationHandoff {
    suspend fun publishOrNull(
        stage: ReadingListPageSnapshotStage,
        publisher: ReadingListPageSnapshotPublisher,
        publication: ReadingListSnapshotPublication,
        currentTimeMs: Long = System.currentTimeMillis()
    ): ReadingListSnapshotPublicationResult? = try {
        withContext(NonCancellable) {
            publisher.publish(publication, currentTimeMs).also { stage.markPublished() }
        }
    } catch (_: ReadingListSnapshotOwnershipLostException) {
        null
    }
}

/** Makes one complete staged generation visible at a single Room transaction boundary. */
internal class ReadingListPageSnapshotPublisher(
    private val context: Context,
    private val database: AppDatabase
) {
    suspend fun publish(
        publication: ReadingListSnapshotPublication,
        currentTimeMs: Long = System.currentTimeMillis()
    ): ReadingListSnapshotPublicationResult {
        val readingListDao = database.readingListPageDao()
        val offlineDao = database.offlineObjectDao()
        val articleDao = database.articleMetaDao()
        val ftsDao = database.offlinePageFtsDao()
        val oldOfflineFiles = mutableListOf<OfflineObject>()
        var oldArticleFile: File? = null
        var totalSizeBytes = 0L

        database.withTransaction {
            val currentPage = readingListDao.getPageById(publication.page.id)
                ?: throw ReadingListSnapshotOwnershipLostException(publication.page.id)
            if (
                currentPage.status != ReadingListPage.STATUS_QUEUE_FOR_SAVE &&
                currentPage.status != ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE
            ) {
                throw ReadingListSnapshotOwnershipLostException(publication.page.id)
            }

            val previousObjects = offlineDao.getObjectsUsedByPageId(publication.page.id)
            val publishedUrls = publication.assets.mapTo(linkedSetOf()) { it.url }
            publication.assets.forEach { staged ->
                val existing = offlineDao.findByUrlAndLangAndSaveType(
                    staged.url,
                    staged.language,
                    OfflineObject.SAVE_TYPE_READING_LIST
                )
                val mergedOwners = ReadingListAssetOwnership.add(
                    existing?.usedByStr.orEmpty(),
                    publication.page.id
                )
                val replacement = OfflineObject(
                    id = existing?.id ?: 0L,
                    url = staged.url,
                    lang = staged.language,
                    path = staged.relativePath,
                    status = OfflineObject.STATUS_SAVED,
                    usedByStr = mergedOwners,
                    saveType = OfflineObject.SAVE_TYPE_READING_LIST
                )
                if (existing == null) {
                    offlineDao.insertOfflineObject(replacement)
                } else {
                    offlineDao.updateOfflineObject(replacement)
                    if (existing.path != replacement.path) oldOfflineFiles += existing
                }
            }
            previousObjects.filter { it.url !in publishedUrls }.forEach { previous ->
                val remainingOwners = ReadingListAssetOwnership.remove(
                    previous.usedByStr,
                    publication.page.id
                )
                if (remainingOwners.isEmpty()) {
                    offlineDao.deleteOfflineObjectQuery(previous.id)
                    oldOfflineFiles += previous
                } else {
                    offlineDao.updateOfflineObject(previous.copy(usedByStr = remainingOwners))
                }
            }

            val existingMeta = articleDao.getMetaByPageId(publication.mediaWikiPageId)
            val newMeta = ArticleMetaEntity(
                id = existingMeta?.id ?: 0L,
                pageId = publication.mediaWikiPageId,
                title = publication.canonicalTitle,
                wikiUrl = "https://oldschool.runescape.wiki/w/${publication.canonicalTitle.replace(" ", "_")}",
                localFilePath = publication.articleFile.absolutePath,
                lastFetchedTimestamp = currentTimeMs,
                revisionId = publication.revisionId,
                categories = existingMeta?.categories
            )
            if (existingMeta == null) articleDao.insert(newMeta) else articleDao.update(newMeta)
            oldArticleFile = existingMeta?.localFilePath
                ?.takeIf { it != publication.articleFile.absolutePath }
                ?.let(::File)

            ftsDao.deletePageContentByUrl(publication.ftsEntry.url)
            ftsDao.insertPageContent(publication.ftsEntry)
            readingListDao.updateMediaWikiPageId(publication.page.id, publication.mediaWikiPageId)
            publication.revisionId?.let { revision ->
                readingListDao.updatePageRevisionId(publication.page.id, revision)
            }
            totalSizeBytes = offlineDao.getTotalBytesForPageId(publication.page.id, context) +
                publication.articleFile.length()
            val transitionWon = SavedPageSyncStatusRecorder.markSaveSuccess(
                readingListPageDao = readingListDao,
                pageId = publication.page.id,
                totalSizeBytes = totalSizeBytes,
                currentTimeMs = currentTimeMs
            )
            if (!transitionWon) {
                throw ReadingListSnapshotOwnershipLostException(publication.page.id)
            }
        }

        oldOfflineFiles.distinctBy { it.path }.forEach { old ->
            database.offlineObjectDao().deleteFilesForObject(old, context)
        }
        oldArticleFile?.delete()
        return ReadingListSnapshotPublicationResult(totalSizeBytes)
    }
}
