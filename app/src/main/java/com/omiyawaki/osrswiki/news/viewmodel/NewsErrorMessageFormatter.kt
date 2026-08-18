package com.omiyawaki.osrswiki.news.viewmodel

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object NewsErrorMessageFormatter {
    fun loadMessage(exception: Throwable): String {
        return when (exception) {
            is UnknownHostException ->
                "Home can’t reach the wiki right now. Your device may be offline."
            is SocketTimeoutException ->
                "Home couldn’t reach the wiki in time. Please try again."
            is IOException ->
                "Home couldn’t reach the wiki. Please try again."
            else -> "Home is temporarily unavailable. Please try again."
        }
    }

    fun refreshMessage(exception: Throwable): String {
        return when (exception) {
            is UnknownHostException ->
                "Home can’t refresh because the wiki is unreachable. Your device may be offline."
            is SocketTimeoutException ->
                "Home refresh timed out while contacting the wiki. Please try again."
            is IOException ->
                "Home couldn’t refresh from the wiki. Please try again."
            else -> "Home refresh is temporarily unavailable. Please try again."
        }
    }
}
