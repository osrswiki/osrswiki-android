package com.omiyawaki.osrswiki.ui.article

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.omiyawaki.osrswiki.MainActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentArticleBinding
import com.omiyawaki.osrswiki.data.repository.ArticleRepository
import com.omiyawaki.osrswiki.network.transformHtml // Assuming this path and function are correct
import com.omiyawaki.osrswiki.page.ArticleDisplayData
import com.omiyawaki.osrswiki.page.PageViewModel
import com.omiyawaki.osrswiki.ui.common.NavigationIconType
import com.omiyawaki.osrswiki.ui.common.ScreenConfiguration
import com.omiyawaki.osrswiki.util.Result
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val ARG_ARTICLE_ID = "article_id"
private const val ARG_ARTICLE_TITLE = "article_title"

/*
 necesarioStrings to add to your res/values/strings.xml:

<string name="error_article_not_specified">Article ID or title not specified.</string>
<string name="error_loading_article_generic">An error occurred while loading the article.</string>
<string name="title_article_error">Error</string>
*/

@AndroidEntryPoint
class PageFragment : Fragment(), ScreenConfiguration {

    @Inject
    lateinit var articleRepository: ArticleRepository

    private lateinit var pageViewModel: PageViewModel

    private var _binding: FragmentArticleBinding? = null
    private val binding get() = _binding!!

    private var currentArticlePageId: Int? = null
    private var currentArticleTitle: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = PageViewModel()

        arguments?.let {
            val articleIdStr = it.getString(ARG_ARTICLE_ID)
            currentArticlePageId = articleIdStr?.toIntOrNull()
            currentArticleTitle = it.getString(ARG_ARTICLE_TITLE)
            if (currentArticlePageId == null && currentArticleTitle == null) {
                Timber.e("PageFragment created without article ID or title.")
            }
        }
    }

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
        setupSaveButtonListener()
        loadArticleData()
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
                        Timber.e(e, "Error opening external link: $url")
                        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                    }
                }
                return false
            }
        }
    }

    private fun loadArticleData() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                pageViewModel.isLoading = true
                renderPageContent()

                val flowToCollect = when {
                    currentArticlePageId != null -> {
                        Timber.d("Loading article by ID: $currentArticlePageId")
                        articleRepository.getArticle(currentArticlePageId!!, pageViewModel.forceNetwork)
                    }
                    currentArticleTitle != null -> {
                        Timber.d("Loading article by Title: $currentArticleTitle")
                        articleRepository.getArticleByTitle(currentArticleTitle!!, pageViewModel.forceNetwork)
                    }
                    else -> {
                        Timber.e("No article ID or title provided to load.")
                        pageViewModel.isLoading = false
                        pageViewModel.errorMessage = getString(R.string.error_article_not_specified)
                        renderPageContent()
                        return@repeatOnLifecycle
                    }
                }

                flowToCollect.collectLatest { result ->
                    when (result) {
                        is Result.Loading -> {
                            pageViewModel.isLoading = true
                            pageViewModel.errorMessage = null
                        }
                        is Result.Success -> {
                            val uiState = result.data
                            pageViewModel.isLoading = false
                            pageViewModel.errorMessage = null
                            if (uiState.pageId != null && uiState.title != null && uiState.htmlContent != null) {
                                pageViewModel.articleData = ArticleDisplayData(
                                    pageId = uiState.pageId,
                                    title = uiState.title,
                                    htmlContent = uiState.htmlContent,
                                    imageUrl = uiState.imageUrl
                                )
                                currentArticlePageId = uiState.pageId
                                currentArticleTitle = uiState.title
                                pageViewModel.isCurrentlyMarkedAsSaved = uiState.isCurrentlyOffline
                            } else {
                                pageViewModel.errorMessage = uiState.error ?: getString(R.string.error_loading_article_generic)
                                Timber.e("Article loaded successfully but essential data missing: $uiState")
                            }
                        }
                        is Result.Error -> {
                            pageViewModel.isLoading = false
                            pageViewModel.errorMessage = result.message ?: getString(R.string.error_loading_article_generic)
                            // Corrected to use result.throwable
                            Timber.e(result.throwable, "Error loading article: ${result.message}")
                        }
                    }
                    renderPageContent()
                }
            }
        }
    }

    private fun renderPageContent() {
        binding.progressBarArticle.isVisible = pageViewModel.isLoading

        if (pageViewModel.errorMessage != null) {
            binding.webviewArticleContent.isVisible = false
            binding.textviewArticleError.isVisible = true
            binding.textviewArticleError.text = pageViewModel.errorMessage
            (activity as? MainActivity)?.updateToolbarTitle(getString(R.string.title_article_error))
        } else {
            binding.textviewArticleError.isVisible = false
        }

        pageViewModel.articleData?.let { article ->
            if (!pageViewModel.isLoading && pageViewModel.errorMessage == null) {
                binding.webviewArticleContent.isVisible = true
                val isDarkTheme = determineDarkTheme()
                val transformedHtml = transformHtml(article.htmlContent, isDarkTheme)
                binding.webviewArticleContent.loadDataWithBaseURL(
                    "https://oldschool.runescape.wiki",
                    transformedHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
                (activity as? MainActivity)?.updateToolbarTitle(article.title)
                if (currentArticlePageId == null) currentArticlePageId = article.pageId
            }
        } ?: run {
            if (!pageViewModel.isLoading && pageViewModel.errorMessage == null) {
                binding.webviewArticleContent.isVisible = false
            }
        }
        updateSaveButtonState()
    }

    private fun determineDarkTheme(): Boolean {
        // val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        // return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        return false
    }

    private fun setupSaveButtonListener() {
        // Corrected to use direct binding ID
        binding.buttonSaveOffline.setOnClickListener {
            val articleToSaveOrRemove = pageViewModel.articleData
            if (articleToSaveOrRemove == null || currentArticlePageId == null) {
                Toast.makeText(context, "Article data not available.", Toast.LENGTH_SHORT).show()
                Timber.w("Save button clicked but articleData or pageId is null. ArticleData: $articleToSaveOrRemove, PageId: $currentArticlePageId")
                return@setOnClickListener
            }

            val pageId = currentArticlePageId!!
            val title = articleToSaveOrRemove.title

            viewLifecycleOwner.lifecycleScope.launch {
                val resultAction = if (pageViewModel.isCurrentlyMarkedAsSaved) {
                    Timber.d("Attempting to remove article $pageId ('$title') from offline.")
                    articleRepository.removeArticleOffline(pageId)
                } else {
                    Timber.d("Attempting to save article $pageId ('$title') offline.")
                    articleRepository.saveArticleOffline(pageId, title)
                }

                when (resultAction) { // Renamed 'result' to 'resultAction' to avoid conflict
                    is Result.Success -> {
                        pageViewModel.isCurrentlyMarkedAsSaved = !pageViewModel.isCurrentlyMarkedAsSaved
                        updateSaveButtonState()
                        val message = if (pageViewModel.isCurrentlyMarkedAsSaved) "Article saved offline" else "Article removed from offline"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        Timber.i("$message for pageId $pageId")
                    }
                    is Result.Error -> {
                        val errorMessage = resultAction.message ?: "Failed to update offline status"
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        // Corrected to use resultAction.throwable
                        Timber.e(resultAction.throwable, "Error updating offline status for pageId $pageId: $errorMessage")
                    }
                    is Result.Loading -> {
                        // Optional: Show loading state on button
                    }
                }
            }
        }
    }

    private fun updateSaveButtonState() {
        // Corrected to use direct binding ID
        val button = binding.buttonSaveOffline
        button.isEnabled = pageViewModel.articleData != null && pageViewModel.errorMessage == null

        if (pageViewModel.isCurrentlyMarkedAsSaved) {
            button.text = getString(R.string.action_saved_offline) // Ensure this string exists
            button.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_bookmark_filled_24, 0, 0)
            button.contentDescription = getString(R.string.cd_remove_from_offline) // Ensure this string exists
        } else {
            button.text = getString(R.string.action_save_offline) // Ensure this string exists
            button.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_bookmark_border_24, 0, 0)
            button.contentDescription = getString(R.string.cd_save_for_offline) // Ensure this string exists
        }
    }

    override fun getToolbarTitle(getString: (id: Int) -> String): String {
        return pageViewModel.articleData?.title ?: getString(R.string.title_article_loading) // Ensure this string exists
    }

    override fun getNavigationIconType(): NavigationIconType {
        return NavigationIconType.BACK
    }

    override fun hasCustomOptionsMenu(): Boolean {
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.webviewArticleContent.destroy()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance(articleId: String?, articleTitle: String?) =
            PageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ARTICLE_ID, articleId)
                    putString(ARG_ARTICLE_TITLE, articleTitle)
                }
            }
    }
}
