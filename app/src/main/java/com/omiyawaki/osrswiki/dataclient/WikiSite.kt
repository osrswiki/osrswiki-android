package com.omiyawaki.osrswiki.dataclient

import android.os.Parcelable
import kotlinx.parcelize.Parcelize // Ensure kotlin-parcelize plugin is applied in build.gradle

@Parcelize
data class WikiSite(
    val authority: String, // Made authority first for clarity in primary constructor
    private val scheme: String = "https",
    var languageCode: String = "en"
) : Parcelable {

    // Secondary constructor if you want to primarily define by authority and use default scheme/lang
    constructor(authority: String) : this(authority, "https", "en")

    fun scheme(): String = scheme
    fun authority(): String = authority
    fun url(): String = "$scheme://$authority"
    fun dbName(): String = authority.replace(".", "_")

    fun mobileUrl(pagePath: String): String {
        return "${url()}/w/$pagePath"
    }

    fun apiUrl(): String {
        return "${url()}/api.php"
    }

    override fun toString(): String = url()

    companion object {
        private const val OSRS_WIKI_AUTHORITY = "oldschool.runescape.wiki"
        val OSRS_WIKI = WikiSite(OSRS_WIKI_AUTHORITY, "https", "en")

        fun forDomain(domain: String, lang: String = "en"): WikiSite {
            // Corrected constructor call using named arguments or matching primary constructor order
            return WikiSite(authority = domain, languageCode = lang)
        }
    }
}
