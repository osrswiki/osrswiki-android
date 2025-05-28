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

    private var pageIdArg: String? = null // Added to store pageId
    private var pageTitleArg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.d("PageFragment onCreate")

        arguments?.let {
            // Retrieve pageId using the key defined in nav_graph.xml
            pageIdArg = it.getString(ARG_PAGE_ID)
            pageTitleArg = it.getString(ARG_PAGE_TITLE)
            L.d("PageFragment created with ID: $pageIdArg, Title: $pageTitleArg")
        }

        // It's generally recommended to inject dependencies rather than accessing through application context directly.
        // This is something to address in later DI refactoring.
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

        updateUiFromViewModel() // Set initial UI

        // Use pageIdArg or pageTitleArg for loading content as appropriate
        // The current logic uses pageTitleArg. If pageId is more reliable or preferred, adjust here.
        pageTitleArg?.let { title ->
            // Existing load logic using title...
            // If pageId is available and preferred for loading, you might use:
            // pageIdArg?.let { id -> pageContentLoader.loadPageById(id) }
            // OR pass both to the loader if it can handle it.
            if (pageViewModel.uiState.title != title || (pageViewModel.uiState.htmlContent == null && !pageViewModel.uiState.isLoading && pageViewModel.uiState.error == null)) {
                L.i("Requesting to load page by title: $title (ID: $pageIdArg)")
                pageContentLoader.loadPageByTitle(title) // Consider if loadPageById is needed
            } else if (pageViewModel.uiState.title == title && (pageViewModel.uiState.isLoading || pageViewModel.uiState.error != null)) {
                L.d("Page '$title' is already loading or in error state. UI will reflect.")
            } else {
                L.d("Page '$title' data already present in ViewModel. UI will reflect.")
            }
        } ?: L.w("No page title provided to PageFragment for initial load (ID was: $pageIdArg).")


        binding.errorTextView.setOnClickListener {
            pageTitleArg?.let { title ->
                L.i("Retry button clicked for page: $title (ID: $pageIdArg)")
                pageContentLoader.loadPageByTitle(title, forceNetwork = true)
            }
        }
    }

    private fun updateUiFromViewModel() {
        L.d("PageFragment updateUiFromViewModel. Current state: ${pageViewModel.uiState}")
        val state = pageViewModel.uiState

        if (state.isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.pageContentTextView.visibility = View.GONE
            binding.errorTextView.visibility = View.GONE
            binding.pageTitleTextView.text = state.title ?: getString(R.string.label_loading)
        } else {
            binding.progressBar.visibility = View.GONE
        }

        state.error?.let { detailedErrorString ->
            L.e("Page load error (technical details): $detailedErrorString")
            binding.errorTextView.text = getString(R.string.page_load_error_network)
            binding.errorTextView.visibility = View.VISIBLE
            binding.pageContentTextView.visibility = View.GONE
            binding.pageTitleTextView.text = state.title ?: getString(R.string.label_error_loading_page)
        } ?: run {
            if (!state.isLoading) binding.errorTextView.visibility = View.GONE
        }

        if (!state.isLoading && state.error == null) {
            binding.pageContentTextView.visibility = View.VISIBLE
            binding.pageTitleTextView.text = state.title ?: getString(R.string.label_title_unavailable)
            binding.pageContentTextView.text = state.htmlContent?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(it)
                }
            } ?: getString(R.string.label_content_unavailable)
        } else if (!state.isLoading && state.error != null) {
            binding.pageContentTextView.visibility = View.GONE
        }
        L.i("PageFragment UI updated for title: '${state.title}', isLoading: ${state.isLoading}, error: ${state.error != null}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        L.d("PageFragment onDestroyView")
        _binding = null
    }

    companion object {
        // Key for pageId MUST match android:name in nav_graph's <argument> tag
        private const val ARG_PAGE_ID = "pageId"
        // Key for pageTitle MUST match android:name in nav_graph's <argument> tag
        // Assuming nav_graph uses "pageTitle" for the argument name. If it was "page_title", adjust this constant.
        private const val ARG_PAGE_TITLE = "pageTitle"

        // This newInstance method is likely not used if navigation occurs via NavController actions/destinations
        // defined in XML and triggered by your Router.
        // If it were used, it should also accept and set pageId.
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
