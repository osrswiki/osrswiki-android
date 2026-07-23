package com.omiyawaki.osrswiki.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ExternalUrlLauncher {
    fun open(
        context: Context,
        url: String,
        failureMessage: String,
        startActivity: (Intent) -> Unit = { intent -> context.startActivity(intent) }
    ): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
            false
        }
    }
}
