package com.omiyawaki.osrswiki.page

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.util.log.L

class PageFragment : Fragment() {

    private var _binding: FragmentPageBinding? = null
    private val binding get() = _binding!!

    private val pageViewModel = PageViewModel() // Consider Hilt/ViewModelProvider for ViewModel instantiation
    private lateinit var pageRepository: PageRepository
    private lateinit var pageContentLoader: PageContentLoader

    private var pageIdArg: String? = null
    private var pageTitleArg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.d("PageFragment onCreate")

        arguments?.let {
            pageIdArg = it.getString(ARG_PAGE_ID)
            pageTitleArg = it.getString(ARG_PAGE_TITLE)
            L.d("PageFragment created with ID: $pageIdArg, Title: $pageTitleArg")
        }

        pageRepository = (requireActivity().applicationContext as OSRSWikiApp).pageRepository

        pageContentLoader = PageContentLoader(
            pageRepository,
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
        L.d("PageFragment onViewCreated. Page ID: $pageIdArg, Page Title: $pageTitleArg")

        updateUiFromViewModel()
        initiatePageLoad(forceNetwork = false)

        binding.errorTextView.setOnClickListener {
            L.i("Retry button clicked. pageIdArg: $pageIdArg, pageTitleArg: $pageTitleArg. Forcing network.")
            initiatePageLoad(forceNetwork = true)
        }
    }

    private fun initiatePageLoad(forceNetwork: Boolean = false) {
        var idToLoad: Int? = null
        if (!pageIdArg.isNullOrBlank()) {
            try {
                idToLoad = pageIdArg!!.toInt()
                // Basic validation: ensure ID is positive, if that's a rule for your page IDs.
                // if (idToLoad <= 0) {
                //     L.w("pageIdArg '$pageIdArg' is not a positive integer. Invalidating.")
                //     idToLoad = null
                // }
            } catch (e: NumberFormatException) {
                L.w("pageIdArg '$pageIdArg' is not a valid integer. Will try title.")
                idToLoad = null // Ensure idToLoad is null if parsing fails
            }
        }

        val currentViewModelPageId = pageViewModel.uiState.pageId
        val currentViewModelTitle = pageViewModel.uiState.title
        val contentAlreadyLoaded = pageViewModel.uiState.htmlContent != null && pageViewModel.uiState.error == null
        val alreadyLoadingOrError = pageViewModel.uiState.isLoading || pageViewModel.uiState.error != null

        if (idToLoad != null) {
            if (forceNetwork || !(currentViewModelPageId == idToLoad && contentAlreadyLoaded)) {
                if (currentViewModelPageId == idToLoad && alreadyLoadingOrError && !forceNetwork) {
                    L.d("Page with ID '$idToLoad' is already loading or in error state. UI will reflect. Not forcing reload.")
                } else {
                    L.i("Requesting to load page by ID: $idToLoad (Title arg was: '$pageTitleArg')")
                    pageContentLoader.loadPageById(idToLoad, pageTitleArg, forceNetwork)
                    return
                }
            } else {
                L.d("Page with ID '$idToLoad' data already present in ViewModel and not forcing network. UI will reflect.")
                updateUiFromViewModel()
                return
            }
        }
        else if (!pageTitleArg.isNullOrBlank()) {
            if (forceNetwork || !(currentViewModelTitle == pageTitleArg && contentAlreadyLoaded)) {
                 if (currentViewModelTitle == pageTitleArg && alreadyLoadingOrError && !forceNetwork) {
                     L.d("Page with title '$pageTitleArg' is already loading or in error state. UI will reflect. Not forcing reload.")
                 } else {
                    L.i("Requesting to load page by title: '$pageTitleArg' (ID arg was: '$pageIdArg')")
                    pageContentLoader.loadPageByTitle(pageTitleArg!!, forceNetwork)
                    return
                 }
            } else {
                L.d("Page with title '$pageTitleArg' data already present in ViewModel and not forcing network. UI will reflect.")
                updateUiFromViewModel()
                return
            }
        }
        else {
            L.e("Cannot load page: No valid pageId or pageTitle provided. pageIdArg: '$pageIdArg', pageTitleArg: '$pageTitleArg'")
            pageViewModel.uiState = PageUiState(
                isLoading = false,
                error = getString(R.string.error_no_article_identifier),
                title = getString(R.string.title_page_not_specified), // Using more specific error title
                pageId = null
            )
            updateUiFromViewModel()
        }
    }

    private fun updateUiFromViewModel() {
        L.d("PageFragment updateUiFromViewModel. Current state: ${pageViewModel.uiState}")
        val state = pageViewModel.uiState

        // Handle loading state title
        val displayTitleText = if (state.isLoading) {
            state.title ?: getString(R.string.label_loading)
        } else {
            // Handle loaded or error state title (strip HTML for successful load)
            state.title?.let { titleHtml ->
                if (state.error == null) { // Only strip HTML if no error and title is present
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        Html.fromHtml(titleHtml, Html.FROM_HTML_MODE_LEGACY).toString()
                    } else {
                        @Suppress("DEPRECATION")
                        Html.fromHtml(titleHtml).toString()
                    }
                } else {
                    titleHtml // For error states, title is already set (e.g., by "no identifier" logic or PageContentLoader)
                }
            } ?: if (state.error != null) {
                getString(R.string.label_error_loading_page) // Generic error title if state.title was null during error
            } else {
                getString(R.string.label_title_unavailable) // Title unavailable for success state
            }
        }
        binding.pageTitleTextView.text = displayTitleText

        if (state.isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.pageContentTextView.visibility = View.GONE
            binding.errorTextView.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.GONE
        }

        state.error?.let { detailedErrorString ->
            L.e("Page load error (technical details): $detailedErrorString")
            // binding.pageTitleTextView.text is already set above with appropriate error title
            binding.errorTextView.text = detailedErrorString // MODIFIED LINE: Use the detailed error string
            binding.errorTextView.visibility = View.VISIBLE
            binding.pageContentTextView.visibility = View.GONE
        } ?: run {
            if (!state.isLoading) binding.errorTextView.visibility = View.GONE
        }

        if (!state.isLoading && state.error == null) {
            binding.pageContentTextView.visibility = View.VISIBLE
            binding.pageContentTextView.text = state.htmlContent?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(it)
                }
            } ?: getString(R.string.label_content_unavailable)
        } else if (!state.isLoading && state.error != null) {
            // Content already hidden by error block logic above
        }
        L.i("PageFragment UI updated for title: '${binding.pageTitleTextView.text}', pageId: ${state.pageId}, isLoading: ${state.isLoading}, error: ${state.error != null}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        L.d("PageFragment onDestroyView")
        _binding = null
    }

    companion object {
        private const val ARG_PAGE_ID = "pageId"
        private const val ARG_PAGE_TITLE = "pageTitle"

        @JvmStatic
        fun newInstance(pageId: String?, pageTitle: String?): PageFragment {
            L.d("PageFragment newInstance for ID: $pageId, Title: $pageTitle")
            return PageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PAGE_ID, pageId)
                    putString(ARG_PAGE_TITLE, pageTitle)
                }
            }
        }
    }
}
