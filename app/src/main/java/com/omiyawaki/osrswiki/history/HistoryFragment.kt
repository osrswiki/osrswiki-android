package com.omiyawaki.osrswiki.history

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
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

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter

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
        setupRecyclerView()
        observeViewModel()
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
        // Set a click listener on the search bar view to launch the search activity.
        binding.root.findViewById<View>(R.id.search_container)?.setOnClickListener {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            startActivity(intent)
        }
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
            val position = viewHolder.adapterPosition
            val item = adapter.currentList[position]
            
            // Only allow swiping on entry items, not date headers
            if (item is HistoryItem.EntryItem) {
                onItemDelete(item.historyEntry)
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
            val item = adapter.currentList.getOrNull(viewHolder.adapterPosition)
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
                }
                else -> { // No swipe
                    background.setBounds(0, 0, 0, 0)
                    return // Don't draw anything when not swiping
                }
            }

            background.draw(c)
            deleteIcon.draw(c)
        }
    }
}