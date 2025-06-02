package com.omiyawaki.osrswiki.readinglist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.repository.SavedPagesRepository
// import dagger.hilt.android.lifecycle.HiltViewModel // Removed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
// import javax.inject.Inject // Removed

/**
 * ViewModel for the Saved Pages screen.
 * Retrieves and exposes the list of fully saved pages for offline viewing.
 */
// @HiltViewModel // Removed
class SavedPagesViewModel constructor( // @Inject removed from constructor
    savedPagesRepository: SavedPagesRepository
) : ViewModel() {

    val savedPages: StateFlow<List<ReadingListPage>> =
        savedPagesRepository.getFullySavedPages()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )
}