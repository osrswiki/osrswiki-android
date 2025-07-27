package com.omiyawaki.osrswiki.page

import android.content.Intent
import android.view.View
import androidx.core.widget.TextViewCompat
import com.google.android.material.textview.MaterialTextView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ActivityPageBinding
import com.omiyawaki.osrswiki.settings.SettingsActivity

class PageActionBarManager(
    private val binding: ActivityPageBinding
) {
    
    fun setupActionBar(fragment: PageFragment) {
        val saveButton = binding.root.findViewById<View>(R.id.page_action_save)
        saveButton.setOnClickListener {
            // Save functionality will be connected via callback
            saveClickCallback?.invoke()
        }
        
        val findButton = binding.root.findViewById<View>(R.id.page_action_find_in_article)
        findButton.setOnClickListener {
            fragment.showFindInPage()
        }
        
        val themeButton = binding.root.findViewById<View>(R.id.page_action_theme)
        themeButton.setOnClickListener {
            fragment.startActivity(Intent(fragment.requireContext(), SettingsActivity::class.java))
        }
        
        val contentsButton = binding.root.findViewById<View>(R.id.page_action_contents)
        contentsButton.setOnClickListener {
            fragment.showContents()
        }
    }
    
    fun updateSaveIcon(isSaved: Boolean) {
        updateSaveIcon(if (isSaved) SaveState.SAVED else SaveState.NOT_SAVED)
    }
    
    fun updateSaveIcon(saveState: SaveState) {
        val saveButton = binding.root.findViewById<MaterialTextView>(R.id.page_action_save)
        val iconRes = when (saveState) {
            SaveState.NOT_SAVED -> R.drawable.ic_page_action_save_border
            SaveState.DOWNLOADING -> R.drawable.ic_search_24 // Temporary: use search icon as loading indicator
            SaveState.SAVED -> R.drawable.ic_page_action_save_filled
            SaveState.ERROR -> R.drawable.ic_error_image // Use existing error icon
        }
        
        saveButton.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0)
        
        // Update text and enable state based on status
        when (saveState) {
            SaveState.NOT_SAVED -> {
                saveButton.isEnabled = true
                saveButton.text = "Save"
            }
            SaveState.DOWNLOADING -> {
                saveButton.isEnabled = false
                saveButton.text = "Saving..."
            }
            SaveState.SAVED -> {
                saveButton.isEnabled = true
                saveButton.text = "Saved"
            }
            SaveState.ERROR -> {
                saveButton.isEnabled = true
                saveButton.text = "Retry"
            }
        }
        
        // Ensure the icon tint is applied
        val tintColor = saveButton.textColors
        TextViewCompat.setCompoundDrawableTintList(saveButton, tintColor)
    }
    
    enum class SaveState {
        NOT_SAVED,
        DOWNLOADING, 
        SAVED,
        ERROR
    }
    
    // Callback for save button clicks - will be set by PageReadingListManager
    var saveClickCallback: (() -> Unit)? = null
}