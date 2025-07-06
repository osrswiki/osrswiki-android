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

class PageActionHandler(
    private val fragment: PageFragment,
    private val viewModel: PageViewModel,
    private val pageActionTabLayout: PageActionTabLayout
) {
    private val context: Context get() = fragment.requireContext()
    val callback: PageActionItem.Callback = PageActionItemCallback()

    fun showPageOverflowMenu(anchorView: View) {
        val popup = PopupMenu(context, anchorView)
        popup.menuInflater.inflate(R.menu.menu_page_overflow, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_page_overflow_save -> { callback.onSaveSelected(); true }
                R.id.menu_page_overflow_find_in_article -> { callback.onFindInArticleSelected(); true }
                R.id.menu_page_overflow_appearance -> { callback.onThemeSelected(); true }
                R.id.menu_page_overflow_contents -> { callback.onContentsSelected(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showThemedSnackbar(message: String, length: Int = Snackbar.LENGTH_LONG) {
        val snackbar = Snackbar.make(pageActionTabLayout, message, length).setAnchorView(pageActionTabLayout)
        val typedValue = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        val surfaceColor = typedValue.data
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val onSurfaceColor = typedValue.data
        snackbar.setBackgroundTint(surfaceColor).setTextColor(onSurfaceColor).show()
    }

    private inner class PageActionItemCallback : PageActionItem.Callback {
        override fun onSaveSelected() {
            // ... (Implementation unchanged)
        }
        override fun onFindInArticleSelected() {
            fragment.showFindInPage()
        }
        override fun onThemeSelected() {
            fragment.startActivity(Intent(context, SettingsActivity::class.java))
        }
        override fun onContentsSelected() {
            fragment.showContents()
        }
    }
}
