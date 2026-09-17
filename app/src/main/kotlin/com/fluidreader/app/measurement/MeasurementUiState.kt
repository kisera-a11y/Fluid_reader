package com.fluidreader.app.measurement

import com.fluidreader.app.vision.model.CupBoundary
import com.fluidreader.core.cup.CupProfile
import com.fluidreader.core.cup.CupProfileRegistry
import com.fluidreader.core.measurement.ConfidenceLevel
import com.fluidreader.core.units.VolumeUnit

/** What the camera screen should currently show, folding in temporal state the raw pipeline can't know. */
enum class DisplayState {
    NO_CUP,
    NEEDS_CENTERING,
    NEEDS_SIDE_VIEW,
    ANGLE_TOO_EXTREME,
    LIQUID_NOT_VISIBLE,
    BELOW_CONFIDENCE_THRESHOLD,
    STABILIZING,
    READY,
    MANUAL,
}

enum class ManualLineType { RIM, BOTTOM, LIQUID }

data class MeasurementUiState(
    val displayState: DisplayState = DisplayState.NO_CUP,
    val volumeOz: Double = 0.0,
    val rangeLowOz: Double = 0.0,
    val rangeHighOz: Double = 0.0,
    val confidenceLevel: ConfidenceLevel = ConfidenceLevel.LOW,
    val confidenceScore: Double = 0.0,
    val units: VolumeUnit = VolumeUnit.FLUID_OUNCES,
    val showOverlay: Boolean = true,
    val cupProfile: CupProfile = CupProfileRegistry.SOLO_16OZ,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val boundary: CupBoundary? = null,
    val liquidY: Int? = null,
    val manualOverrideEnabled: Boolean = false,
    val manualRimY: Int? = null,
    val manualBottomY: Int? = null,
    val manualLiquidY: Int? = null,
    val debugLog: List<String> = emptyList(),
) {
    val displayVolumeText: String get() = units.format(volumeOz)
    val displayRangeText: String get() =
        "${units.formatValue(rangeLowOz)}-${units.formatValue(rangeHighOz)} ${units.label}"
}
