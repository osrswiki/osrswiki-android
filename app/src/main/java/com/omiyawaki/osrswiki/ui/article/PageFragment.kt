package com.omiyawaki.osrswiki.ui.article

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.OSRSWikiApplication // Your Application class
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.data.repository.ArticleRepository
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.page.PageViewModel
import com.omiyawaki.osrswiki.util.log.L
// Removed: import dagger.hilt.android.EntryPointAccessors

class PageFragment : Fragment() {

    private var _binding: FragmentPageBinding? = null
    private val binding get() = _binding!!

    private val pageViewModel = PageViewModel()
    private lateinit var articleRepository: ArticleRepository // Will be obtained from Application
    private lateinit var articleContentLoader: ArticleContentLoader

    private var articleTitleArg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.d("PageFragment onCreate")

        arguments?.let {
            articleTitleArg = it.getString(ARG_ARTICLE_TITLE)
        }

        // Retrieve ArticleRepository directly from the Application instance
        // Ensure OSRSWikiApplication class has a public 'articleRepository' property
        articleRepository = (requireActivity().applicationContext as OSRSWikiApplication).articleRepository

        articleContentLoader = ArticleContentLoader(
            articleRepository,
            pageViewModel,
            lifecycleScope,
            onStateUpdated = {
                updateUiFromViewModel()
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        L.d("PageFragment onCreateView")
        _binding = FragmentPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("PageFragment onViewCreated. Article title from arg: $articleTitleArg")

        updateUiFromViewModel() // Set initial UI

        articleTitleArg?.let { title ->
            if (pageViewModel.uiState.title != title || pageViewModel.uiState.htmlContent == null && !pageViewModel.uiState.isLoading && pageViewModel.uiState.error == null) {
                 L.i("Requesting to load article: $title")
                articleContentLoader.loadArticleByTitle(title)
            } else if (pageViewModel.uiState.title == title && (pageViewModel.uiState.isLoading || pageViewModel.uiState.error != null)) {
                L.d("Article '$title' is already loading or in error state. UI will reflect.")
            } else {
                L.d("Article '$title' data already present in ViewModel. UI will reflect.")
            }
        } ?: L.w("No article title provided to PageFragment.")

        binding.errorTextView.setOnClickListener {
            articleTitleArg?.let { title ->
                L.i("Retry button clicked for article: $title")
                articleContentLoader.loadArticleByTitle(title, forceNetwork = true)
            }
        }
    }

    private fun updateUiFromViewModel() {
        L.d("PageFragment updateUiFromViewModel. Current state: ${pageViewModel.uiState}")
        val state = pageViewModel.uiState

        if (state.isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.articleContentTextView.visibility = View.GONE
            binding.errorTextView.visibility = View.GONE
            binding.articleTitleTextView.text = state.title ?: getString(R.string.label_loading)
        } else {
            binding.progressBar.visibility = View.GONE
        }

        state.error?.let {
            binding.errorTextView.text = it
            binding.errorTextView.visibility = View.VISIBLE
            binding.articleContentTextView.visibility = View.GONE
            binding.articleTitleTextView.text = state.title ?: getString(R.string.label_error_loading_article)
        } ?: run {
            if (!state.isLoading) binding.errorTextView.visibility = View.GONE
        }

        if (!state.isLoading && state.error == null) {
            binding.articleContentTextView.visibility = View.VISIBLE
            binding.articleTitleTextView.text = state.title ?: getString(R.string.label_title_unavailable)
            binding.articleContentTextView.text = state.htmlContent?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(it)
                }
            } ?: getString(R.string.label_content_unavailable)
        } else if (!state.isLoading && state.error != null) {
            binding.articleContentTextView.visibility = View.GONE
        }
        L.i("PageFragment UI updated for title: '${state.title}', isLoading: ${state.isLoading}, error: ${state.error != null}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        L.d("PageFragment onDestroyView")
        _binding = null
    }

    companion object {
        private const val ARG_ARTICLE_TITLE = "article_title"

        @JvmStatic
        fun newInstance(articleTitle: String): PageFragment {
            L.d("PageFragment newInstance for title: $articleTitle")
            return PageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ARTICLE_TITLE, articleTitle)
                }
            }
        }
    }
}
