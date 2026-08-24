package com.omiyawaki.osrswiki.page

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.omiyawaki.osrswiki.network.NetworkModuleCache
import com.omiyawaki.osrswiki.network.OkHttpClientFactory
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Same-origin proxy for wiki calculator traffic originating in the article WebView.
 */
object osrsWikiWebViewProxy {
    private const val TAG = "osrsCalcProxy"
    private val formMediaType = "application/x-www-form-urlencoded; charset=utf-8".toMediaTypeOrNull()

    fun intercept(request: WebResourceRequest, context: Context?): WebResourceResponse? {
        val uri = request.url
        if (!osrsWikiWebViewUrl.shouldProxy(uri)) {
            return null
        }
        val method = request.method ?: "GET"
        if (method.equals("POST", ignoreCase = true)) {
            // WebResourceRequest does not expose the POST body. Android calculator
            // POSTs are handled by osrsCalculatorApiBridge instead.
            return null
        }
        val wikiUrl = osrsWikiWebViewUrl.rewriteToWiki(uri.toString())
        if (wikiUrl.contains("/load.php") && context != null) {
            val moduleCache = NetworkModuleCache.getInstance(context)
            moduleCache.getCachedResponseIfPresent(wikiUrl)?.let { cached ->
                return javascriptResponse(osrsResourceLoaderScript.sanitize(cached))
            }
        }
        val cacheable = wikiUrl.contains("/api.php") || wikiUrl.contains("/load.php")
        if (cacheable) {
            osrsCalculatorParseCache.read(context, "GET", wikiUrl, "")
                ?.takeIf { it.isNotEmpty() }
                ?.let { return bytesResponse(it, contentTypeFor(wikiUrl)) }
        }
        val fetched = fetch(wikiUrl, "GET", emptyMap(), null, context) ?: return null
        if (wikiUrl.contains("/cors/") && fetched.body.isEmpty()) {
            return null
        }
        if (wikiUrl.contains("/load.php") && context != null) {
            val body = osrsResourceLoaderScript.sanitize(
                fetched.body.toString(StandardCharsets.UTF_8)
            )
            NetworkModuleCache.getInstance(context).cacheResponse(wikiUrl, body)
            return javascriptResponse(body)
        }
        if (cacheable && fetched.body.isNotEmpty()) {
            osrsCalculatorParseCache.write(context, "GET", wikiUrl, "", fetched.body)
        }
        return bytesResponse(fetched.body, fetched.contentType)
    }

    fun request(context: Context?, method: String, rawUrl: String, data: Any?): JSONObject {
        val result = JSONObject()
        var wikiUrl = osrsWikiWebViewUrl.rewriteToWiki(absoluteWikiUrl(rawUrl))
        val encoded = encodeRequestData(data)
        if (method.equals("GET", ignoreCase = true) && data is JSONObject) {
            wikiUrl = appendQuery(wikiUrl, data)
        }
        val cacheable = wikiUrl.contains("/api.php") || wikiUrl.contains("/load.php")
        val cacheBody = if (method.equals("GET", ignoreCase = true)) "" else encoded.bodyText
        if (cacheable) {
            osrsCalculatorParseCache.read(context, method, wikiUrl, cacheBody)?.takeIf { it.isNotEmpty() }?.let { cached ->
                result.put("ok", true)
                result.put("body", cached.toString(StandardCharsets.UTF_8))
                result.put("cached", true)
                return result
            }
        }
        val fetched = fetch(wikiUrl, method, encoded.headers, encoded.body, context)
        if (fetched == null) {
            result.put("ok", false)
            result.put("error", "calculator proxy returned no response")
            result.put("body", "")
            return result
        }
        if (wikiUrl.contains("/cors/") && fetched.body.isEmpty()) {
            result.put("ok", false)
            result.put("error", "empty-hiscores-response")
            result.put("body", "")
            return result
        }
        if (cacheable && fetched.body.isNotEmpty()) {
            osrsCalculatorParseCache.write(context, method, wikiUrl, cacheBody, fetched.body)
        }
        result.put("ok", true)
        result.put("body", fetched.body.toString(StandardCharsets.UTF_8))
        result.put("cached", false)
        return result
    }

    private fun appendQuery(url: String, data: JSONObject): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        val builder = parsed.newBuilder()
        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            builder.addQueryParameter(key, data.opt(key)?.toString() ?: "")
        }
        return builder.build().toString()
    }

    private fun absoluteWikiUrl(rawUrl: String): String {
        return when {
            rawUrl.startsWith("https://") || rawUrl.startsWith("http://") -> rawUrl
            rawUrl.startsWith("/") -> osrsWikiWebViewUrl.WIKI_ORIGIN + rawUrl
            else -> osrsWikiWebViewUrl.WIKI_ORIGIN + "/" + rawUrl
        }
    }

    private data class EncodedBody(
        val headers: Map<String, String>,
        val body: okhttp3.RequestBody?,
        val bodyText: String
    )

    private fun encodeRequestData(data: Any?): EncodedBody {
        if (data == null) {
            return EncodedBody(emptyMap(), null, "")
        }
        if (data is JSONObject) {
            val form = FormBody.Builder()
            val keys = data.keys()
            val pieces = mutableListOf<String>()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = data.opt(key)?.toString() ?: ""
                form.add(key, value)
                pieces.add("$key=$value")
            }
            return EncodedBody(
                mapOf("Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"),
                form.build(),
                pieces.sorted().joinToString("&")
            )
        }
        val text = data.toString()
        return EncodedBody(
            mapOf("Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"),
            text.toRequestBody(formMediaType),
            text
        )
    }

    private data class FetchedResponse(
        val body: ByteArray,
        val contentType: String
    )

    private fun fetch(
        wikiUrl: String,
        method: String,
        headers: Map<String, String>,
        body: okhttp3.RequestBody?,
        context: Context?
    ): FetchedResponse? {
        return try {
            val httpUrl = wikiUrl.toHttpUrlOrNull() ?: return null
            val builder = Request.Builder()
                .url(httpUrl)
                .header("User-Agent", "OSRSWiki-Android-Calculator")
                .header("Accept", "*/*")
            headers.forEach { (key, value) -> builder.header(key, value) }
            if (method.equals("POST", ignoreCase = true)) {
                builder.post(body ?: ByteArray(0).toRequestBody(formMediaType))
            } else {
                builder.get()
            }
            val client = try {
                OkHttpClientFactory.offlineClient
            } catch (_: Exception) {
                okhttp3.OkHttpClient()
            }
            client.newCall(builder.build()).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                val contentType = response.header("Content-Type")
                    ?: contentTypeFor(wikiUrl)
                if (!response.isSuccessful) {
                    Log.w(TAG, "Calculator proxy HTTP ${response.code} for $wikiUrl")
                    return@use null
                }
                FetchedResponse(bytes, contentType)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Calculator proxy failed for $wikiUrl: ${error.message}")
            null
        }
    }

    private fun contentTypeFor(url: String): String {
        return when {
            url.contains("/load.php") -> "application/javascript; charset=utf-8"
            url.contains("/api.php") -> "application/json; charset=utf-8"
            else -> "text/plain; charset=utf-8"
        }
    }

    private fun javascriptResponse(source: String): WebResourceResponse {
        val sanitized = osrsResourceLoaderScript.sanitize(source)
        return bytesResponse(
            sanitized.toByteArray(StandardCharsets.UTF_8),
            "application/javascript; charset=utf-8"
        )
    }

    private fun bytesResponse(bytes: ByteArray, contentType: String): WebResourceResponse {
        val mime = contentType.substringBefore(";").trim().ifEmpty { "application/octet-stream" }
        val charset = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE)
            .find(contentType)
            ?.groupValues
            ?.getOrNull(1)
            ?: "UTF-8"
        return WebResourceResponse(mime, charset, ByteArrayInputStream(bytes))
    }

    internal fun jsonObjectFromBridgeData(raw: Any?): Any? {
        return when (raw) {
            null, JSONObject.NULL -> null
            is JSONObject, is JSONArray -> raw
            else -> raw
        }
    }
}
