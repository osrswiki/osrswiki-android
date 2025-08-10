package com.omiyawaki.osrswiki.settings

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
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
    private var previousConfiguration: Configuration? = null

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
        preWarmThemePreviewCache()
        preWarmTablePreviewCache()
        
        // Store initial configuration for fold/unfold detection
        previousConfiguration = Configuration(resources.configuration)
        
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
            },
            onThemeSelected = { themeKey ->
                viewModel.onThemeSelected(themeKey)
                notifyGlobalThemeChange()
                L.d("CustomAppearanceSettingsFragment: Theme selected inline - $themeKey")
            },
            onTablePreviewSelected = { collapseTablesEnabled ->
                viewModel.onTablePreviewSelected(collapseTablesEnabled)
                L.d("CustomAppearanceSettingsFragment: Table preview selected - collapseTablesEnabled: $collapseTablesEnabled")
            },
            lifecycleScope = viewLifecycleOwner.lifecycleScope
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
        
        MaterialAlertDialogBuilder(requireContext())
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

    internal fun notifyGlobalThemeChange() {
        try {
            // Send local broadcast to notify other activities about theme change
            val intent = Intent(ACTION_THEME_CHANGED)
            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
            L.d("CustomAppearanceSettingsFragment: Global theme change broadcast sent")
        } catch (e: Exception) {
            L.e("CustomAppearanceSettingsFragment: Error sending theme change broadcast: ${e.message}")
        }
    }

    /**
     * Pre-warms the theme preview cache by generating all theme screenshots in the background.
     * This ensures smooth scrolling when the theme selection UI is displayed.
     */
    private fun preWarmThemePreviewCache() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                L.d("CustomAppearanceSettingsFragment: Pre-warming theme preview cache")
                
                // Generate previews for all themes in background
                ThemePreviewRenderer.getPreview(requireContext(), R.style.Theme_OSRSWiki_OSRSLight, "light")
                ThemePreviewRenderer.getPreview(requireContext(), R.style.Theme_OSRSWiki_OSRSDark, "dark") 
                ThemePreviewRenderer.getPreview(requireContext(), 0, "auto") // Uses composite generation
                
                L.d("CustomAppearanceSettingsFragment: Theme preview cache pre-warming completed")
            } catch (e: Exception) {
                L.e("CustomAppearanceSettingsFragment: Error pre-warming theme preview cache: ${e.message}")
            }
        }
    }

    /**
     * Pre-warms the table preview cache by generating the table collapse comparison in the background.
     * This ensures smooth scrolling when the table preview UI is displayed.
     */
    private fun preWarmTablePreviewCache() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                L.d("CustomAppearanceSettingsFragment: Pre-warming table preview cache")
                
                // Generate the side-by-side table preview in background
                // We only need to call it once since it generates both states in a single bitmap
                TablePreviewRenderer.getPreview(requireContext(), true) // Value doesn't matter, it generates both states
                
                L.d("CustomAppearanceSettingsFragment: Table preview cache pre-warming completed")
            } catch (e: Exception) {
                L.e("CustomAppearanceSettingsFragment: Error pre-warming table preview cache: ${e.message}")
            }
        }
    }

    override fun onThemeChanged() {
        if (!isAdded || view == null) {
            return
        }
        
        L.d("CustomAppearanceSettingsFragment: onThemeChanged called - clearing cache and refreshing UI")
        
        // Clear preview caches since themes have changed
        ThemePreviewRenderer.clearCache(requireContext())
        TablePreviewRenderer.clearCache(requireContext())
        
        // The RecyclerView and its items should automatically pick up the new theme
        // since they use theme attributes. Just notify the adapter to refresh.
        adapter.notifyDataSetChanged()
        
        // Re-warm the cache with new theme and table previews
        preWarmThemePreviewCache()
        preWarmTablePreviewCache()
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        if (!isAdded || view == null) {
            return
        }
        
        // Check for screen size or orientation changes that could indicate fold/unfold
        val isScreenConfigurationChanged = previousConfiguration?.let { prevConfig ->
            prevConfig.screenWidthDp != newConfig.screenWidthDp ||
            prevConfig.screenHeightDp != newConfig.screenHeightDp ||
            prevConfig.orientation != newConfig.orientation ||
            prevConfig.densityDpi != newConfig.densityDpi
        } ?: false
        
        if (isScreenConfigurationChanged) {
            L.d("CustomAppearanceSettingsFragment: Screen configuration changed - refreshing theme previews")
            L.d("CustomAppearanceSettingsFragment: Previous: ${previousConfiguration?.screenWidthDp}x${previousConfiguration?.screenHeightDp} ${previousConfiguration?.orientation}")
            L.d("CustomAppearanceSettingsFragment: Current: ${newConfig.screenWidthDp}x${newConfig.screenHeightDp} ${newConfig.orientation}")
            
            // Handle theme preview cache invalidation and regeneration
            handleConfigurationChangeForPreviews()
        }
        
        // Update stored configuration for next comparison
        previousConfiguration = Configuration(newConfig)
    }
    
    /**
     * Handles theme preview updates when device configuration changes (fold/unfold).
     */
    private fun handleConfigurationChangeForPreviews() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                L.d("CustomAppearanceSettingsFragment: Handling configuration change for theme previews")
                
                // Clear the old cache - ThemePreviewRenderer.onConfigurationChanged already handled cache clearing
                // but we need to ensure the UI refreshes with new previews
                
                // Refresh the adapter to trigger new preview generation and responsive layout recalculation
                adapter.notifyDataSetChanged()
                
                // Handle responsive layout recalculation for theme selection
                handleResponsiveLayoutRecalculation()
                
                // Re-warm the cache with new device configuration previews
                preWarmThemePreviewCache()
                preWarmTablePreviewCache()
                
                L.d("CustomAppearanceSettingsFragment: Configuration change handling completed")
            } catch (e: Exception) {
                L.e("CustomAppearanceSettingsFragment: Error handling configuration change for previews: ${e.message}")
            }
        }
    }
    
    /**
     * Handles responsive layout recalculation for theme selection RecyclerViews
     * when screen configuration changes.
     */
    private fun handleResponsiveLayoutRecalculation() {
        try {
            L.d("CustomAppearanceSettingsFragment: Handling responsive layout recalculation")
            
            // Find all theme selection RecyclerViews and trigger layout recalculation
            binding.recyclerView.post {
                // Iterate through visible items to find theme selection items
                for (i in 0 until binding.recyclerView.childCount) {
                    val childView = binding.recyclerView.getChildAt(i)
                    val themeRecyclerView = childView?.findViewById<androidx.recyclerview.widget.RecyclerView>(
                        com.omiyawaki.osrswiki.R.id.themePreviewRecyclerView
                    )
                    
                    themeRecyclerView?.let { recyclerView ->
                        val layoutManager = recyclerView.layoutManager
                        if (layoutManager is ResponsiveThemeLayoutManager) {
                            layoutManager.recalculateLayout()
                            L.d("CustomAppearanceSettingsFragment: Triggered layout recalculation for theme RecyclerView")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            L.e("CustomAppearanceSettingsFragment: Error handling responsive layout recalculation: ${e.message}")
        }
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