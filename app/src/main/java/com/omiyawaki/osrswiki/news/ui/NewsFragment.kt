package com.omiyawaki.osrswiki.news.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.news.viewmodel.NewsViewModel
import com.omiyawaki.osrswiki.page.PageActivity
import com.omiyawaki.osrswiki.search.SearchActivity

/**
 * A fragment to display a feed of recent news and updates from the OSRS Wiki.
 * This UI is modeled after the Wikipedia app's "Explore" feed, presenting
 * different types of content in a card-based RecyclerView.
 */
class NewsFragment : Fragment() {

    private val viewModel: NewsViewModel by viewModels()
    private lateinit var newsFeedAdapter: NewsFeedAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // This function should only inflate and return the view.
        return inflater.inflate(R.layout.fragment_news, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSearch(view)
        setupRecyclerView(view)
        observeViewModel()

        // Initial data fetch
        if (savedInstanceState == null) {
            viewModel.fetchNews()
        }
    }

    private fun setupSearch(view: View) {
        // Set a click listener on the search bar view to launch the search activity.
        view.findViewById<View>(R.id.news_search_view).setOnClickListener {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView(view: View) {
        // The adapter is instantiated here with the correct click handler.
        newsFeedAdapter = NewsFeedAdapter { updateItem ->
            // This is the new, correct way to start the PageActivity,
            // using the newIntent overload that correctly parses the title from the URL.
            startActivity(
                PageActivity.newIntent(requireContext(), updateItem, HistoryEntry.SOURCE_NEWS)
            )
        }

        // Find the RecyclerView in the fragment's layout and set it up.
        // The ID is now correctly set to 'recyclerViewNews'.
        view.findViewById<RecyclerView>(R.id.recyclerViewNews).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = newsFeedAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.feedItems.observe(viewLifecycleOwner) { feedItems ->
            // Pass the fetched data to the adapter.
            newsFeedAdapter.setItems(feedItems)
        }
    }
}
