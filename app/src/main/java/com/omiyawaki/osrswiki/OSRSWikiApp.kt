package com.omiyawaki.osrswiki

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.event.ThemeChangeEvent
import com.omiyawaki.osrswiki.network.RetrofitClient
import com.omiyawaki.osrswiki.page.PageRepository
import com.omiyawaki.osrswiki.page.tabs.Tab
import com.omiyawaki.osrswiki.search.SearchRepository
import com.omiyawaki.osrswiki.theme.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class OSRSWikiApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _eventBus = MutableSharedFlow<Any>()
    val eventBus = _eventBus.asSharedFlow()

    private lateinit var prefs: SharedPreferences
    private var currentThemeInternal: Theme = Theme.DEFAULT_LIGHT

    lateinit var pageRepository: PageRepository
        private set

    lateinit var searchRepository: SearchRepository
        private set

    // Tab Management Stubs
    var currentTab: Tab? = null
    val tabList: MutableList<Tab> = mutableListOf()
    private val _tabCountFlow = MutableStateFlow(0)
    val tabCountFlow: StateFlow<Int> = _tabCountFlow.asStateFlow()

    // Network Status
    private val _currentNetworkStatus = MutableStateFlow(false)
    val currentNetworkStatus: StateFlow<Boolean> = _currentNetworkStatus.asStateFlow()
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private const val PREFS_NAME = "osrswiki_app_prefs"
        private const val KEY_CURRENT_THEME_TAG = "current_theme_tag"
        lateinit var instance: OSRSWikiApp
            private set

        /**
         * Logs a throwable for crash reporting or detailed local logging.
         * This is a STUB implementation.
         * @param throwable The error/exception to log.
         * @param message An optional additional message.
         */
        @JvmStatic // Ensures it's a static method from Java's perspective if needed
        fun logCrashManually(throwable: Throwable, message: String? = null) {
            // TODO: Replace with actual crash reporting (e.g., FirebaseCrashlytics.getInstance().recordException(throwable))
            val logMessage = message?.let { "$it: ${throwable.message}" } ?: throwable.message
            Log.e("OSRSWikiApp_CrashLog", "Manual crash log: $logMessage", throwable)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadCurrentTheme()

        val appContext = this.applicationContext
        val appDb = AppDatabase.instance
        val mediaWikiApiService = RetrofitClient.apiService

        val articleMetaDaoForPageRepo = appDb.articleMetaDao()
        pageRepository = PageRepository(
            mediaWikiApiService = mediaWikiApiService,
            articleMetaDao = articleMetaDaoForPageRepo,
            applicationContext = appContext
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

    private fun loadCurrentTheme() {
        val savedThemeTag = prefs.getString(KEY_CURRENT_THEME_TAG, null)
        currentThemeInternal = if (savedThemeTag != null) {
            Theme.ofTag(savedThemeTag) ?: determineDefaultTheme()
        } else {
            determineDefaultTheme()
        }
    }

    private fun determineDefaultTheme(): Theme {
        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            Theme.DEFAULT_DARK
        } else {
            Theme.DEFAULT_LIGHT
        }
    }

    fun getCurrentTheme(): Theme {
        return currentThemeInternal
    }

    fun setCurrentTheme(theme: Theme, persist: Boolean = true) {
        if (currentThemeInternal.tag != theme.tag) {
            currentThemeInternal = theme
            if (persist) {
                prefs.edit().putString(KEY_CURRENT_THEME_TAG, theme.tag).apply()
            }
            applicationScope.launch {
                _eventBus.emit(ThemeChangeEvent(theme))
            }
        }
    }
}