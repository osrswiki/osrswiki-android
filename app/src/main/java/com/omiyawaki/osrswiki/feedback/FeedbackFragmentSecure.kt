package com.omiyawaki.osrswiki.feedback

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentFeedbackBinding
import com.omiyawaki.osrswiki.util.log.L

/**
 * Secure version of FeedbackFragment that uses Cloud Function for GitHub integration.
 * This version doesn't expose any GitHub tokens in the app.
 */
class FeedbackFragmentSecure : Fragment() {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

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
                // Silently fail - user can manually navigate to Play Store if needed
            }
        }
    }
    
    private fun showReportIssueDialog() {
        L.d("FeedbackFragmentSecure: Report issue clicked - opening ReportIssueActivity")
        val intent = ReportIssueActivity.newIntent(requireContext())
        startActivity(intent)
    }
    
    private fun showRequestFeatureDialog() {
        L.d("FeedbackFragmentSecure: Request feature clicked - opening RequestFeatureActivity")
        val intent = RequestFeatureActivity.newIntent(requireContext())
        startActivity(intent)
    }

    override fun onDestroyView() {
        L.d("FeedbackFragmentSecure: onDestroyView called.")
        _binding = null
        super.onDestroyView()
    }
}