package com.omiyawaki.osrswiki.ui.article

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.Glide
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentArticleBinding
// Make sure ArticleViewModelFactory is correctly imported if it's in a different package,
// or remove if in the same package and not explicitly needed.
// For example: import com.omiyawaki.osrswiki.ui.article.ArticleViewModelFactory
import kotlinx.coroutines.launch

class ArticleFragment : Fragment() {

    private var _binding: FragmentArticleBinding? = null
    private val binding get() = _binding!!

    private val navArgs: ArticleFragmentArgs by navArgs()

    private val viewModel: ArticleViewModel by viewModels {
        // The factory receives the fragment instance (owner) and the raw arguments bundle.
        // It should internally use SavedStateHandle to access navArgs.
        ArticleViewModelFactory(this, arguments)
    }

    private companion object {
        private const val TAG = "ArticleFragment"
        private const val OSRS_WIKI_BASE_URL_HOST = "oldschool.runescape.wiki"
        private val WIKI_ARTICLE_PATH_PREFIXES = listOf("/w/", "/wiki/")
    }

    private val webViewBackgroundColor = "#E2DBC8"
    private val webViewTextColor = "#333333"
    private val webViewLinkColor = "#0645AD"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Fragment created. NavArgs - articleId: '${navArgs.articleId}', articleTitle: '${navArgs.articleTitle}'")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArticleBinding.inflate(inflater, container, false)
        Log.d(TAG, "onCreateView: Binding inflated.")
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: View created.")
        setupToolbar()
        setupWebView()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbarArticle.setupWithNavController(findNavController())
    }

    private fun setupWebView() {
        binding.webviewArticleContent.webViewClient = InternalLinkWebViewClient()
        binding.webviewArticleContent.setBackgroundColor(Color.parseColor(webViewBackgroundColor))
        Log.d(TAG, "WebView setup complete with InternalLinkWebViewClient.")
    }

    private fun observeUiState() {
        Log.d(TAG, "Setting up UI state observer for NavArgs - articleId: '${navArgs.articleId}', articleTitle: '${navArgs.articleTitle}'")
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.d(TAG, "New UI State received: isLoading=${state.isLoading}, title=${state.title}, error=${state.error}")
                    binding.progressBarArticle.isVisible = state.isLoading
                    binding.toolbarArticle.title = state.title ?: navArgs.articleTitle ?: navArgs.articleId ?: getString(R.string.app_name)

                    if (state.error != null && !state.isLoading) {
                        binding.textviewArticleError.text = state.error
                        binding.textviewArticleError.isVisible = true
                        Log.e(TAG, "UI Error displayed: ${state.error}")
                        binding.webviewArticleContent.isVisible = false
                        binding.imageArticleMain.isVisible = false
                    } else {
                        binding.textviewArticleError.isVisible = false
                        if (state.htmlContent != null) {
                            val baseUrl = "https://$OSRS_WIKI_BASE_URL_HOST"
                            val injectedCss = """
                                <style>
                                    body { background-color: $webViewBackgroundColor; color: $webViewTextColor; margin: 0; padding: 8px; }
                                    a { color: $webViewLinkColor; }
                                    img { max-width: 100%; height: auto; }
                                </style>
                            """.trimIndent()
                            val fullHtml = "<html><head>$injectedCss</head><body>${state.htmlContent}</body></html>"
                            binding.webviewArticleContent.loadDataWithBaseURL(baseUrl, fullHtml, "text/html; charset=utf-8", "UTF-8", null)
                            binding.webviewArticleContent.isVisible = true
                        } else {
                            binding.webviewArticleContent.isVisible = !state.isLoading
                        }

                        if (state.imageUrl != null) {
                            Glide.with(this@ArticleFragment)
                                .load(state.imageUrl)
                                .placeholder(R.drawable.ic_placeholder_image)
                                .error(R.drawable.ic_error_image)
                                .into(binding.imageArticleMain)
                            binding.imageArticleMain.isVisible = true
                        } else {
                            binding.imageArticleMain.isVisible = false
                        }
                    }
                }
            }
        }
    }

    private fun extractTitleFromPath(path: String?): String? {
        if (path == null) return null
        for (prefix in WIKI_ARTICLE_PATH_PREFIXES) {
            if (path.startsWith(prefix, ignoreCase = true) && path.length > prefix.length) {
                val titleSegment = path.substring(prefix.length)
                val decodedSegment = Uri.decode(titleSegment)
                return decodedSegment.replace('_', ' ')
            }
        }
        return null
    }

    private fun navigateToArticleByTitle(title: String) {
        Log.d(TAG, "Attempting to navigate to ArticleFragment for title: '$title'")
        try {
            val action = ArticleFragmentDirections.actionArticleFragmentSelf(
                articleTitle = title,
                articleId = null // Pass null for articleId when navigating by title
            )
            findNavController().navigate(action)
        } catch (e: Exception) {
            Log.e(TAG, "Navigation by title failed for '$title'", e)
            Toast.makeText(context, "Could not navigate to article: $title", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToArticleById(id: String) {
        Log.d(TAG, "Attempting to navigate to ArticleFragment for ID: '$id'")
        try {
            val action = ArticleFragmentDirections.actionArticleFragmentSelf(
                articleId = id,
                articleTitle = null // Pass null for articleTitle when navigating by ID
            )
            findNavController().navigate(action)
        } catch (e: Exception) {
            Log.e(TAG, "Navigation by ID failed for '$id'", e)
            Toast.makeText(context, "Could not navigate to article ID: $id", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class InternalLinkWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            Log.d(TAG, ">>> InternalLinkWebViewClient.shouldOverrideUrlLoading CALLED <<<")
            val urlString = request?.url?.toString()
            Log.d(TAG, "WebView attempting to load URL: $urlString")

            if (urlString == null) {
                Log.d(TAG, "URL is null, WebView will handle.")
                return false
            }

            val uri = Uri.parse(urlString)

            if (uri.host?.equals(OSRS_WIKI_BASE_URL_HOST, ignoreCase = true) == true) {
                val articleTitleFromPath = extractTitleFromPath(uri.path)
                val pageIdFromQuery = uri.getQueryParameter("pageid") ?: uri.getQueryParameter("curid")

                if (articleTitleFromPath != null) {
                    Log.d(TAG, "Internal OSRS Wiki article link detected by path. Title: '$articleTitleFromPath'. Navigating in-app by title.")
                    navigateToArticleByTitle(articleTitleFromPath)
                    return true
                } else if (pageIdFromQuery != null && pageIdFromQuery.all { Character.isDigit(it) } && pageIdFromQuery.isNotEmpty()) {
                    Log.d(TAG, "Internal OSRS Wiki link detected by query param. PageID: '$pageIdFromQuery'. Navigating in-app by ID.")
                    navigateToArticleById(pageIdFromQuery)
                    return true
                } else {
                    Log.d(TAG, "Internal OSRS Wiki link (non-standard article path or other resource): $urlString. Allowing WebView to load it.")
                    return false // Let WebView load other internal links (e.g., special pages, category pages)
                }
            } else {
                Log.d(TAG, "External link detected: $urlString. Attempting to open in external browser.")
                try {
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    requireContext().startActivity(intent)
                    return true
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "No application can handle this external link: $urlString", e)
                    Toast.makeText(requireContext(), "Cannot open link: No application found.", Toast.LENGTH_SHORT).show()
                    return false
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open external link $urlString", e)
                    return false
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Clearing binding and preparing to destroy WebView.")
        _binding?.webviewArticleContent?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
            Log.d(TAG, "WebView destroyed.")
        }
        _binding = null
        Log.d(TAG, "Binding is null.")
    }
}
