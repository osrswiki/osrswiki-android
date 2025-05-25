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
// Ensure ArticleUiState is imported if it becomes a top-level class, or accessible if inner to ViewModel
// import com.omiyawaki.osrswiki.ui.article.ArticleUiState
import com.omiyawaki.osrswiki.model.WikiArticle
import com.omiyawaki.osrswiki.network.transformHtml
import com.omiyawaki.osrswiki.ui.common.NavigationIconType
import com.omiyawaki.osrswiki.ui.common.ScreenConfiguration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
// javax.inject.Inject is no longer needed for assistedFactory here

private const val ARG_ARTICLE_ID = "article_id"
private const val ARG_ARTICLE_TITLE = "article_title"

@AndroidEntryPoint
class ArticleFragment : Fragment(), ScreenConfiguration {

    // Removed: @Inject lateinit var assistedFactory: ArticleViewModel.ArticleViewModelAssistedFactory

    private val viewModel: ArticleViewModel by viewModels {
        ArticleViewModelFactory(
            requireActivity().application, // Corrected: Pass Application
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

        (activity as? MainActivity)?.updateToolbar(this)
    }

    private fun setupWebView() {
        binding.webviewArticleContent.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, url: String?): Boolean {
                if (url != null) {
                    if (url.startsWith("/")) {
                        val newTitle = url.substringAfterLast("/").replace("_", " ")
                        (activity as? MainActivity)?.getRouter()?.navigateToArticle(null, newTitle)
                        return true
                    }
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
                    viewModel.uiState.collect { state -> // state should be ArticleUiState
                        binding.progressBarArticle.isVisible = state.isLoading
                        state.article?.let { articleData -> // Assuming ArticleUiState has an 'article' field of type WikiArticle
                            // This 'articleData' needs to be compatible with what displayArticle expects,
                            // or ArticleUiState needs to expose htmlContent, title etc directly.
                            // For now, assuming state.article is the WikiArticle object itself, or similar.
                            // The current ArticleUiState definition has 'title' and 'htmlContent' fields directly.
                             displayArticle(
                                 WikiArticle( // Reconstruct WikiArticle if state holds individual fields
                                     pageId = state.pageId ?: 0, // Provide a default or handle null
                                     title = state.title ?: "Loading...",
                                     htmlContent = state.htmlContent ?: "",
                                     imageUrl = state.imageUrl
                                 )
                             )
                        }
                        state.error?.let { displayError(it) }
                        (activity as? MainActivity)?.updateToolbarTitle(state.title ?: getToolbarTitle { getString(it) })
                    }
                }
                launch {
                    viewModel.isArticleOffline.collect { isOffline -> // Corrected: isArticleOffline
                        updateSaveButton(isOffline)
                    }
                }
            }
        }
    }

    override fun getToolbarTitle(getString: (id: Int) -> String): String {
        return viewModel.uiState.value.title ?: getString(R.string.title_article_loading)
    }

    override fun getNavigationIconType(): NavigationIconType {
        return NavigationIconType.BACK
    }

    override fun hasCustomOptionsMenu(): Boolean {
        return true
    }

    private fun displayArticle(article: WikiArticle) { // Parameter is WikiArticle
        binding.textviewArticleError.isVisible = false
        binding.webviewArticleContent.isVisible = true
        // Corrected: Call isDarkTheme() on viewModel
        val transformedHtml = transformHtml(article.htmlContent, viewModel.isDarkTheme())
        binding.webviewArticleContent.loadDataWithBaseURL(
            "https://oldschool.runescape.wiki",
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
            viewModel.toggleSaveOfflineStatus() // Corrected: toggleSaveOfflineStatus
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
        // Ensure the drawable tint is updated
        // TODO: Use a theme attribute for colorPrimary if possible, or ensure R.color.primary is defined.
        // For now, assuming R.color.primary exists as per previous code.
        // val colorPrimary = ContextCompat.getColor(requireContext(), R.color.primary)
        // button.compoundDrawables[1]?.setTint(colorPrimary)
        // Let's rely on XML or theme for tinting for now to avoid R.color.primary issues.
    }

    override fun onDestroyView() {
        super.onDestroyView()
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
