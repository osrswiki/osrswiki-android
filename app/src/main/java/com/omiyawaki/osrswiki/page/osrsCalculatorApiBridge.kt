package com.omiyawaki.osrswiki.page

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONObject

class osrsCalculatorApiBridge(private val context: Context) {
    @JavascriptInterface
    fun request(json: String): String {
        return try {
            val payload = JSONObject(json)
            val method = payload.optString("method", "GET")
            val url = payload.optString("url")
            val data = if (payload.has("data") && !payload.isNull("data")) {
                payload.opt("data")
            } else {
                null
            }
            android.util.Log.i("osrsCalcApi", "$method $url")
            osrsWikiWebViewProxy.request(context, method, url, data).toString()
        } catch (error: Exception) {
            JSONObject()
                .put("ok", false)
                .put("error", error.message ?: "calculator-api-failed")
                .toString()
        }
    }
}
