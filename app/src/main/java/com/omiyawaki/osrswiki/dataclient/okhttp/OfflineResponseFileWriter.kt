package com.omiyawaki.osrswiki.dataclient.okhttp

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

internal object OfflineResponseFileWriter {
    const val DEFAULT_BUFFER_SIZE_BYTES = 8 * 1024

    fun copyToFile(
        inputStream: InputStream,
        destination: File,
        bufferSize: Int = DEFAULT_BUFFER_SIZE_BYTES
    ) {
        inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(bufferSize)
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    output.write(buffer, 0, bytesRead)
                }
                output.fd.sync()
            }
        }
    }

    data class StagedResponse(
        val path: String,
        val metadataFile: File,
        val contentFile: File
    ) {
        fun discard() {
            metadataFile.delete()
            contentFile.delete()
        }
    }

    /**
     * Writes both files under a new versioned path before the DB pointer is switched. The prior
     * path remains completely untouched if body read, fsync, rename, cancellation, or DB upsert
     * fails; the Room transaction becomes the single atomic visibility point for the pair.
     */
    fun stageResponse(
        storageDir: File,
        hashedBaseName: String,
        metadata: ByteArray,
        body: InputStream,
        versionToken: String = nextVersionToken()
    ): StagedResponse {
        storageDir.mkdirs()
        val path = "$hashedBaseName-$versionToken"
        val metadataFile = File(storageDir, "$path.0")
        val contentFile = File(storageDir, "$path.1")
        val metadataTemp = File(storageDir, "$path.0.tmp")
        val contentTemp = File(storageDir, "$path.1.tmp")
        try {
            FileOutputStream(metadataTemp).use { output ->
                output.write(metadata)
                output.fd.sync()
            }
            copyToFile(body, contentTemp)
            check(metadataTemp.renameTo(metadataFile)) { "Could not commit offline metadata" }
            check(contentTemp.renameTo(contentFile)) { "Could not commit offline content" }
            return StagedResponse(path, metadataFile, contentFile)
        } catch (failure: Throwable) {
            metadataTemp.delete()
            contentTemp.delete()
            metadataFile.delete()
            contentFile.delete()
            throw failure
        }
    }

    private val nextVersion = AtomicLong(0L)

    private fun nextVersionToken(): String =
        "${System.nanoTime().toString(16)}-${nextVersion.incrementAndGet().toString(16)}"
}
