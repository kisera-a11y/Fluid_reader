package com.fluidreader.app.drinklog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun logPour(name: String, ounces: Double) {
        viewModelScope.launch { store.addPour(name, ounces) }
    }

    fun deleteEntry(name: String) {
        viewModelScope.launch { store.removeEntry(name) }
    }

    fun clearAll() {
        viewModelScope.launch { store.clearAll() }
    }
}
