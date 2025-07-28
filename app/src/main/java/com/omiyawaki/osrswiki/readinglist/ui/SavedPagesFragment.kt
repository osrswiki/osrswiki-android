package com.omiyawaki.osrswiki.readinglist.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider // Added for manual ViewModel instantiation
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.omiyawaki.osrswiki.util.SpeechRecognitionManager
import com.omiyawaki.osrswiki.util.createVoiceRecognitionManager
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
    
    private lateinit var voiceRecognitionManager: SpeechRecognitionManager
    private val voiceSearchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        voiceRecognitionManager.handleActivityResult(result.resultCode, result.data)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedPagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set the application context on the ViewModel for deletion operations
        viewModel.setApplicationContext(requireContext())
        
        setupHeader()
        setupSearch()
        setupRecyclerView()
        observeSavedPages()
        setupMenu()
    }

    private fun setupHeader() {
        // Set the page title to "Saved"
        binding.root.findViewById<TextView>(R.id.page_title)?.text = getString(R.string.nav_saved)
    }

    private fun setupSearch() {
        // Initialize voice recognition manager
        voiceRecognitionManager = createVoiceRecognitionManager(
            onResult = { query ->
                // Open saved pages search activity with the voice query
                val intent = SavedPagesSearchActivity.newIntent(requireContext()).apply {
                    putExtra("query", query)
                }
                startActivity(intent)
            }
        )
        
        // Set a click listener on the search bar view to launch the saved pages search activity.
        binding.root.findViewById<View>(R.id.search_container)?.setOnClickListener {
            val intent = SavedPagesSearchActivity.newIntent(requireContext())
            startActivity(intent)
        }
        
        // Set up voice search button
        binding.root.findViewById<ImageView>(R.id.voice_search_button)?.setOnClickListener {
            voiceRecognitionManager.startVoiceRecognition(voiceSearchLauncher)
        }
        
        // Update search hint text for saved pages
        binding.root.findViewById<TextView>(R.id.search_text)?.text = getString(R.string.search_hint_saved_pages)
    }

    private fun setupRecyclerView() {
        savedPagesAdapter = SavedPagesAdapter { readingListPage ->
            navigateToPage(readingListPage)
        }
        
        // Setup swipe-to-delete
        val swipeCallback = SwipeToDeleteCallback { savedPage ->
            viewModel.deleteSavedPage(savedPage)
        }
        val itemTouchHelper = ItemTouchHelper(swipeCallback)
        
        binding.savedPagesRecyclerView.apply {
            adapter = savedPagesAdapter
            layoutManager = LinearLayoutManager(requireContext())
            // Attach the ItemTouchHelper to enable swipe-to-delete
            itemTouchHelper.attachToRecyclerView(this)
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

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::voiceRecognitionManager.isInitialized) {
            voiceRecognitionManager.handlePermissionResult(requestCode, grantResults, voiceSearchLauncher)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.savedPagesRecyclerView.adapter = null // Important to prevent memory leaks with RecyclerView adapter
        _binding = null
    }
    
    private inner class SwipeToDeleteCallback(
        private val onItemDelete: (ReadingListPage) -> Unit
    ) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

        private val deleteIcon: Drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_delete_24)!!
        private val background = ColorDrawable()
        private val backgroundColor = ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val savedPage = savedPagesAdapter.currentList[position]
                onItemDelete(savedPage)
            }
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

            val itemView = viewHolder.itemView
            val backgroundCornerOffset = 20

            when {
                dX > 0 -> { // Swiping to the right
                    background.color = backgroundColor
                    background.setBounds(
                        itemView.left,
                        itemView.top,
                        itemView.left + dX.toInt() + backgroundCornerOffset,
                        itemView.bottom
                    )
                    
                    val iconTop = itemView.top + (itemView.height - deleteIcon.intrinsicHeight) / 2
                    val iconMargin = (itemView.height - deleteIcon.intrinsicHeight) / 2
                    val iconLeft = itemView.left + iconMargin
                    val iconRight = itemView.left + iconMargin + deleteIcon.intrinsicWidth
                    val iconBottom = iconTop + deleteIcon.intrinsicHeight
                    
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    
                    // Draw background first
                    background.draw(c)
                    
                    // Clip canvas to background area and draw icon
                    c.save()
                    c.clipRect(
                        itemView.left.toFloat(),
                        itemView.top.toFloat(),
                        (itemView.left + dX + backgroundCornerOffset).toFloat(),
                        itemView.bottom.toFloat()
                    )
                    deleteIcon.draw(c)
                    c.restore()
                }
                dX < 0 -> { // Swiping to the left
                    background.color = backgroundColor
                    background.setBounds(
                        itemView.right + dX.toInt() - backgroundCornerOffset,
                        itemView.top,
                        itemView.right,
                        itemView.bottom
                    )
                    
                    val iconTop = itemView.top + (itemView.height - deleteIcon.intrinsicHeight) / 2
                    val iconMargin = (itemView.height - deleteIcon.intrinsicHeight) / 2
                    val iconLeft = itemView.right - iconMargin - deleteIcon.intrinsicWidth
                    val iconRight = itemView.right - iconMargin
                    val iconBottom = iconTop + deleteIcon.intrinsicHeight
                    
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    
                    // Draw background first
                    background.draw(c)
                    
                    // Clip canvas to background area and draw icon
                    c.save()
                    c.clipRect(
                        (itemView.right + dX - backgroundCornerOffset).toFloat(),
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat()
                    )
                    deleteIcon.draw(c)
                    c.restore()
                }
                else -> { // No swipe
                    background.setBounds(0, 0, 0, 0)
                    return // Don't draw anything when not swiping
                }
            }
        }
    }
}