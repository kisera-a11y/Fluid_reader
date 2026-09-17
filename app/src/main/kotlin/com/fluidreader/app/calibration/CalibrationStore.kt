package com.fluidreader.app.calibration

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fluidreader.core.calibration.CalibrationPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.calibrationDataStore by preferencesDataStore(name = "fluid_reader_calibration")

/** Per-cup-profile measured overrides: dimensions plus any height->volume calibration points. */
data class CalibrationData(
    val points: List<CalibrationPoint> = emptyList(),
    val heightOverrideMm: Double? = null,
    val topDiameterOverrideMm: Double? = null,
    val bottomDiameterOverrideMm: Double? = null,
    val usableInteriorHeightOverrideMm: Double? = null,
)

/**
 * Persists user-entered calibration measurements (see the app's Calibration screen), keyed by
 * cup profile id so multiple cup models can eventually be calibrated independently.
 *
 * Calibration points are stored as a simple "height,volume;height,volume;..." string - no
 * extra serialization dependency needed for a handful of small numeric pairs.
 */
class CalibrationStore(private val context: Context) {

    private fun pointsKey(profileId: String) = stringPreferencesKey("${profileId}_points")
    private fun heightKey(profileId: String) = floatPreferencesKey("${profileId}_height")
    private fun topDiameterKey(profileId: String) = floatPreferencesKey("${profileId}_top_diameter")
    private fun bottomDiameterKey(profileId: String) = floatPreferencesKey("${profileId}_bottom_diameter")
    private fun usableHeightKey(profileId: String) = floatPreferencesKey("${profileId}_usable_height")

    fun observe(profileId: String): Flow<CalibrationData> = context.calibrationDataStore.data.map { prefs ->
        CalibrationData(
            points = decodePoints(prefs[pointsKey(profileId)]),
            heightOverrideMm = prefs[heightKey(profileId)]?.toDouble(),
            topDiameterOverrideMm = prefs[topDiameterKey(profileId)]?.toDouble(),
            bottomDiameterOverrideMm = prefs[bottomDiameterKey(profileId)]?.toDouble(),
            usableInteriorHeightOverrideMm = prefs[usableHeightKey(profileId)]?.toDouble(),
        )
    }

    suspend fun addPoint(profileId: String, point: CalibrationPoint) {
        context.calibrationDataStore.edit { prefs ->
            val current = decodePoints(prefs[pointsKey(profileId)]).toMutableList()
            current.removeAll { it.heightMm == point.heightMm }
            current.add(point)
            current.sortBy { it.heightMm }
            prefs[pointsKey(profileId)] = encodePoints(current)
        }
    }

    suspend fun removePoint(profileId: String, heightMm: Double) {
        context.calibrationDataStore.edit { prefs ->
            val current = decodePoints(prefs[pointsKey(profileId)]).filterNot { it.heightMm == heightMm }
            prefs[pointsKey(profileId)] = encodePoints(current)
        }
    }

    suspend fun updateDimensions(
        profileId: String,
        heightMm: Double?,
        topDiameterMm: Double?,
        bottomDiameterMm: Double?,
        usableInteriorHeightMm: Double?,
    ) {
        context.calibrationDataStore.edit { prefs ->
            heightMm?.let { prefs[heightKey(profileId)] = it.toFloat() }
            topDiameterMm?.let { prefs[topDiameterKey(profileId)] = it.toFloat() }
            bottomDiameterMm?.let { prefs[bottomDiameterKey(profileId)] = it.toFloat() }
            usableInteriorHeightMm?.let { prefs[usableHeightKey(profileId)] = it.toFloat() }
        }
    }

    suspend fun resetToDefaults(profileId: String) {
        context.calibrationDataStore.edit { prefs ->
            prefs.remove(pointsKey(profileId))
            prefs.remove(heightKey(profileId))
            prefs.remove(topDiameterKey(profileId))
            prefs.remove(bottomDiameterKey(profileId))
            prefs.remove(usableHeightKey(profileId))
        }
    }

    private fun encodePoints(points: List<CalibrationPoint>): String =
        points.joinToString(";") { "${it.heightMm},${it.volumeOz}" }

    private fun decodePoints(raw: String?): List<CalibrationPoint> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size != 2) return@mapNotNull null
            val h = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val v = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            CalibrationPoint(heightMm = h, volumeOz = v)
        }
    }
}
