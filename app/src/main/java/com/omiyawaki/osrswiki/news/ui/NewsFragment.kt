package com.omiyawaki.osrswiki.news.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.news.viewmodel.NewsViewModel
import com.omiyawaki.osrswiki.page.PageActivity
import com.omiyawaki.osrswiki.search.SearchActivity
import com.omiyawaki.osrswiki.util.log.L
import java.net.URLDecoder

/**
 * A fragment to display a feed of recent news and updates from the OSRS Wiki.
 * This UI is modeled after the Wikipedia app's "Explore" feed, presenting
 * different types of content in a card-based RecyclerView.
 */
class NewsFragment : Fragment() {

    private val viewModel: NewsViewModel by viewModels()
    private lateinit var newsFeedAdapter: NewsFeedAdapter
    private val wikiBaseUrl = "https://oldschool.runescape.wiki"

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
        setupHeader(view)
        setupSearch(view)
        setupRecyclerView(view)
        observeViewModel()

        // Initial data fetch
        if (savedInstanceState == null) {
            viewModel.fetchNews()
        }
    }

    private fun setupHeader(view: View) {
        // Set the page title to "News"
        view.findViewById<TextView>(R.id.page_title)?.text = getString(R.string.nav_news)
    }

    private fun setupSearch(view: View) {
        // Set a click listener on the search bar view to launch the search activity.
        view.findViewById<View>(R.id.search_container).setOnClickListener {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            startActivity(intent)
        }
    }

    private fun handleLinkClick(url: String) {
        L.d("Link clicked. Original URL: $url")

        // Defensively handle relative URLs by prepending the base URL if needed.
        val absoluteUrl = if (url.startsWith("/")) {
            "$wikiBaseUrl$url"
        } else {
            url
        }

        if (absoluteUrl.startsWith(wikiBaseUrl)) {
            // Internal link, open in PageActivity
            try {
                val title = getPageTitleFromUrl(absoluteUrl)
                startActivity(
                    PageActivity.newIntent(requireContext(), title, null, HistoryEntry.SOURCE_NEWS)
                )
            } catch (e: Exception) {
                L.e("Could not parse page title from internal URL: $absoluteUrl", e)
                Toast.makeText(requireContext(), "Error opening internal link.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // External link, open in browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(absoluteUrl))
                startActivity(intent)
            } catch (e: Exception) {
                L.e("Failed to open external link: $absoluteUrl", e)
                Toast.makeText(requireContext(), "Could not open external link.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getPageTitleFromUrl(url: String): String {
        val pathSegment = url.substringAfterLast('/')
        val withSpaces = pathSegment.replace('_', ' ')
        return URLDecoder.decode(withSpaces, "UTF-8")
    }

    private fun setupRecyclerView(view: View) {
        newsFeedAdapter = NewsFeedAdapter(
            onUpdateItemClicked = { updateItem ->
                startActivity(
                    PageActivity.newIntent(requireContext(), updateItem, HistoryEntry.SOURCE_NEWS)
                )
            },
            onLinkClicked = { url ->
                handleLinkClick(url)
            }
        )

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
