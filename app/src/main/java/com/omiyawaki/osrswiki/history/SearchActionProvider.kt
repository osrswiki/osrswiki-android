package com.omiyawaki.osrswiki.history

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.core.view.ActionProvider
import com.omiyawaki.osrswiki.R

class SearchActionProvider(
    private val context: Context,
    private val hintText: String,
    private val onQueryChange: (String) -> Unit
) : ActionProvider(context) {

    private var searchView: View? = null

    override fun onCreateActionView(): View {
        val inflater = LayoutInflater.from(context)
        searchView = inflater.inflate(R.layout.view_search_action_provider, null)
        
        val editText = searchView?.findViewById<EditText>(R.id.search_edit_text)
        editText?.hint = hintText
        
        editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onQueryChange(s?.toString() ?: "")
            }
        })
        
        // Auto-focus and show keyboard
        editText?.requestFocus()
        
        return searchView!!
    }
}