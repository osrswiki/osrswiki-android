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
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReadingListPageSnapshotTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var offlineRoot: File
    private lateinit var articleRoot: File

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
    fun lateAssetFailureDiscardsStageAndLeavesEveryPriorReferenceAndByteReadable() = runTest {
        val fixture = seedPriorSnapshot()
        val stage = ReadingListPageSnapshotStage(
            context = context,
            client = snapshotClient(failingPath = "/new-fail.png"),
            readingListPageId = fixture.pageId,
            generationId = "failed-generation"
        )

        stage.stageDocument(API_URL)
        val result = ReadingListOfflineAssetSaver(stage).persistAll(
            fixture.pageId,
            "<img src='$NEW_OK_URL'><img src='$NEW_FAIL_URL'>"
        )
        assertFalse(result.isComplete)
        assertTrue(stage.stagedResponses().any { it.url == NEW_OK_URL })
        stage.close()
        SavedPageSyncStatusRecorder.markSaveFailure(
            database.readingListPageDao(),
            fixture.pageId,
            currentTimeMs = 2_000L
        )

        assertPriorSnapshotIsByteIdentical(fixture)
        val page = requireNotNull(database.readingListPageDao().getPageById(fixture.pageId))
        assertEquals(ReadingListPage.STATUS_ERROR, page.status)
        assertEquals(ReadingListPage.DURABLE_SETTLEMENT_VERSION_NONE, page.durableSettlementVersion)
        assertTrue(page.hasReadableOfflineSnapshot)
        assertFalse(File(offlineRoot, ".generations/failed-generation").exists())
    }

    @Test
    fun cancellationBeforePublicationDeletesOnlyUnreferencedGeneration() = runTest {
        val fixture = seedPriorSnapshot()
        val stage = ReadingListPageSnapshotStage(
            context = context,
            client = snapshotClient(),
            readingListPageId = fixture.pageId,
            generationId = "cancelled-generation"
        )

        stage.stageDocument(API_URL)
        assertTrue(
            ReadingListOfflineAssetSaver(stage)
                .persistAll(fixture.pageId, "<img src='$NEW_OK_URL'>")
                .isComplete
        )
        assertTrue(File(offlineRoot, ".generations/cancelled-generation").exists())
        stage.close()

        assertPriorSnapshotIsByteIdentical(fixture)
        assertFalse(File(offlineRoot, ".generations/cancelled-generation").exists())
    }

    @Test
    fun exhaustiveSuccessAtomicallyPublishesMarkerPointersArticleAndFtsThenCleansOldFiles() = runTest {
        val fixture = seedPriorSnapshot()
        val stage = ReadingListPageSnapshotStage(
            context = context,
            client = snapshotClient(),
            readingListPageId = fixture.pageId,
            generationId = "published-generation"
        )
        stage.stageDocument(API_URL)
        val assetResult = ReadingListOfflineAssetSaver(stage).persistAll(
            fixture.pageId,
            "<img src='$NEW_OK_URL'>"
        )
        assertTrue(assetResult.isComplete)
        val articleFile = stage.stageArticleHtml(MEDIA_WIKI_PAGE_ID, NEW_ARTICLE_HTML)

        val publication = ReadingListPageSnapshotPublisher(context, database).publish(
            ReadingListSnapshotPublication(
                page = requireNotNull(database.readingListPageDao().getPageById(fixture.pageId)),
                mediaWikiPageId = MEDIA_WIKI_PAGE_ID,
                canonicalTitle = "Atomic snapshot",
                revisionId = 99L,
                articleFile = articleFile,
                ftsEntry = OfflinePageFts(
                    url = FTS_URL,
                    title = "Atomic snapshot",
                    body = "new searchable body"
                ),
                assets = stage.stagedResponses()
            ),
            currentTimeMs = 3_000L
        )
        stage.markPublished()
        stage.close()

        val settled = requireNotNull(database.readingListPageDao().getPageById(fixture.pageId))
        assertEquals(ReadingListPage.STATUS_SAVED, settled.status)
        assertEquals(100, settled.downloadProgress)
        assertEquals(99L, settled.revId)
        assertEquals(MEDIA_WIKI_PAGE_ID, settled.mediaWikiPageId)
        assertEquals(publication.totalSizeBytes, settled.sizeBytes)
        assertEquals(
            ReadingListPage.CURRENT_DURABLE_SETTLEMENT_VERSION,
            settled.durableSettlementVersion
        )

        val article = requireNotNull(database.articleMetaDao().getMetaByPageId(MEDIA_WIKI_PAGE_ID))
        assertEquals(NEW_ARTICLE_HTML, File(article.localFilePath).readText())
        assertNotEquals(fixture.oldArticleFile.absolutePath, article.localFilePath)
        assertFalse(fixture.oldArticleFile.exists())
        assertEquals("new searchable body", database.offlinePageFtsDao().getAll().single().body)

        val publishedAsset = requireNotNull(
            database.offlineObjectDao().findByUrlAndLangAndSaveType(
                NEW_OK_URL,
                "en",
                OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
        assertTrue(publishedAsset.path.startsWith(".generations/published-generation/"))
        assertTrue(File(offlineRoot, publishedAsset.path + ".1").isFile)
        assertNull(
            database.offlineObjectDao().findByUrlAndLangAndSaveType(
                OLD_ASSET_URL,
                "en",
                OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
        assertFalse(fixture.oldAssetContent.exists())
        assertFalse(fixture.oldAssetMetadata.exists())
    }

    private suspend fun seedPriorSnapshot(): PriorSnapshotFixture {
        val pageId = database.readingListPageDao().insertReadingListPage(
            ReadingListPage(
                wiki = WikiSite.OSRS_WIKI,
                namespace = Namespace.MAIN,
                displayTitle = "Atomic snapshot",
                apiTitle = "Atomic_snapshot",
                offline = true,
                status = ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE,
                sizeBytes = 4_096L,
                lang = "en",
                mediaWikiPageId = MEDIA_WIKI_PAGE_ID,
                durableSettlementVersion = ReadingListPage.DURABLE_SETTLEMENT_VERSION_NONE
            )
        )
        offlineRoot.mkdirs()
        val oldAssetMetadata = File(offlineRoot, "old-asset.0").apply {
            writeText("Content-Type: image/png")
        }
        val oldAssetContent = File(offlineRoot, "old-asset.1").apply {
            writeBytes(OLD_ASSET_BYTES)
        }
        database.offlineObjectDao().insertOfflineObject(
            OfflineObject(
                url = OLD_ASSET_URL,
                lang = "en",
                path = "old-asset",
                status = OfflineObject.STATUS_SAVED,
                usedByStr = "|$pageId|",
                saveType = OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
        articleRoot.mkdirs()
        val oldArticleFile = File(articleRoot, "old.html").apply { writeText(OLD_ARTICLE_HTML) }
        database.articleMetaDao().insert(
            ArticleMetaEntity(
                pageId = MEDIA_WIKI_PAGE_ID,
                title = "Atomic snapshot",
                wikiUrl = FTS_URL,
                localFilePath = oldArticleFile.absolutePath,
                lastFetchedTimestamp = 1_000L,
                revisionId = 1L,
                categories = null
            )
        )
        database.offlinePageFtsDao().insertPageContent(
            OfflinePageFts(FTS_URL, "Atomic snapshot", "old searchable body")
        )
        return PriorSnapshotFixture(pageId, oldAssetMetadata, oldAssetContent, oldArticleFile)
    }

    private suspend fun assertPriorSnapshotIsByteIdentical(fixture: PriorSnapshotFixture) {
        val oldObject = requireNotNull(
            database.offlineObjectDao().findByUrlAndLangAndSaveType(
                OLD_ASSET_URL,
                "en",
                OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
        assertEquals("old-asset", oldObject.path)
        assertEquals(OLD_ASSET_BYTES.toList(), fixture.oldAssetContent.readBytes().toList())
        assertEquals("Content-Type: image/png", fixture.oldAssetMetadata.readText())
        val article = requireNotNull(database.articleMetaDao().getMetaByPageId(MEDIA_WIKI_PAGE_ID))
        assertEquals(fixture.oldArticleFile.absolutePath, article.localFilePath)
        assertEquals(OLD_ARTICLE_HTML, fixture.oldArticleFile.readText())
        assertEquals("old searchable body", database.offlinePageFtsDao().getAll().single().body)
    }

    private fun snapshotClient(failingPath: String? = null): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.encodedPath == failingPath) throw IOException("injected late failure")
                val isApi = request.url.encodedPath == "/api.php"
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", if (isApi) "application/json" else "image/png")
                    .body(
                        if (isApi) {
                            "{\"parse\":{}}".toResponseBody("application/json".toMediaTypeOrNull())
                        } else {
                            NEW_ASSET_BYTES.toResponseBody("image/png".toMediaTypeOrNull())
                        }
                    )
                    .build()
            }
            .build()

    private data class PriorSnapshotFixture(
        val pageId: Long,
        val oldAssetMetadata: File,
        val oldAssetContent: File,
        val oldArticleFile: File
    )

    private companion object {
        const val MEDIA_WIKI_PAGE_ID = 12_345
        const val API_URL = "https://oldschool.runescape.wiki/api.php?page=Atomic_snapshot"
        const val OLD_ASSET_URL = "https://oldschool.runescape.wiki/old.png"
        const val NEW_OK_URL = "https://oldschool.runescape.wiki/new-ok.png"
        const val NEW_FAIL_URL = "https://oldschool.runescape.wiki/new-fail.png"
        const val FTS_URL = "https://oldschool.runescape.wiki/wiki/Atomic_snapshot"
        const val OLD_ARTICLE_HTML = "<html>old article</html>"
        const val NEW_ARTICLE_HTML = "<html>new article</html>"
        val OLD_ASSET_BYTES = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 1)
        val NEW_ASSET_BYTES = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 2)
    }
}
