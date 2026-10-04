package com.omiyawaki.osrswiki.ui.map

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ActivityStandardNavigationTestBinding
import com.omiyawaki.osrswiki.ui.main.MainFragment
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.launch

/**
 * Test Activity to demonstrate standard Android navigation with MapLibre preloading
 * 
 * This Activity uses:
 * - Standard FragmentTransaction show/hide operations (NOT alpha-based)
 * - AndroidMapPreloader for instant map tile display
 * - Proper fragment lifecycle management
 * 
 * The goal is to prove that background preloading can enable standard navigation
 * while maintaining the instant tile display benefits of the current alpha system.
 */
class StandardNavigationTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStandardNavigationTestBinding
    
    private lateinit var homeFragment: MainFragment
    private lateinit var mapFragment: StandardNavigationMapFragment
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.d("StandardNavigationTest: onCreate")
        
        binding = ActivityStandardNavigationTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Start background preloading immediately
        lifecycleScope.launch {
            L.d("StandardNavigationTest: Starting background map preloading...")
            AndroidMapPreloader.getInstance().preloadMapInBackground(this@StandardNavigationTestActivity)
        }
        
        setupFragments()
        setupBottomNavigation()
    }

    private fun setupFragments() {
        // Create fragment instances
        homeFragment = MainFragment.newInstance()
        mapFragment = StandardNavigationMapFragment.newInstance(null, null, null, null)
        
        // Add both fragments, initially hiding the map fragment
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, homeFragment, "HOME")
            .add(R.id.fragment_container, mapFragment, "MAP") 
            .hide(mapFragment)  // Standard hide operation
            .commit()
            
        activeFragment = homeFragment
        L.d("StandardNavigationTest: Fragments set up with standard navigation")
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchToFragment(homeFragment, "Home")
                    true
                }
                R.id.nav_map -> {
                    switchToFragment(mapFragment, "Map") 
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Standard Fragment navigation using show/hide transactions
     * This will trigger fragment lifecycle methods (onPause, onResume, etc.)
     */
    private fun switchToFragment(fragment: Fragment, name: String) {
        if (fragment === activeFragment) return
        
        L.d("StandardNavigationTest: Switching to $name using STANDARD navigation")
        
        val transaction = supportFragmentManager.beginTransaction()
        
        // Hide current fragment (triggers onPause/onStop)
        activeFragment?.let { transaction.hide(it) }
        
        // Show new fragment (triggers onStart/onResume)
        transaction.show(fragment)
        transaction.commit()
        
        activeFragment = fragment
        
        L.d("StandardNavigationTest: Standard navigation to $name complete")
    }
}