package com.omiyawaki.osrswiki.ui.article

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Resources
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
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.omiyawaki.osrswiki.MainActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentArticleBinding
import com.omiyawaki.osrswiki.navigation.NavigationIconType
import com.omiyawaki.osrswiki.navigation.Router
import com.omiyawaki.osrswiki.navigation.ScreenConfiguration
import kotlinx.coroutines.launch

class ArticleFragment : Fragment(), ScreenConfiguration {

    private var _binding: FragmentArticleBinding? = null
    private val binding get() = _binding!! // This should be used carefully, only between onCreateView and onDestroyView

    private var articleIdArg: String? = null
    private var articleTitleArg: String? = null
    private var router: Router? = null

    // Updated to pass arguments to the ViewModel.
    // Assumes ArticleViewModelFactory is updated to accept articleId and articleTitle (both nullable).
    private val viewModel: ArticleViewModel by viewModels {
        ArticleViewModelFactory(
            requireActivity().application,
            articleIdArg,
            articleTitleArg
        )
    }

    private val webViewBackgroundColor = "#E2DBC8"
    private val webViewTextColor = "#333333"
    private val webViewLinkColor = "#0645AD"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            articleIdArg = it.getString(ARG_ARTICLE_ID)
            articleTitleArg = it.getString(ARG_ARTICLE_TITLE)
        }
        Log.d(TAG, "onCreate: Fragment created. Args - articleId: '$articleIdArg', articleTitle: '$articleTitleArg'")
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

        if (activity is MainActivity) {
            router = (activity as MainActivity).getRouter()
        } else {
            Log.e(TAG, "Host activity is not MainActivity, router cannot be obtained.")
        }

        setupWebView()
        observeUiState()
        setupSaveButtonActions()
        observeOfflineButtonState()
        observeOfflineActionMessages()
    }

    // Implementation for ScreenConfiguration
    override fun getScreenTitle(resources: Resources): String? {
        return articleTitleArg ?: articleIdArg ?: resources.getString(R.string.title_article_loading) // Fallback to a generic loading title
    }

    // Implementation for ScreenConfiguration
    override fun getNavigationIconType(): NavigationIconType {
        return NavigationIconType.BACK
    }

    private fun setupWebView() {
        binding.webviewArticleContent.webViewClient = InternalLinkWebViewClient()
        binding.webviewArticleContent.setBackgroundColor(webViewBackgroundColor.toColorInt())
        // Ensure JavaScript is enabled if needed by the content or interaction, but be mindful of security.
        // binding.webviewArticleContent.settings.javaScriptEnabled = true
        Log.d(TAG, "WebView setup complete with InternalLinkWebViewClient.")
    }

    private fun observeUiState() {
        Log.d(TAG, "Setting up UI state observer for Args - articleId: '$articleIdArg', articleTitle: '$articleTitleArg'")
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.d(TAG, "New UI State received: isLoading=${state.isLoading}, pageId=${state.pageId}, title=${state.title}, error=${state.error}")
                    binding.progressBarArticle.isVisible = state.isLoading

                    // Update MainActivity's toolbar title if a new title comes from the ViewModel
                    val newToolbarTitle = state.title ?: articleTitleArg ?: articleIdArg // Determine the best title
                    (activity as? MainActivity)?.updateToolbarTitle(newToolbarTitle)


                    val isArticleContentAvailable = !state.isLoading && state.error == null && state.htmlContent != null && state.pageId != null
                    binding.bottomActionBarArticle.isVisible = isArticleContentAvailable
                    binding.buttonSaveOffline.isEnabled = state.pageId != null

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
                            binding.webviewArticleContent.loadDataWithBaseURL(baseUrl, fullHtml, "text/html; charset=utf-8", "UTF-8", baseUrl)
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

    private fun setupSaveButtonActions() {
        binding.buttonSaveOffline.setOnClickListener {
            Log.d(TAG, "Save for offline button clicked.")
            viewModel.toggleSaveOfflineStatus()
        }
    }

    private fun observeOfflineButtonState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isArticleOffline.collect { isOffline ->
                    Log.d(TAG, "Offline status received in Fragment: $isOffline")
                    if (isOffline) {
                        binding.buttonSaveOffline.text = getString(R.string.action_saved_offline)
                        binding.buttonSaveOffline.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_bookmark_filled_24, 0, 0)
                    } else {
                        binding.buttonSaveOffline.text = getString(R.string.action_save_offline)
                        binding.buttonSaveOffline.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_bookmark_border_24, 0, 0)
                    }
                }
            }
        }
    }

    private fun observeOfflineActionMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.offlineActionMessage.collect { event ->
                    event?.getContentIfNotHandled()?.let { userMessage ->
                        val messageText = when (userMessage) {
                            is ArticleOfflineUserMessage.Success -> userMessage.message
                            is ArticleOfflineUserMessage.Error -> userMessage.message
                        }
                        val snackbar = Snackbar.make(binding.root, messageText, Snackbar.LENGTH_LONG)
                        if (binding.bottomActionBarArticle.isVisible) {
                            snackbar.anchorView = binding.bottomActionBarArticle
                        }
                        snackbar.show()
                        Log.d(TAG, "Snackbar message shown: $messageText")
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
        // Assumes Router interface and its implementation (AppRouterImpl) will have this method.
        router?.navigateToArticle(articleTitle = title, articleId = null) ?: run {
            Log.e(TAG, "Router not available for navigation by title: '$title'")
            Toast.makeText(context, "Navigation error: Could not navigate to article: $title", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToArticleById(id: String) {
        Log.d(TAG, "Attempting to navigate to ArticleFragment for ID: '$id'")
        // Assumes Router interface and its implementation (AppRouterImpl) will have this method.
        router?.navigateToArticle(articleTitle = null, articleId = id) ?: run {
            Log.e(TAG, "Router not available for navigation by ID: '$id'")
            Toast.makeText(context, "Navigation error: Could not navigate to article ID: $id", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class InternalLinkWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            Log.d(TAG, ">>> InternalLinkWebViewClient.shouldOverrideUrlLoading CALLED <<<")
            val urlString = request?.url?.toString()
            Log.d(TAG, "WebView attempting to load URL: $urlString")

            if (urlString == null) {
                Log.d(TAG, "URL is null, WebView will handle.")
                return false // Let WebView handle it
            }

            val uri = urlString.toUri()

            if (uri.host?.equals(OSRS_WIKI_BASE_URL_HOST, ignoreCase = true) == true) {
                val articleTitleFromPath = extractTitleFromPath(uri.path)
                val pageIdFromQuery = uri.getQueryParameter("pageid") ?: uri.getQueryParameter("curid")

                if (articleTitleFromPath != null) {
                    Log.d(TAG, "Internal OSRS Wiki article link detected by path. Title: '$articleTitleFromPath'. Navigating in-app by title.")
                    navigateToArticleByTitle(articleTitleFromPath)
                    return true // Indicate that the URL loading is handled
                } else if (pageIdFromQuery != null && pageIdFromQuery.all { Character.isDigit(it) } && pageIdFromQuery.isNotEmpty()) {
                    Log.d(TAG, "Internal OSRS Wiki link detected by query param. PageID: '$pageIdFromQuery'. Navigating in-app by ID.")
                    navigateToArticleById(pageIdFromQuery)
                    return true // Indicate that the URL loading is handled
                } else {
                    Log.d(TAG, "Internal OSRS Wiki link (non-standard article path or other resource): $urlString. Allowing WebView to load it.")
                    return false // Let WebView load this internal, non-article link
                }
            } else {
                Log.d(TAG, "External link detected: $urlString. Attempting to open in external browser.")
                try {
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    requireContext().startActivity(intent)
                    return true // Indicate that the URL loading is handled
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "No application can handle this external link: $urlString", e)
                    Toast.makeText(requireContext(), "Cannot open link: No application found.", Toast.LENGTH_SHORT).show()
                    return false // Could not handle, let WebView try or show error
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open external link $urlString", e)
                    return false // Could not handle
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Clearing binding and preparing to destroy WebView.")
        _binding?.webviewArticleContent?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView) // Crucial for preventing memory leaks
            webView.stopLoading()
            webView.onPause() // Pause JavaScript execution
            webView.clearHistory()
            webView.destroy()
            Log.d(TAG, "WebView destroyed.")
        }
        _binding = null
        Log.d(TAG, "Binding is null.")
    }

    companion object {
        private const val TAG = "ArticleFragment" // Kept as private

        // Argument keys
        private const val ARG_ARTICLE_ID = "com.omiyawaki.osrswiki.ui.article.ARTICLE_ID"
        private const val ARG_ARTICLE_TITLE = "com.omiyawaki.osrswiki.ui.article.ARTICLE_TITLE"

        // Base URL and path prefixes for internal link detection
        private const val OSRS_WIKI_BASE_URL_HOST = "oldschool.runescape.wiki"
        private val WIKI_ARTICLE_PATH_PREFIXES = listOf("/w/", "/wiki/")

        /**
         * Factory method to create a new instance of this fragment.
         * @param articleId The ID of the article to load (optional).
         * @param articleTitle The title of the article to load (optional, can be used if ID is not known).
         * @return A new instance of fragment ArticleFragment.
         */
        @JvmStatic
        fun newInstance(articleId: String?, articleTitle: String?): ArticleFragment {
            val fragment = ArticleFragment()
            val args = Bundle()
            args.putString(ARG_ARTICLE_ID, articleId)
            args.putString(ARG_ARTICLE_TITLE, articleTitle)
            fragment.arguments = args
            return fragment
        }
    }
}
