package com.omiyawaki.osrswiki.savedpages

import android.content.Context
import androidx.room.withTransaction
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmRequest
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import java.io.File

/**
 * Atomically claims and detaches reading-list snapshot records, then deletes unreferenced physical
 * files only after Room commits. This serializes deletion against snapshot publication and keeps
 * shared OfflineObject, FTS, and ArticleMeta identities alive until their final owner is removed.
 */
internal class ReadingListSnapshotDeletion(
    private val context: Context,
    private val database: AppDatabase,
    private val invalidatePreparedArticle: (ArticlePrewarmRequest) -> Unit
) {
    suspend fun deleteReadingListRows(pageIds: List<Long>): List<ReadingListPage> =
        mutate(pageIds = pageIds, removeRows = true, currentTimeMs = 0L)

    suspend fun removeOfflineSnapshots(
        pageIds: List<Long>,
        currentTimeMs: Long = System.currentTimeMillis()
    ): List<ReadingListPage> = mutate(
        pageIds = pageIds,
        removeRows = false,
        currentTimeMs = currentTimeMs
    )

    private suspend fun mutate(
        pageIds: List<Long>,
        removeRows: Boolean,
        currentTimeMs: Long
    ): List<ReadingListPage> {
        if (pageIds.isEmpty()) return emptyList()
        val readingListPageDao = database.readingListPageDao()
        val offlineObjectDao = database.offlineObjectDao()
        val offlinePageFtsDao = database.offlinePageFtsDao()
        val articleMetaDao = database.articleMetaDao()

        val mutation = database.withTransaction {
            val claimedPages = readingListPageDao.claimPagesForDeletion(pageIds.distinct())
            val orphanedOfflineObjects = offlineObjectDao.releaseObjectsForPageIds(
                claimedPages.map(ReadingListPage::id)
            )
            val orphanedArticleFiles = mutableListOf<File>()

            claimedPages.distinctBy(::pageIdentity).forEach { page ->
                if (
                    !readingListPageDao.hasOfflineReferenceForPageIdentity(
                        wiki = page.wiki,
                        lang = page.lang,
                        namespace = page.namespace,
                        apiTitle = page.apiTitle
                    )
                ) {
                    offlinePageFtsDao.deletePageContentByUrl(ReadingListPage.toPageTitle(page).uri)
                }
            }

            claimedPages.mapNotNull(ReadingListPage::mediaWikiPageId).distinct()
                .forEach { mediaWikiPageId ->
                    if (!readingListPageDao.hasOfflineReferenceForMediaWikiPageId(mediaWikiPageId)) {
                        articleMetaDao.getMetaByPageId(mediaWikiPageId)?.let { meta ->
                            articleMetaDao.delete(meta)
                            meta.localFilePath.takeIf(String::isNotEmpty)
                                ?.let(::File)
                                ?.let(orphanedArticleFiles::add)
                        }
                    }
                }

            if (removeRows) {
                readingListPageDao.purgeClaimedPagesByIds(
                    claimedPages.map(ReadingListPage::id)
                )
            } else {
                claimedPages.forEach { page ->
                    readingListPageDao.updatePageAfterOfflineDeletion(
                        pageId = page.id,
                        newStatus = ReadingListPage.STATUS_SAVED,
                        currentTimeMs = currentTimeMs
                    )
                }
            }

            ReadingListDeletionMutation(
                pages = claimedPages,
                orphanedOfflineObjects = orphanedOfflineObjects,
                orphanedArticleFiles = orphanedArticleFiles
            )
        }

        mutation.orphanedOfflineObjects.distinctBy(OfflineObject::path).forEach { orphaned ->
            offlineObjectDao.deleteFilesForObject(orphaned, context)
        }
        mutation.orphanedArticleFiles.distinctBy(File::getAbsolutePath).forEach(File::delete)
        mutation.pages.distinctBy(ReadingListPage::id).forEach { page ->
            invalidatePreparedArticle(
                ArticlePrewarmRequest(
                    pageId = page.mediaWikiPageId,
                    title = page.apiTitle
                )
            )
        }
        return mutation.pages
    }

    private fun pageIdentity(page: ReadingListPage): String = listOf(
        page.wiki.authority(),
        page.lang,
        page.namespace.name,
        page.apiTitle
    ).joinToString("\u0000")
}

private data class ReadingListDeletionMutation(
    val pages: List<ReadingListPage>,
    val orphanedOfflineObjects: List<OfflineObject>,
    val orphanedArticleFiles: List<File>
)
