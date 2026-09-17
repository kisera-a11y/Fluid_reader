package com.fluidreader.app.ui.camera

import com.fluidreader.app.R
import com.fluidreader.app.measurement.DisplayState
import com.fluidreader.core.measurement.ConfidenceLevel

/** Primary on-screen guidance banner text for each [DisplayState]. Null = no banner needed. */
fun DisplayState.primaryGuidanceRes(): Int? = when (this) {
    DisplayState.NO_CUP -> R.string.guidance_not_detected
    DisplayState.NEEDS_CENTERING -> R.string.guidance_center_cup
    DisplayState.NEEDS_SIDE_VIEW -> R.string.guidance_move_side
    DisplayState.ANGLE_TOO_EXTREME -> R.string.guidance_too_close_angle
    DisplayState.LIQUID_NOT_VISIBLE -> R.string.guidance_unavailable
    DisplayState.BELOW_CONFIDENCE_THRESHOLD -> R.string.guidance_unavailable
    DisplayState.STABILIZING -> R.string.guidance_hold_steady
    DisplayState.READY -> null
    DisplayState.MANUAL -> null
}

/** Secondary/subtitle guidance text, shown smaller under the primary banner. */
fun DisplayState.secondaryGuidanceRes(): Int? = when (this) {
    DisplayState.LIQUID_NOT_VISIBLE, DisplayState.BELOW_CONFIDENCE_THRESHOLD -> R.string.guidance_unavailable_hint
    else -> null
}

fun ConfidenceLevel.labelRes(): Int = when (this) {
    ConfidenceLevel.HIGH -> R.string.confidence_high
    ConfidenceLevel.MEDIUM -> R.string.confidence_medium
    ConfidenceLevel.LOW -> R.string.confidence_low
}
