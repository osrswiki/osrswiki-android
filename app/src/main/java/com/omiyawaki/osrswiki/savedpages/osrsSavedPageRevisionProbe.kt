package com.omiyawaki.osrswiki.savedpages

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object osrsSavedPageRevisionProbe {
    const val WIKI_QUERY_ENDPOINT = "https://oldschool.runescape.wiki/api.php"

    data class RemoteRevision(
        val pageTitle: String,
        val revisionId: Long
    )

    fun queryUrl(pageTitle: String): String {
        val encodedTitle = URLEncoder.encode(pageTitle, Charsets.UTF_8.name())
        return "$WIKI_QUERY_ENDPOINT?action=query&format=json&formatversion=2" +
            "&prop=revisions&rvprop=ids&rvlimit=1&titles=$encodedTitle"
    }

    fun remoteRevision(json: String, requestedTitle: String): RemoteRevision? {
        val page = JSONObject(json)
            .optJSONObject("query")
            ?.optJSONArray("pages")
            ?.optJSONObject(0)
            ?: return null
        if (page.optBoolean("missing", false)) {
            return null
        }
        val revision = page.optJSONArray("revisions")?.optJSONObject(0) ?: return null
        val revisionId = (revision.opt("revid") as? Number)?.toLong() ?: 0L
        if (revisionId <= 0L) {
            return null
        }
        return RemoteRevision(
            pageTitle = page.optString("title", requestedTitle),
            revisionId = revisionId
        )
    }

    fun snapshotNeedsRefresh(localRevisionId: Long?, remoteRevisionId: Long): Boolean {
        if (localRevisionId == null || localRevisionId <= 0L) {
            return true
        }
        return remoteRevisionId != localRevisionId
    }

    fun fetchRemoteRevision(pageTitle: String, client: OkHttpClient): RemoteRevision? {
        return runCatching {
            val request = Request.Builder().url(queryUrl(pageTitle)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }
                val body = response.body?.string() ?: return@use null
                remoteRevision(body, pageTitle)
            }
        }.getOrNull()
    }
}
