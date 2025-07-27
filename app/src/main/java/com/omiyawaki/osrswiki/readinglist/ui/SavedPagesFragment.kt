package com.omiyawaki.osrswiki.readinglist.ui

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider // Added for manual ViewModel instantiation
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.database.AppDatabase // For accessing DAO
import com.omiyawaki.osrswiki.databinding.FragmentSavedPagesBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry // Added import
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.page.PageActivity
import com.omiyawaki.osrswiki.page.PageFragment
import com.omiyawaki.osrswiki.page.PageTitle
import com.omiyawaki.osrswiki.readinglist.adapter.SavedPagesAdapter
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.repository.SavedPagesRepository // For manual Repository instantiation
import com.omiyawaki.osrswiki.readinglist.viewmodel.SavedPagesViewModel
import com.omiyawaki.osrswiki.readinglist.viewmodel.SavedPagesViewModelFactory // For ViewModel factory
import com.omiyawaki.osrswiki.savedpages.SavedPageSyncWorker
// import dagger.hilt.android.AndroidEntryPoint // Removed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// @AndroidEntryPoint // Removed
class SavedPagesFragment : Fragment() {

    private var _binding: FragmentSavedPagesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SavedPagesViewModel by viewModels {
        // Manually create the ViewModel using the factory
        val readingListPageDao = AppDatabase.instance.readingListPageDao()
        val repository = SavedPagesRepository(readingListPageDao)
        SavedPagesViewModelFactory(repository)
    }

    private lateinit var savedPagesAdapter: SavedPagesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedPagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeSavedPages()
        setupMenu()
    }

    private fun setupRecyclerView() {
        savedPagesAdapter = SavedPagesAdapter { readingListPage ->
            navigateToPage(readingListPage)
        }
        binding.savedPagesRecyclerView.apply {
            adapter = savedPagesAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun observeSavedPages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.savedPages.collect { pages ->
                    savedPagesAdapter.submitList(pages)
                    binding.emptyStateTextView.isVisible = pages.isEmpty()
                    binding.savedPagesRecyclerView.isVisible = pages.isNotEmpty()
                }
            }
        }
    }

    private fun navigateToPage(savedPage: ReadingListPage) {
        val pageTitle = savedPage.apiTitle
        val pageId = savedPage.mediaWikiPageId?.toString() // Use the stored MediaWiki page ID if available

        Log.d("SavedPagesFragment", "Opening PageActivity for saved page: '$pageTitle', pageId: '$pageId', source: SOURCE_SAVED_PAGE")

        val intent = PageActivity.newIntent(
            context = requireContext(),
            pageTitle = pageTitle,
            pageId = pageId,
            source = HistoryEntry.SOURCE_SAVED_PAGE,
            snippet = savedPage.description,
            thumbnailUrl = savedPage.thumbUrl
        )
        
        startActivity(intent)
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_saved_pages, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_delete_all -> {
                        showDeleteAllConfirmation()
                        true
                    }
                    R.id.action_retry_failed -> {
                        retryFailedDownloads()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showDeleteAllConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete All Saved Pages")
            .setMessage("Are you sure you want to delete all saved pages? This action cannot be undone.")
            .setPositiveButton("Delete All") { _, _ ->
                deleteAllSavedPages()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAllSavedPages() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val readingListPageDao = AppDatabase.instance.readingListPageDao()
                    val offlineObjectDao = AppDatabase.instance.offlineObjectDao()
                    val ftsDao = AppDatabase.instance.offlinePageFtsDao()
                    
                    // Get all saved pages
                    val allSavedPages = readingListPageDao.getPagesByStatusAndOffline(
                        ReadingListPage.STATUS_SAVED, true
                    )
                    
                    // Delete offline objects and FTS entries for each page
                    for (page in allSavedPages) {
                        try {
                            // Delete offline objects
                            offlineObjectDao.deleteObjectsForPageIds(listOf(page.id), requireContext())
                            
                            // Delete FTS entry
                            val pageTitleHelper = ReadingListPage.toPageTitle(page)
                            val canonicalPageUrlForFts = pageTitleHelper.uri
                            ftsDao.deletePageContentByUrl(canonicalPageUrlForFts)
                        } catch (e: Exception) {
                            Log.e("SavedPagesFragment", "Error deleting offline data for page: ${page.displayTitle}", e)
                        }
                    }
                    
                    // Delete all pages from reading list
                    val pageIds = allSavedPages.map { it.id }
                    if (pageIds.isNotEmpty()) {
                        readingListPageDao.deletePagesByIds(pageIds)
                    }
                }
                
                Toast.makeText(requireContext(), "All saved pages deleted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SavedPagesFragment", "Error deleting all saved pages", e)
                Toast.makeText(requireContext(), "Error deleting saved pages", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun retryFailedDownloads() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val readingListPageDao = AppDatabase.instance.readingListPageDao()
                    
                    // Get all pages with error status
                    val failedPages = readingListPageDao.getPagesByStatus(ReadingListPage.STATUS_ERROR)
                    
                    if (failedPages.isNotEmpty()) {
                        // Mark all failed pages for retry
                        for (page in failedPages) {
                            readingListPageDao.updatePageStatusToSavedAndMtime(
                                page.id, 
                                ReadingListPage.STATUS_QUEUE_FOR_SAVE, 
                                System.currentTimeMillis()
                            )
                        }
                        
                        // Enqueue the sync worker to retry downloads
                        SavedPageSyncWorker.enqueue(requireContext())
                    }
                }
                
                Toast.makeText(requireContext(), "Retrying failed downloads", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SavedPagesFragment", "Error retrying failed downloads", e)
                Toast.makeText(requireContext(), "Error retrying downloads", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.savedPagesRecyclerView.adapter = null // Important to prevent memory leaks with RecyclerView adapter
        _binding = null
    }
}