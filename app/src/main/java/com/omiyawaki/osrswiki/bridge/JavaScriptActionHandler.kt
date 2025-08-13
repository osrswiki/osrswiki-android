package com.omiyawaki.osrswiki.bridge

import android.content.Context
import com.omiyawaki.osrswiki.util.asset.AssetReader

object JavaScriptActionHandler {
    fun getCollapsibleContentJs(context: Context): String? {
        return AssetReader.readAssetFile(context, "web/collapsible_content.js")
    }

    fun getCollapsibleTableCssPath(): String {
        return "web/collapsible_tables.css"
    }

    fun getInfoboxSwitcherCssPath(): String {
        return "web/switch_infobox_styles.css"
    }

    fun getInfoboxSwitcherBootstrapJsPath(): String {
        return "web/infobox_switcher_bootstrap.js"
    }

    fun getInfoboxSwitcherJsPath(): String {
        return "web/switch_infobox.js"
    }
}
