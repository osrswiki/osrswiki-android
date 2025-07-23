package com.omiyawaki.osrswiki.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.search.db.RecentSearch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecentSearchesViewModel(
    private val searchRepository: SearchRepository
) : ViewModel() {

    val recentSearches: StateFlow<List<RecentSearch>> =
        searchRepository.getRecentSearches()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    fun onClearAllClicked() {
        viewModelScope.launch {
            searchRepository.clearAllRecentSearches()
        }
    }
}

@Suppress("UNCHECKED_CAST")
class RecentSearchesViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecentSearchesViewModel::class.java)) {
            val repository = (OSRSWikiApp.instance).searchRepository
            return RecentSearchesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
