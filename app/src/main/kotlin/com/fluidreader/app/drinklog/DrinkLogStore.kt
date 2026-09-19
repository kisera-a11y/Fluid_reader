package com.fluidreader.app.drinklog

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.drinkLogDataStore by preferencesDataStore(name = "fluid_reader_drink_log")

/**
 * Persists a running "tonight's drinks" log: each named entry accumulates total ounces and a
 * pour count across repeated [addPour] calls for the same (case-insensitive, trimmed) name,
 * rather than growing a new row per pour. Entries survive app restarts and are only ever
 * removed by an explicit [removeEntry] or [clearAll] call - never automatically.
 */
class DrinkLogStore(private val context: Context) {

    private object Keys {
        val ENTRIES = stringPreferencesKey("entries")
    }

    /** Field separator (never typed by a user) and record separator for the encoded blob. */
    private val FIELD_SEP = "\u0001"
    private val RECORD_SEP = "\n"

    val entries: Flow<List<DrinkLogEntry>> = context.drinkLogDataStore.data.map { prefs ->
        decode(prefs[Keys.ENTRIES])
    }

    suspend fun addPour(name: String, ounces: Double) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || ounces <= 0.0) return

        context.drinkLogDataStore.edit { prefs ->
            val current = decode(prefs[Keys.ENTRIES]).toMutableList()
            val existingIndex = current.indexOfFirst { it.name.equals(trimmedName, ignoreCase = true) }
            val now = System.currentTimeMillis()

            if (existingIndex >= 0) {
                val existing = current[existingIndex]
                current[existingIndex] = existing.copy(
                    totalOz = existing.totalOz + ounces,
                    pourCount = existing.pourCount + 1,
                    lastLoggedAtEpochMillis = now,
                )
            } else {
                current.add(DrinkLogEntry(trimmedName, ounces, pourCount = 1, lastLoggedAtEpochMillis = now))
            }
            prefs[Keys.ENTRIES] = encode(current)
        }
    }

    suspend fun removeEntry(name: String) {
        context.drinkLogDataStore.edit { prefs ->
            val current = decode(prefs[Keys.ENTRIES]).filterNot { it.name.equals(name, ignoreCase = true) }
            prefs[Keys.ENTRIES] = encode(current)
        }
    }

    suspend fun clearAll() {
        context.drinkLogDataStore.edit { prefs -> prefs[Keys.ENTRIES] = "" }
    }

    private fun encode(entries: List<DrinkLogEntry>): String =
        entries.joinToString(RECORD_SEP) {
            listOf(it.name, it.totalOz, it.pourCount, it.lastLoggedAtEpochMillis).joinToString(FIELD_SEP)
        }

    private fun decode(raw: String?): List<DrinkLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(RECORD_SEP).mapNotNull { line ->
            val parts = line.split(FIELD_SEP)
            if (parts.size != 4) return@mapNotNull null
            val total = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val count = parts[2].toIntOrNull() ?: return@mapNotNull null
            val timestamp = parts[3].toLongOrNull() ?: return@mapNotNull null
            DrinkLogEntry(name = parts[0], totalOz = total, pourCount = count, lastLoggedAtEpochMillis = timestamp)
        }
    }
}
