package com.fluidreader.app.calibration

import android.content.Context
import com.fluidreader.core.cup.CupProfile
import com.fluidreader.core.cup.CupProfileRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Combines a base [CupProfile] (from [CupProfileRegistry]) with whatever the user has entered
 * in the Calibration screen into the single "effective" profile the vision pipeline and
 * [com.fluidreader.core.measurement.VolumeCalculator] should actually use.
 */
class CupProfileRepository(context: Context) {

    private val calibrationStore = CalibrationStore(context)

    fun observeEffectiveProfile(profileId: String): Flow<CupProfile> {
        val base = CupProfileRegistry.byId(profileId)
        return calibrationStore.observe(profileId).map { calibration -> applyOverrides(base, calibration) }
    }

    private fun applyOverrides(base: CupProfile, calibration: CalibrationData): CupProfile = base.copy(
        height = calibration.heightOverrideMm ?: base.height,
        topDiameter = calibration.topDiameterOverrideMm ?: base.topDiameter,
        bottomDiameter = calibration.bottomDiameterOverrideMm ?: base.bottomDiameter,
        usableInteriorHeight = calibration.usableInteriorHeightOverrideMm ?: base.usableInteriorHeight,
        calibrationPoints = calibration.points,
    )

    fun store(): CalibrationStore = calibrationStore
}
