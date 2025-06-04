package com.omiyawaki.osrswiki.database.converters

import androidx.room.TypeConverter
import java.util.Date

/**
 * Type converters for Room to handle Date objects by storing them as Long timestamps.
 */
object DateConverter {
    @TypeConverter
    @JvmStatic // Important for Room if the methods are in an object
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    @JvmStatic // Important for Room if the methods are in an object
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
