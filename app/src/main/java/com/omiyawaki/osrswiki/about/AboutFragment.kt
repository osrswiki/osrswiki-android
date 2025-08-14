package com.omiyawaki.osrswiki.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.graphics.Typeface
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentAboutBinding
import com.omiyawaki.osrswiki.util.FontUtil
import com.omiyawaki.osrswiki.util.log.L

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!
    

    companion object {
        fun newInstance() = AboutFragment()
        const val TAG = "AboutFragment"
        
        private const val OSRS_WIKI_URL = "https://oldschool.runescape.wiki/"
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
        setupWikiButton()
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
    
    private fun setupWikiButton() {
        binding.wikiButton.setOnClickListener {
            L.d("AboutFragment: Wiki button clicked")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(OSRS_WIKI_URL))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                L.e("AboutFragment: Error opening OSRS Wiki URL", e)
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
        FontUtil.applyAlegreyaTitle(binding.wikipediaTitle)
        
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