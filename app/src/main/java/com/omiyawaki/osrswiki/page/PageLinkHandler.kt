package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageLinkHandler(
    constructionContext: Context,
    private val coroutineScope: CoroutineScope,
    private val pageRepository: PageRepository,
    private val theme: Theme
) : LinkHandler(constructionContext) {

    companion object {
        private const val TAG = "PageLinkHandler"
    }

    override fun onInternalArticleLinkClicked(articleTitleWithUnderscores: String, fullUri: Uri) {
        val targetPageTitleWithSpaces = articleTitleWithUnderscores.replace('_', ' ')
        Log.d(TAG, "Attempting to handle internal link. Target title: '$targetPageTitleWithSpaces', URI: $fullUri")

        // Simplified approach: Direct loading only (no repository calls to avoid HTML building)
        if (isNetworkAvailable()) {
            Log.i(TAG, "Network available. Using direct loading for '$targetPageTitleWithSpaces'.")
            context.startActivity(PageActivity.newIntent(
                context,
                targetPageTitleWithSpaces,
                null,
                HistoryEntry.SOURCE_INTERNAL_LINK
            ))
        } else {
            Log.w(TAG, "No network connection. Direct loading requires network.")
            coroutineScope.launch {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No network connection available to load page '$targetPageTitleWithSpaces'.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onExternalLinkClicked(uri: Uri) {
        Log.i(TAG, "onExternalLinkClicked called with URI: $uri")
        
        if (isYouTubeUrl(uri)) {
            openYouTubeUrl(uri)
        } else {
            openGenericExternalLink(uri)
        }
    }
    
    private fun openYouTubeUrl(uri: Uri) {
        Log.i(TAG, "Attempting to open YouTube URL: $uri")
        
        val cleanedUri = cleanYouTubeUrl(uri)
        
        // Find all YouTube video apps (exclude Music apps since these are videos)
        val youtubeApps = findYouTubeVideoApps()
        Log.i(TAG, "Found ${youtubeApps.size} YouTube video apps: $youtubeApps")
        
        // Try YouTube-specific packages first
        for (packageName in youtubeApps) {
            if (tryOpenWithPackage(cleanedUri, packageName)) {
                Log.i(TAG, "Successfully opened YouTube URL with: $packageName")
                return
            }
        }
        
        // Try YouTube app with vnd.youtube scheme
        if (tryOpenWithYouTubeScheme(cleanedUri)) {
            Log.i(TAG, "Successfully opened YouTube URL with vnd.youtube scheme")
            return
        }
        
        // Fall back to generic intent resolution
        if (tryOpenGeneric(cleanedUri)) {
            Log.i(TAG, "Successfully opened YouTube URL with generic intent")
            return
        }
        
        // Final fallback to browser or chooser
        if (tryOpenWithChooser(cleanedUri)) {
            Log.i(TAG, "Opened YouTube URL with chooser")
            return
        }
        
        Log.w(TAG, "Failed to open YouTube URL: $cleanedUri")
        Toast.makeText(context, "Unable to open YouTube video", Toast.LENGTH_SHORT).show()
    }
    
    private fun tryOpenWithPackage(uri: Uri, packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                Log.d(TAG, "Package $packageName cannot handle YouTube URL")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening with package $packageName: ${e.message}")
            false
        }
    }
    
    private fun tryOpenWithYouTubeScheme(uri: Uri): Boolean {
        val videoId = extractVideoId(uri)
        if (videoId == null) {
            Log.d(TAG, "Could not extract video ID from: $uri")
            return false
        }
        
        return try {
            val youtubeAppUri = Uri.parse("vnd.youtube:$videoId")
            val youtubeIntent = Intent(Intent.ACTION_VIEW, youtubeAppUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (youtubeIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(youtubeIntent)
                true
            } else {
                Log.d(TAG, "No app handles vnd.youtube scheme")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open with vnd.youtube scheme: ${e.message}")
            false
        }
    }
    
    private fun extractVideoId(uri: Uri): String? {
        val host = uri.host?.lowercase()
        return when {
            host == "youtu.be" -> uri.lastPathSegment
            host?.contains("youtube.com") == true -> uri.getQueryParameter("v")
            else -> null
        }
    }
    
    private fun tryOpenGeneric(uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening with generic intent: ${e.message}")
            false
        }
    }
    
    private fun tryOpenWithChooser(uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val chooser = Intent.createChooser(intent, "Open YouTube video with:").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (chooser.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooser)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show chooser: ${e.message}")
            false
        }
    }
    
    private fun openGenericExternalLink(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                Log.i(TAG, "Starting external activity for URI: $uri")
                context.startActivity(intent)
                Log.i(TAG, "External activity started successfully for URI: $uri")
            } else {
                Log.w(TAG, "No application can handle this external link: $uri")
                Toast.makeText(context, "No application can handle this link.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not handle external link $uri", e)
            Toast.makeText(context, "Error opening link.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun findYouTubeVideoApps(): List<String> {
        val youtubeApps = mutableListOf<String>()
        
        try {
            val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            }
            
            for (appInfo in installedApps) {
                val packageName = appInfo.packageName.lowercase()
                
                // Match patterns like *.android.youtube, *.youtube, com.google.android.youtube
                // but exclude music apps
                if (isYouTubeVideoApp(packageName)) {
                    youtubeApps.add(appInfo.packageName)
                    Log.d(TAG, "Found YouTube video app: ${appInfo.packageName}")
                }
            }
            
            // Sort to prioritize official YouTube app first
            youtubeApps.sortWith { a, b ->
                when {
                    a == "com.google.android.youtube" -> -1
                    b == "com.google.android.youtube" -> 1
                    else -> a.compareTo(b)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error finding YouTube apps: ${e.message}")
            // Fallback to known YouTube apps if dynamic detection fails
            youtubeApps.addAll(listOf(
                "com.google.android.youtube",
                "app.rvx.android.youtube"
            ))
        }
        
        return youtubeApps
    }
    
    private fun isYouTubeVideoApp(packageName: String): Boolean {
        // Exclude music apps first
        if (packageName.contains("music")) {
            return false
        }
        
        // Match various YouTube app patterns
        return when {
            // Official YouTube app
            packageName == "com.google.android.youtube" -> true
            
            // Pattern: *.android.youtube (like app.rvx.android.youtube, com.vanced.android.youtube, etc.)
            packageName.endsWith(".android.youtube") -> true
            
            // Pattern: *.youtube (like com.vanced.youtube, app.revanced.youtube, etc.)
            packageName.endsWith(".youtube") && !packageName.endsWith(".android.youtube") -> true
            
            // Pattern: youtube.* (like youtube.vanced, youtube.revanced, etc.)
            packageName.startsWith("youtube.") -> true
            
            // Catch other common YouTube variants
            packageName.contains("youtube") && (
                packageName.contains("vanced") || 
                packageName.contains("revanced") ||
                packageName.contains("rvx") ||
                packageName.contains("newpipe") ||
                packageName.contains("smarttube")
            ) -> true
            
            else -> false
        }
    }
    
    private fun isYouTubeUrl(uri: Uri): Boolean {
        val host = uri.host?.lowercase()
        return host == "www.youtube.com" || host == "youtube.com" || 
               host == "youtu.be" || host == "m.youtube.com"
    }
    
    private fun cleanYouTubeUrl(uri: Uri): Uri {
        if (!isYouTubeUrl(uri)) return uri
        
        val host = uri.host?.lowercase()
        val path = uri.path ?: ""
        
        return when {
            // Handle youtu.be short URLs
            host == "youtu.be" -> {
                val videoId = path.removePrefix("/")
                Uri.parse("https://www.youtube.com/watch?v=$videoId")
            }
            // Handle youtube.com watch URLs - keep only essential parameters
            path.startsWith("/watch") -> {
                val videoId = uri.getQueryParameter("v")
                if (videoId != null) {
                    Uri.parse("https://www.youtube.com/watch?v=$videoId")
                } else {
                    uri
                }
            }
            // Handle other YouTube URLs as-is
            else -> uri
        }
    }

    @SuppressLint("MissingPermission")
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                return networkInfo?.isConnected == true
            }
        }
        return false
    }
}
