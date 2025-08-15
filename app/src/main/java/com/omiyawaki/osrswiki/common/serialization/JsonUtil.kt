package com.omiyawaki.osrswiki.common.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule

/**
 * Utility object for providing a configured instance of Kotlinx Serialization's Json.
 */
object JsonUtil {

    /**
     * A general-purpose Json instance.
     * - Ignores unknown keys to be resilient to API changes or diverse data sources.
     * - Is lenient to allow for minor deviations in JSON structure if necessary (e.g. unquoted strings, though generally not recommended).
     * - Explicitly uses EmptySerializersModule for now; can be expanded if contextual serializers are needed.
     */
    @OptIn(ExperimentalSerializationApi::class) // For EmptySerializersModule
    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        // prettyPrint = BuildConfig.DEBUG // Optional: for debugging, makes JSON output readable
        // encodeDefaults = true // Optional: if you want default values to always be present in the JSON
        serializersModule = EmptySerializersModule() // Explicitly set, can be customized later
    }

    // Example of how to create a more specific instance if needed later:
    // val specificInstance: Json = Json(from = instance) {
    //     // customize further if necessary
    // }
}
