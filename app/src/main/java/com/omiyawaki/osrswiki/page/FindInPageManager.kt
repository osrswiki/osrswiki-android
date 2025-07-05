package com.omiyawaki.osrswiki.page

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import com.omiyawaki.osrswiki.R

class FindInPageManager(
    private val context: Context,
    private val webView: WebView,
    private val onActionModeClosed: () -> Unit
) : ActionMode.Callback, WebView.FindListener {

    private var actionMode: ActionMode? = null
    private var findInPageEditText: EditText? = null
    private var findInPageCountView: TextView? = null

    init {
        // Set this class as the listener for find results from the WebView.
        webView.setFindListener(this)
    }

    /**
     * Called when the action mode is created. This is where the custom
     * view is inflated and listeners are set up.
     */
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val customView = LayoutInflater.from(context).inflate(R.layout.find_in_page_view, null)
        mode.customView = customView
        this.actionMode = mode

        findInPageEditText = customView.findViewById(R.id.find_in_page_edit_text)
        findInPageCountView = customView.findViewById(R.id.find_in_page_count)
        val closeButton = customView.findViewById<View>(R.id.find_in_page_close)

        // The close button now clears the text field.
        closeButton.setOnClickListener {
            findInPageEditText?.setText("")
        }

        // Set up the next and previous button listeners.
        customView.findViewById<View>(R.id.find_in_page_next).setOnClickListener {
            webView.findNext(true)
        }
        customView.findViewById<View>(R.id.find_in_page_prev).setOnClickListener {
            webView.findNext(false)
        }

        // Set up the listener for text changes in the search input field.
        findInPageEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newText = s.toString()
                // The close button is only visible when there is text.
                closeButton.isVisible = newText.isNotEmpty()
                if (newText.isNotEmpty()) {
                    webView.findAllAsync(newText)
                } else {
                    // If the search query is empty, clear all matches.
                    webView.clearMatches()
                    findInPageCountView?.text = ""
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Automatically focus the input field and show the keyboard.
        findInPageEditText?.requestFocus()
        showKeyboard(findInPageEditText)
        // Initially hide the close button.
        closeButton.isVisible = false
        return true
    }

    /**
     * Called each time the action mode is shown. Returning false here
     * means the action mode is created only once.
     */
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return false
    }

    /**
     * Called when a menu item is clicked. Not used since we use a custom view.
     */
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return false
    }

    /**
     * Called when the action mode is closed. This is where we perform cleanup.
     */
    override fun onDestroyActionMode(mode: ActionMode) {
        this.actionMode = null
        webView.clearMatches()
        webView.setFindListener(null)
        hideKeyboard(findInPageEditText)
        // Notify the activity that the action mode has been closed.
        onActionModeClosed()
    }

    /**
     * This is the callback from WebView.FindListener. It is invoked when
     * the WebView has search results.
     */
    override fun onFindResultReceived(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean) {
        if (isDoneCounting) {
            if (numberOfMatches > 0) {
                // Update the count text, e.g., "1/15". Ordinal is 0-based.
                findInPageCountView?.text = context.getString(R.string.find_in_page_result, activeMatchOrdinal + 1, numberOfMatches)
            } else {
                // If there are no matches, show "0/0".
                findInPageCountView?.text = context.getString(R.string.find_in_page_result, 0, 0)
            }
        }
    }

    private fun showKeyboard(view: View?) {
        view?.let {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard(view: View?) {
        view?.let {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
}
