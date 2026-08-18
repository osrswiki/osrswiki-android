package com.omiyawaki.osrswiki.about

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentPrivacyPolicyBinding
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
    }
    
    private fun setupPrivacyContent() {
        binding.privacyContentContainer.removeAllViews()
        PrivacyPolicyContentFormatter.sections(getString(R.string.privacy_policy_content))
            .forEach { section ->
                binding.privacyContentContainer.addView(createSectionTextView(section))
            }
    }

    private fun createSectionTextView(section: PrivacyPolicyContentFormatter.Section): TextView {
        return TextView(requireContext()).apply {
            text = section.text
            setTextAppearance(
                if (section.isHeading) {
                    R.style.AppTextAppearance_SettingsSubheading
                } else {
                    R.style.AppTextAppearance_SettingsBody
                }
            )
            if (section.isHeading) {
                ViewCompat.setAccessibilityHeading(this, true)
            } else {
                setLineSpacing(0f, 1.4f)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = if (section.isHeading) dp(8) else dp(16)
            }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
    
    override fun onDestroyView() {
        L.d("PrivacyPolicyFragment: onDestroyView called.")
        _binding = null
        super.onDestroyView()
    }
}
