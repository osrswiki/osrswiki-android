package com.omiyawaki.osrswiki.dataclient.okhttp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import com.omiyawaki.osrswiki.page.Namespace
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.savedpages.ReadingListAssetRequestMarker
import com.omiyawaki.osrswiki.savedpages.ReadingListAssetValidationException
import com.omiyawaki.osrswiki.savedpages.OkHttpReadingListAssetFetcher
import com.omiyawaki.osrswiki.savedpages.ReadingListSnapshotNetworkRequestMarker
import com.omiyawaki.osrswiki.savedpages.SavedPageSaveCompletionPolicy
import com.omiyawaki.osrswiki.network.WikiHttpCachePolicy
import com.omiyawaki.osrswiki.network.WikiHttpCachePolicyInterceptor
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
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
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OfflineCacheInterceptorTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "offline_pages_rl").deleteRecursively()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "offline_pages_rl").deleteRecursively()
    }

    @Test
    fun interceptServesCachedReadingListResponseWhenNetworkThrowsIOException() {
        val url = "https://oldschool.runescape.wiki/api.php?action=parse&page=Varrock"
        seedReadingListCache(
            url = url,
            path = "cached-varrock",
            metadata = "Content-Type: text/html; charset=utf-8\nX-Offline-Test: hit",
            content = "<html>cached varrock</html>"
        )
        val client = offlineClient()

        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("OK (served from cache)", response.message)
            assertEquals("hit", response.header("X-Offline-Test"))
            assertEquals("text/html; charset=utf-8", response.body?.contentType().toString())
            assertEquals("<html>cached varrock</html>", response.body?.string())
        }
    }

    @Test
    fun interceptServesCachedReadingListWhenHttpDiskCacheMissesAndNetworkThrowsIOException() {
        val url = "https://oldschool.runescape.wiki/api.php?action=parse&page=Varrock"
        seedReadingListCache(
            url = url,
            path = "cached-varrock-http-disk",
            metadata = "Content-Type: text/html; charset=utf-8\nX-Offline-Test: room-hit",
            content = "<html>room varrock</html>"
        )
        val httpCacheDir = File(context.cacheDir, "okhttp_wiki_http_interceptor_test")
        httpCacheDir.deleteRecursively()
        val client = OkHttpClient.Builder()
            .cache(Cache(httpCacheDir, WikiHttpCachePolicy.CACHE_MAX_BYTES))
            .addInterceptor(WikiHttpCachePolicyInterceptor())
            .addInterceptor(
                OfflineCacheInterceptor(
                    context = context,
                    offlineObjectDao = database.offlineObjectDao(),
                    appDatabase = database
                )
            )
            .addInterceptor {
                throw IOException("simulated network outage")
            }
            .build()

        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("OK (served from cache)", response.message)
            assertEquals("room-hit", response.header("X-Offline-Test"))
            assertEquals("<html>room varrock</html>", response.body?.string())
        }
        httpCacheDir.deleteRecursively()
    }

    @Test
    fun interceptRethrowsIOExceptionWhenNetworkAndCacheMiss() {
        val url = "https://oldschool.runescape.wiki/api.php?action=parse&page=Unsaved"
        val client = offlineClient()

        val result = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute()
        }

        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("simulated network outage", result.exceptionOrNull()?.message)
    }

    @Test
    fun snapshotRefreshNeverFallsBackToGenerationBeingReplaced() {
        val url = "https://oldschool.runescape.wiki/images/prior.png"
        seedReadingListCache(
            url = url,
            path = "prior-generation",
            metadata = "Content-Type: image/png",
            content = "prior bytes"
        )

        val failure = runCatching {
            offlineClient().newCall(
                Request.Builder()
                    .url(url)
                    .tag(
                        ReadingListSnapshotNetworkRequestMarker::class.java,
                        ReadingListSnapshotNetworkRequestMarker
                    )
                    .build()
            ).execute()
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals("simulated network outage", failure?.message)
        assertEquals(
            "prior-generation",
            database.offlineObjectDao().findByUrlAndLangAndSaveType(
                url,
                "en",
                OfflineObject.SAVE_TYPE_READING_LIST
            )?.path
        )
    }

    @Test
    fun offlineSaveStreamsBodyWithoutPrematurelyMarkingReadingListSaved() {
        val url = "https://oldschool.runescape.wiki/api.php?action=parse&page=Zulrah"
        val pageId = database.readingListPageDao().insertReadingListPage(
            ReadingListPage(
                wiki = WikiSite.OSRS_WIKI,
                namespace = Namespace.MAIN,
                displayTitle = "Zulrah",
                apiTitle = "Zulrah",
                offline = true,
                status = ReadingListPage.STATUS_QUEUE_FOR_SAVE,
                lang = "en"
            )
        )
        val client = offlineClientWithNetworkBody(
            url = url,
            responseBody = "{\"parse\":{\"title\":\"Zulrah\"}}"
                .toResponseBody("application/json".toMediaTypeOrNull())
        )

        client.newCall(
            Request.Builder()
                .url(url)
                .header("X-Offline-Save", "readinglist")
                .header("X-Offline-Save-PageLibIds", "|$pageId|")
                .build()
        ).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("{\"parse\":{\"title\":\"Zulrah\"}}", response.body?.string())
        }

        assertNotNull(database.offlineObjectDao().getOfflineObjectByUrl(url))
        assertEquals(
            ReadingListPage.STATUS_QUEUE_FOR_SAVE,
            database.readingListPageDao().getPageById(pageId)?.status
        )
    }

    @Test
    fun gifWithQueryAndSuccessfulHtmlResponseIsNotPersistedOrReportedComplete() {
        val url = "https://oldschool.runescape.wiki/images/animated.GIF?revision=12"
        val pageId = database.readingListPageDao().insertReadingListPage(
            ReadingListPage(
                wiki = WikiSite.OSRS_WIKI,
                namespace = Namespace.MAIN,
                displayTitle = "Captive asset",
                apiTitle = "Captive asset",
                offline = true,
                status = ReadingListPage.STATUS_QUEUE_FOR_SAVE,
                lang = "en"
            )
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(
                OfflineCacheInterceptor(context, database.offlineObjectDao(), database)
            )
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "text/html; charset=utf-8")
                    .body("<html><body>captive portal</body></html>".toResponseBody())
                    .build()
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("X-Offline-Save", "readinglist")
            .header("X-Offline-Save-PageLibIds", "|$pageId|")
            .tag(ReadingListAssetRequestMarker::class.java, ReadingListAssetRequestMarker)
            .build()

        val failure = runCatching { client.newCall(request).execute() }.exceptionOrNull()

        assertTrue(failure is ReadingListAssetValidationException)
        assertNull(
            database.offlineObjectDao().findByUrlAndLangAndSaveType(
                url,
                "en",
                OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
        val storageDir = File(context.filesDir, "offline_pages_rl")
        assertTrue(storageDir.listFiles().orEmpty().isEmpty())
        assertEquals(
            ReadingListPage.STATUS_QUEUE_FOR_SAVE,
            database.readingListPageDao().getPageById(pageId)?.status
        )
        assertTrue(
            !SavedPageSaveCompletionPolicy.isComplete(
                htmlFetched = true,
                textIndexed = true,
                articlePersisted = true,
                assetsPersisted = false
            )
        )
    }

    @Test
    fun fetcherEvictsLegacyInvalidArtworkInsteadOfAttachingAnotherOwner() = runTest {
        val url = "https://oldschool.runescape.wiki/images/legacy.gif?revision=7"
        seedReadingListCache(
            url = url,
            path = "legacy-invalid-gif",
            metadata = "Content-Type: text/html; charset=utf-8",
            content = "<html><body>legacy captive portal</body></html>"
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(
                OfflineCacheInterceptor(context, database.offlineObjectDao(), database)
            )
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "text/html")
                    .body("<html><body>still captive</body></html>".toResponseBody())
                    .build()
            }
            .build()

        val persisted = OkHttpReadingListAssetFetcher(
            context = context,
            client = client,
            offlineObjectDao = database.offlineObjectDao()
        ).fetchAndPersist(url, readingListPageId = 42L)

        assertTrue(!persisted)
        assertNull(
            database.offlineObjectDao().findByUrlAndLangAndSaveType(
                url,
                "en",
                OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
        assertFalse(File(context.filesDir, "offline_pages_rl/legacy-invalid-gif.0").exists())
        assertFalse(File(context.filesDir, "offline_pages_rl/legacy-invalid-gif.1").exists())
    }

    @Test
    fun sameUrlCoexistsAcrossReadingListAndFullArchiveAndDeletingOneRealmPreservesOther() {
        val url = "https://oldschool.runescape.wiki/images/shared.gif"
        val pageId = database.readingListPageDao().insertReadingListPage(
            ReadingListPage(
                wiki = WikiSite.OSRS_WIKI,
                namespace = Namespace.MAIN,
                displayTitle = "Shared asset",
                apiTitle = "Shared asset",
                offline = true,
                status = ReadingListPage.STATUS_QUEUE_FOR_SAVE,
                lang = "en"
            )
        )
        fun save(saveType: String, pageOwners: String?) {
            val request = Request.Builder()
                .url(url)
                .header("X-Offline-Save", saveType)
                .apply {
                    pageOwners?.let { header("X-Offline-Save-PageLibIds", it) }
                }
                .build()
            offlineClientWithNetworkBody(
                url,
                byteArrayOf(1, 2, 3).toResponseBody("image/gif".toMediaTypeOrNull())
            ).newCall(request).execute().close()
        }

        save("readinglist", "|$pageId|")
        save("fullarchive", null)

        assertNotNull(
            database.offlineObjectDao().findByUrlAndLangAndSaveType(
                url,
                "en",
                OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
        assertNotNull(
            database.offlineObjectDao().findByUrlAndLangAndSaveType(
                url,
                "en",
                OfflineObject.SAVE_TYPE_FULL_ARCHIVE
            )
        )

        database.offlineObjectDao().deleteObjectsForPageIds(listOf(pageId), context)

        assertEquals(
            null,
            database.offlineObjectDao().findByUrlAndLangAndSaveType(
                url,
                "en",
                OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
        assertNotNull(
            database.offlineObjectDao().findByUrlAndLangAndSaveType(
                url,
                "en",
                OfflineObject.SAVE_TYPE_FULL_ARCHIVE
            )
        )
    }

    @Test
    fun deletingLastSavedRealmFallsBackToSurvivingRealmDespiteMemoryHint() {
        val url = "https://oldschool.runescape.wiki/images/shared-hint.gif"
        val offline = AtomicBoolean(false)
        val interceptor = OfflineCacheInterceptor(
            context = context,
            offlineObjectDao = database.offlineObjectDao(),
            appDatabase = database
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                if (offline.get()) throw IOException("offline")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "image/gif")
                    .body(byteArrayOf(7, 8, 9).toResponseBody("image/gif".toMediaTypeOrNull()))
                    .build()
            }
            .build()
        client.newCall(
            Request.Builder().url(url)
                .header("X-Offline-Save", "readinglist")
                .header("X-Offline-Save-PageLibIds", "|12|")
                .build()
        ).execute().close()
        client.newCall(
            Request.Builder().url(url).header("X-Offline-Save", "fullarchive").build()
        ).execute().close()
        val fullArchive = database.offlineObjectDao().findByUrlAndLangAndSaveType(
            url,
            "en",
            OfflineObject.SAVE_TYPE_FULL_ARCHIVE
        )!!
        database.offlineObjectDao().deleteFilesForObject(fullArchive, context)
        database.offlineObjectDao().deleteOfflineObjectQuery(fullArchive.id)
        offline.set(true)

        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            assertEquals(listOf<Byte>(7, 8, 9), response.body!!.bytes().toList())
        }
    }

    @Test
    fun resourceUrlIdentityPreservesMeaningfulPathCase() {
        val upper = "https://cdn.example/images/A.png"
        val lower = "https://cdn.example/images/a.png"
        listOf(upper, lower).forEachIndexed { index, url ->
            database.offlineObjectDao().insertOfflineObject(
                OfflineObject(
                    url = url,
                    lang = "en",
                    path = "case-$index",
                    status = OfflineObject.STATUS_SAVED,
                    usedByStr = "|1|",
                    saveType = OfflineObject.SAVE_TYPE_READING_LIST
                )
            )
        }

        assertEquals("case-0", database.offlineObjectDao().findByUrlAndLangAndSaveType(
            upper, "en", OfflineObject.SAVE_TYPE_READING_LIST
        )?.path)
        assertEquals("case-1", database.offlineObjectDao().findByUrlAndLangAndSaveType(
            lower, "en", OfflineObject.SAVE_TYPE_READING_LIST
        )?.path)
    }

    private fun offlineClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                OfflineCacheInterceptor(
                    context = context,
                    offlineObjectDao = database.offlineObjectDao(),
                    appDatabase = database
                )
            )
            .addInterceptor {
                throw IOException("simulated network outage")
            }
            .build()
    }

    private fun offlineClientWithNetworkBody(url: String, responseBody: ResponseBody): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                OfflineCacheInterceptor(
                    context = context,
                    offlineObjectDao = database.offlineObjectDao(),
                    appDatabase = database
                )
            )
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "application/json")
                    .body(responseBody)
                    .build()
            }
            .build()
    }

    private fun seedReadingListCache(
        url: String,
        path: String,
        metadata: String,
        content: String
    ) {
        val storageDir = File(context.filesDir, "offline_pages_rl")
        storageDir.mkdirs()
        File(storageDir, "$path.0").writeText(metadata)
        File(storageDir, "$path.1").writeText(content)

        database.offlineObjectDao().insertOfflineObject(
            OfflineObject(
                url = url,
                lang = "en",
                path = path,
                status = OfflineObject.STATUS_SAVED,
                usedByStr = "|1|",
                saveType = OfflineObject.SAVE_TYPE_READING_LIST
            )
        )
    }

}
