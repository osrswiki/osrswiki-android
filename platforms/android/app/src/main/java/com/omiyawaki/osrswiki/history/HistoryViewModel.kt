package com.omiyawaki.osrswiki.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val historyDao by lazy {
        AppDatabase.instance.historyEntryDao()
    }
    
    private val _searchQuery = MutableLiveData<String>("")
    private var searchJob: Job? = null
    
    val historyItems: LiveData<List<HistoryItem>> = _searchQuery.switchMap { query ->
        historyDao.getAllEntries().asLiveData().switchMap { allEntries ->
            val filteredEntries = if (query.isBlank()) {
                allEntries
            } else {
                allEntries.filter { entry ->
                    entry.displayText.contains(query, ignoreCase = true) ||
                    entry.apiPath.contains(query, ignoreCase = true)
                }
            }
            
            val groupedItems = groupByDate(filteredEntries)
            MutableLiveData(groupedItems)
        }
    }
    
    fun searchHistory(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // Debounce search
            _searchQuery.postValue(query)
        }
    }
    
    private fun groupByDate(entries: List<HistoryEntry>): List<HistoryItem> {
        val result = mutableListOf<HistoryItem>()
        
        if (entries.isEmpty()) {
            return result
        }
        
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        var prevDay = 0
        
        entries.sortedByDescending { it.timestamp }.forEach { entry ->
            calendar.time = entry.timestamp
            val curDay = calendar[Calendar.YEAR] + calendar[Calendar.DAY_OF_YEAR]
            
            // Add date header if it's a new day
            if (prevDay == 0 || curDay != prevDay) {
                val dateString = DateFormat.getDateInstance().format(entry.timestamp)
                result.add(HistoryItem.DateHeader(dateString))
            }
            prevDay = curDay
            result.add(HistoryItem.EntryItem(entry))
        }
        
        return result
    }
    
    fun deleteHistoryItem(historyEntry: HistoryEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.deleteEntryByUrl(historyEntry.wikiUrl)
            // The switchMap will automatically update when the database changes
        }
    }
    
    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.deleteAllEntries()
            // The switchMap will automatically update when the database changes
        }
    }
}