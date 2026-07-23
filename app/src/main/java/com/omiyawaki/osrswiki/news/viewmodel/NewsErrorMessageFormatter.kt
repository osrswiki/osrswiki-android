package com.omiyawaki.osrswiki.news.viewmodel

object NewsErrorMessageFormatter {
    fun loadMessage(exception: Throwable): String {
        return "Failed to load Home. Please check your connection and try again."
    }

    fun refreshMessage(exception: Throwable): String {
        return "Failed to refresh Home. Please check your connection and try again."
    }
}
