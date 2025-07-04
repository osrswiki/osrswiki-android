package com.omiyawaki.osrswiki.page

import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.page.action.PageActionItem
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.settings.SettingsActivity
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.page.PageTitle as PagePackagePageTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages UI actions for the PageFragment, such as handling clicks on the
 * overflow menu and the bottom action bar.
 */
class PageActionHandler(
    private val fragment: Fragment,
    private val viewModel: PageViewModel,
    private val binding: FragmentPageBinding
) {
    private val context: Context get() = fragment.requireContext()
    val callback: PageActionItem.Callback = PageActionItemCallback()

    fun showPageOverflowMenu(anchorView: View) {
        val popup = PopupMenu(context, anchorView)
        popup.menuInflater.inflate(R.menu.menu_page_overflow, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_page_overflow_save -> {
                    callback.onSaveSelected()
                    true
                }
                R.id.menu_page_overflow_find_in_article -> {
                    callback.onFindInArticleSelected()
                    true
                }
                R.id.menu_page_overflow_appearance -> {
                    callback.onThemeSelected()
                    true
                }
                R.id.menu_page_overflow_contents -> {
                    callback.onContentsSelected()
                    true
                }
                else -> false
            }
        }
        popup.show()
        L.d("Page overflow menu shown.")
    }

    private fun showThemedSnackbar(message: String, length: Int = Snackbar.LENGTH_LONG) {
        val snackbar = Snackbar.make(binding.root, message, length)
            .setAnchorView(binding.pageActionsTabLayout)

        val typedValue = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        val surfaceColor = typedValue.data
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val onSurfaceColor = typedValue.data

        snackbar.setBackgroundTint(surfaceColor).setTextColor(onSurfaceColor).show()
    }

    private inner class PageActionItemCallback : PageActionItem.Callback {
        override fun onSaveSelected() {
            val titleForDaoLookup = viewModel.uiState.plainTextTitle?.takeIf { it.isNotBlank() && viewModel.uiState.htmlContent != null }
                ?: fragment.arguments?.getString(PageFragment.ARG_PAGE_TITLE)?.takeIf { it.isNotBlank() }

            if (titleForDaoLookup.isNullOrBlank()) {
                showThemedSnackbar(context.getString(R.string.cannot_save_page_no_title), Snackbar.LENGTH_SHORT)
                return
            }

            val pagePackagePageTitle = PagePackagePageTitle(
                namespace = Namespace.MAIN,
                text = titleForDaoLookup,
                wikiSite = WikiSite.OSRS_WIKI,
                displayText = viewModel.uiState.title ?: titleForDaoLookup,
                thumbUrl = viewModel.uiState.imageUrl
            )
            val titleForSnackbar = pagePackagePageTitle.displayText

            fragment.viewLifecycleOwner.lifecycleScope.launch {
                var message: String = context.getString(R.string.error_generic_save_unsave)
                try {
                    val readingListDao = AppDatabase.instance.readingListDao()
                    val localReadingListPageDao = AppDatabase.instance.readingListPageDao()
                    val defaultList = withContext(Dispatchers.IO) { readingListDao.getDefaultList() ?: readingListDao.createDefaultListIfNotExist() }

                    val existingEntry = withContext(Dispatchers.IO) {
                        localReadingListPageDao.getPageByListIdAndTitle(
                            pagePackagePageTitle.wikiSite,
                            pagePackagePageTitle.wikiSite.languageCode,
                            pagePackagePageTitle.namespace(),
                            pagePackagePageTitle.prefixedText,
                            defaultList.id,
                            -1 // STATUS_QUEUE_FOR_DELETE, using literal to avoid dependency
                        )
                    }

                    if (existingEntry != null) {
                        if (existingEntry.offline && existingEntry.status == 1L) { // STATUS_SAVED
                            withContext(Dispatchers.IO) { localReadingListPageDao.markPagesForDeletion(defaultList.id, listOf(existingEntry)) }
                            message = "'$titleForSnackbar' offline version will be removed."
                        } else {
                            val downloadWillBeAttempted = Prefs.isDownloadingReadingListArticlesEnabled
                            withContext(Dispatchers.IO) { localReadingListPageDao.markPagesForOffline(listOf(existingEntry), offline = true, forcedSave = false) }
                            message = if (downloadWillBeAttempted) "'$titleForSnackbar' queued for download." else "'$titleForSnackbar' marked for offline availability."
                        }
                    } else {
                        val downloadEnabled = Prefs.isDownloadingReadingListArticlesEnabled
                        val titlesAdded = withContext(Dispatchers.IO) { localReadingListPageDao.addPagesToList(defaultList, listOf(pagePackagePageTitle), downloadEnabled) }
                        if (titlesAdded.isNotEmpty()) {
                            message = if (downloadEnabled) "'$titleForSnackbar' saved and queued for download." else "'$titleForSnackbar' saved to reading list."
                        } else {
                            message = "Page '$titleForSnackbar' could not be saved."
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PageActionHandler", "Error during save/unsave for '$titleForSnackbar'", e)
                    message = context.getString(R.string.error_generic_save_unsave)
                }
                showThemedSnackbar(message)
            }
        }

        override fun onFindInArticleSelected() {
            showThemedSnackbar("Find in page: Not yet implemented.", Snackbar.LENGTH_SHORT)
        }

        override fun onThemeSelected() {
            val intent = Intent(context, SettingsActivity::class.java)
            fragment.startActivity(intent)
        }

        override fun onContentsSelected() {
            (fragment as? PageFragment)?.showContents()
        }
    }
}
