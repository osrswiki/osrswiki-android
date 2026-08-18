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
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import com.omiyawaki.osrswiki.R

class FindInPageManager(
    private val context: Context,
    private val webView: WebView,
    private val onActionModeClosed: () -> Unit
) : ActionMode.Callback, WebView.FindListener {

    private var actionMode: ActionMode? = null
    private var findInPageCountView: TextView? = null
    private var previousButton: View? = null
    private var nextButton: View? = null
    private var keyboardTarget: View? = null

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val customView = LayoutInflater.from(context).inflate(R.layout.find_in_page_view, null)
        mode.customView = customView
        this.actionMode = mode

        val searchView = customView.findViewById<SearchView>(R.id.find_in_page_input)
        findInPageCountView = customView.findViewById(R.id.find_in_page_count)
        previousButton = customView.findViewById(R.id.find_in_page_prev)
        nextButton = customView.findViewById(R.id.find_in_page_next)
        updateNavigationControls(hasMatches = false)

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
                    updateNavigationControls(hasMatches = false)
                    webView.findAllAsync(newText)
                } else {
                    webView.clearMatches()
                    findInPageCountView?.text = ""
                    updateNavigationControls(hasMatches = false)
                }
                return true
            }
        })
        
        val searchInput = searchView.findViewById<EditText>(
            androidx.appcompat.R.id.search_src_text
        )
        keyboardTarget = searchInput
        customView.doOnAttach {
            searchInput.post {
                if (actionMode !== mode) return@post
                searchInput.requestFocus()
                ViewCompat.getWindowInsetsController(customView)
                    ?.show(WindowInsetsCompat.Type.ime())
                showKeyboard(searchInput)
            }
        }

        nextButton?.setOnClickListener {
            webView.findNext(true)
        }
        previousButton?.setOnClickListener {
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
        previousButton = null
        nextButton = null
        hideKeyboard(keyboardTarget)
        keyboardTarget?.clearFocus()
        keyboardTarget = null
        webView.clearMatches()
        webView.setFindListener(null)
        onActionModeClosed()
    }

    override fun onFindResultReceived(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean) {
        if (isDoneCounting) {
            if (numberOfMatches > 0) {
                findInPageCountView?.text = context.getString(R.string.find_in_page_result, activeMatchOrdinal + 1, numberOfMatches)
                updateNavigationControls(hasMatches = true)
            } else {
                findInPageCountView?.text = "0/0"
                updateNavigationControls(hasMatches = false)
            }
        }
    }

    private fun updateNavigationControls(hasMatches: Boolean) {
        FindInPageNavigationControls.apply(
            previousButton = previousButton,
            nextButton = nextButton,
            hasMatches = hasMatches
        )
    }

    private fun showKeyboard(view: View?) {
        view?.let {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard(view: View?) {
        view ?: return
        ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
