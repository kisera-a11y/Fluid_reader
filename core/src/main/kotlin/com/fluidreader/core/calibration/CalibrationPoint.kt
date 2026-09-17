package com.fluidreader.core.calibration

/**
 * One measured data point used to build/override the geometric volume model:
 * "when the liquid stood [heightMm] tall inside the cup, it measured [volumeOz]
 * fluid ounces". Entered by hand in the app's Calibration screen.
 */
data class CalibrationPoint(
    val heightMm: Double,
    val volumeOz: Double,
) {
    init {
        require(heightMm >= 0.0) { "heightMm must be >= 0" }
        require(volumeOz >= 0.0) { "volumeOz must be >= 0" }
    }
}
