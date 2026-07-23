package com.omiyawaki.osrswiki.util.asset

import android.content.Context
import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException

object AssetReader {
    private const val TAG = "AssetReader"

    fun readAssetFile(context: Context, filePath: String): String? {
        return try {
            val content = context.assets.open(filePath).bufferedReader().use { it.readText() }
            Log.d(TAG, "Successfully read '$filePath'. Content length: ${content.length}")
            content
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "Optional asset file not found: $filePath")
            null
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read asset file: $filePath", e)
            null
        }
    }
}
