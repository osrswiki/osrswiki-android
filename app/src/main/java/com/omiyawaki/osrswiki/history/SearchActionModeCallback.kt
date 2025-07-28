package com.omiyawaki.osrswiki.history

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.core.view.MenuItemCompat
import com.omiyawaki.osrswiki.R

class SearchActionModeCallback(
    private val context: Context,
    private val onQueryChange: (String) -> Unit,
    private val onActionModeFinish: () -> Unit
) : ActionMode.Callback {

    companion object {
        const val ACTION_MODE_TAG = "searchActionMode"
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.tag = ACTION_MODE_TAG
        val menuItem = menu.add(context.getString(R.string.search_hint))
        
        val searchActionProvider = SearchActionProvider(
            context, 
            context.getString(R.string.search_hint),
            onQueryChange = { query -> onQueryChange(query) },
            voiceRecognitionManager = null,
            voiceSearchLauncher = null
        )
        
        MenuItemCompat.setActionProvider(menuItem, searchActionProvider)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode) {
        onActionModeFinish()
    }
}