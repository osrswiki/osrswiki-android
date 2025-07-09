package com.omiyawaki.osrswiki.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMapBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

class MapFragment : Fragment() {

    private val logTag = "MapFragment"
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var assetLoader: WebViewAssetLoader

    // State flags
    private var isPageLoaded = false
    private var isTilesUnpacked = false
    private var currentFloor = 0

    // Loading view management
    private var loadingView: View? = null
    private var loadingProgressBar: ProgressBar? = null
    private var loadingTextView: TextView? = null

    // Floor control UI
    private var floorControls: View? = null
    private var floorUpButton: ImageButton? = null
    private var floorDownButton: ImageButton? = null
    private var floorTextView: TextView? = null

    // Inner class for the Javascript Interface
    inner class WebViewInterface(private val context: Context) {
        @JavascriptInterface
        fun getThemeColors(): String {
            // This remains the same as your project's implementation
            val colors = mapOf(
                "surfaceVariant" to String.format("#%06X", 0xFFFFFF and context.getColor(com.google.android.material.R.color.material_dynamic_neutral30)),
                "onSurfaceVariant" to String.format("#%06X", 0xFFFFFF and context.getColor(com.google.android.material.R.color.material_dynamic_neutral80))
            )
            return "{\"surfaceVariant\":\"${colors["surfaceVariant"]}\",\"onSurfaceVariant\":\"${colors["onSurfaceVariant"]}\"}"
        }

        @JavascriptInterface
        fun logJavaScriptError(message: String, stack: String) {
            Log.e("JSError", "Message: $message")
            Log.e("JSError", "Stack: $stack")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(requireContext()))
            .addPathHandler("/files/", WebViewAssetLoader.InternalStoragePathHandler(requireContext(), requireContext().filesDir))
            .build()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebView()
        setupFloorControls()
        lifecycleScope.launch {
            unpackMapTiles()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.mapWebview
        val webViewInterface = WebViewInterface(requireContext())

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(logTag, "[JS Console] ${consoleMessage.message()} -- line ${consoleMessage.lineNumber()}")
                return true
            }
        }

        webView.webViewClient = LocalContentWebViewClient(assetLoader)
        webView.addJavascriptInterface(webViewInterface, "Android")

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
        }

        val url = "https://appassets.androidplatform.net/assets/map.html"
        webView.loadUrl(url)
    }

    private fun setupFloorControls() {
        floorControls = binding.floorControls
        floorUpButton = binding.floorControlUp
        floorDownButton = binding.floorControlDown
        floorTextView = binding.floorControlText

        floorUpButton?.setOnClickListener {
            if (currentFloor < 3) {
                currentFloor++
                updateFloor()
            }
        }

        floorDownButton?.setOnClickListener {
            if (currentFloor > 0) {
                currentFloor--
                updateFloor()
            }
        }
    }

    private fun updateFloor() {
        floorTextView?.text = currentFloor.toString()
        val script = "setFloor($currentFloor)"
        binding.mapWebview.evaluateJavascript(script, null)
        Log.d(logTag, "Set floor to $currentFloor")
    }

    private fun tryToInitializeMap() {
        if (isPageLoaded && isTilesUnpacked) {
            val tilesUrl = "https://appassets.androidplatform.net/files/map_tiles/"
            Log.d(logTag, "All prerequisites met. Initializing map, theme, and POIs.")

            val initScript = "initializeMap('$tilesUrl')"
            val themeScript = "applyTheme()"

            binding.mapWebview.evaluateJavascript(initScript) {
                binding.mapWebview.evaluateJavascript(themeScript) {
                    injectPoiData()
                    floorControls?.visibility = View.VISIBLE
                    floorTextView?.text = currentFloor.toString()
                }
            }
        } else {
            Log.d(logTag, "Prerequisites not met. Page loaded: $isPageLoaded, Tiles unpacked: $isTilesUnpacked")
        }
    }

    private suspend fun unpackMapTiles() {
        val tilesDir = File(requireContext().filesDir, "map_tiles")
        val versionFile = File(tilesDir, ".unpacked_ok")

        if (versionFile.exists()) {
            Log.d(logTag, "Tiles already unpacked. Skipping.")
            withContext(Dispatchers.Main) {
                isTilesUnpacked = true
                tryToInitializeMap()
            }
            return
        }

        withContext(Dispatchers.Main) {
            if (loadingView == null) {
                loadingView = binding.loadingViewStub.inflate()
                loadingProgressBar = loadingView?.findViewById(R.id.loading_progress_bar)
                loadingTextView = loadingView?.findViewById(R.id.loading_text)
            }
            loadingView?.visibility = View.VISIBLE
            loadingProgressBar?.progress = 0
            loadingTextView?.text = "Unpacking map tiles - 0%"
        }

        withContext(Dispatchers.IO) {
            Log.d(logTag, "Starting tile unpacking process...")
            if (tilesDir.exists()) {
                tilesDir.deleteRecursively()
            }
            tilesDir.mkdirs()

            try {
                val assetManager = requireContext().assets
                val zipFileName = "map_tiles.zip"

                var totalUncompressedSize: Long = 0
                var bytesUnpacked: Long = 0
                var lastReportedProgress = -1

                assetManager.open(zipFileName).use { firstPassStream ->
                    ZipInputStream(firstPassStream.buffered()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (entry.name == "uncompressed_size.txt") {
                                val reader = BufferedReader(InputStreamReader(zip))
                                totalUncompressedSize = reader.readLine().toLong()
                                break
                            }
                            entry = zip.nextEntry
                        }
                    }
                }

                if (totalUncompressedSize == 0L) {
                    throw IOException("Could not read 'uncompressed_size.txt' from zip.")
                }
                Log.d(logTag, "Found total uncompressed size: $totalUncompressedSize bytes")

                assetManager.open(zipFileName).use { secondPassStream ->
                    ZipInputStream(secondPassStream.buffered()).use { zipInputStream ->
                        val buffer = ByteArray(8192)
                        var entry = zipInputStream.nextEntry
                        while (entry != null) {
                            if (entry.name == "uncompressed_size.txt") {
                                entry = zipInputStream.nextEntry
                                continue
                            }

                            val file = File(tilesDir, entry.name)
                            if (entry.isDirectory) {
                                file.mkdirs()
                            } else {
                                file.parentFile?.mkdirs()
                                FileOutputStream(file).use { fileOutputStream ->
                                    var len: Int
                                    while (zipInputStream.read(buffer).also { len = it } > 0) {
                                        fileOutputStream.write(buffer, 0, len)
                                        bytesUnpacked += len
                                        val progress = ((bytesUnpacked * 100) / totalUncompressedSize).toInt()

                                        if (progress > lastReportedProgress) {
                                            lastReportedProgress = progress
                                            withContext(Dispatchers.Main) {
                                                loadingProgressBar?.progress = progress.coerceAtMost(100)
                                                loadingTextView?.text = "Unpacking map tiles - $progress%"
                                            }
                                        }
                                    }
                                }
                            }
                            zipInputStream.closeEntry()
                            entry = zipInputStream.nextEntry
                        }
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
                    loadingView?.visibility = View.GONE
                    tryToInitializeMap()
                }
            }
        }
    }

    private fun injectPoiData() {
        try {
            val jsonString = requireContext().assets.open("pois.json")
                .bufferedReader()
                .use { it.readText() }
            val script = "loadPois(`$jsonString`);"
            binding.mapWebview.evaluateJavascript(script, null)
            Log.d(logTag, "Successfully injected POI data.")
        } catch (e: IOException) {
            Log.e(logTag, "Failed to read or inject pois.json", e)
        }
    }

    private inner class LocalContentWebViewClient(private val assetLoader: WebViewAssetLoader) : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            val response = assetLoader.shouldInterceptRequest(request.url)
            if (response == null && !url.startsWith("data:")) { // Don't log data URIs
                Log.w(logTag, "AssetLoader could not handle: $url")
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
