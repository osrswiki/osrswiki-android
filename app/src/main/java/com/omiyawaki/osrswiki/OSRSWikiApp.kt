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
import com.omiyawaki.osrswiki.network.ModuleCacheWarmer
import com.omiyawaki.osrswiki.network.OkHttpClientFactory
import com.omiyawaki.osrswiki.network.RetrofitClient
import com.omiyawaki.osrswiki.page.PageAssetDownloader
import com.omiyawaki.osrswiki.page.PageHtmlBuilder
import com.omiyawaki.osrswiki.page.PageLocalDataSource
import com.omiyawaki.osrswiki.page.PageRemoteDataSource
import com.omiyawaki.osrswiki.page.PageRepository
import com.omiyawaki.osrswiki.page.osrsArticleFloorConvention
import com.omiyawaki.osrswiki.page.preemptive.AndroidArticlePrewarmEnvironmentProvider
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmRequest
import com.omiyawaki.osrswiki.page.preemptive.AppForegroundTracker
import com.omiyawaki.osrswiki.search.SearchRepository
import com.omiyawaki.osrswiki.savedpages.SavedPageSyncWorker
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.settings.osrsDownloadSettings
import com.omiyawaki.osrswiki.settings.osrsSavedPageUpdatePolicy
import com.omiyawaki.osrswiki.settings.ActivityContextPool
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsMapFloorNumberingHost
import com.omiyawaki.osrswiki.util.AppNetworkStatusEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OSRSWikiApp : Application(), osrsMapFloorNumberingHost {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val imageCache: LruCache<String, Bitmap> = LruCache(10)

    lateinit var pageRepository: PageRepository
        private set

    lateinit var searchRepository: SearchRepository
        private set

    // Added for preemptive loading
    lateinit var pageAssetDownloader: PageAssetDownloader
        private set

    // Added for preemptive loading
    lateinit var pageHtmlBuilder: PageHtmlBuilder
        private set

    // Repository initialization status
    val isRepositoriesInitialized: Boolean 
        get() = ::pageRepository.isInitialized && ::searchRepository.isInitialized


    private val _currentNetworkStatus = MutableStateFlow(false)
    val currentNetworkStatus: StateFlow<Boolean> = _currentNetworkStatus.asStateFlow()
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var appForegroundTracker: AppForegroundTracker
    private var articlePrewarmEnvironment: AndroidArticlePrewarmEnvironmentProvider? = null

    companion object {
        lateinit var instance: OSRSWikiApp
            private set

        internal fun invalidatePreparedArticleIfAvailable(request: ArticlePrewarmRequest) {
            if (::instance.isInitialized) instance.invalidatePreparedArticle(request)
        }

        @JvmStatic
        fun logCrashManually(throwable: Throwable, message: String? = null) {
            val logMessage = message?.let { "$it: ${throwable.message}" } ?: throwable.message
            Log.e("OSRSWikiApp_CrashLog", "Manual crash log: $logMessage", throwable)
        }
    }

    override fun osrsMapFloorUsEntranceIsFirstFloor(): Boolean =
        osrsArticleFloorConvention.resolved().usEntranceIsFirstFloor

    override fun onCreate() {
        val startupStartTime = System.currentTimeMillis()
        Log.i("StartupTiming", "OSRSWikiApp.onCreate() - App startup begins")
        
        super.onCreate()
        instance = this
        appForegroundTracker = AppForegroundTracker {
            if (::pageAssetDownloader.isInitialized) {
                pageAssetDownloader.notifyPrewarmEnvironmentChanged()
            }
        }
        registerActivityLifecycleCallbacks(appForegroundTracker)
        Prefs.migrateLegacyAppearancePreferences()

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Pre-warm database on background thread to prevent main thread blocking
        applicationScope.launch(Dispatchers.IO) {
            Log.d("StartupTiming", "Database pre-warming started in background")
            val dbStartTime = System.currentTimeMillis()
            AppDatabase.instance // Force database creation/migration on background thread
            val dbEndTime = System.currentTimeMillis()
            Log.d("StartupTiming", "Database pre-warming completed in ${dbEndTime - dbStartTime}ms")
        }

        Log.d("StartupTiming", "Starting initializeDependencies() after ${System.currentTimeMillis() - startupStartTime}ms")
        initializeDependencies()
        if (osrsDownloadSettings.load().updatePolicy == osrsSavedPageUpdatePolicy.AUTOMATIC) {
            SavedPageSyncWorker.enqueue(this)
        }
        Log.i("StartupTiming", "OSRSWikiApp.onCreate() completed in ${System.currentTimeMillis() - startupStartTime}ms")
    }

    private fun invalidatePreparedArticle(request: ArticlePrewarmRequest) {
        if (::pageAssetDownloader.isInitialized) {
            pageAssetDownloader.invalidatePreparedArticle(request.pageId, request.title)
        }
    }

    private fun initializeDependencies() {
        val initStartTime = System.currentTimeMillis()
        Log.d("StartupTiming", "initializeDependencies() - Starting dependency initialization")
        
        val appContext = this.applicationContext
        
        // Access pre-warmed database instance (should be ready from background thread)
        Log.d("StartupTiming", "Accessing database instance...")
        val dbAccessStartTime = System.currentTimeMillis()
        val appDb = AppDatabase.instance
        Log.d("StartupTiming", "Database accessed in ${System.currentTimeMillis() - dbAccessStartTime}ms")
        
        Log.d("StartupTiming", "Creating network services...")
        val networkStartTime = System.currentTimeMillis()
        val mediaWikiApiService = RetrofitClient.apiService
        // Correctly get the shared OkHttpClient from the factory.
        val okHttpClient = OkHttpClientFactory.offlineClient
        Log.d("StartupTiming", "Network services created in ${System.currentTimeMillis() - networkStartTime}ms")

        try {
            // Create PageHtmlBuilder first
            Log.d("StartupTiming", "Creating PageHtmlBuilder...")
            val htmlBuilderStartTime = System.currentTimeMillis()
            pageHtmlBuilder = PageHtmlBuilder(this)
            Log.d("StartupTiming", "PageHtmlBuilder created in ${System.currentTimeMillis() - htmlBuilderStartTime}ms")

            // Create PageRepository
            Log.d("StartupTiming", "Creating PageRepository...")
            val pageRepoStartTime = System.currentTimeMillis()
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
                htmlBuilder = pageHtmlBuilder,
                readingListPageDao = appDb.readingListPageDao()
            )
            Log.d("StartupTiming", "PageRepository created in ${System.currentTimeMillis() - pageRepoStartTime}ms")
            
            // Instantiate PageAssetDownloader with PageRepository for cache checking
            Log.d("StartupTiming", "Creating PageAssetDownloader...")
            val assetDownloaderStartTime = System.currentTimeMillis()
            articlePrewarmEnvironment = AndroidArticlePrewarmEnvironmentProvider(
                context = this,
                isAppInForeground = { appForegroundTracker.isAppInForeground },
                isNetworkAvailable = { currentNetworkStatus.value }
            )
            pageAssetDownloader = PageAssetDownloader(okHttpClient, pageRepository, applicationScope).also { downloader ->
                downloader.configurePrewarmEnvironment(requireNotNull(articlePrewarmEnvironment))
            }
            articlePrewarmEnvironment?.startObserving(pageAssetDownloader::notifyPrewarmEnvironmentChanged)
            Log.d("StartupTiming", "PageAssetDownloader created in ${System.currentTimeMillis() - assetDownloaderStartTime}ms")

            // Instantiate the SearchRepository with all its DAO dependencies.
            Log.d("StartupTiming", "Creating SearchRepository...")
            val searchRepoStartTime = System.currentTimeMillis()
            val articleMetaDaoForSearchRepo = appDb.articleMetaDao()
            val offlinePageFtsDao = appDb.offlinePageFtsDao()
            val recentSearchDao = appDb.recentSearchDao() // Get the new DAO from the database.
            searchRepository = SearchRepository(
                apiService = mediaWikiApiService,
                articleMetaDao = articleMetaDaoForSearchRepo,
                offlinePageFtsDao = offlinePageFtsDao,
                recentSearchDao = recentSearchDao // Provide the new DAO to the repository.
            )
            Log.d("StartupTiming", "SearchRepository created in ${System.currentTimeMillis() - searchRepoStartTime}ms")

            Log.d("StartupTiming", "Initializing network callback...")
            val networkCallbackStartTime = System.currentTimeMillis()
            initializeNetworkCallback()
            Log.d("StartupTiming", "Network callback initialized in ${System.currentTimeMillis() - networkCallbackStartTime}ms")
            
            Log.i("StartupTiming", "Dependencies initialized")
            ModuleCacheWarmer.getInstance(this).warmCacheWithEssentials()
            
        } catch (e: Exception) {
            logCrashManually(e, "Failed to initialize dependencies")
            throw e // Re-throw to prevent app from continuing in broken state
        }
        
        Log.i("StartupTiming", "initializeDependencies() completed in ${System.currentTimeMillis() - initStartTime}ms")
    }

    override fun onTerminate() {
        articlePrewarmEnvironment?.stopObserving()
        if (::appForegroundTracker.isInitialized) {
            unregisterActivityLifecycleCallbacks(appForegroundTracker)
        }
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
        return if (isNightMode) Theme.OSRS_DARK else Theme.OSRS_LIGHT
    }

    private fun initializeNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkStatusEvaluator = AppNetworkStatusEvaluator(
            debugAlwaysOnline = BuildConfig.DEBUG,
            isCurrentlyConnected = ::isCurrentlyConnected
        )
        
        updateNetworkStatus(networkStatusEvaluator.initialStatus())
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                updateNetworkStatus(networkStatusEvaluator.statusAfterAvailableNetwork())
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                updateNetworkStatus(networkStatusEvaluator.statusAfterLostNetwork())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)

                // Only require NET_CAPABILITY_INTERNET, not VALIDATED
                // VALIDATED can fail in emulator environments even when internet works
                val isConnected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                updateNetworkStatus(networkStatusEvaluator.statusAfterCapabilitiesChanged(isConnected))
            }
        }
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    private fun updateNetworkStatus(isOnline: Boolean) {
        _currentNetworkStatus.value = isOnline
        if (::pageAssetDownloader.isInitialized) {
            pageAssetDownloader.notifyPrewarmEnvironmentChanged()
        }
    }

    /**
     * Wait for an available Activity context for WebView creation.
     * Used by background preview generation that runs in Application scope.
     */
    suspend fun waitForActivityContext() = ActivityContextPool.waitForActivityContext()

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
                // Only require NET_CAPABILITY_INTERNET, not VALIDATED
                // VALIDATED can fail in emulator environments even when internet works
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } ?: false
        } else {
            cm.activeNetworkInfo?.isConnected ?: false
        }
    }

}
