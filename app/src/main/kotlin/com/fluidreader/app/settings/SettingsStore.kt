package com.fluidreader.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fluidreader.core.cup.CupProfileRegistry
import com.fluidreader.core.units.VolumeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "fluid_reader_settings")

data class AppSettings(
    val units: VolumeUnit = VolumeUnit.FLUID_OUNCES,
    val cupProfileId: String = CupProfileRegistry.SOLO_16OZ.id,
    /** Minimum overall confidence (0..1) required to show a numeric estimate at all. */
    val confidenceThreshold: Float = 0f,
    val showOverlay: Boolean = true,
    /** Reveals the Calibration and Debug/Test entries in Settings. */
    val developerOptionsEnabled: Boolean = false,
)

/** Persists the handful of user-facing preferences via Jetpack DataStore. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val UNITS = stringPreferencesKey("units")
        val CUP_PROFILE_ID = stringPreferencesKey("cup_profile_id")
        val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
        val SHOW_OVERLAY = booleanPreferencesKey("show_overlay")
        val DEVELOPER_OPTIONS = booleanPreferencesKey("developer_options_enabled")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            units = prefs[Keys.UNITS]?.let { runCatching { VolumeUnit.valueOf(it) }.getOrNull() }
                ?: VolumeUnit.FLUID_OUNCES,
            cupProfileId = prefs[Keys.CUP_PROFILE_ID] ?: CupProfileRegistry.SOLO_16OZ.id,
            confidenceThreshold = prefs[Keys.CONFIDENCE_THRESHOLD] ?: 0f,
            showOverlay = prefs[Keys.SHOW_OVERLAY] ?: true,
            developerOptionsEnabled = prefs[Keys.DEVELOPER_OPTIONS] ?: false,
        )
    }

    suspend fun setUnits(units: VolumeUnit) {
        context.settingsDataStore.edit { it[Keys.UNITS] = units.name }
    }

    suspend fun setCupProfileId(id: String) {
        context.settingsDataStore.edit { it[Keys.CUP_PROFILE_ID] = id }
    }

    suspend fun setConfidenceThreshold(threshold: Float) {
        context.settingsDataStore.edit { it[Keys.CONFIDENCE_THRESHOLD] = threshold.coerceIn(0f, 1f) }
    }

    suspend fun setShowOverlay(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_OVERLAY] = show }
    }

    suspend fun setDeveloperOptionsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DEVELOPER_OPTIONS] = enabled }
    }
}
