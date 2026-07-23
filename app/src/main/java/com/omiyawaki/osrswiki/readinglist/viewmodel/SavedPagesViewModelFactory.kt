package com.omiyawaki.osrswiki.readinglist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.omiyawaki.osrswiki.readinglist.repository.SavedPagesRepository

/**
 * Factory for creating SavedPagesViewModel instances with a SavedPagesRepository dependency.
 */
class SavedPagesViewModelFactory(
    private val repository: SavedPagesRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SavedPagesViewModel::class.java)) {
            return SavedPagesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}