package com.fluidreader.app.drinklog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Owns tonight's drink log: exposes the running per-name totals and mutation actions. */
class DrinkLogViewModel(application: Application) : AndroidViewModel(application) {

    private val store = DrinkLogStore(application)

    /** Most-recently-logged drink first, so the last thing you added is always at the top. */
    val entries: StateFlow<List<DrinkLogEntry>> = store.entries
        .map { list -> list.sortedByDescending { it.lastLoggedAtEpochMillis } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val grandTotalOz: StateFlow<Double> = entries
        .map { list -> list.sumOf { it.totalOz } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    /**
     * When tonight's log started, for display next to "Total tonight" - the first pour's
     * timestamp, held steady across midnight until the log is emptied out and a new first pour
     * re-sets it. Falls back to the earliest pour still in the log if the dedicated start-date
     * field isn't set (e.g. a log saved before this field existed), so an existing log doesn't
     * just show a blank date after updating.
     */
    val logDateEpochMillis: StateFlow<Long?> = combine(entries, store.sessionStartEpochMillis) { list, stored ->
        stored ?: list.flatMap { it.pours }.minOfOrNull { it.loggedAtEpochMillis }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun logPour(name: String, ounces: Double) {
        viewModelScope.launch { store.addPour(name, ounces) }
    }

    fun deleteEntry(name: String) {
        viewModelScope.launch { store.removeEntry(name) }
    }

    fun deletePour(name: String, loggedAtEpochMillis: Long) {
        viewModelScope.launch { store.removePour(name, loggedAtEpochMillis) }
    }

    fun clearAll() {
        viewModelScope.launch { store.clearAll() }
    }
}
