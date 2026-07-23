package com.omiyawaki.osrswiki.dataclient.okhttp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import com.omiyawaki.osrswiki.page.Namespace
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

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
    fun offlineSaveStreamsBodyAndUpdatesReadingListStatusBeforeReturning() {
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
        assertEquals(ReadingListPage.STATUS_SAVED, database.readingListPageDao().getPageById(pageId)?.status)
    }

    private fun offlineClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                OfflineCacheInterceptor(
                    context = context,
                    offlineObjectDao = database.offlineObjectDao(),
                    readingListPageDao = database.readingListPageDao(),
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
                    readingListPageDao = database.readingListPageDao(),
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
