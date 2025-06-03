package com.omiyawaki.osrswiki.page

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.omiyawaki.osrswiki.util.log.L // Assuming L is your logging utility, adjust if needed

class PageLinkHandler(
    context: Context
) : LinkHandler(context) {

    override fun onInternalArticleLinkClicked(articleTitle: String, fullUri: Uri) {
        L.i("Internal article link clicked: Title='${articleTitle}', URI='${fullUri}'")

        // The 'articleTitle' from LinkHandler is the raw path segment (e.g., "Rune_scimitar").
        // PageActivity.newIntent takes a pageTitle (String?) and pageId (String?).
        // We'll convert underscores to spaces for the pageTitle argument.
        val displayTitle = articleTitle.replace('_', ' ')

        L.d("Attempting to launch PageActivity with Title: '${displayTitle}', PageId: null (from URI: ${fullUri})")

        val intent = PageActivity.newIntent(
            context = context,
            pageTitle = displayTitle,
            pageId = null // We don't have a separate pageId from this type of URL click.
                         // PageFragment will need to handle a null pageId if it typically relies on one.
        )
        context.startActivity(intent)
    }

    override fun onExternalLinkClicked(uri: Uri) {
        L.i("External link clicked: URI='${uri}'")
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            L.e("No application can handle this request. URI: '${uri}'", e)
            // Optionally, show a Toast to the user
        }
    }

    // Optionally, override onNonArticleInternalLinkClicked(uri: Uri) if specific handling is needed.
    // override fun onNonArticleInternalLinkClicked(uri: Uri) {
    //     L.i("Non-article internal link clicked: URI='${uri}'")
    //     super.onNonArticleInternalLinkClicked(uri) // Default is to treat as external
    // }
}
