package com.omiyawaki.osrswiki.dataclient.okhttp

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

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
            }
        }
    }
}
