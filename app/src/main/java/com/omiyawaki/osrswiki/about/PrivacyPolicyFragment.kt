package com.omiyawaki.osrswiki.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentPrivacyPolicyBinding
import com.omiyawaki.osrswiki.util.FontUtil
import com.omiyawaki.osrswiki.util.log.L

class PrivacyPolicyFragment : Fragment() {

    private var _binding: FragmentPrivacyPolicyBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance() = PrivacyPolicyFragment()
        const val TAG = "PrivacyPolicyFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        L.d("PrivacyPolicyFragment: onCreateView called.")
        _binding = FragmentPrivacyPolicyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("PrivacyPolicyFragment: onViewCreated called.")
        
        setupPrivacyContent()
        setupFonts()
    }
    
    private fun setupPrivacyContent() {
        // The content is already set in the layout XML using string resources
        // This method can be used for any dynamic content if needed
    }
    
    private fun setupFonts() {
        L.d("PrivacyPolicyFragment: Setting up fonts...")
        
        // Apply fonts to headers
        FontUtil.applyAlegreyaDisplay(binding.privacyTitle)
        FontUtil.applyAlegreyaHeadline(binding.lastUpdated)
        
        L.d("PrivacyPolicyFragment: Fonts applied")
    }

    override fun onDestroyView() {
        L.d("PrivacyPolicyFragment: onDestroyView called.")
        _binding = null
        super.onDestroyView()
    }
}