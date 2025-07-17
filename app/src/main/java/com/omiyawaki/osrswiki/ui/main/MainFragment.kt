package com.omiyawaki.osrswiki.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMainBinding
import com.omiyawaki.osrswiki.news.ui.NewsFragment
import com.omiyawaki.osrswiki.util.log.L
// Removed SavedPagesFragment and MapFragment imports as they are no longer directly managed here.

/**
 * The main fragment that now acts as a simple container for the "main" section's content,
 * which appears to be the NewsFragment. The primary navigation is now handled by MainActivity.
 */
class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance() = MainFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        L.d("MainFragment: onCreateView called.")
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("MainFragment: onViewCreated called.")

        // The BottomNavigationView is gone. This fragment now just hosts its own content.
        // For now, it appears the main content is the NewsFragment.
        if (savedInstanceState == null) {
            L.d("MainFragment: savedInstanceState is null, adding NewsFragment as content.")
            childFragmentManager.beginTransaction()
                .replace(R.id.main_fragment_internal_container, NewsFragment(), null)
                .commit()
        }
    }

    override fun onDestroyView() {
        L.d("MainFragment: onDestroyView called.")
        _binding = null
        super.onDestroyView()
    }
}
