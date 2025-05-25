package com.omiyawaki.osrswiki.ui.article

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.omiyawaki.osrswiki.MainActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentArticleBinding
import com.omiyawaki.osrswiki.model.WikiArticle
import com.omiyawaki.osrswiki.network.transformHtml
import com.omiyawaki.osrswiki.ui.common.NavigationIconType // Added import
import com.omiyawaki.osrswiki.ui.common.ScreenConfiguration // Added import
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val ARG_ARTICLE_ID = "article_id"
private const val ARG_ARTICLE_TITLE = "article_title"

@AndroidEntryPoint
class ArticleFragment : Fragment(), ScreenConfiguration { // Implements ScreenConfiguration

    @Inject
    lateinit var assistedFactory: ArticleViewModel.ArticleViewModelAssistedFactory

    private val viewModel: ArticleViewModel by viewModels {
        ArticleViewModelFactory(
            assistedFactory,
            arguments?.getString(ARG_ARTICLE_ID),
            arguments?.getString(ARG_ARTICLE_TITLE)
        )
    }

    private var _binding: FragmentArticleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArticleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebView()
        observeViewModel()
        setupSaveButton()

        // Request MainActivity to update toolbar based on this fragment's configuration
        (activity as? MainActivity)?.updateToolbar(this)
    }

    private fun setupWebView() {
        binding.webviewArticleContent.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, url: String?): Boolean {
                if (url != null) {
                    // If it's a relative link (internal wiki link), try to navigate to a new ArticleFragment
                    if (url.startsWith("/")) {
                        val newTitle = url.substringAfterLast("/").replace("_", " ")
                        // TODO: This is a simplified navigation. Robust solution needed.
                        // Consider using the Router for this if article ID can be derived or isn't strictly needed.
                        (activity as? MainActivity)?.getRouter()?.navigateToArticle(null, newTitle)
                        return true
                    }
                    // If it's an external link, open in browser
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        Timber.e(e, "Error opening external link")
                        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                    }
                }
                return false
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBarArticle.isVisible = state.isLoading
                        state.article?.let { displayArticle(it) }
                        state.error?.let { displayError(it) }
                        // Update toolbar title via MainActivity when article data is available
                        (activity as? MainActivity)?.updateToolbarTitle(state.article?.title ?: getToolbarTitle { getString(it) })
                    }
                }
                launch {
                    viewModel.isSaved.collect { isSaved ->
                        updateSaveButton(isSaved)
                    }
                }
            }
        }
    }

    // Implement ScreenConfiguration
    override fun getToolbarTitle(getString: (id: Int) -> String): String {
        // Return a loading title or actual title from ViewModel if available
        return viewModel.uiState.value.article?.title ?: getString(R.string.title_article_loading)
    }

    override fun getNavigationIconType(): NavigationIconType {
        return NavigationIconType.BACK // Article screen typically has a back button
    }

    override fun hasCustomOptionsMenu(): Boolean {
        return true // Assuming ArticleFragment might have options like "Save", "Open in browser"
    }

    private fun displayArticle(article: WikiArticle) {
        binding.textviewArticleError.isVisible = false
        binding.webviewArticleContent.isVisible = true
        val transformedHtml = transformHtml(article.htmlContent, viewModel.isDarkTheme())
        binding.webviewArticleContent.loadDataWithBaseURL(
            "https://oldschool.runescape.wiki", // Base URL for resolving relative links
            transformedHtml,
            "text/html",
            "UTF-8",
            null
        )
        (activity as? MainActivity)?.updateToolbarTitle(article.title)
    }

    private fun displayError(errorMessage: String) {
        binding.webviewArticleContent.isVisible = false
        binding.textviewArticleError.isVisible = true
        binding.textviewArticleError.text = errorMessage
        Timber.e("Article display error: %s", errorMessage)
    }

    private fun setupSaveButton() {
        binding.bottomActionBarArticle.buttonSaveOffline.setOnClickListener {
            viewModel.toggleSaveState()
        }
    }

    private fun updateSaveButton(isSaved: Boolean) {
        val button = binding.bottomActionBarArticle.buttonSaveOffline
        if (isSaved) {
            button.text = getString(R.string.action_saved_offline)
            button.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_bookmark_filled_24, 0, 0)
            button.contentDescription = getString(R.string.cd_remove_from_offline)
        } else {
            button.text = getString(R.string.action_save_offline)
            button.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_bookmark_border_24, 0, 0)
            button.contentDescription = getString(R.string.cd_save_for_offline)
        }
        // Ensure the drawable tint is updated if necessary, e.g., after theme change or if not set by XML
        val colorPrimary = ContextCompat.getColor(requireContext(), R.color.primary) // Example, use your actual color attribute
        button.compoundDrawables[1]?.setTint(colorPrimary) // Index 1 is for drawableTop
    }


    override fun onDestroyView() {
        super.onDestroyView()
        // Important for WebView to prevent memory leaks
        binding.webviewArticleContent.destroy()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance(articleId: String?, articleTitle: String?) =
            ArticleFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ARTICLE_ID, articleId)
                    putString(ARG_ARTICLE_TITLE, articleTitle)
                }
            }
    }
}
