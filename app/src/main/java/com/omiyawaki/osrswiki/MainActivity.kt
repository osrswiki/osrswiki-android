package com.omiyawaki.osrswiki

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.omiyawaki.osrswiki.databinding.ActivityMainBinding // Assumes ViewBinding is used for activity_main.xml

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

@Suppress("unused")
private companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "onCreate: Intent action: ${intent.action}, data: ${intent.dataString}")

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment // <<<< CORRECTED ID HERE
        navController = navHostFragment.navController

        // The NavController should automatically handle deep links passed to the activity's intent
        // when <nav-graph> is specified in the manifest.
        // If issues persist, explicitly calling navController.handleDeepLink(intent) might be needed
        // for specific launch modes or setups, usually in onNewIntent or here if not already handled.
        // Example: if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
        //     if (!navController.handleDeepLink(intent)) {
        //          Log.w(TAG, "NavController could not handle deep link in onCreate.")
        //      }
        // }

        // If a Toolbar is used, defined in the Activity's layout (e.g., via binding.toolbar)
        // and want it to interact with the NavController (e.g., for titles, up button):
        // setSupportActionBar(binding.appToolbar) // Assumes the Toolbar in activity_main.xml has id "appToolbar"
        // val appBarConfiguration = AppBarConfiguration(navController.graph)
        // setupActionBarWithNavController(navController, appBarConfiguration)
        // Since the app theme is NoActionBar and fragments have their own toolbars,
        // this global ActionBar setup might not be necessary or desired here.
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent: Intent action: ${intent?.action}, data: ${intent?.dataString}")
        // If the activity's launchMode causes it to receive new intents while running (e.g., singleTop),
        // the NavController needs to be informed to handle any potential deep links in the new intent.
        if (intent != null) {
            if (!navController.handleDeepLink(intent)) {
                Log.w(TAG, "NavController could not handle deep link in onNewIntent.")
            }
        }
    }

    // If MainActivity were managing an ActionBar that should show the Up button:
    // override fun onSupportNavigateUp(): Boolean {
    //     return navController.navigateUp() || super.onSupportNavigateUp()
    // }
}
