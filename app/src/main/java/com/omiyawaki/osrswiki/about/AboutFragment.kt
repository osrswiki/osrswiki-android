package com.omiyawaki.osrswiki.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentAboutBinding
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

    override fun onDestroyView() {
        L.d("AboutFragment: onDestroyView called.")
        _binding = null
        super.onDestroyView()
    }
}