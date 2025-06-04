package com.omiyawaki.osrswiki

import android.app.Application
import android.util.Log
import com.omiyawaki.osrswiki.common.serialization.JsonUtil
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.OfflinePageFtsDao
import com.omiyawaki.osrswiki.network.RetrofitClient
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.page.PageRepository
import com.omiyawaki.osrswiki.page.tabs.Tab
import com.omiyawaki.osrswiki.search.SearchRepository
import com.omiyawaki.osrswiki.settings.PrefsIoUtil
import com.omiyawaki.osrswiki.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer // Required
import kotlinx.serialization.serializer // Required for Tab.serializer()

class OSRSWikiApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: AppDatabase by lazy { AppDatabase.instance }
    private val articleMetaDao: ArticleMetaDao by lazy { database.articleMetaDao() }
    private val offlinePageFtsDao: OfflinePageFtsDao by lazy { database.offlinePageFtsDao() }
    private val wikiApiService: WikiApiService by lazy { RetrofitClient.apiService }
    private val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(applicationContext) }

    val currentNetworkStatus: StateFlow<Boolean> by lazy { networkMonitor.isOnline }

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

    val tabList = mutableListOf<Tab>()
    var currentTab: Tab? = null
        private set

    private val _tabCountFlow = MutableStateFlow(0)
    val tabCountFlow: StateFlow<Int> = _tabCountFlow.asStateFlow()

    private fun updateTabCountFlow() {
        _tabCountFlow.value = tabList.size
    }

    private fun initTabs() {
        val jsonString = PrefsIoUtil.getString(applicationContext.getString(R.string.preference_key_tabs), null)
        var loadedSuccessfully = false
        if (!jsonString.isNullOrEmpty()) {
            try {
                val tabsSerializer = ListSerializer(Tab.serializer())
                val decodedTabs = JsonUtil.instance.decodeFromString(tabsSerializer, jsonString)
                if (decodedTabs.isNotEmpty()) {
                    tabList.addAll(decodedTabs)
                    currentTab = tabList.first()
                    loadedSuccessfully = true
                }
            } catch (e: SerializationException) {
                Log.e(TAG, "Error decoding tabs from SharedPreferences: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error loading tabs from SharedPreferences: ${e.message}", e)
            }
        }

        if (!loadedSuccessfully) {
            tabList.clear()
            addNewTabAndMakeCurrent(notifyObservers = false)
            Log.i(TAG, "Initialized with a new default tab.")
        } else {
            Log.i(TAG, "Tabs loaded from SharedPreferences. Count: ${tabList.size}. Current tab ID: ${currentTab?.id}")
        }

        if (currentTab == null && tabList.isNotEmpty()) {
            currentTab = tabList.first()
        }
        updateTabCountFlow()
    }

    fun commitTabState() {
        try {
            val tabsSerializer = ListSerializer(Tab.serializer())
            val jsonString = JsonUtil.instance.encodeToString(tabsSerializer, tabList)
            PrefsIoUtil.setString(applicationContext.getString(R.string.preference_key_tabs), jsonString)
            Log.i(TAG, "Tab state committed. Tab count: ${tabList.size}")
        } catch (e: SerializationException) {
            Log.e(TAG, "Error encoding tabs to SharedPreferences: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error saving tabs: ${e.message}", e)
        }
    }

    fun addNewTabAndMakeCurrent(notifyObservers: Boolean = true): Tab {
        val newTab = Tab()
        tabList.add(newTab)
        currentTab = newTab
        Log.i(TAG, "Added new tab and made it current. Tab ID: ${newTab.id}")
        if (notifyObservers) {
            updateTabCountFlow()
        }
        commitTabState()
        return newTab
    }

    fun setCurrentTab(tab: Tab) {
        if (tabList.contains(tab)) {
            currentTab = tab
            Log.i(TAG, "Set current tab to ID: ${tab.id}")
        } else {
            Log.w(TAG, "Attempted to set current tab to one not in tabList. Tab ID: ${tab.id}")
        }
    }

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
                tabList.getOrNull(index.coerceAtMost(tabList.size - 1)) ?: tabList.first()
            } else { null }
            Log.i(TAG, "Current tab updated after closing. New current tab ID: ${currentTab?.id}")
        }
        if (tabList.isEmpty()) {
            addNewTabAndMakeCurrent(notifyObservers = false)
            Log.i(TAG, "All tabs were closed, added a new default tab.")
        } else {
            updateTabCountFlow()
        }
        commitTabState()
        return true
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "OSRSWikiApplication created and instance set.")
        initTabs()
    }

    companion object {
        private const val TAG = "OSRSWikiApp"
        lateinit var instance: OSRSWikiApp
            private set
        fun logCrashManually(throwable: Throwable) {
            Log.e("OSRSWikiAppCrash", "Manual crash log from Application", throwable)
        }
    }
}
