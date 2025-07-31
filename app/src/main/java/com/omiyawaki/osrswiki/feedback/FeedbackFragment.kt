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

class FeedbackFragment : Fragment() {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!
    
    // TODO: Switch to SecureFeedbackRepository after Cloud Function is deployed
    // private val feedbackRepository = SecureFeedbackRepository()
    private val feedbackRepository = FeedbackRepository()

    companion object {
        fun newInstance() = FeedbackFragment()
        const val TAG = "FeedbackFragment"
        
        private const val GOOGLE_PLAY_URL = "market://details?id=com.omiyawaki.osrswiki"
        private const val GOOGLE_PLAY_WEB_URL = "https://play.google.com/store/apps/details?id=com.omiyawaki.osrswiki"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        L.d("FeedbackFragment: onCreateView called.")
        _binding = FragmentFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("FeedbackFragment: onViewCreated called.")
        
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
        L.d("FeedbackFragment: Rate app clicked")
        
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
                L.e("FeedbackFragment: Error opening Play Store", e2)
                showErrorDialog("Unable to open Google Play Store")
            }
        }
    }
    
    private fun showReportIssueDialog() {
        L.d("FeedbackFragment: Report issue clicked")
        showFeedbackForm(
            title = getString(R.string.feedback_report_issue_title),
            titleHint = getString(R.string.feedback_issue_title_hint),
            descriptionHint = getString(R.string.feedback_issue_description_hint),
            submitButtonText = getString(R.string.feedback_submit_issue),
            isFeatureRequest = false
        )
    }
    
    private fun showRequestFeatureDialog() {
        L.d("FeedbackFragment: Request feature clicked")
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
        L.d("FeedbackFragment: Submitting ${if (isFeatureRequest) "feature request" else "issue"}: $title")
        
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
                    onSuccess = { issueResponse ->
                        showSuccessDialog(issueResponse.htmlUrl)
                    },
                    onFailure = { error ->
                        L.e("FeedbackFragment: Error submitting feedback", error)
                        showErrorDialogWithFallback(error.message ?: getString(R.string.feedback_error_message), title, description, isFeatureRequest)
                    }
                )
            } catch (e: Exception) {
                progressDialog.dismiss()
                L.e("FeedbackFragment: Exception submitting feedback", e)
                showErrorDialog(getString(R.string.feedback_error_message))
            }
        }
    }
    
    private fun showSuccessDialog(issueUrl: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.feedback_success_title))
            .setMessage(getString(R.string.feedback_success_message))
            .setPositiveButton(getString(R.string.feedback_success_view_button)) { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl))
                    startActivity(intent)
                } catch (e: Exception) {
                    L.e("FeedbackFragment: Error opening issue URL", e)
                    showErrorDialog("Unable to open issue link")
                }
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }
    
    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.feedback_error_title))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    private fun showErrorDialogWithFallback(message: String, title: String, description: String, isFeatureRequest: Boolean) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.feedback_error_title))
            .setMessage("$message\n\nWould you like to open GitHub in your browser to submit this manually?")
            .setPositiveButton("Open GitHub") { _, _ ->
                openGitHubIssueInBrowser(title, description, isFeatureRequest)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun openGitHubIssueInBrowser(title: String, description: String, isFeatureRequest: Boolean) {
        val deviceInfo = buildString {
            appendLine("**Device Information:**")
            appendLine("- App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("- Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("- Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("- Device Brand: ${Build.BRAND}")
            appendLine("- Device Product: ${Build.PRODUCT}")
        }
        
        val fullBody = buildString {
            appendLine(description)
            appendLine()
            appendLine("---")
            appendLine(deviceInfo)
        }
        
        val label = if (isFeatureRequest) "enhancement" else "bug"
        val githubUrl = "https://github.com/omiyawaki/osrswiki-android/issues/new?" +
                "title=${Uri.encode(title)}&" +
                "body=${Uri.encode(fullBody)}&" +
                "labels=$label"
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
            startActivity(intent)
        } catch (e: Exception) {
            L.e("FeedbackFragment: Error opening GitHub URL", e)
            showErrorDialog("Unable to open browser. Please visit: https://github.com/omiyawaki/osrswiki-android/issues")
        }
    }

    override fun onDestroyView() {
        L.d("FeedbackFragment: onDestroyView called.")
        _binding = null
        super.onDestroyView()
    }
}