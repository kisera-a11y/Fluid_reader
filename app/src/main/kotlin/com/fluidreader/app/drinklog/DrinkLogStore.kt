package com.fluidreader.app.drinklog

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.drinkLogDataStore by preferencesDataStore(name = "fluid_reader_drink_log")

/**
 * Persists a running "tonight's drinks" log: each named entry keeps its full pour history (see
 * [DrinkPour]), so it can be broken back down on demand, while [addPour] still appends to an
 * existing (case-insensitive, trimmed) name instead of growing a new top-level entry per pour.
 * Entries survive app restarts and are only ever removed by an explicit [removePour],
 * [removeEntry], or [clearAll] call - never automatically.
 */
class DrinkLogStore(private val context: Context) {

    private object Keys {
        val ENTRIES = stringPreferencesKey("entries")
        val SESSION_START = longPreferencesKey("session_start_epoch_millis")
        val BAR_NAME = stringPreferencesKey("bar_name")
        val BAR_PROMPTED = booleanPreferencesKey("bar_prompted")
    }

    // Three levels of separator, all non-printable so they never collide with a typed drink
    // name: entries, then the pours within an entry, then the fields within one pour.
    private val entrySep = "\n"
    private val entryFieldSep = "\u0001"
    private val pourSep = "\u0002"
    private val pourFieldSep = "\u0003"

    val entries: Flow<List<DrinkLogEntry>> = context.drinkLogDataStore.data.map { prefs ->
        decode(prefs[Keys.ENTRIES])
    }

    /**
     * When tonight's log started - the timestamp of the first pour added after the log was last
     * empty. Kept separate from the entries themselves so it survives individual pours/entries
     * being deleted around it; only clears (and gets re-set by the next [addPour]) once the log
     * has been emptied out completely, by whatever combination of [removePour], [removeEntry],
     * or [clearAll] got it there.
     */
    val sessionStartEpochMillis: Flow<Long?> = context.drinkLogDataStore.data.map { prefs ->
        prefs[Keys.SESSION_START]
    }

    /** The bar this log is for, if the user entered one - optional, blank by default. */
    val barName: Flow<String?> = context.drinkLogDataStore.data.map { prefs ->
        prefs[Keys.BAR_NAME]
    }

    /**
     * Whether the "which bar?" prompt has already been shown (and answered, or explicitly
     * skipped) for the current log. Separate from [barName] itself so a blank answer doesn't
     * look the same as "never asked" and trigger the prompt again on every pour.
     */
    val barPrompted: Flow<Boolean> = context.drinkLogDataStore.data.map { prefs ->
        prefs[Keys.BAR_PROMPTED] ?: false
    }

    /** Records the bar prompt's answer - a blank/null [name] just records that it was skipped. */
    suspend fun setBarName(name: String?) {
        context.drinkLogDataStore.edit { prefs ->
            val trimmed = name?.trim()
            if (trimmed.isNullOrEmpty()) {
                prefs.remove(Keys.BAR_NAME)
            } else {
                prefs[Keys.BAR_NAME] = trimmed
            }
            prefs[Keys.BAR_PROMPTED] = true
        }
    }

    suspend fun addPour(name: String, ounces: Double) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || ounces <= 0.0) return

        context.drinkLogDataStore.edit { prefs ->
            val current = decode(prefs[Keys.ENTRIES]).toMutableList()
            val wasEmpty = current.isEmpty()
            val existingIndex = current.indexOfFirst { it.name.equals(trimmedName, ignoreCase = true) }
            val newPour = DrinkPour(ounces, System.currentTimeMillis())

            if (existingIndex >= 0) {
                val existing = current[existingIndex]
                current[existingIndex] = existing.copy(pours = existing.pours + newPour)
            } else {
                current.add(DrinkLogEntry(trimmedName, listOf(newPour)))
            }
            prefs[Keys.ENTRIES] = encode(current)
            if (wasEmpty) prefs[Keys.SESSION_START] = newPour.loggedAtEpochMillis
        }
    }

    /** Removes a single pour from an entry (by its timestamp); removes the whole entry if that was its last pour. */
    suspend fun removePour(name: String, loggedAtEpochMillis: Long) {
        context.drinkLogDataStore.edit { prefs ->
            val current = decode(prefs[Keys.ENTRIES]).toMutableList()
            val index = current.indexOfFirst { it.name.equals(name, ignoreCase = true) }
            if (index >= 0) {
                val remainingPours = current[index].pours.filterNot { it.loggedAtEpochMillis == loggedAtEpochMillis }
                if (remainingPours.isEmpty()) {
                    current.removeAt(index)
                } else {
                    current[index] = current[index].copy(pours = remainingPours)
                }
                prefs[Keys.ENTRIES] = encode(current)
                if (current.isEmpty()) {
                    prefs.remove(Keys.SESSION_START)
                    prefs.remove(Keys.BAR_NAME)
                    prefs.remove(Keys.BAR_PROMPTED)
                }
            }
        }
    }

    suspend fun removeEntry(name: String) {
        context.drinkLogDataStore.edit { prefs ->
            val current = decode(prefs[Keys.ENTRIES]).filterNot { it.name.equals(name, ignoreCase = true) }
            prefs[Keys.ENTRIES] = encode(current)
            if (current.isEmpty()) {
                prefs.remove(Keys.SESSION_START)
                prefs.remove(Keys.BAR_NAME)
                prefs.remove(Keys.BAR_PROMPTED)
            }
        }
    }

    suspend fun clearAll() {
        context.drinkLogDataStore.edit { prefs ->
            prefs[Keys.ENTRIES] = ""
            prefs.remove(Keys.SESSION_START)
            prefs.remove(Keys.BAR_NAME)
            prefs.remove(Keys.BAR_PROMPTED)
        }
    }

    private fun encode(entries: List<DrinkLogEntry>): String =
        entries.joinToString(entrySep) { entry ->
            val poursBlob = entry.pours.joinToString(pourSep) { pour ->
                listOf(pour.ounces, pour.loggedAtEpochMillis).joinToString(pourFieldSep)
            }
            listOf(entry.name, poursBlob).joinToString(entryFieldSep)
        }

    private fun decode(raw: String?): List<DrinkLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(entrySep).mapNotNull { line ->
            val parts = line.split(entryFieldSep)
            if (parts.size != 2) return@mapNotNull null
            val name = parts[0]
            val pours = parts[1].split(pourSep).mapNotNull { pourRecord ->
                val pourParts = pourRecord.split(pourFieldSep)
                if (pourParts.size != 2) return@mapNotNull null
                val ounces = pourParts[0].toDoubleOrNull() ?: return@mapNotNull null
                val timestamp = pourParts[1].toLongOrNull() ?: return@mapNotNull null
                DrinkPour(ounces, timestamp)
            }
            if (pours.isEmpty()) return@mapNotNull null
            DrinkLogEntry(name, pours)
        }
    }
}
