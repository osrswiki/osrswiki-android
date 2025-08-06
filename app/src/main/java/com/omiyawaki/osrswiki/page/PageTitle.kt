package com.omiyawaki.osrswiki.page

import android.net.Uri
import android.os.Parcelable
import com.omiyawaki.osrswiki.dataclient.WikiSite
import kotlinx.parcelize.Parcelize
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder

@Parcelize
data class PageTitle(
    var namespace: Namespace?, // Can be null initially, resolved later
    val text: String,          // Prefixed text, like "Category:Items" or "Rune_scimitar"
    val wikiSite: WikiSite,
    var thumbUrl: String? = null,
    var description: String? = null,
    var displayText: String = text.replace("_", " "), // Default display text
    var fragment: String? = null // For section links, e.g., #History
) : Parcelable {

    // Secondary constructor for simpler cases or when namespace needs resolving
    constructor(text: String, wikiSite: WikiSite, thumbUrl: String? = null) :
            this(null, text, wikiSite, thumbUrl) {
        resolveNamespace()
        this.displayText = getDisplayTextFromPrefixedText(this.text, this.namespace)
    }
    
    init {
        resolveNamespace() // Ensure namespace is resolved if passed as null but text has prefix
        // If displayText was the default (from text property), re-evaluate after namespace resolution
        if (this.displayText == this.text.replace("_", " ") && this.namespace != null && this.namespace != Namespace.MAIN) {
            this.displayText = getDisplayTextFromPrefixedText(this.text, this.namespace)
        }
    }

    private fun resolveNamespace() {
        if (this.namespace == null && text.contains(':')) {
            val parts = text.split(":", limit = 2)
            val ns = Namespace.entries.find { nsEntry ->
                nsEntry.name.equals(parts[0].replace(" ", "_"), ignoreCase = true) ||
                nsEntry.customName().equals(parts[0], ignoreCase = true)
            }
            if (ns != null) {
                this.namespace = ns
                // The actual text part might be parts[1] if a namespace was found
                // but this constructor assumes 'text' is already the prefixed title.
            } else {
                this.namespace = Namespace.MAIN // Default to MAIN if prefix not recognized
            }
        } else if (this.namespace == null) {
            this.namespace = Namespace.MAIN
        }
    }

    private fun getDisplayTextFromPrefixedText(prefixedText: String, ns: Namespace?): String {
        if (ns != null && ns != Namespace.MAIN && prefixedText.startsWith(ns.customName() + ":", ignoreCase = true)) {
            return prefixedText.substring(ns.customName().length + 1).replace("_", " ")
        } else if (ns != null && ns != Namespace.MAIN && prefixedText.startsWith(ns.name + ":", ignoreCase = true)) {
            return prefixedText.substring(ns.name.length + 1).replace("_", " ")
        }
        return prefixedText.replace("_", " ")
    }


    val prefixedText: String
        get() = text // Assumes 'text' field already holds the prefixed, underscored title

    val mobileUri: String
        get() = wikiSite.mobileUrl(UriUtil.encodeURL(prefixedText) + if (!fragment.isNullOrEmpty()) "#${UriUtil.encodeURL(fragment)}" else "")

    val uri: String // For canonical URI
        get() = "${wikiSite.url()}/wiki/${UriUtil.encodeURL(prefixedText)}${if (!fragment.isNullOrEmpty()) "#${UriUtil.encodeURL(fragment)}" else ""}"

    fun namespace(): Namespace = namespace ?: Namespace.MAIN // Provide a non-null namespace

    // Simplified equals/hashCode for demonstration, Wikipedia's is more complex
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PageTitle) return false
        return text == other.text && wikiSite == other.wikiSite && namespace == other.namespace && fragment == other.fragment
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + wikiSite.hashCode()
        result = 31 * result + (namespace?.hashCode() ?: 0)
        result = 31 * result + (fragment?.hashCode() ?: 0)
        return result
    }

    // Placeholder for UriUtil if not fully implemented from Wikipedia
    private object UriUtil {
        fun encodeURL(s: String?): String {
            return try {
                URLEncoder.encode(s ?: "", "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                throw RuntimeException(e)
            }
        }
        // Add decodeURL if needed by other parts
        fun decodeURL(s: String?): String {
            return try {
                URLDecoder.decode(s ?: "", "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                throw RuntimeException(e)
            }
        }
    }
}
