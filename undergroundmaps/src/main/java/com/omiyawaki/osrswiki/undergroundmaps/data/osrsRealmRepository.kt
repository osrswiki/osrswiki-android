package com.omiyawaki.osrswiki.undergroundmaps.data

import android.content.Context
import android.os.SystemClock
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmAsset
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCatalog
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmManifestParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

data class osrsStagedRealmAsset(
    val file: File,
    val sha256: String,
    val elapsedNanos: Long,
    val reusedVerifiedCopy: Boolean
)

class osrsRealmRepository(
    context: Context,
    private val parser: osrsRealmManifestParser = osrsRealmManifestParser()
) {
    private val applicationContext = context.applicationContext
    private val assetManager = applicationContext.assets
    private val stagedDirectory = File(applicationContext.filesDir, "osrs-underground-realm-assets-v1")

    suspend fun loadCatalog(): osrsRealmCatalog = withContext(Dispatchers.IO) {
        val manifestText = assetManager.open(OSRS_REALM_MANIFEST_ASSET).bufferedReader().use { it.readText() }
        parser.parse(manifestText)
    }

    suspend fun stage(asset: osrsRealmAsset): osrsStagedRealmAsset = withContext(Dispatchers.IO) {
        require(osrsRealmManifestParser.isSafeRelativeAssetPath(asset.mbtilesPath)) {
            "Unsafe realm asset path ${asset.mbtilesPath}"
        }
        val started = SystemClock.elapsedRealtimeNanos()
        stagedDirectory.mkdirs()
        val normalizedSha = asset.mbtilesSha256.lowercase()
        val destination = File(stagedDirectory, "$normalizedSha.mbtiles")
        val marker = File(stagedDirectory, "$normalizedSha.verified")

        if (isVerifiedCopy(destination, marker, asset)) {
            return@withContext osrsStagedRealmAsset(
                file = destination,
                sha256 = normalizedSha,
                elapsedNanos = SystemClock.elapsedRealtimeNanos() - started,
                reusedVerifiedCopy = true
            )
        }

        val temporary = File(
            stagedDirectory,
            "$normalizedSha.tmp-${android.os.Process.myPid()}-${Thread.currentThread().hashCode()}"
        )
        temporary.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            assetManager.open(asset.mbtilesPath).use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(OSRS_COPY_BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            val actualSha = digest.digest().toHex()
            check(actualSha == normalizedSha) {
                "Checksum mismatch for ${asset.mbtilesPath}: expected $normalizedSha, got $actualSha"
            }
            if (asset.mbtilesBytes > 0L) {
                check(temporary.length() == asset.mbtilesBytes) {
                    "Size mismatch for ${asset.mbtilesPath}: expected ${asset.mbtilesBytes}, got ${temporary.length()}"
                }
            }
            if (destination.exists()) destination.delete()
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            marker.writeText("$normalizedSha\n${destination.length()}\n")
            osrsStagedRealmAsset(
                file = destination,
                sha256 = actualSha,
                elapsedNanos = SystemClock.elapsedRealtimeNanos() - started,
                reusedVerifiedCopy = false
            )
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    private fun isVerifiedCopy(destination: File, marker: File, asset: osrsRealmAsset): Boolean {
        if (!destination.isFile || !marker.isFile) return false
        if (asset.mbtilesBytes > 0L && destination.length() != asset.mbtilesBytes) return false
        val lines = runCatching { marker.readLines() }.getOrDefault(emptyList())
        return lines.getOrNull(0) == asset.mbtilesSha256.lowercase() &&
            lines.getOrNull(1)?.toLongOrNull() == destination.length()
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(OSRS_COPY_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    companion object {
        const val OSRS_REALM_MANIFEST_ASSET = "underground-realms.json"
        private const val OSRS_COPY_BUFFER_BYTES = 1024 * 1024
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
