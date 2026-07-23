package com.omiyawaki.osrswiki.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.graphics.Typeface
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentAboutBinding
import com.omiyawaki.osrswiki.util.ExternalUrlLauncher
import com.omiyawaki.osrswiki.util.FontUtil
import com.omiyawaki.osrswiki.util.log.L

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!
    

    companion object {
        fun newInstance() = AboutFragment()
        const val TAG = "AboutFragment"
        
        private const val OSRS_URL = "https://oldschool.runescape.com/"
        private const val OSRS_WIKI_URL = "https://oldschool.runescape.wiki/"
        private const val OPENRS2_URL = "https://archive.openrs2.org/"
        private const val MAPLIBRE_URL = "https://maplibre.org/"
        private const val WIKIPEDIA_URL = "https://www.wikipedia.org/"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        L.d("AboutFragment: onCreateView called.")
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("AboutFragment: onViewCreated called.")
        
        setupAppInfo()
        setupOSRSButton()
        setupWikiButton()
        setupOpenRS2Button()
        setupMapLibreButton()
        setupWikipediaButton()
        setupPrivacyButton()
        setupFonts()
        
        // Debug font loading
        debugFontLoading()
    }
    
    private fun setupAppInfo() {
        val versionText = getString(
            R.string.about_app_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
        binding.appVersionText.text = versionText
    }
    
    private fun setupOSRSButton() {
        binding.osrsButton.setOnClickListener {
            L.d("AboutFragment: OSRS button clicked")
            openExternalUrl(OSRS_URL)
        }
    }
    
    private fun setupWikiButton() {
        binding.wikiButton.setOnClickListener {
            L.d("AboutFragment: Wiki button clicked")
            openExternalUrl(OSRS_WIKI_URL)
        }
    }
    
    private fun setupOpenRS2Button() {
        binding.openrs2Button.setOnClickListener {
            L.d("AboutFragment: OpenRS2 button clicked")
            openExternalUrl(OPENRS2_URL)
        }
    }
    
    private fun setupMapLibreButton() {
        binding.maplibreButton.setOnClickListener {
            L.d("AboutFragment: MapLibre button clicked")
            openExternalUrl(MAPLIBRE_URL)
        }
    }
    
    private fun setupWikipediaButton() {
        binding.wikipediaButton.setOnClickListener {
            L.d("AboutFragment: Wikipedia button clicked")
            openExternalUrl(WIKIPEDIA_URL)
        }
    }

    private fun openExternalUrl(url: String) {
        val opened = ExternalUrlLauncher.open(
            context = requireContext(),
            url = url,
            failureMessage = getString(R.string.about_external_link_unavailable),
            startActivity = { intent -> startActivity(intent) }
        )
        if (!opened) {
            L.e("AboutFragment: Error opening external URL: $url")
        }
    }
    
    private fun setupPrivacyButton() {
        binding.privacyButton.setOnClickListener {
            L.d("AboutFragment: Privacy Policy button clicked")
            try {
                val intent = PrivacyPolicyActivity.newIntent(requireContext())
                startActivity(intent)
            } catch (e: Exception) {
                L.e("AboutFragment: Error opening Privacy Policy screen", e)
            }
        }
    }
    
    private fun setupFonts() {
        L.d("AboutFragment: Setting up fonts...")
        
        // Apply fonts using utility to bypass Huawei font system
        FontUtil.applyAlegreyaDisplay(binding.aboutTitle)
        FontUtil.applyAlegreyaHeadline(binding.creditsTitle)
        FontUtil.applyAlegreyaTitle(binding.jagexTitle)
        FontUtil.applyAlegreyaTitle(binding.wikiTitle)
        FontUtil.applyAlegreyaTitle(binding.openrs2Title)
        FontUtil.applyAlegreyaTitle(binding.maplibreTitle)
        FontUtil.applyAlegreyaTitle(binding.wikipediaTitle)
        FontUtil.applyAlegreyaTitle(binding.privacyTitle)
        
        L.d("AboutFragment: Fonts applied to all TextViews and buttons")
    }
    
    private fun debugFontLoading() {
        L.d("AboutFragment: ==== FONT DEBUG START ====")
        
        // Check if fonts are actually applied after using FontUtil
        val aboutTitle = binding.aboutTitle
        val typeface = aboutTitle.typeface
        L.d("AboutFragment: Title typeface after FontUtil: $typeface")
        L.d("AboutFragment: Title typeface style: ${typeface?.style}")
        L.d("AboutFragment: Title typeface isBold: ${typeface?.isBold}")
        
        L.d("AboutFragment: ==== FONT DEBUG END ====")
    }

    override fun onDestroyView() {
        L.d("AboutFragment: onDestroyView called.")
        
        
        _binding = null
        super.onDestroyView()
    }
}
