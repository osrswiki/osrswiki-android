package com.omiyawaki.osrswiki.page

import android.content.Intent
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.textview.MaterialTextView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ActivityPageBinding
import com.omiyawaki.osrswiki.settings.AppearanceSettingsActivity

class PageActionBarManager(
    private val binding: ActivityPageBinding
) {
    
    fun setupActionBar(fragment: PageFragment) {
        val saveButton = binding.root.findViewById<MaterialTextView>(R.id.page_action_save)
        saveButton.setOnClickListener {
            // Save functionality will be connected via callback
            saveClickCallback?.invoke()
        }
        
        val findButton = binding.root.findViewById<MaterialTextView>(R.id.page_action_find_in_article)
        findButton.setOnClickListener {
            fragment.showFindInPage()
        }
        
        val themeButton = binding.root.findViewById<MaterialTextView>(R.id.page_action_theme)
        themeButton.setOnClickListener {
            fragment.startActivity(AppearanceSettingsActivity.newIntent(fragment.requireContext()))
        }
        
        val contentsButton = binding.root.findViewById<MaterialTextView>(R.id.page_action_contents)
        contentsButton.setOnClickListener {
            fragment.showContents()
        }
    }
    
    fun updateSaveIcon(isSaved: Boolean) {
        updateSaveIcon(if (isSaved) SaveState.SAVED else SaveState.NOT_SAVED)
    }
    
    fun updateSaveIcon(saveState: SaveState, progress: Int = 0) {
        val saveButton = binding.root.findViewById<MaterialTextView>(R.id.page_action_save)
        
        when (saveState) {
            SaveState.NOT_SAVED -> {
                saveButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_page_action_save_border, 0, 0)
                saveButton.isEnabled = true
                saveButton.text = "Save"
            }
            SaveState.DOWNLOADING -> {
                // Use the progress drawable and update its progress
                val progressDrawable = ContextCompat.getDrawable(binding.root.context, R.drawable.ic_page_action_save_progress)
                if (progressDrawable is LayerDrawable) {
                    val clipDrawable = progressDrawable.getDrawable(1) as? ClipDrawable
                    clipDrawable?.level = (progress * 100).coerceIn(0, 10000) // Map 0-100 to 0-10000
                }
                saveButton.setCompoundDrawablesWithIntrinsicBounds(null, progressDrawable, null, null)
                saveButton.isEnabled = false
                saveButton.text = "Saving... ${progress}%"
            }
            SaveState.SAVED -> {
                saveButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_page_action_save_filled, 0, 0)
                saveButton.isEnabled = true
                saveButton.text = "Saved"
            }
            SaveState.ERROR -> {
                saveButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_error_image, 0, 0)
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