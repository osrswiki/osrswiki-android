package com.omiyawaki.osrswiki.feedback

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ActivityRequestFeatureBinding
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.launch

/**
 * Full-screen activity for requesting new features.
 * Provides a comprehensive form for users to suggest improvements and new functionality.
 */
class RequestFeatureActivity : BaseActivity() {

    private lateinit var binding: ActivityRequestFeatureBinding
    private val feedbackRepository = SecureFeedbackRepository()
    private var submitMenuItem: MenuItem? = null

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, RequestFeatureActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestFeatureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupFormValidation()
        loadDeviceInfo()
        setupSubmitButton()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_submit, menu)
        submitMenuItem = menu.findItem(R.id.action_submit)
        updateSubmitButtonState()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_submit -> {
                submitFeatureRequest()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupFormValidation() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSubmitButtonState()
            }
        }

        binding.titleInput.addTextChangedListener(textWatcher)
        binding.descriptionInput.addTextChangedListener(textWatcher)
        // Use case is optional, so it doesn't affect validation
    }

    private fun updateSubmitButtonState() {
        val titleText = binding.titleInput.text?.toString()?.trim() ?: ""
        val descriptionText = binding.descriptionInput.text?.toString()?.trim() ?: ""
        
        val isValid = titleText.isNotEmpty() && descriptionText.isNotEmpty()
        
        binding.submitButton.isEnabled = isValid
        submitMenuItem?.isEnabled = isValid
        
        // Update button appearance
        binding.submitButton.alpha = if (isValid) 1.0f else 0.6f
    }

    private fun setupSubmitButton() {
        binding.submitButton.setOnClickListener {
            submitFeatureRequest()
        }
    }

    private fun loadDeviceInfo() {
        val deviceInfo = buildString {
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Architecture: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"}")
        }
        
        binding.deviceInfoText.text = deviceInfo
        L.d("RequestFeatureActivity: Device info loaded")
    }

    private fun submitFeatureRequest() {
        val title = binding.titleInput.text?.toString()?.trim() ?: ""
        val description = binding.descriptionInput.text?.toString()?.trim() ?: ""
        val useCase = binding.useCaseInput.text?.toString()?.trim() ?: ""

        if (!validateInput(title, description)) {
            return
        }

        // Combine description and use case if provided
        val fullDescription = if (useCase.isNotEmpty()) {
            "$description\n\n**Use Case:**\n$useCase"
        } else {
            description
        }

        L.d("RequestFeatureActivity: Submitting feature request: $title")

        // Disable form during submission
        setFormEnabled(false)
        binding.submitButton.text = getString(R.string.feedback_submitting)

        lifecycleScope.launch {
            try {
                val result = feedbackRepository.requestFeature(this@RequestFeatureActivity, title, fullDescription)
                
                result.fold(
                    onSuccess = { message ->
                        showSuccessDialog(message)
                    },
                    onFailure = { error ->
                        L.e("RequestFeatureActivity: Error submitting feature request", error)
                        showErrorDialog(error.message ?: getString(R.string.feedback_error_message))
                        setFormEnabled(true)
                        binding.submitButton.text = getString(R.string.feedback_submit_feature)
                    }
                )
            } catch (e: Exception) {
                L.e("RequestFeatureActivity: Exception submitting feature request", e)
                showErrorDialog(getString(R.string.feedback_error_message))
                setFormEnabled(true)
                binding.submitButton.text = getString(R.string.feedback_submit_feature)
            }
        }
    }

    private fun validateInput(title: String, description: String): Boolean {
        when {
            title.isEmpty() -> {
                binding.titleInputLayout.error = getString(R.string.feedback_validation_title_required)
                return false
            }
            title.length > 100 -> {
                binding.titleInputLayout.error = "Title is too long (max 100 characters)"
                return false
            }
            description.isEmpty() -> {
                binding.titleInputLayout.error = null
                binding.descriptionInputLayout.error = getString(R.string.feedback_validation_description_required)
                return false
            }
            description.length > 2000 -> {
                binding.titleInputLayout.error = null
                binding.descriptionInputLayout.error = "Description is too long (max 2000 characters)"
                return false
            }
            else -> {
                binding.titleInputLayout.error = null
                binding.descriptionInputLayout.error = null
                binding.useCaseInputLayout.error = null
                return true
            }
        }
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.titleInput.isEnabled = enabled
        binding.descriptionInput.isEnabled = enabled
        binding.useCaseInput.isEnabled = enabled
        binding.submitButton.isEnabled = enabled && validateFormQuick()
        submitMenuItem?.isEnabled = enabled && validateFormQuick()
        
        // Update visual state
        val alpha = if (enabled) 1.0f else 0.6f
        binding.titleInputLayout.alpha = alpha
        binding.descriptionInputLayout.alpha = alpha
        binding.useCaseInputLayout.alpha = alpha
    }

    private fun validateFormQuick(): Boolean {
        val title = binding.titleInput.text?.toString()?.trim() ?: ""
        val description = binding.descriptionInput.text?.toString()?.trim() ?: ""
        return title.isNotEmpty() && description.isNotEmpty()
    }

    private fun showSuccessDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.feedback_success_title))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                finish() // Close the activity on success
            }
            .setCancelable(false)
            .show()
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.feedback_error_title))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}