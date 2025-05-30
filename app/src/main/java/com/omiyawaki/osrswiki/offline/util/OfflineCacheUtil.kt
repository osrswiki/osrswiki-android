package com.omiyawaki.osrswiki.offline.util

import android.content.Context
// import com.omiyawaki.osrswiki.dataclient.okhttp.OfflineCacheInterceptor // Not strictly needed for constants if defined here
import com.omiyawaki.osrswiki.network.model.ArticleParseApiResponse
import com.omiyawaki.osrswiki.network.model.ParseResult // Added direct import
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object OfflineCacheUtil {

    private const val METADATA_SUFFIX = ".0"
    private const val CONTENT_SUFFIX = ".1"

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    fun getStorageDirectory(context: Context, saveType: String): File? {
        return when (saveType) {
            OfflineObject.SAVE_TYPE_READING_LIST -> {
                File(context.filesDir, "offline_pages_rl")
            }
            OfflineObject.SAVE_TYPE_FULL_ARCHIVE -> {
                val externalDir = context.getExternalFilesDir(null)
                externalDir?.let { File(File(it, "wiki_archive"), "content") }
            }
            else -> {
                L.e("Unknown saveType: $saveType")
                null
            }
        }
    }

    fun hashUrl(url: String, lang: String): String {
        val key = "$url-$lang"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(key.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun readAndParseOfflinePageContent(
        context: Context,
        offlineObjectDao: OfflineObjectDao,
        apiUrl: String,
        lang: String
    ): ParseResult? = withContext(Dispatchers.IO) { // Changed return type here
        val offlineObject = offlineObjectDao.findByUrlAndLangAndSaveType(
            url = apiUrl,
            lang = lang,
            saveType = OfflineObject.SAVE_TYPE_READING_LIST
        )

        if (offlineObject == null) {
            L.d("No OfflineObject found for URL: $apiUrl, lang: $lang")
            return@withContext null
        }

        val storageDir = getStorageDirectory(context, offlineObject.saveType)
        if (storageDir == null) {
            L.e("Could not get storage directory for OfflineObject (URL: $apiUrl, saveType: ${offlineObject.saveType})")
            return@withContext null
        }

        val contentFile = File(storageDir, offlineObject.path + CONTENT_SUFFIX)
        if (!contentFile.exists()) {
            L.w("Offline content file does not exist: ${contentFile.absolutePath}")
            return@withContext null
        }

        return@withContext try {
            val jsonString = contentFile.readText(StandardCharsets.UTF_8)
            val apiResponse = jsonParser.decodeFromString<ArticleParseApiResponse>(jsonString)
            L.d("Successfully read and parsed offline content for URL: $apiUrl")
            apiResponse.parse
        } catch (e: Exception) {
            L.e("Error reading or parsing offline content file ${contentFile.absolutePath} for URL $apiUrl", e)
            null
        }
    }
}