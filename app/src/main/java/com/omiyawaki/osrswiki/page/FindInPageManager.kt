package com.omiyawaki.osrswiki.page

import android.content.Context
import android.graphics.Color
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import com.omiyawaki.osrswiki.R

class FindInPageManager(
    private val context: Context,
    private val webView: WebView,
    private val onActionModeClosed: () -> Unit
) : ActionMode.Callback, WebView.FindListener {

    private var actionMode: ActionMode? = null
    private var findInPageCountView: TextView? = null

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val customView = LayoutInflater.from(context).inflate(R.layout.find_in_page_view, null)
        mode.customView = customView
        this.actionMode = mode

        val searchView = customView.findViewById<SearchView>(R.id.find_in_page_input)
        findInPageCountView = customView.findViewById(R.id.find_in_page_count)

        // --- THE CRITICAL FIX FROM THE WIKIPEDIA APP ---
        // Find the internal 'search_plate' view within the SearchView and make its background transparent.
        // This is what removes the unwanted underline/border.
        val searchEditPlate = searchView.findViewById<View>(androidx.appcompat.R.id.search_plate)
        searchEditPlate?.setBackgroundColor(Color.TRANSPARENT)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (!newText.isNullOrEmpty()) {
                    webView.findAllAsync(newText)
                } else {
                    webView.clearMatches()
                    findInPageCountView?.text = ""
                }
                return true
            }
        })
        
        searchView.requestFocus()
        showKeyboard(searchView)

        customView.findViewById<View>(R.id.find_in_page_next).setOnClickListener {
            webView.findNext(true)
        }
        customView.findViewById<View>(R.id.find_in_page_prev).setOnClickListener {
            webView.findNext(false)
        }
        
        webView.setFindListener(this)

        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        // This method is not needed; the framework handles the close action.
        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        this.actionMode = null
        webView.clearMatches()
        webView.setFindListener(null)
        onActionModeClosed()
    }

    override fun onFindResultReceived(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean) {
        if (isDoneCounting) {
            if (numberOfMatches > 0) {
                findInPageCountView?.text = context.getString(R.string.find_in_page_result, activeMatchOrdinal + 1, numberOfMatches)
            } else {
                findInPageCountView?.text = "0/0"
            }
        }
    }

    private fun showKeyboard(view: View?) {
        view?.let {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
