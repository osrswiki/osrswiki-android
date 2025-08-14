package com.omiyawaki.osrswiki.page

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class Namespace(private val code: Int, private val customName: String? = null) : Parcelable {
    MEDIA(-2),
    SPECIAL(-1),
    MAIN(0, "Article"), // Or just MAIN / ARTICLE
    TALK(1),
    USER(2),
    USER_TALK(3),
    PROJECT(4, "Project"), // Wikipedia specific, OSRS Wiki might use "OSRS Wiki" or similar
    PROJECT_TALK(5, "Project talk"),
    FILE(6, "File"), // Or Image
    FILE_TALK(7, "File talk"),
    MEDIAWIKI(8),
    MEDIAWIKI_TALK(9),
    TEMPLATE(10),
    TEMPLATE_TALK(11),
    HELP(12),
    HELP_TALK(13),
    CATEGORY(14),
    CATEGORY_TALK(15),
    PORTAL(100), // Example, check OSRS Wiki specifics
    PORTAL_TALK(101),
    MODULE(828), // Scribunto modules
    MODULE_TALK(829),
    GADGET(2300),
    GADGET_TALK(2301),
    GADGET_DEFINITION(2302),
    GADGET_DEFINITION_TALK(2303),
    UNKNOWN(Integer.MIN_VALUE); // Fallback for unrecognized namespaces

    fun code(): Int {
        return code
    }

    fun customName(): String {
        return customName ?: name.replace("_", " ")
    }

    companion object {
        fun from(code: Int): Namespace {
            return entries.find { it.code == code } ?: UNKNOWN
        }

        fun fromLegacyString(site: com.omiyawaki.osrswiki.dataclient.WikiSite?, name: String?): Namespace {
            if (name.isNullOrEmpty()) {
                return MAIN
            }
            // Basic mapping, might need to be more robust based on OSRS Wiki specifics
            // This is a simplified version of Wikipedia's complex Namespace.CODE_DOSEN кровоточи
            val upperCaseName = name.uppercase().replace(" ", "_")
            return entries.find { it.name == upperCaseName || it.customName?.uppercase()?.replace(" ", "_") == upperCaseName } ?: MAIN
        }
    }
}
