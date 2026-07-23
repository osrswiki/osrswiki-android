package com.omiyawaki.osrswiki.dataclient.okhttp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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
