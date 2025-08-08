package com.omiyawaki.osrswiki.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.databinding.FragmentCustomSettingsBinding
import com.omiyawaki.osrswiki.theme.ThemeAware
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.launch

/**
 * Custom settings fragment replacing PreferenceFragmentCompat.
 * Implements modern Material Design with proper text styling.
 */
class CustomAppearanceSettingsFragment : Fragment(), ThemeAware {

    private var _binding: FragmentCustomSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SettingsViewModel
    private lateinit var adapter: SettingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViewModel()
        setupRecyclerView()
        observeViewModel()
        
        L.d("CustomAppearanceSettingsFragment: Fragment created with proper Material3 styling")
    }

    private fun setupViewModel() {
        val sharedPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        val repository = SettingsRepository(sharedPrefs)
        
        viewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(repository)
        )[SettingsViewModel::class.java]
    }

    private fun setupRecyclerView() {
        adapter = SettingsAdapter(
            onSwitchToggle = { key, isChecked ->
                viewModel.onSwitchSettingToggled(key, isChecked)
                L.d("CustomAppearanceSettingsFragment: Switch toggled - $key: $isChecked")
            },
            onListClick = { key ->
                viewModel.onListSettingClicked(key)
                L.d("CustomAppearanceSettingsFragment: List setting clicked - $key")
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CustomAppearanceSettingsFragment.adapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settingsList.collect { items ->
                        adapter.submitList(items)
                        L.d("CustomAppearanceSettingsFragment: Settings list updated with ${items.size} items")
                    }
                }
                
                launch {
                    viewModel.showThemeDialog.collect { showDialog ->
                        if (showDialog) {
                            showThemeSelectionDialog()
                        }
                    }
                }
            }
        }
    }

    private fun showThemeSelectionDialog() {
        val options = viewModel.getThemeOptions()
        val currentTheme = viewModel.getCurrentTheme()
        val currentIndex = options.indexOfFirst { it.first == currentTheme }
        
        val optionTitles = options.map { it.second }.toTypedArray()
        
        AlertDialog.Builder(requireContext())
            .setTitle("App theme")
            .setSingleChoiceItems(optionTitles, currentIndex) { dialog, which ->
                val selectedTheme = options[which].first
                viewModel.onThemeSelected(selectedTheme)
                
                // Notify about theme change
                notifyGlobalThemeChange()
                
                dialog.dismiss()
                L.d("CustomAppearanceSettingsFragment: Theme selected - $selectedTheme")
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                viewModel.onDialogDismissed()
                dialog.dismiss()
            }
            .setOnDismissListener {
                viewModel.onDialogDismissed()
            }
            .show()
    }

    private fun notifyGlobalThemeChange() {
        try {
            // Send local broadcast to notify other activities about theme change
            val intent = Intent(ACTION_THEME_CHANGED)
            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
            L.d("CustomAppearanceSettingsFragment: Global theme change broadcast sent")
        } catch (e: Exception) {
            L.e("CustomAppearanceSettingsFragment: Error sending theme change broadcast: ${e.message}")
        }
    }

    override fun onThemeChanged() {
        if (!isAdded || view == null) {
            return
        }
        
        L.d("CustomAppearanceSettingsFragment: onThemeChanged called - refreshing UI")
        
        // The RecyclerView and its items should automatically pick up the new theme
        // since they use theme attributes. Just notify the adapter to refresh.
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CustomAppearanceSettingsFragment"
        const val ACTION_THEME_CHANGED = "com.omiyawaki.osrswiki.THEME_CHANGED"
        
        fun newInstance(): CustomAppearanceSettingsFragment {
            return CustomAppearanceSettingsFragment()
        }
    }
}