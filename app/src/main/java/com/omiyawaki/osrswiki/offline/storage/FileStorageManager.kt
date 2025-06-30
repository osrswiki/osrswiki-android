package com.omiyawaki.osrswiki.offline.storage

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class FileStorageManager(private val context: Context) {

    companion object {
        private const val TAG = "FileStorageManager"
        private const val HTML_DIR = "offline_html"
        private const val IMAGES_DIR = "offline_images"
        private const val GENERIC_ASSETS_DIR = "offline_assets" // For other types or unknown
    }

    /**
     * Generates a filename for an asset based on its original URL and content type.
     * Uses an MD5 hash of the URL to ensure uniqueness and a fixed length.
     * Appends an extension based on the content type.
     */
    private fun getHashedFilename(originalUrl: String, contentType: String?): String {
        val extension = when {
            contentType?.contains("text/html", ignoreCase = true) == true -> ".html"
            contentType?.startsWith("image/jpeg", ignoreCase = true) == true -> ".jpg"
            contentType?.startsWith("image/png", ignoreCase = true) == true -> ".png"
            contentType?.startsWith("image/gif", ignoreCase = true) == true -> ".gif"
            contentType?.startsWith("image/webp", ignoreCase = true) == true -> ".webp"
            // Add more common types if needed
            else -> "" // No extension or rely on a generic one if necessary
        }
        return try {
            val bytes = MessageDigest.getInstance("MD5").digest(originalUrl.toByteArray())
            bytes.joinToString("") { "%02x".format(it) } + extension
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate MD5 hash for URL: $originalUrl", e)
            // Fallback to a simple hash of the URL's hashcode if MD5 fails, though unlikely
            originalUrl.hashCode().toString() + extension
        }
    }

    /**
     * Saves an asset from an InputStream to local storage.
     *
     * @param inputStream The InputStream of the asset content.
     * @param originalUrl The original URL of the asset, used for generating a unique filename.
     * @param assetType A string indicating the type of asset (e.g., "html", "image")
     * to determine the storage subdirectory.
     * @param contentType The MIME type of the asset, used for determining the file extension.
     * @return The [File] object representing the saved asset.
     * @throws IOException if any file I/O error occurs.
     */
    @Throws(IOException::class)
    fun saveAsset(
        inputStream: InputStream,
        originalUrl: String,
        assetType: String,
        contentType: String?
    ): File {
        val baseDirName = when (assetType.lowercase()) {
            "html" -> HTML_DIR
            "image" -> IMAGES_DIR
            else -> GENERIC_ASSETS_DIR
        }
        val directory = File(context.filesDir, baseDirName)
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw IOException("Failed to create directory: ${directory.absolutePath}")
            }
        }

        val filename = getHashedFilename(originalUrl, contentType)
        val file = File(directory, filename)

        Log.d(TAG, "Saving asset from $originalUrl to ${file.absolutePath}")

        FileOutputStream(file).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        Log.i(TAG, "Successfully saved asset to ${file.absolutePath}")
        return file
    }

    /**
     * Retrieves a file from local storage given its full path.
     *
     * @param localPath The absolute path to the file.
     * @return The [File] object if it exists, null otherwise.
     */
    fun getFile(localPath: String): File? {
        if (localPath.isBlank()) {
            return null
        }
        val file = File(localPath)
        return if (file.exists() && file.isFile) file else null
    }

    /**
     * Deletes a file from local storage.
     *
     * @param localPath The absolute path to the file.
     * @return True if the file was successfully deleted, false otherwise.
     */
    fun deleteFile(localPath: String): Boolean {
        if (localPath.isBlank()) {
            return false
        }
        return try {
            val file = File(localPath)
            if (file.exists()) {
                file.delete()
            } else {
                Log.w(TAG, "File not found for deletion: $localPath")
                false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to delete file due to security exception: $localPath", e)
            false
        }
    }
}
