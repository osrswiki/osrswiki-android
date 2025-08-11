package com.omiyawaki.osrswiki.bridge

import android.content.Context
import com.omiyawaki.osrswiki.util.asset.AssetReader

object JavaScriptActionHandler {
    fun getCollapsibleContentJs(context: Context): String? {
        return AssetReader.readAssetFile(context, "web/app/collapsible_content.js")
    }

    fun getCollapsibleTableCssPath(): String {
        return "web/app/collapsible_tables.css"
    }

    fun getInfoboxSwitcherCssPath(): String {
        return "web/app/switch_infobox_styles.css"
    }

    fun getInfoboxSwitcherBootstrapJsPath(): String {
        return "web/app/infobox_switcher_bootstrap.js"
    }

    fun getInfoboxSwitcherJsPath(): String {
        return "web/app/switch_infobox.js"
    }
}
