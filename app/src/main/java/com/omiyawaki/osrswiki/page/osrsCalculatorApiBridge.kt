package com.omiyawaki.osrswiki.page

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONObject
import org.json.JSONArray
import com.omiyawaki.osrswiki.ui.common.ThemedAlertDialogs
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class osrsCalculatorApiBridge(private val context: Context) {
    private fun activityContext(): Context {
        var ctx: Context? = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return context
    }

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

    /**
     * Show native OS-level choice picker for calculator dropdowns.
     * 
     * @param json JSON with structure:
     *   {
     *     "label": "Item type",
     *     "options": [{"label": "Bronze", "value": "bronze"}, ...],
     *     "currentValue": "bronze"
     *   }
     * @return JSON response: {"selected": true, "value": "iron"} or {"selected": false}
     */
    @JavascriptInterface
    fun showChoicePicker(json: String): String {
        android.util.Log.d("osrsCalcApi", "showChoicePicker invoked")
        return try {
            val payload = JSONObject(json)
            val label = payload.optString("label", "Choose option")
            val optionsArray = payload.optJSONArray("options")
            val currentValue = payload.optString("currentValue", "")

            android.util.Log.d("osrsCalcApi", "showChoicePicker options=${optionsArray?.length() ?: -1} label=$label")
            if (optionsArray == null || optionsArray.length() == 0) {
                return JSONObject()
                    .put("selected", false)
                    .put("error", "No options provided")
                    .toString()
            }

            // Extract option labels and values
            val optionLabels = mutableListOf<String>()
            val optionValues = mutableListOf<String>()
            var selectedIndex = -1

            for (i in 0 until optionsArray.length()) {
                val option = optionsArray.getJSONObject(i)
                val optionLabel = option.optString("label", "")
                val optionValue = option.optString("value", optionLabel)
                
                optionLabels.add(optionLabel)
                optionValues.add(optionValue)
                
                if (optionValue == currentValue || (selectedIndex == -1 && optionLabel == currentValue)) {
                    selectedIndex = i
                }
            }

            // Show on main thread and block until selection
            val result = JSONObject()
            val semaphore = java.util.concurrent.Semaphore(0)
            
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    ThemedAlertDialogs.show(
                        ThemedAlertDialogs.builder(activityContext())
                            .setTitle(label)
                            .setSingleChoiceItems(
                                optionLabels.toTypedArray(),
                                selectedIndex
                            ) { dialog, which ->
                                result.put("selected", true)
                                result.put("value", optionValues[which])
                                dialog.dismiss()
                                semaphore.release()
                            }
                            .setNegativeButton("Cancel") { dialog, _ ->
                                result.put("selected", false)
                                dialog.dismiss()
                                semaphore.release()
                            }
                            .setOnCancelListener {
                                result.put("selected", false)
                                semaphore.release()
                            }
                    )
                } catch (e: Exception) {
                    android.util.Log.e("osrsCalcApi", "Failed to show picker: ${e.message}")
                    result.put("selected", false)
                    result.put("error", e.message ?: "dialog-failed")
                    semaphore.release()
                }
            }

            // Wait for dialog result (with timeout)
            if (semaphore.tryAcquire(30, java.util.concurrent.TimeUnit.SECONDS)) {
                return result.toString()
            } else {
                return JSONObject()
                    .put("selected", false)
                    .put("error", "timeout")
                    .toString()
            }
        } catch (error: Exception) {
            android.util.Log.e("osrsCalcApi", "showChoicePicker failed: ${error.message}")
            return JSONObject()
                .put("selected", false)
                .put("error", error.message ?: "picker-failed")
                .toString()
        }
    }
}
