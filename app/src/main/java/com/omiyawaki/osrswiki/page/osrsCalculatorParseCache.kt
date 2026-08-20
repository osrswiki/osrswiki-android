package com.omiyawaki.osrswiki.page

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Exact-request cache for calculator parse/hiscores responses so a saved
 * calculator page can replay previously computed results offline.
 *
 * Arbitrary new input combinations still need the wiki parser; those requests
 * are stored after a successful online submit.
 */
object osrsCalculatorParseCache {
    private const val DIR_NAME = "calculator_parse"

    fun canonical(method: String, url: String, body: String): Triple<String, String, String> {
        val uri = Uri.parse(url)
        val path = buildString {
            append(uri.scheme ?: "https")
            append("://")
            append(uri.host ?: osrsWikiWebViewUrl.WIKI_HOST)
            append(uri.path ?: "")
        }
        val params = sortedMapOf<String, String>()
        uri.queryParameterNames.forEach { name ->
            params[name] = uri.getQueryParameter(name) ?: ""
        }
        if (body.isNotBlank()) {
            body.split('&').forEach { pair ->
                if (pair.isEmpty()) return@forEach
                val parts = pair.split('=', limit = 2)
                val key = decodeComponent(parts[0])
                val value = decodeComponent(parts.getOrElse(1) { "" })
                if (key.isNotEmpty()) {
                    params[key] = value
                }
            }
        }
        val canonicalBody = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return Triple(method.uppercase(), path, canonicalBody)
    }

    fun key(method: String, url: String, body: String): String {
        val (canonicalMethod, canonicalUrl, canonicalBody) = canonical(method, url, body)
        val digest = MessageDigest.getInstance("SHA-256")
        val material = "$canonicalMethod\n$canonicalUrl\n$canonicalBody".toByteArray(StandardCharsets.UTF_8)
        return digest.digest(material).joinToString("") { "%02x".format(it) }
    }

    fun read(context: Context?, method: String, url: String, body: String): ByteArray? {
        val file = fileFor(context, method, url, body) ?: return null
        return if (file.isFile) file.readBytes() else null
    }

    fun write(context: Context?, method: String, url: String, body: String, bytes: ByteArray) {
        val file = fileFor(context, method, url, body) ?: return
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    private fun fileFor(context: Context?, method: String, url: String, body: String): File? {
        val ctx = context ?: return null
        return File(File(ctx.filesDir, DIR_NAME), key(method, url, body) + ".bin")
    }

    private fun decodeComponent(raw: String): String {
        return try {
            URLDecoder.decode(raw.replace('+', ' '), "UTF-8")
        } catch (_: Exception) {
            raw
        }
    }
}
