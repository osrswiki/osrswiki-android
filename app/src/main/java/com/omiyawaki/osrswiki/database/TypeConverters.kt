package com.omiyawaki.osrswiki.database

import androidx.room.TypeConverter
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.page.Namespace

class TypeConverters {
    @TypeConverter
    fun fromWikiSite(wikiSite: WikiSite?): String? {
        // Store WikiSite as a combination of authority and language code,
        // assuming authority is the primary identifier.
        // Example: "oldschool.runescape.wiki|en"
        return wikiSite?.let { "${it.authority()}|${it.languageCode}" }
    }

    @TypeConverter
    fun toWikiSite(value: String?): WikiSite? {
        return value?.split('|')?.let { parts ->
            if (parts.size == 2) {
                WikiSite(authority = parts[0], languageCode = parts[1])
            } else if (parts.isNotEmpty()) {
                // Fallback if only authority was stored or format is old
                WikiSite(authority = parts[0])
            } else {
                null
            }
        }
    }

    @TypeConverter
    fun fromNamespace(namespace: Namespace?): String? {
        return namespace?.name // Store enum by its string name
    }

    @TypeConverter
    fun toNamespace(value: String?): Namespace? {
        return value?.let {
            try {
                Namespace.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null // Or return Namespace.UNKNOWN if you prefer
            }
        }
    }
}
