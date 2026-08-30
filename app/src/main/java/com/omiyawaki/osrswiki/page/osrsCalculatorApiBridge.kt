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
            val searchable = payload.optBoolean("searchable", false) || (optionsArray?.length() ?: 0) > 12

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
                    val activity = activityContext()
                    val builder = ThemedAlertDialogs.builder(activity).setTitle(label)
                    if (searchable) {
                        val density = activity.resources.displayMetrics.density
                        val pad = (12 * density).toInt()
                        val column = android.widget.LinearLayout(activity).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            setPadding(pad, pad, pad, 0)
                        }
                        val search = android.widget.EditText(activity).apply {
                            hint = "Filter"
                            inputType = android.text.InputType.TYPE_CLASS_TEXT
                            setSingleLine(true)
                        }
                        val list = android.widget.ListView(activity)
                        val visibleLabels = optionLabels.toMutableList()
                        val visibleValues = optionValues.toMutableList()
                        val adapter = android.widget.ArrayAdapter(
                            activity,
                            android.R.layout.simple_list_item_single_choice,
                            visibleLabels
                        )
                        list.adapter = adapter
                        list.choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
                        if (selectedIndex >= 0) list.setItemChecked(selectedIndex, true)
                        fun applyFilter(query: String) {
                            val needle = query.trim().lowercase()
                            visibleLabels.clear()
                            visibleValues.clear()
                            for (i in optionLabels.indices) {
                                if (needle.isEmpty() || optionLabels[i].lowercase().contains(needle)) {
                                    visibleLabels.add(optionLabels[i])
                                    visibleValues.add(optionValues[i])
                                }
                            }
                            adapter.notifyDataSetChanged()
                        }
                        search.addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                applyFilter(s?.toString() ?: "")
                            }
                            override fun afterTextChanged(s: android.text.Editable?) {}
                        })
                        column.addView(search)
                        column.addView(
                            list,
                            android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                (320 * density).toInt()
                            )
                        )
                        builder.setView(column)
                        var dialogRef: android.app.Dialog? = null
                        list.setOnItemClickListener { _, _, which, _ ->
                            if (which in visibleValues.indices) {
                                result.put("selected", true)
                                result.put("value", visibleValues[which])
                                dialogRef?.dismiss()
                                semaphore.release()
                            }
                        }
                        builder.setNegativeButton("Cancel") { dialog, _ ->
                            result.put("selected", false)
                            dialog.dismiss()
                            semaphore.release()
                        }
                        builder.setOnCancelListener {
                            result.put("selected", false)
                            semaphore.release()
                        }
                        dialogRef = ThemedAlertDialogs.show(builder)
                    } else {
                        ThemedAlertDialogs.show(
                            builder
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
                    }
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
