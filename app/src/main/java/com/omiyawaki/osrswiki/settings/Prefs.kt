package com.omiyawaki.osrswiki.settings

// Minimal placeholder for Prefs.
// You'll need to integrate this with your actual SharedPreferences implementation.
object Prefs {
    // Default to true for now, mirroring Wikipedia's ReadingListPage entity's default behavior.
    // Replace with actual preference loading logic.
    var isDownloadingReadingListArticlesEnabled: Boolean = true
        private set // Or implement get() to read from SharedPreferences

    // Add other preferences as needed, e.g., from Wikipedia's Prefs if you mirror them.
}
