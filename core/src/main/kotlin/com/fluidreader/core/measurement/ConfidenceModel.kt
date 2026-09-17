package com.fluidreader.core.measurement

import kotlin.math.max
import kotlin.math.min

enum class ConfidenceLevel { HIGH, MEDIUM, LOW }

/**
 * All the 0..1 quality signals the vision pipeline can estimate for a single frame (or a
 * short rolling window of frames). Each is independent so the UI/debug screen can show which
 * one is dragging the overall score down.
 */
data class ConfidenceInputs(
    /** How confidently the frame contains something shaped like the target cup. */
    val cupDetectionScore: Double,
    /** Sharpness/consistency of the detected rim + base edges. */
    val boundaryQuality: Double,
    /** 1.0 = dead-on side view, 0.0 = extreme/unusable viewing angle. */
    val angleQuality: Double,
    /** How distinct the detected liquid-surface boundary is from noise. */
    val liquidLineVisibility: Double,
    /** How stable the estimate has been over the last ~1 second of frames. */
    val temporalStability: Double,
) {
    private fun clamp(v: Double) = v.coerceIn(0.0, 1.0)

    /** Weighted overall score, 0..1. Angle and liquid visibility matter most. */
    val overallScore: Double = clamp(
        cupDetectionScore * 0.20 +
            boundaryQuality * 0.20 +
            angleQuality * 0.25 +
            liquidLineVisibility * 0.25 +
            temporalStability * 0.10
    )
}

data class ConfidenceResult(
    val score: Double,
    val level: ConfidenceLevel,
    /** Symmetric uncertainty half-width, in fluid ounces, to apply around the point estimate. */
    val uncertaintyOz: Double,
)

object ConfidenceModel {

    private const val HIGH_THRESHOLD = 0.75
    private const val MEDIUM_THRESHOLD = 0.45

    /** Best case (good lighting, square-on side view): roughly the app's target +/-0.5oz. */
    private const val MIN_UNCERTAINTY_OZ = 0.45
    /** Worst case still considered "usable" (not rejected outright): roughly +/-1.3oz. */
    private const val MAX_UNCERTAINTY_OZ = 1.3

    fun evaluate(inputs: ConfidenceInputs, estimatedVolumeOz: Double): ConfidenceResult {
        val score = inputs.overallScore
        val level = when {
            score >= HIGH_THRESHOLD -> ConfidenceLevel.HIGH
            score >= MEDIUM_THRESHOLD -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
        val geometricUncertainty =
            MAX_UNCERTAINTY_OZ - score * (MAX_UNCERTAINTY_OZ - MIN_UNCERTAINTY_OZ)
        // A little proportional slack for larger measured volumes, but the additive floor
        // above always dominates for a 16oz cup - this just avoids overconfidence near-full.
        val proportional = estimatedVolumeOz * 0.03
        val uncertainty = max(geometricUncertainty, proportional)
        return ConfidenceResult(
            score = score,
            level = level,
            uncertaintyOz = min(uncertainty, MAX_UNCERTAINTY_OZ + 0.3),
        )
    }

    /** Convenience: [ConfidenceResult] plus the [lower, upper] display range around a value. */
    fun rangeOz(result: ConfidenceResult, estimatedVolumeOz: Double): ClosedFloatingPointRange<Double> {
        val lower = max(0.0, estimatedVolumeOz - result.uncertaintyOz)
        val upper = estimatedVolumeOz + result.uncertaintyOz
        return lower..upper
    }
}
