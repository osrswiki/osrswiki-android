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
import androidx.core.graphics.drawable.DrawableCompat
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
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.database.AppDatabase // For accessing DAO
import com.omiyawaki.osrswiki.databinding.FragmentSavedPagesBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry // Added import
import com.omiyawaki.osrswiki.page.PageActivity
import com.omiyawaki.osrswiki.page.PageFragment
import com.omiyawaki.osrswiki.page.PageTitle
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmRequest
import com.omiyawaki.osrswiki.page.preemptive.VisibleArticlePrewarmBinder
import com.omiyawaki.osrswiki.readinglist.adapter.SavedPagesAdapter
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.repository.SavedPagesRepository // For manual Repository instantiation
import com.omiyawaki.osrswiki.readinglist.viewmodel.SavedPagesViewModel
import com.omiyawaki.osrswiki.readinglist.viewmodel.SavedPagesViewModelFactory // For ViewModel factory
import com.omiyawaki.osrswiki.savedpages.SavedPageSyncWorker
import com.omiyawaki.osrswiki.theme.ThemeAware
import com.omiyawaki.osrswiki.util.SpeechRecognitionManager
import com.omiyawaki.osrswiki.util.StringUtil
import com.omiyawaki.osrswiki.util.createVoiceRecognitionManager
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline
import com.omiyawaki.osrswiki.util.log.L
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.color.MaterialColors
// import dagger.hilt.android.AndroidEntryPoint // Removed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// @AndroidEntryPoint // Removed
class SavedPagesFragment : Fragment(), ThemeAware {

    private var _binding: FragmentSavedPagesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SavedPagesViewModel by viewModels {
        // Manually create the ViewModel using the factory
        val readingListPageDao = AppDatabase.instance.readingListPageDao()
        val repository = SavedPagesRepository(readingListPageDao)
        SavedPagesViewModelFactory(repository)
    }

    private lateinit var savedPagesAdapter: SavedPagesAdapter
    private var articlePrewarmBinder: VisibleArticlePrewarmBinder? = null
    private var latestSavedPages: List<ReadingListPage> = emptyList()
    private val hiddenSavedPageIds = mutableSetOf<Long>()
    private var pendingDeletePage: ReadingListPage? = null
    private var pendingDeleteSnackbar: Snackbar? = null
    
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
        setupFonts()
        setupRecyclerView()
        setupArticlePrewarm()
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
            voiceRecognitionManager.startVoiceRecognition()
        }
        
        // Update search hint text for saved pages
        binding.root.findViewById<TextView>(R.id.search_text)?.text = getString(R.string.search_hint_saved_pages)
    }
    
    private fun setupFonts() {
        L.d("SavedPagesFragment: Setting up fonts...")
        
        // Apply fonts to header elements
        binding.root.findViewById<TextView>(R.id.page_title)?.applyAlegreyaHeadline()
        
        // Apply font to empty state message
        
        L.d("SavedPagesFragment: Fonts applied to header elements and empty state")
    }

    private fun setupRecyclerView() {
        savedPagesAdapter = SavedPagesAdapter(
            onItemClicked = { readingListPage ->
                navigateToPage(readingListPage)
            },
            onUpdateClicked = { readingListPage ->
                updateSavedPage(readingListPage)
            }
        )
        
        // Setup swipe-to-delete
        val swipeCallback = SwipeToDeleteCallback { savedPage ->
            showSwipeDeleteUndo(savedPage)
        }
        val itemTouchHelper = ItemTouchHelper(swipeCallback)
        
        binding.savedPagesRecyclerView.apply {
            adapter = savedPagesAdapter
            layoutManager = LinearLayoutManager(requireContext())
            // Attach the ItemTouchHelper to enable swipe-to-delete
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    private fun setupArticlePrewarm() {
        val app = requireActivity().application as OSRSWikiApp
        articlePrewarmBinder = VisibleArticlePrewarmBinder(
            recyclerView = binding.savedPagesRecyclerView,
            lifecycleOwner = viewLifecycleOwner,
            scope = viewLifecycleOwner.lifecycleScope,
            candidatesAt = { position, _ ->
                setOfNotNull(savedPagesAdapter.currentList.getOrNull(position)?.let { page ->
                    ArticlePrewarmRequest(pageId = page.mediaWikiPageId, title = page.apiTitle)
                })
            },
            onDwell = app.pageAssetDownloader::prewarmArticle,
            observeEnvironmentChanges = app.pageAssetDownloader::addPrewarmEnvironmentListener
        )
    }

    private fun observeSavedPages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.savedPages.collect { pages ->
                    latestSavedPages = pages
                    hiddenSavedPageIds.removeAll { hiddenId -> pages.none { it.id == hiddenId } }
                    renderVisibleSavedPages()
                }
            }
        }
    }

    private fun navigateToPage(savedPage: ReadingListPage) {
        val pageTitle = savedPage.apiTitle
        val pageId = savedPage.mediaWikiPageId?.toString() // Use the stored MediaWiki page ID if available

        Log.d("SavedPagesFragment", "Opening PageActivity for saved page: '$pageTitle', pageId: '$pageId', source: SOURCE_SAVED_PAGE")

        PageActivity.open(
            context = requireContext(),
            pageTitle = pageTitle,
            pageId = pageId,
            source = HistoryEntry.SOURCE_SAVED_PAGE,
            snippet = savedPage.description,
            thumbnailUrl = savedPage.thumbUrl
        )
    }

    private fun updateSavedPage(page: ReadingListPage) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.instance.readingListPageDao()
                    .transitionPageToForcedOfflineSave(page.id)
            }
            SavedPageSyncWorker.enqueue(requireContext())
            Toast.makeText(
                requireContext(),
                getString(R.string.saved_page_updating, StringUtil.extractMainTitle(page.displayTitle)),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showSwipeDeleteUndo(savedPage: ReadingListPage) {
        commitPendingDelete()
        pendingDeletePage = savedPage
        hiddenSavedPageIds += savedPage.id
        renderVisibleSavedPages()

        val displayTitle = StringUtil.extractMainTitle(savedPage.displayTitle)
        val snackbar = Snackbar.make(
            binding.root,
            getString(R.string.saved_page_delete_pending, displayTitle),
            Snackbar.LENGTH_LONG
        )
            .setAction(R.string.action_undo) {
                hiddenSavedPageIds -= savedPage.id
                pendingDeletePage = null
                pendingDeleteSnackbar = null
                renderVisibleSavedPages()
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (pendingDeletePage?.id == savedPage.id && event != DISMISS_EVENT_ACTION) {
                        pendingDeletePage = null
                        pendingDeleteSnackbar = null
                        viewModel.deleteSavedPage(savedPage)
                    }
                }
            })

        snackbar.setBackgroundTint(
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceVariant)
        )
        snackbar.setTextColor(
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
        )
        snackbar.setActionTextColor(
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        )
        requireActivity().findViewById<View>(R.id.bottom_nav)?.let(snackbar::setAnchorView)
        pendingDeleteSnackbar = snackbar
        snackbar.show()
    }

    private fun renderVisibleSavedPages() {
        val currentBinding = _binding ?: return
        val visiblePages = latestSavedPages.filterNot { it.id in hiddenSavedPageIds }
        savedPagesAdapter.submitList(visiblePages)
        currentBinding.emptyStateTextView.isVisible = visiblePages.isEmpty()
        currentBinding.savedPagesRecyclerView.isVisible = visiblePages.isNotEmpty()
    }

    private fun commitPendingDelete() {
        val page = pendingDeletePage ?: return
        pendingDeletePage = null
        pendingDeleteSnackbar?.dismiss()
        pendingDeleteSnackbar = null
        viewModel.deleteSavedPage(page)
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
                val appContext = requireContext().applicationContext
                withContext(Dispatchers.IO) {
                    val readingListPageDao = AppDatabase.instance.readingListPageDao()
                    val repository = SavedPagesRepository(readingListPageDao)
                    
                    // Get all saved pages
                    val allSavedPages = repository.getReadableOfflinePagesSnapshot()
                    
                    repository.deleteSavedPages(allSavedPages, appContext)
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
                                page.retryQueueStatus,
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
            voiceRecognitionManager.handlePermissionResult(requestCode, grantResults)
        }
    }

    override fun onDestroyView() {
        commitPendingDelete()
        pendingDeleteSnackbar?.dismiss()
        pendingDeleteSnackbar = null
        articlePrewarmBinder?.dispose()
        articlePrewarmBinder = null
        super.onDestroyView()
        binding.savedPagesRecyclerView.adapter = null // Important to prevent memory leaks with RecyclerView adapter
        _binding = null
    }

    override fun onThemeChanged() {
        L.d("SavedPagesFragment: onThemeChanged called")
        // Re-apply theme attributes to views that use theme attributes
        refreshThemeAttributes()
    }

    private fun refreshThemeAttributes() {
        if (_binding != null) {
            // Get the current theme's paper_color attribute
            val typedValue = android.util.TypedValue()
            val theme = requireContext().theme
            theme.resolveAttribute(com.omiyawaki.osrswiki.R.attr.paper_color, typedValue, true)
            
            // Apply the new background color to the root layout
            binding.root.setBackgroundColor(typedValue.data)
            
            // Apply the new background color to the AppBarLayout
            val appBarLayout = binding.root.findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.saved_pages_app_bar)
            appBarLayout?.setBackgroundColor(typedValue.data)
            
            L.d("SavedPagesFragment: Theme attributes refreshed")
        }
    }
    
    private inner class SwipeToDeleteCallback(
        private val onItemDelete: (ReadingListPage) -> Unit
    ) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

        private val deleteIcon: Drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_delete_24)!!.mutate()
        private val background = ColorDrawable()
        private val backgroundColor = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorErrorContainer
        )

        init {
            DrawableCompat.setTint(
                deleteIcon,
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnErrorContainer)
            )
        }

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
