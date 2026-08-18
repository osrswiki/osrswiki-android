package com.omiyawaki.osrswiki.savedpages

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import com.omiyawaki.osrswiki.page.Namespace
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmRequest
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReadingListSnapshotDeletionTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var offlineRoot: File
    private lateinit var articleRoot: File
    private val invalidations = mutableListOf<ArticlePrewarmRequest>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        offlineRoot = File(context.filesDir, "offline_pages_rl").apply { deleteRecursively() }
        articleRoot = File(context.filesDir, "osrs_wiki_articles").apply { deleteRecursively() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        offlineRoot.deleteRecursively()
        articleRoot.deleteRecursively()
    }

    @Test
    fun staleSingleRowClaimsPublishedIdentityBeforeDetachingAndDeletingFiles() = runTest {
        val pageId = insertForcedLegacyPage("Single", "Single")
        val staleUiRow = requireNotNull(database.readingListPageDao().getPageById(pageId)).copy()
        val published = publish(pageId, mediaWikiPageId = 101, revisionId = 11L, generation = "single")

        val deleted = deletion().deleteReadingListRows(listOf(staleUiRow.id)).single()

        assertEquals(101, deleted.mediaWikiPageId)
        assertEquals(11L, deleted.revId)
        assertEquals(ReadingListPage.CURRENT_DURABLE_SETTLEMENT_VERSION, deleted.durableSettlementVersion)
        assertTrue(deleted.sizeBytes > 0L)
        assertNull(database.readingListPageDao().getPageById(pageId))
        assertNull(database.articleMetaDao().getMetaByPageId(101))
        assertFalse(published.articleFile.exists())
        assertTrue(database.offlinePageFtsDao().getAll().isEmpty())
        assertEquals(listOf(ArticlePrewarmRequest(pageId = 101, title = "Single")), invalidations)
    }

    @Test
    fun staleBulkRowsClaimBothPublishedGenerationsBeforeCleanup() = runTest {
        val firstId = insertForcedLegacyPage("First", "First")
        val secondId = insertForcedLegacyPage("Second", "Second")
        val staleRows = listOf(firstId, secondId).map {
            requireNotNull(database.readingListPageDao().getPageById(it)).copy()
        }
        val first = publish(firstId, mediaWikiPageId = 201, revisionId = 21L, generation = "bulk-a")
        val second = publish(secondId, mediaWikiPageId = 202, revisionId = 22L, generation = "bulk-b")

        val deleted = deletion().deleteReadingListRows(staleRows.map(ReadingListPage::id))

        assertEquals(setOf(201, 202), deleted.mapNotNull { it.mediaWikiPageId }.toSet())
        assertEquals(setOf(21L, 22L), deleted.map { it.revId }.toSet())
        assertTrue(deleted.all {
            it.durableSettlementVersion == ReadingListPage.CURRENT_DURABLE_SETTLEMENT_VERSION
        })
        assertTrue(listOf(first.articleFile, second.articleFile).none(File::exists))
        assertTrue(database.readingListPageDao().getAllPages().isEmpty())
        assertTrue(database.offlinePageFtsDao().getAll().isEmpty())
    }

    @Test
    fun duplicateListOwnerPreservesSharedSnapshotUntilLastOwnerThenDeletesEveryFile() = runTest {
        val firstId = insertCurrentPage("Shared", "Shared", listId = 1L, mediaWikiPageId = 301)
        val secondId = insertCurrentPage("Shared", "Shared", listId = 2L, mediaWikiPageId = 301)
        offlineRoot.mkdirs()
        val metadata = File(offlineRoot, "shared.0").apply { writeText("Content-Type: image/png") }
        val content = File(offlineRoot, "shared.1").apply { writeBytes(SHARED_BYTES) }
        database.offlineObjectDao().insertOfflineObject(
            OfflineObject(
                url = SHARED_ASSET_URL,
                lang = "en",
                path = "shared",
                status = OfflineObject.STATUS_SAVED,
                usedByStr = "|$firstId|$secondId|",
                saveType = OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
        articleRoot.mkdirs()
        val articleFile = File(articleRoot, "301.html").apply { writeText("shared article") }
        database.articleMetaDao().insert(articleMeta(301, "Shared", articleFile))
        database.offlinePageFtsDao().insertPageContent(fts("Shared", "shared body"))

        deletion().deleteReadingListRows(listOf(firstId))

        val sharedObject = requireNotNull(
            database.offlineObjectDao().findByUrlAndLangAndSaveType(
                SHARED_ASSET_URL,
                "en",
                OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
        assertEquals("|$secondId|", sharedObject.usedByStr)
        assertTrue(metadata.exists())
        assertTrue(content.exists())
        assertNotNull(database.articleMetaDao().getMetaByPageId(301))
        assertTrue(articleFile.exists())
        assertEquals(1, database.offlinePageFtsDao().getAll().size)

        deletion().deleteReadingListRows(listOf(secondId))

        assertNull(
            database.offlineObjectDao().findByUrlAndLangAndSaveType(
                SHARED_ASSET_URL,
                "en",
                OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
        assertFalse(metadata.exists())
        assertFalse(content.exists())
        assertNull(database.articleMetaDao().getMetaByPageId(301))
        assertFalse(articleFile.exists())
        assertTrue(database.offlinePageFtsDao().getAll().isEmpty())
    }

    @Test
    fun databaseDetachFailureRollsBackClaimAndLeavesEveryReferencedByte() = runTest {
        val pageId = insertCurrentPage("Rollback", "Rollback", listId = 1L, mediaWikiPageId = 401)
        offlineRoot.mkdirs()
        val metadata = File(offlineRoot, "rollback.0").apply {
            writeText("Content-Type: image/png")
        }
        val content = File(offlineRoot, "rollback.1").apply { writeBytes(SHARED_BYTES) }
        database.offlineObjectDao().insertOfflineObject(
            OfflineObject(
                url = ROLLBACK_ASSET_URL,
                lang = "en",
                path = "rollback",
                status = OfflineObject.STATUS_SAVED,
                usedByStr = "|$pageId|",
                saveType = OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
        articleRoot.mkdirs()
        val articleFile = File(articleRoot, "401.html").apply { writeText("rollback article") }
        database.articleMetaDao().insert(articleMeta(401, "Rollback", articleFile))
        database.offlinePageFtsDao().insertPageContent(fts("Rollback", "rollback body"))
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_reading_list_object_delete
            BEFORE DELETE ON offline_objects
            BEGIN
                SELECT RAISE(ABORT, 'injected detach failure');
            END
            """.trimIndent()
        )

        val failure = runCatching {
            deletion().deleteReadingListRows(listOf(pageId))
        }.exceptionOrNull()

        assertNotNull(failure)
        val page = requireNotNull(database.readingListPageDao().getPageById(pageId))
        assertEquals(ReadingListPage.STATUS_SAVED, page.status)
        assertNotNull(
            database.offlineObjectDao().findByUrlAndLangAndSaveType(
                ROLLBACK_ASSET_URL,
                "en",
                OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
        assertEquals("Content-Type: image/png", metadata.readText())
        assertEquals(SHARED_BYTES.toList(), content.readBytes().toList())
        assertEquals("rollback article", articleFile.readText())
        assertNotNull(database.articleMetaDao().getMetaByPageId(401))
        assertEquals("rollback body", database.offlinePageFtsDao().getAll().single().body)
    }

    @Test
    fun idScopedOfflineTransitionsNeverOverwritePublishedSnapshotColumns() = runTest {
        val pageId = insertCurrentPage("Transition", "Transition", listId = 1L, mediaWikiPageId = 501)
        database.readingListPageDao().updatePageRevisionId(pageId, 55L)
        database.readingListPageDao().updatePageSizeBytes(pageId, 5_555L)
        val staleUiRow = requireNotNull(database.readingListPageDao().getPageById(pageId)).copy(
            revId = 1L,
            sizeBytes = 1L,
            mediaWikiPageId = null,
            durableSettlementVersion = ReadingListPage.DURABLE_SETTLEMENT_VERSION_NONE
        )

        assertEquals(
            1,
            database.readingListPageDao().transitionPageIdsForOffline(
                pageIds = listOf(staleUiRow.id),
                offline = true,
                forcedSave = true
            )
        )
        var current = requireNotNull(database.readingListPageDao().getPageById(pageId))
        assertEquals(ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE, current.status)
        assertPublishedColumns(current)

        assertEquals(
            1,
            database.readingListPageDao().transitionPageIdsForOffline(
                pageIds = listOf(staleUiRow.id),
                offline = false,
                forcedSave = false
            )
        )
        current = requireNotNull(database.readingListPageDao().getPageById(pageId))
        assertEquals(ReadingListPage.STATUS_QUEUE_FOR_DELETE, current.status)
        assertFalse(current.offline)
        assertPublishedColumns(current)
    }

    @Test
    fun ownershipLossForFirstPageIsNonCancellationAndSecondPageStillPublishes() = runTest {
        val firstId = insertForcedLegacyPage("Lost", "Lost")
        val secondId = insertForcedLegacyPage("Continues", "Continues")
        val firstPage = requireNotNull(database.readingListPageDao().getPageById(firstId))
        val secondPage = requireNotNull(database.readingListPageDao().getPageById(secondId))
        val firstStage = ReadingListPageSnapshotStage(
            context,
            okhttp3.OkHttpClient(),
            firstId,
            generationId = "lost-first"
        )
        val secondStage = ReadingListPageSnapshotStage(
            context,
            okhttp3.OkHttpClient(),
            secondId,
            generationId = "continued-second"
        )
        val firstArticle = firstStage.stageArticleHtml(601, "<html>lost</html>")
        val secondArticle = secondStage.stageArticleHtml(602, "<html>continued</html>")
        database.readingListPageDao().claimPagesForDeletion(listOf(firstId))

        val publisher = ReadingListPageSnapshotPublisher(context, database)
        val firstResult = ReadingListSnapshotPublicationHandoff.publishOrNull(
            firstStage,
            publisher,
            publication(firstPage, 601, firstArticle),
            currentTimeMs = 6_001L
        )
        val secondResult = ReadingListSnapshotPublicationHandoff.publishOrNull(
            secondStage,
            publisher,
            publication(secondPage, 602, secondArticle),
            currentTimeMs = 6_002L
        )
        firstStage.close()
        secondStage.close()

        assertNull(firstResult)
        assertNotNull(secondResult)
        assertFalse(
            kotlinx.coroutines.CancellationException::class.java.isAssignableFrom(
                ReadingListSnapshotOwnershipLostException::class.java
            )
        )
        assertFalse(firstArticle.exists())
        assertTrue(secondArticle.exists())
        assertEquals(
            ReadingListPage.STATUS_QUEUE_FOR_DELETE,
            database.readingListPageDao().getPageById(firstId)?.status
        )
        val second = requireNotNull(database.readingListPageDao().getPageById(secondId))
        assertEquals(ReadingListPage.STATUS_SAVED, second.status)
        assertEquals(ReadingListPage.CURRENT_DURABLE_SETTLEMENT_VERSION, second.durableSettlementVersion)
    }

    private fun assertPublishedColumns(page: ReadingListPage) {
        assertEquals(55L, page.revId)
        assertEquals(5_555L, page.sizeBytes)
        assertEquals(501, page.mediaWikiPageId)
        assertEquals(
            ReadingListPage.CURRENT_DURABLE_SETTLEMENT_VERSION,
            page.durableSettlementVersion
        )
    }

    private suspend fun publish(
        pageId: Long,
        mediaWikiPageId: Int,
        revisionId: Long,
        generation: String
    ): PublishedFixture {
        val page = requireNotNull(database.readingListPageDao().getPageById(pageId))
        val stage = ReadingListPageSnapshotStage(
            context = context,
            client = okhttp3.OkHttpClient(),
            readingListPageId = pageId,
            generationId = generation
        )
        val articleFile = stage.stageArticleHtml(mediaWikiPageId, "<html>$generation</html>")
        val result = ReadingListSnapshotPublicationHandoff.publishOrNull(
            stage = stage,
            publisher = ReadingListPageSnapshotPublisher(context, database),
            publication = ReadingListSnapshotPublication(
                page = page,
                mediaWikiPageId = mediaWikiPageId,
                canonicalTitle = page.displayTitle,
                revisionId = revisionId,
                articleFile = articleFile,
                ftsEntry = fts(page.apiTitle, "body $generation"),
                assets = emptyList()
            ),
            currentTimeMs = 2_000L + mediaWikiPageId
        )
        assertNotNull(result)
        stage.close()
        return PublishedFixture(articleFile)
    }

    private fun publication(
        page: ReadingListPage,
        mediaWikiPageId: Int,
        articleFile: File
    ) = ReadingListSnapshotPublication(
        page = page,
        mediaWikiPageId = mediaWikiPageId,
        canonicalTitle = page.displayTitle,
        revisionId = mediaWikiPageId.toLong(),
        articleFile = articleFile,
        ftsEntry = fts(page.apiTitle, "body ${page.apiTitle}"),
        assets = emptyList()
    )

    private fun deletion() = ReadingListSnapshotDeletion(
        context = context,
        database = database,
        invalidatePreparedArticle = invalidations::add
    )

    private fun insertForcedLegacyPage(title: String, apiTitle: String): Long =
        database.readingListPageDao().insertReadingListPage(
            page(title, apiTitle).copy(
                status = ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE,
                sizeBytes = 4_096L,
                durableSettlementVersion = ReadingListPage.DURABLE_SETTLEMENT_VERSION_NONE
            )
        )

    private fun insertCurrentPage(
        title: String,
        apiTitle: String,
        listId: Long,
        mediaWikiPageId: Int
    ): Long = database.readingListPageDao().insertReadingListPage(
        page(title, apiTitle).copy(
            listId = listId,
            mediaWikiPageId = mediaWikiPageId,
            sizeBytes = 4_096L,
            durableSettlementVersion = ReadingListPage.CURRENT_DURABLE_SETTLEMENT_VERSION
        )
    )

    private fun page(title: String, apiTitle: String) = ReadingListPage(
        wiki = WikiSite.OSRS_WIKI,
        namespace = Namespace.MAIN,
        displayTitle = title,
        apiTitle = apiTitle,
        offline = true,
        status = ReadingListPage.STATUS_SAVED,
        lang = "en"
    )

    private fun articleMeta(pageId: Int, title: String, file: File) = ArticleMetaEntity(
        pageId = pageId,
        title = title,
        wikiUrl = "https://oldschool.runescape.wiki/w/$title",
        localFilePath = file.absolutePath,
        lastFetchedTimestamp = 1_000L,
        revisionId = 1L,
        categories = null
    )

    private fun fts(apiTitle: String, body: String) = OfflinePageFts(
        url = "https://oldschool.runescape.wiki/wiki/$apiTitle",
        title = apiTitle,
        body = body
    )

    private data class PublishedFixture(val articleFile: File)

    private companion object {
        const val SHARED_ASSET_URL = "https://oldschool.runescape.wiki/shared.png"
        const val ROLLBACK_ASSET_URL = "https://oldschool.runescape.wiki/rollback.png"
        val SHARED_BYTES = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 9)
    }
}
