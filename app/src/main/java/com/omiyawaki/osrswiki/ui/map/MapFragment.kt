package com.omiyawaki.osrswiki.ui.map

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.omiyawaki.osrswiki.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

class MapFragment : Fragment() {

    private val logTag = "MapDebug"
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader

    // State flags to ensure map initialization happens only when all prerequisites are met.
    private var isPageLoaded = false
    private var isTilesUnpacked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            // This handler serves files from the app's /assets directory.
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(requireContext()))
            // This handler serves files from the app's internal storage directory.
            .addPathHandler("/files/", WebViewAssetLoader.InternalStoragePathHandler(requireContext(), requireContext().filesDir))
            .build()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webView = view.findViewById(R.id.map_webview)

        // Launch the tile unpacking process in a background coroutine.
        lifecycleScope.launch {
            unpackMapTiles()
        }

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(logTag, "[JS Console] ${consoleMessage.message()} -- line ${consoleMessage.lineNumber()}")
                return true
            }
        }

        webView.webViewClient = LocalContentWebViewClient(assetLoader)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
        }

        // Load the main map.html file using the asset loader's special URL.
        val url = "https://appassets.androidplatform.net/assets/map.html"
        webView.loadUrl(url)
    }

    private fun tryToInitializeMap() {
        if (isPageLoaded && isTilesUnpacked) {
            val tilesUrl = "https://appassets.androidplatform.net/files/map_tiles/"
            Log.d(logTag, "All prerequisites met. Initializing map with URL: $tilesUrl")

            val script = "initializeMap('$tilesUrl')"
            webView.evaluateJavascript(script) {
                injectPoiData()
            }
        } else {
            Log.d(logTag, "Prerequisites not met. Page loaded: $isPageLoaded, Tiles unpacked: $isTilesUnpacked")
        }
    }

    private suspend fun unpackMapTiles() = withContext(Dispatchers.IO) {
        val tilesDir = File(requireContext().filesDir, "map_tiles")
        val versionFile = File(tilesDir, ".unpacked_ok")

        if (versionFile.exists()) {
            Log.d(logTag, "Tiles already unpacked. Skipping.")
            withContext(Dispatchers.Main) {
                isTilesUnpacked = true
                tryToInitializeMap()
            }
            return@withContext
        }

        Log.d(logTag, "Starting tile unpacking process...")
        if (tilesDir.exists()) {
            tilesDir.deleteRecursively()
        }
        tilesDir.mkdirs()

        try {
            val inputStream = requireContext().assets.open("map_tiles.zip")
            ZipInputStream(inputStream.buffered()).use { zipInputStream ->
                var entry = zipInputStream.nextEntry
                while (entry != null) {
                    val file = File(tilesDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { fileOutputStream ->
                            zipInputStream.copyTo(fileOutputStream)
                        }
                    }
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
            versionFile.createNewFile()
            Log.d(logTag, "Tile unpacking completed successfully.")
        } catch (e: IOException) {
            Log.e(logTag, "Failed to unpack map tiles", e)
            tilesDir.deleteRecursively()
        } finally {
            withContext(Dispatchers.Main) {
                isTilesUnpacked = true
                tryToInitializeMap()
            }
        }
    }

    private fun injectPoiData() {
        try {
            val jsonString = requireContext().assets.open("pois.json")
                .bufferedReader()
                .use { it.readText() }
            val script = "loadPois(`$jsonString`);"
            webView.evaluateJavascript(script, null)
        } catch (e: IOException) {
            Log.e(logTag, "Failed to read or inject pois.json", e)
        }
    }

    private inner class LocalContentWebViewClient(private val assetLoader: WebViewAssetLoader) : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()
            // Add detailed logging to see every request and the asset loader's response.
            Log.d(logTag, "Intercepting request for: $url")
            val response = assetLoader.shouldInterceptRequest(request.url)
            if (response == null) {
                Log.w(logTag, "AssetLoader could not handle: $url")
            } else {
                Log.i(logTag, "AssetLoader successfully handled: $url")
            }
            return response
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            if (url == "https://appassets.androidplatform.net/assets/map.html") {
                Log.d(logTag, "map.html finished loading.")
                isPageLoaded = true
                tryToInitializeMap()
            }
        }
    }
}
