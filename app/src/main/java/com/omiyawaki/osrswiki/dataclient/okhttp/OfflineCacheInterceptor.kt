package com.omiyawaki.osrswiki.dataclient.okhttp

import android.content.Context
import android.util.Log
import com.omiyawaki.osrswiki.OSRSWikiApp // Added import for your Application class
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage // For STATUS_SAVED constant
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import kotlinx.coroutines.launch // Added import for coroutine launch
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class OfflineCacheInterceptor(
    private val context: Context, // context is available if OSRSWikiApp.instance is not preferred directly
    private val offlineObjectDao: OfflineObjectDao,
    private val readingListPageDao: ReadingListPageDao,
    private val appDatabase: AppDatabase // appDatabase instance is passed in
) : Interceptor {

    companion object {
        private const val TAG = "OfflineCacheIntercept"
        private const val HEADER_OFFLINE_SAVE = "X-Offline-Save"
        private const val HEADER_PAGE_LIB_IDS = "X-Offline-Save-PageLibIds"

        private const val SAVE_TYPE_VALUE_READING_LIST = "readinglist"
        private const val SAVE_TYPE_VALUE_FULL_ARCHIVE = "fullarchive"

        private const val SUBDIR_INTERNAL_RL = "offline_pages_rl"
        private const val SUBDIR_EXTERNAL_FA = "wiki_archive"
        private const val SUBDIR_EXTERNAL_FA_CONTENT = "content"

        private const val METADATA_SUFFIX = ".0"
        private const val CONTENT_SUFFIX = ".1"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val offlineSaveHeaderValue = originalRequest.header(HEADER_OFFLINE_SAVE)
        val shouldSaveOffline = offlineSaveHeaderValue == SAVE_TYPE_VALUE_READING_LIST ||
                offlineSaveHeaderValue == SAVE_TYPE_VALUE_FULL_ARCHIVE

        try {
            val response = chain.proceed(originalRequest)
            if (shouldSaveOffline && response.isSuccessful && response.body != null) {
                try {
                    saveResponse(originalRequest, response, offlineSaveHeaderValue!!)
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving response for URL: ${originalRequest.url}", e)
                }
            }
            return response
        } catch (e: IOException) {
            Log.w(TAG, "Network request failed for ${originalRequest.url}, attempting to serve from cache.", e)
            val cachedResponse = serveFromCache(originalRequest)
            if (cachedResponse != null) {
                Log.i(TAG, "Serving ${originalRequest.url} from cache.")
                return cachedResponse
            }
            Log.e(TAG, "Failed to serve ${originalRequest.url} from cache. Rethrowing.")
            throw e
        }
    }

    private fun saveResponse(request: Request, response: Response, saveHeaderValue: String) {
        val url = request.url.toString()
        // TODO: Determine lang more dynamically if needed, e.g., from request headers or WikiSite object
        val lang = request.header("Accept-Language")?.split(",")?.firstOrNull()?.split(";")?.firstOrNull()?.trim() ?: "en"

        val saveType = if (saveHeaderValue == SAVE_TYPE_VALUE_READING_LIST) {
            OfflineObject.SAVE_TYPE_READING_LIST
        } else {
            OfflineObject.SAVE_TYPE_FULL_ARCHIVE
        }

        val hashedFilename = hashUrl(url, lang)
        val storageDir = getStorageDirectory(saveType)
        if (storageDir == null) {
            Log.e(TAG, "Could not get storage directory for saveType: $saveType. Aborting save.")
            return
        }
        storageDir.mkdirs()

        val metadataFile = File(storageDir, hashedFilename + METADATA_SUFFIX)
        val contentFile = File(storageDir, hashedFilename + CONTENT_SUFFIX)

        val headersString = response.headers.toMultimap().map { entry ->
            "${entry.key}: ${entry.value.joinToString(", ")}"
        }.joinToString("\n")
        FileOutputStream(metadataFile).use { it.write(headersString.toByteArray(StandardCharsets.UTF_8)) }
        Log.d(TAG, "Saved metadata for $url to ${metadataFile.absolutePath}")

        response.peekBody(Long.MAX_VALUE).byteStream().use { inputStream ->
            FileOutputStream(contentFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        Log.d(TAG, "Saved content for $url to ${contentFile.absolutePath}")

        val pageLibIdsStr = request.header(HEADER_PAGE_LIB_IDS) ?: ""

        val offlineObject = OfflineObject(
            url = url,
            lang = lang,
            path = hashedFilename, // This is just the hash, full path constructed using storageDir
            status = OfflineObject.STATUS_SAVED,
            usedByStr = if (saveType == OfflineObject.SAVE_TYPE_READING_LIST) pageLibIdsStr else "",
            saveType = saveType
        )

        // Database operations are performed within a transaction.
        // Calls to suspend functions from here need to be in a coroutine.
        appDatabase.runInTransaction {
            val insertedOfflineObjectId = offlineObjectDao.insertOfflineObject(offlineObject)
            Log.i(TAG, "Offline object metadata saved to DB for $url with ID: $insertedOfflineObjectId")

            if (saveType == OfflineObject.SAVE_TYPE_READING_LIST && pageLibIdsStr.isNotBlank()) {
                val pageIds = pageLibIdsStr.trim('|').split('|').mapNotNull { it.toLongOrNull() }
                if (pageIds.isNotEmpty()) {
                    val currentTime = System.currentTimeMillis()
                    pageIds.forEach { pageId ->
                        // Launch a coroutine to call the suspend DAO function
                        OSRSWikiApp.instance.applicationScope.launch {
                            try {
                                readingListPageDao.updatePageStatusToSavedAndMtime(
                                    pageId,
                                    ReadingListPage.STATUS_SAVED,
                                    currentTime
                                )
                                Log.d(TAG, "Updated status for ReadingListPage ID $pageId to SAVED.")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating status for ReadingListPage ID $pageId via app scope", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun serveFromCache(request: Request): Response? {
        val url = request.url.toString()
        // TODO: Determine lang more dynamically
        val lang = request.header("Accept-Language")?.split(",")?.firstOrNull()?.split(";")?.firstOrNull()?.trim() ?: "en"


        val typesToTry = listOf(OfflineObject.SAVE_TYPE_FULL_ARCHIVE, OfflineObject.SAVE_TYPE_READING_LIST)
        var offlineObjectFromDb: OfflineObject? = null
        var contentFile: File? = null
        var metadataFile: File? = null

        for (currentSaveType in typesToTry) {
            val foundObject = offlineObjectDao.findByUrlAndLangAndSaveType(url, lang, currentSaveType)
            if (foundObject != null) {
                val storageDir = getStorageDirectory(currentSaveType)
                if (storageDir == null) {
                    Log.w(TAG, "Storage directory for type $currentSaveType is null, skipping cache check for this type.")
                    continue
                }

                val currentContentFile = File(storageDir, foundObject.path + CONTENT_SUFFIX)
                val currentMetadataFile = File(storageDir, foundObject.path + METADATA_SUFFIX)

                if (currentContentFile.exists() && currentMetadataFile.exists()) {
                    offlineObjectFromDb = foundObject
                    contentFile = currentContentFile
                    metadataFile = currentMetadataFile
                    Log.d(TAG, "Found valid cache entry for $url with type: $currentSaveType")
                    break
                } else {
                    Log.w(TAG, "Cache files missing for $url (type: $currentSaveType) despite DB entry. Path: ${currentContentFile.path}")
                }
            }
        }

        if (offlineObjectFromDb == null || contentFile == null || metadataFile == null || !contentFile.exists() || !metadataFile.exists()) {
            Log.d(TAG, "No valid cache entry found for $url after checking all types.")
            return null
        }

        try {
            val headersMap = metadataFile.readLines(StandardCharsets.UTF_8)
                .mapNotNull { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
                }
                .groupBy({ it.first }, { it.second })

            val okHttpHeadersBuilder = okhttp3.Headers.Builder()
            headersMap.forEach { (name, values) ->
                values.forEach { value -> okHttpHeadersBuilder.add(name, value) }
            }

            val contentTypeString = headersMap["Content-Type"]?.firstOrNull()
                ?: headersMap["content-type"]?.firstOrNull()
                ?: "application/octet-stream" // Default if not found
            val mediaType = contentTypeString.toMediaTypeOrNull()

            // Use okio's buffer and source correctly with use to ensure closure
            val responseBody = contentFile.source().buffer().use { bufferedSource ->
                bufferedSource.readByteString().toResponseBody(mediaType)
            }

            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1) // Or determine from saved metadata if available
                .code(HttpURLConnection.HTTP_OK) // Or determine from saved metadata
                .message("OK (served from cache)")
                .headers(okHttpHeadersBuilder.build())
                .body(responseBody)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error serving $url from cache files.", e)
            return null
        }
    }

    private fun getStorageDirectory(saveType: String): File? {
        return when (saveType) {
            OfflineObject.SAVE_TYPE_READING_LIST -> {
                File(context.filesDir, SUBDIR_INTERNAL_RL)
            }
            OfflineObject.SAVE_TYPE_FULL_ARCHIVE -> {
                // Ensure this directory is robustly handled, permission checks might be needed if truly external.
                // context.getExternalFilesDir() is app-specific external storage, no special permissions needed.
                val externalDir = context.getExternalFilesDir(null) // Using null for root of app-specific external files
                externalDir?.let { File(File(it, SUBDIR_EXTERNAL_FA), SUBDIR_EXTERNAL_FA_CONTENT) }
            }
            else -> {
                Log.e(TAG, "Unknown saveType: $saveType")
                null
            }
        }
    }

    private fun hashUrl(url: String, lang: String): String {
        val key = "$url-$lang" // Ensure lang is part of the key for uniqueness
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(key.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}