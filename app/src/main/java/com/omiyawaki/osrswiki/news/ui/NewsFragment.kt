package com.omiyawaki.osrswiki.news.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.news.model.UpdateItem
import com.omiyawaki.osrswiki.news.viewmodel.NewsViewModel

/**
 * A fragment to display a feed of recent news and updates from the OSRS Wiki.
 * This UI is modeled after the Wikipedia app's "Explore" feed, presenting
 * different types of content in a card-based RecyclerView.
 */
class NewsFragment : Fragment() {

    interface OnNewsCardClickListener {
        fun onUpdateCardClicked(item: UpdateItem)
    }

    private val viewModel: NewsViewModel by viewModels()
    private lateinit var newsFeedAdapter: NewsFeedAdapter
    private var cardClickListener: OnNewsCardClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnNewsCardClickListener) {
            cardClickListener = context
        } else {
            throw RuntimeException("$context must implement OnNewsCardClickListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_news, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView(view)
        observeViewModel()

        // Initial data fetch
        if (savedInstanceState == null) {
            viewModel.fetchNews()
        }
    }

    private fun setupRecyclerView(view: View) {
        newsFeedAdapter = NewsFeedAdapter { item ->
            // Pass the click event up to the hosting activity.
            cardClickListener?.onUpdateCardClicked(item)
        }
        val recyclerView = view.findViewById<RecyclerView>(R.id.news_feed_recycler_view)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = newsFeedAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.feedItems.observe(viewLifecycleOwner) { items ->
            newsFeedAdapter.setItems(items)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Here you would show/hide a progress bar
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        cardClickListener = null
    }
}
