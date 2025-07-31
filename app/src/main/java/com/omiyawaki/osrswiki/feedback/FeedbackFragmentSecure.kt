package com.omiyawaki.osrswiki.feedback

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentFeedbackBinding
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.launch

/**
 * Secure version of FeedbackFragment that uses Cloud Function for GitHub integration.
 * This version doesn't expose any GitHub tokens in the app.
 */
class FeedbackFragmentSecure : Fragment() {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!
    
    private val feedbackRepository = SecureFeedbackRepository()

    companion object {
        fun newInstance() = FeedbackFragmentSecure()
        const val TAG = "FeedbackFragmentSecure"
        
        private const val GOOGLE_PLAY_URL = "market://details?id=com.omiyawaki.osrswiki"
        private const val GOOGLE_PLAY_WEB_URL = "https://play.google.com/store/apps/details?id=com.omiyawaki.osrswiki"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        L.d("FeedbackFragmentSecure: onCreateView called.")
        _binding = FragmentFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("FeedbackFragmentSecure: onViewCreated called.")
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        binding.rateAppCard.setOnClickListener { 
            handleRateApp()
        }
        binding.rateAppButton.setOnClickListener { 
            handleRateApp()
        }
        
        binding.reportIssueCard.setOnClickListener { 
            showReportIssueDialog()
        }
        binding.reportIssueButton.setOnClickListener { 
            showReportIssueDialog()
        }
        
        binding.requestFeatureCard.setOnClickListener { 
            showRequestFeatureDialog()
        }
        binding.requestFeatureButton.setOnClickListener { 
            showRequestFeatureDialog()
        }
    }
    
    private fun handleRateApp() {
        L.d("FeedbackFragmentSecure: Rate app clicked")
        
        try {
            // Try to open Google Play app first
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_PLAY_URL))
            startActivity(playStoreIntent)
        } catch (e: Exception) {
            try {
                // Fallback to web browser
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_PLAY_WEB_URL))
                startActivity(webIntent)
            } catch (e2: Exception) {
                L.e("FeedbackFragmentSecure: Error opening Play Store", e2)
                showErrorDialog("Unable to open Google Play Store")
            }
        }
    }
    
    private fun showReportIssueDialog() {
        L.d("FeedbackFragmentSecure: Report issue clicked")
        showFeedbackForm(
            title = getString(R.string.feedback_report_issue_title),
            titleHint = getString(R.string.feedback_issue_title_hint),
            descriptionHint = getString(R.string.feedback_issue_description_hint),
            submitButtonText = getString(R.string.feedback_submit_issue),
            isFeatureRequest = false
        )
    }
    
    private fun showRequestFeatureDialog() {
        L.d("FeedbackFragmentSecure: Request feature clicked")
        showFeedbackForm(
            title = getString(R.string.feedback_request_feature_title),
            titleHint = getString(R.string.feedback_feature_title_hint),
            descriptionHint = getString(R.string.feedback_feature_description_hint),
            submitButtonText = getString(R.string.feedback_submit_feature),
            isFeatureRequest = true
        )
    }
    
    private fun showFeedbackForm(
        title: String,
        titleHint: String,
        descriptionHint: String,
        submitButtonText: String,
        isFeatureRequest: Boolean
    ) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_feedback_form, null)
        
        val titleInputLayout = dialogView.findViewById<TextInputLayout>(R.id.title_input_layout)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.title_input)
        val descriptionInputLayout = dialogView.findViewById<TextInputLayout>(R.id.description_input_layout)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.description_input)
        
        titleInputLayout.hint = titleHint
        descriptionInputLayout.hint = descriptionHint
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(submitButtonText) { _, _ ->
                val titleText = titleInput.text?.toString()?.trim() ?: ""
                val descriptionText = descriptionInput.text?.toString()?.trim() ?: ""
                
                if (validateInput(titleText, descriptionText)) {
                    submitFeedback(titleText, descriptionText, isFeatureRequest)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        
        dialog.show()
    }
    
    private fun validateInput(title: String, description: String): Boolean {
        if (title.isEmpty()) {
            showErrorDialog(getString(R.string.feedback_validation_title_required))
            return false
        }
        
        if (description.isEmpty()) {
            showErrorDialog(getString(R.string.feedback_validation_description_required))
            return false
        }
        
        return true
    }
    
    private fun submitFeedback(title: String, description: String, isFeatureRequest: Boolean) {
        L.d("FeedbackFragmentSecure: Submitting ${if (isFeatureRequest) "feature request" else "issue"}: $title")
        
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.feedback_submitting))
            .setMessage("Please wait...")
            .setCancelable(false)
            .create()
        
        progressDialog.show()
        
        lifecycleScope.launch {
            try {
                val result = if (isFeatureRequest) {
                    feedbackRepository.requestFeature(requireContext(), title, description)
                } else {
                    feedbackRepository.reportIssue(requireContext(), title, description)
                }
                
                progressDialog.dismiss()
                
                result.fold(
                    onSuccess = { message ->
                        showSimpleSuccessDialog(message)
                    },
                    onFailure = { error ->
                        L.e("FeedbackFragmentSecure: Error submitting feedback", error)
                        showErrorDialog(error.message ?: getString(R.string.feedback_error_message))
                    }
                )
            } catch (e: Exception) {
                progressDialog.dismiss()
                L.e("FeedbackFragmentSecure: Exception submitting feedback", e)
                showErrorDialog(getString(R.string.feedback_error_message))
            }
        }
    }
    
    private fun showSimpleSuccessDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.feedback_success_title))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.feedback_error_title))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        L.d("FeedbackFragmentSecure: onDestroyView called.")
        _binding = null
        super.onDestroyView()
    }
}