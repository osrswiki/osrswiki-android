package com.omiyawaki.osrswiki

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import android.webkit.WebView
import androidx.collection.LruCache
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.network.RetrofitClient
import com.omiyawaki.osrswiki.page.PageHtmlBuilder
import com.omiyawaki.osrswiki.page.PageLocalDataSource
import com.omiyawaki.osrswiki.page.PageRemoteDataSource
import com.omiyawaki.osrswiki.page.PageRepository
import com.omiyawaki.osrswiki.page.tabs.Tab
import com.omiyawaki.osrswiki.search.SearchRepository
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.theme.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OSRSWikiApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val imageCache: LruCache<String, Bitmap> = LruCache(10)

    lateinit var pageRepository: PageRepository
        private set

    lateinit var searchRepository: SearchRepository
        private set

    var currentTab: Tab? = null
    val tabList: MutableList<Tab> = mutableListOf()
    private val _tabCountFlow = MutableStateFlow(0)
    val tabCountFlow: StateFlow<Int> = _tabCountFlow.asStateFlow()

    private val _currentNetworkStatus = MutableStateFlow(false)
    val currentNetworkStatus: StateFlow<Boolean> = _currentNetworkStatus.asStateFlow()
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        lateinit var instance: OSRSWikiApp
            private set

        @JvmStatic
        fun logCrashManually(throwable: Throwable, message: String? = null) {
            val logMessage = message?.let { "$it: ${throwable.message}" } ?: throwable.message
            Log.e("OSRSWikiApp_CrashLog", "Manual crash log: $logMessage", throwable)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        initializeDependencies()
    }

    private fun initializeDependencies() {
        val appContext = this.applicationContext
        val appDb = AppDatabase.instance
        val mediaWikiApiService = RetrofitClient.apiService

        val pageHtmlBuilder = PageHtmlBuilder(this)
        val pageLocalDataSource = PageLocalDataSource(
            articleMetaDao = appDb.articleMetaDao(),
            applicationContext = appContext
        )
        val pageRemoteDataSource = PageRemoteDataSource(
            mediaWikiApiService = mediaWikiApiService
        )
        pageRepository = PageRepository(
            localDataSource = pageLocalDataSource,
            remoteDataSource = pageRemoteDataSource,
            htmlBuilder = pageHtmlBuilder
        )

        val articleMetaDaoForSearchRepo = appDb.articleMetaDao()
        val offlinePageFtsDao = appDb.offlinePageFtsDao()
        searchRepository = SearchRepository(
            apiService = mediaWikiApiService,
            articleMetaDao = articleMetaDaoForSearchRepo,
            offlinePageFtsDao = offlinePageFtsDao
        )

        if (tabList.isEmpty()) {
            val initialTab = Tab()
            tabList.add(initialTab)
            currentTab = initialTab
        } else {
            currentTab = tabList.firstOrNull()
        }
        _tabCountFlow.value = tabList.size

        initializeNetworkCallback()
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterNetworkCallback()
    }

    fun getCurrentTheme(): Theme {
        val themeMode = Prefs.appThemeMode
        val isNightMode = when (themeMode) {
            "light" -> false
            "dark" -> true
            "auto" -> {
                val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
            else -> {
                val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
        }
        val themeTag = if (isNightMode) Prefs.darkThemeChoice else Prefs.lightThemeChoice
        return Theme.ofTag(themeTag) ?: Theme.DEFAULT_LIGHT
    }

    private fun initializeNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        _currentNetworkStatus.value = isCurrentlyConnected()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                _currentNetworkStatus.value = true
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                _currentNetworkStatus.value = false
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val isConnected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                _currentNetworkStatus.value = isConnected
            }
        }
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { callback ->
            connectivityManager?.unregisterNetworkCallback(callback)
        }
        networkCallback = null
        connectivityManager = null
    }

    @Suppress("DEPRECATION")
    private fun isCurrentlyConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.getNetworkCapabilities(cm.activeNetwork)?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } ?: false
        } else {
            cm.activeNetworkInfo?.isConnected ?: false
        }
    }

    fun commitTabState() {
        Log.d("OSRSWikiApp", "commitTabState() called (STUB). Current tab ID: ${currentTab?.id}, Tab count: ${tabList.size}")
    }
}
