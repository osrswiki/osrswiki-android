package com.omiyawaki.osrswiki

import android.app.Application
import android.util.Log
import com.omiyawaki.osrswiki.common.serialization.JsonUtil // <<< ADDED JsonUtil import
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.OfflinePageFtsDao
import com.omiyawaki.osrswiki.network.RetrofitClient
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.page.PageRepository
import com.omiyawaki.osrswiki.page.tabs.Tab // <<< ADDED Tab import
import com.omiyawaki.osrswiki.search.SearchRepository
import com.omiyawaki.osrswiki.settings.PrefsIoUtil // <<< ADDED PrefsIoUtil import
import com.omiyawaki.osrswiki.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerializationException // <<< ADDED SerializationException import

class OSRSWikiApp : Application() {
    // Define an application-wide CoroutineScope
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- Singleton dependencies provided by the Application class ---

    private val database: AppDatabase by lazy {
        AppDatabase.instance
    }

    private val articleMetaDao: ArticleMetaDao by lazy {
        database.articleMetaDao()
    }

    private val offlinePageFtsDao: OfflinePageFtsDao by lazy {
        database.offlinePageFtsDao()
    }

    private val wikiApiService: WikiApiService by lazy {
        RetrofitClient.apiService
    }

    private val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(applicationContext)
    }

    val currentNetworkStatus: StateFlow<Boolean> by lazy {
        networkMonitor.isOnline
    }

    val pageRepository: PageRepository by lazy {
        PageRepository(
            mediaWikiApiService = wikiApiService,
            articleMetaDao = articleMetaDao,
            applicationContext = this
        )
    }

    val searchRepository: SearchRepository by lazy {
        SearchRepository(
            apiService = wikiApiService,
            articleMetaDao = articleMetaDao,
            offlinePageFtsDao = offlinePageFtsDao
        )
    }

    // --- Tab Management ---
    val tabList = mutableListOf<Tab>()
    var currentTab: Tab? = null
        private set // Allow external read, but modification via specific methods

    private fun initTabs() {
        val jsonString = PrefsIoUtil.getString(applicationContext.getString(R.string.preference_key_tabs), null)
        var loadedSuccessfully = false
        if (!jsonString.isNullOrEmpty()) {
            try {
                // Ensure JsonUtil is correctly imported and accessible
                val decodedTabs = JsonUtil.instance.decodeFromString<List<Tab>>(jsonString)
                if (decodedTabs.isNotEmpty()) {
                    tabList.addAll(decodedTabs)
                    // TODO: Implement logic to restore the last active tab index if saved.
                    // For now, set the first tab as current.
                    currentTab = tabList.first()
                    loadedSuccessfully = true
                }
            } catch (e: SerializationException) {
                Log.e(TAG, "Error decoding tabs from SharedPreferences: ${e.message}", e)
                // Fall through to default tab creation
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error loading tabs from SharedPreferences: ${e.message}", e)
                // Fall through to default tab creation
            }
        }

        if (!loadedSuccessfully) {
            tabList.clear() // Ensure it's empty before adding default
            addNewTabAndMakeCurrent() // Create and set a new default tab
            Log.i(TAG, "Initialized with a new default tab.")
        } else {
            Log.i(TAG, "Tabs loaded from SharedPreferences. Count: ${tabList.size}. Current tab ID: ${currentTab?.id}")
        }
        // After initialization, ensure there's always a current tab if tabList is not empty
        if (currentTab == null && tabList.isNotEmpty()) {
            currentTab = tabList.first()
        }
    }

    fun commitTabState() {
        try {
            val jsonString = JsonUtil.instance.encodeToString(tabList)
            PrefsIoUtil.setString(applicationContext.getString(R.string.preference_key_tabs), jsonString)
            Log.i(TAG, "Tab state committed to SharedPreferences. Tab count: ${tabList.size}")
        } catch (e: SerializationException) {
            Log.e(TAG, "Error encoding tabs to SharedPreferences: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error saving tabs to SharedPreferences: ${e.message}", e)
        }
    }

    /**
     * Creates a new tab, adds it to the tab list, makes it current, and commits state.
     * @return The newly created Tab.
     */
    fun addNewTabAndMakeCurrent(): Tab {
        val newTab = Tab()
        tabList.add(newTab)
        currentTab = newTab
        commitTabState() // Save state after adding new tab
        Log.i(TAG, "Added new tab and made it current. Tab ID: ${newTab.id}")
        return newTab
    }

    /**
     * Sets the given tab as the current active tab.
     * Does not commit state by itself; caller should ensure state is committed if necessary.
     * @param tab The tab to set as current. Must be present in the tabList.
     */
    fun setCurrentTab(tab: Tab) {
        if (tabList.contains(tab)) {
            currentTab = tab
            Log.i(TAG, "Set current tab to ID: ${tab.id}")
            // Consider if commitTabState() should be called here or by the UI layer managing tab selection.
            // For now, manual commit after selection change is assumed.
        } else {
            Log.w(TAG, "Attempted to set current tab to one not in tabList. Tab ID: ${tab.id}")
        }
    }

    /**
     * Closes the given tab. If it's the current tab, attempts to set a new current tab.
     * Commits the state after removal.
     * @param tab The tab to close.
     * @return True if the tab was closed, false otherwise.
     */
    fun closeTab(tab: Tab): Boolean {
        val index = tabList.indexOf(tab)
        if (index == -1) {
            Log.w(TAG, "Attempted to close a tab not in tabList. Tab ID: ${tab.id}")
            return false
        }

        tabList.removeAt(index)
        Log.i(TAG, "Closed tab. Tab ID: ${tab.id}. Remaining tabs: ${tabList.size}")

        if (currentTab == tab) {
            currentTab = if (tabList.isNotEmpty()) {
                // Try to set the previous tab as current, or the first one if it was the first
                tabList.getOrNull(index.coerceAtMost(tabList.size - 1)) ?: tabList.first()
            } else {
                null // No tabs left
            }
            Log.i(TAG, "Current tab updated after closing. New current tab ID: ${currentTab?.id}")
        }

        if (tabList.isEmpty()) {
            // If all tabs are closed, optionally create a new default one.
            // Or, this could be handled by the UI layer (e.g., Activity closes if no tabs).
            // For now, let's add a new one to maintain consistency with init logic.
            addNewTabAndMakeCurrent()
            Log.i(TAG, "All tabs were closed, added a new default tab.")
        }

        commitTabState()
        return true
    }


    // --- Application Lifecycle ---

    override fun onCreate() {
        super.onCreate()
        instance = this // Set the static instance reference
        Log.d(TAG, "OSRSWikiApplication created and instance set.")
        initTabs() // <<< INITIALIZE TABS
        // networkMonitor and other lazy properties will be initialized on first access.
    }

    companion object {
        private const val TAG = "OSRSWikiApp" // Changed from OSRSWikiApplication for brevity
        lateinit var instance: OSRSWikiApp
            private set // Setter remains private

        fun logCrashManually(throwable: Throwable) {
            Log.e("OSRSWikiAppCrash", "Manual crash log from Application", throwable)
        }
    }
}
