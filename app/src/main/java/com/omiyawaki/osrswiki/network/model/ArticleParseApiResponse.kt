package com.omiyawaki.osrswiki.network.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents the top-level response from the MediaWiki API's parse action.
 */
@Serializable
data class ArticleParseApiResponse(
    val parse: ParseResult? = null
)

/**
 * Custom serializer for the 'text' field that handles both formats:
 * - Online viewing: {"*": "HTML content"}
 * - Offline saving: "HTML content"
 */
object TextFieldSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TextField", PrimitiveKind.STRING)

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("TextFieldSerializer can only be used with Json")
        
        val element = jsonDecoder.decodeJsonElement()
        
        return when (element) {
            is JsonObject -> {
                // Online format: {"*": "HTML content"}
                element["*"]?.jsonPrimitive?.content
            }
            is JsonPrimitive -> {
                // Offline format: "HTML content"
                if (element.isString) element.content else null
            }
            else -> null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) {
            encoder.encodeString(value)
        } else {
            encoder.encodeNull()
        }
    }
}

/**
 * Contains the actual parsed article data.
 */
@Serializable
data class ParseResult(
    val title: String,
    val pageid: Int,
    val revid: Long,
    @Serializable(with = TextFieldSerializer::class)
    val text: String?,
    val displaytitle: String?
)
