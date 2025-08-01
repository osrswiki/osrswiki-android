package com.omiyawaki.osrswiki.history

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.viewModels
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentHistoryBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.page.PageActivity
import com.omiyawaki.osrswiki.search.SearchActivity
import com.omiyawaki.osrswiki.util.SpeechRecognitionManager
import com.omiyawaki.osrswiki.util.createVoiceRecognitionManager
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline
import com.omiyawaki.osrswiki.util.applyRubikUIHint
import com.omiyawaki.osrswiki.util.log.L

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter
    
    private lateinit var voiceRecognitionManager: SpeechRecognitionManager
    private val voiceSearchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        voiceRecognitionManager.handleActivityResult(result.resultCode, result.data)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupHeader()
        setupSearch()
        setupFonts()
        setupRecyclerView()
        observeViewModel()

        // Diagnostic logging for header position
        binding.root.findViewById<TextView>(R.id.page_title)?.doOnLayout {
            val location = IntArray(2)
            binding.root.findViewById<TextView>(R.id.page_title)?.getLocationOnScreen(location)
            com.omiyawaki.osrswiki.util.log.L.d("HeaderPosition: History title Y-coordinate: ${location[1]}")
        }
    }

    private fun setupHeader() {
        // Set the page title to "History"
        binding.root.findViewById<TextView>(R.id.page_title)?.text = getString(R.string.history_title)
        
        // Show and configure the Clear All button
        binding.root.findViewById<ImageButton>(R.id.clear_all_button)?.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                showClearAllConfirmationDialog()
            }
        }
    }
    
    private fun showClearAllConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear all history")
            .setMessage("This will delete all of your browsing history. Are you sure?")
            .setPositiveButton("Clear All") { _, _ ->
                viewModel.clearHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupSearch() {
        // Initialize voice recognition manager
        voiceRecognitionManager = createVoiceRecognitionManager(
            onResult = { query ->
                // Open search activity with the voice query
                val intent = Intent(requireContext(), SearchActivity::class.java).apply {
                    putExtra("query", query)
                }
                startActivity(intent)
            }
        )
        
        // Set a click listener on the search bar view to launch the search activity.
        binding.root.findViewById<View>(R.id.search_container)?.setOnClickListener {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            startActivity(intent)
        }
        
        // Set up voice search button
        binding.root.findViewById<ImageView>(R.id.voice_search_button)?.setOnClickListener {
            voiceRecognitionManager.startVoiceRecognition()
        }
    }
    
    private fun setupFonts() {
        L.d("HistoryFragment: Setting up fonts...")
        
        // Apply fonts to header elements
        binding.root.findViewById<TextView>(R.id.page_title)?.applyAlegreyaHeadline()
        binding.root.findViewById<TextView>(R.id.search_text)?.applyRubikUIHint()
        
        // Apply font to empty state text
        val emptyStateTextView = binding.emptyStateContainer.getChildAt(1) as? TextView
        emptyStateTextView?.let { 
            it.applyRubikUIHint()
            L.d("HistoryFragment: Applied Rubik font to empty state text")
        }
        
        L.d("HistoryFragment: Fonts applied to header elements and empty state")
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { historyEntry ->
                navigateToPage(historyEntry)
            }
        )
        
        // Setup swipe-to-delete
        val swipeCallback = SwipeToDeleteCallback { historyEntry ->
            viewModel.deleteHistoryItem(historyEntry)
        }
        val itemTouchHelper = ItemTouchHelper(swipeCallback)
        
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HistoryFragment.adapter
            // Attach the ItemTouchHelper to enable swipe-to-delete
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    private fun observeViewModel() {
        viewModel.historyItems.observe(viewLifecycleOwner) { historyList ->
            adapter.submitList(historyList)
            updateEmptyState(historyList)
        }
    }

    private fun updateEmptyState(historyList: List<HistoryItem>) {
        // Show empty state only if there are no entries
        val hasEntries = historyList.any { it is HistoryItem.EntryItem }
        binding.historyRecyclerView.visibility = if (hasEntries) View.VISIBLE else View.GONE
        binding.emptyStateContainer.visibility = if (hasEntries) View.GONE else View.VISIBLE
    }

    private fun navigateToPage(historyEntry: HistoryEntry) {
        val intent = Intent(requireContext(), PageActivity::class.java).apply {
            putExtra(PageActivity.EXTRA_PAGE_TITLE, historyEntry.apiPath)
            putExtra(PageActivity.EXTRA_PAGE_SOURCE, HistoryEntry.SOURCE_HISTORY)
            // Pass the snippet and thumbnail so they're preserved in the history update
            putExtra(PageActivity.EXTRA_PAGE_SNIPPET, historyEntry.snippet)
            putExtra(PageActivity.EXTRA_PAGE_THUMBNAIL, historyEntry.thumbnailUrl)
        }
        startActivity(intent)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HistoryFragment()
    }

    private inner class SwipeToDeleteCallback(
        private val onItemDelete: (HistoryEntry) -> Unit
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
                val item = adapter.currentList[position]
                
                // Only allow swiping on entry items, not date headers
                if (item is HistoryItem.EntryItem) {
                    onItemDelete(item.historyEntry)
                }
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

            // Only show background for entry items, not date headers
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) {
                return
            }
            val item = adapter.currentList.getOrNull(position)
            if (item !is HistoryItem.EntryItem) {
                return
            }

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

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::voiceRecognitionManager.isInitialized) {
            voiceRecognitionManager.handlePermissionResult(requestCode, grantResults)
        }
    }
}