package com.omiyawaki.osrswiki.util

/**
 * A generic class that holds a value with its loading status.
 * @param <T>
 */
@Suppress("unused")
sealed class Result<out T> {
   @Suppress("unused")
    data class Success<out T>(val data: T) : Result<T>()
   @Suppress("unused")
    data class Error(val message: String, val throwable: Throwable? = null) : Result<Nothing>()
   @Suppress("unused")
    data object Loading : Result<Nothing>() // Changed to data object for consistency, or can be data class if it ever needs params

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$data]"
            is Error -> "Error[message=$message, throwable=$throwable]"
            Loading -> "Loading"
        }
    }
}
