package com.omiyawaki.osrswiki.dataclient.okhttp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.InputStream

class OfflineResponseFileWriterTest {

    @Test
    fun copyToFileUsesBoundedReadBuffer() {
        val input = TrackingInputStream(ByteArray(32 * 1024) { (it % 127).toByte() })
        val output = File.createTempFile("offline-response", ".bin")

        try {
            OfflineResponseFileWriter.copyToFile(input, output, bufferSize = 4096)

            assertEquals(32 * 1024L, output.length())
            assertTrue(input.maxRequestedRead <= 4096)
        } finally {
            output.delete()
        }
    }

    @Test
    fun failedOrCanceledReplacementLeavesPreviouslySavedPairByteValid() {
        val directory = kotlin.io.path.createTempDirectory("offline-atomic").toFile()
        val oldMetadata = File(directory, "old.0").apply { writeText("Content-Type: image/png") }
        val oldContent = File(directory, "old.1").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val failingBody = object : InputStream() {
            private var reads = 0
            override fun read(): Int = throw IOException("injected cancellation")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (reads++ == 0) {
                    buffer[offset] = 1
                    return 1
                }
                throw IOException("injected cancellation")
            }
        }

        try {
            val failure = runCatching {
                OfflineResponseFileWriter.stageResponse(
                    storageDir = directory,
                    hashedBaseName = "replacement",
                    metadata = "Content-Type: image/gif".toByteArray(),
                    body = failingBody,
                    versionToken = "injected"
                )
            }.exceptionOrNull()

            assertTrue(failure is IOException)
            assertEquals("Content-Type: image/png", oldMetadata.readText())
            assertEquals(listOf<Byte>(9, 8, 7), oldContent.readBytes().toList())
            assertEquals(
                emptyList<String>(),
                directory.listFiles().orEmpty()
                    .filter { it.name.startsWith("replacement-") }
                    .map(File::getName)
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    private class TrackingInputStream(
        private val bytes: ByteArray
    ) : InputStream() {
        var maxRequestedRead = 0
            private set
        private var offset = 0

        override fun read(): Int {
            if (offset >= bytes.size) return -1
            return bytes[offset++].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, byteOffset: Int, byteCount: Int): Int {
            maxRequestedRead = maxOf(maxRequestedRead, byteCount)
            if (offset >= bytes.size) return -1
            val count = minOf(byteCount, bytes.size - offset)
            bytes.copyInto(buffer, byteOffset, offset, offset + count)
            offset += count
            return count
        }
    }
}
