package com.fluidreader.app.vision.model

import android.graphics.RectF

/** Result of the coarse cup-candidate detection stage (ML Kit object detection + heuristics). */
data class CupCandidate(
    val boundingBox: RectF,
    /** 0..1 - how confident ML Kit was that this is a prominent foreground object. */
    val detectorScore: Double,
)

/**
 * Result of refining a [CupCandidate] into a precise rim/base/wall outline via edge scanning.
 * All pixel coordinates are in the coordinate space of the analyzed frame.
 */
data class CupBoundary(
    val rimY: Int,
    val bottomY: Int,
    /** Left wall x-coordinate for each row from rimY..bottomY, indexed by (row - rimY). */
    val leftEdge: IntArray,
    /** Right wall x-coordinate for each row from rimY..bottomY, indexed by (row - rimY). */
    val rightEdge: IntArray,
    /** How well the measured top/bottom width ratio matches the target [expectedTaperRatio]. */
    val shapeMatchScore: Double,
    /** How sharp/consistent the detected rim + base edges were (0..1). */
    val edgeQuality: Double,
    /**
     * Wall x-coordinates at the rim/base, each averaged over several rows (same rows used for
     * [shapeMatchScore]) rather than a single noisy row - the four corners of the cup's
     * straight-sided silhouette, used to draw a clean trapezoid outline instead of a jagged
     * per-row line.
     */
    val topLeftX: Double,
    val topRightX: Double,
    val bottomLeftX: Double,
    val bottomRightX: Double,
) {
    fun leftAt(y: Int): Int = leftEdge.getOrElse((y - rimY).coerceIn(0, leftEdge.size - 1)) { leftEdge.first() }
    fun rightAt(y: Int): Int = rightEdge.getOrElse((y - rimY).coerceIn(0, rightEdge.size - 1)) { rightEdge.first() }
    fun widthAt(y: Int): Int = (rightAt(y) - leftAt(y)).coerceAtLeast(1)
}

/** Result of estimating how square-on the current viewing angle is. */
data class PerspectiveEstimate(
    /** 0 (unusable, extreme angle) .. 1 (dead-on side view). */
    val angleQuality: Double,
    val topWidthPx: Double,
    val bottomWidthPx: Double,
    val tooExtreme: Boolean,
)

/** Result of scanning the cup interior for the liquid surface. */
data class LiquidLevelResult(
    val detected: Boolean,
    /** Absolute row (frame coordinates) of the detected surface, valid only if [detected]. */
    val surfaceY: Int,
    /** 0..1 - how distinct the winning row was versus the noise floor of the scan. */
    val visibilityScore: Double,
)

/**
 * Per-frame classification from the vision pipeline alone (no temporal history). The
 * measurement layer (see `measurement/MeasurementViewModel`) turns [MEASURED] into either a
 * "hold steady, stabilizing" or a final "ready" UI state once it has seen enough consistent
 * frames in a row, and turns everything else into the matching on-screen guidance text.
 */
enum class PipelineState {
    NO_CUP,
    NEEDS_CENTERING,
    NEEDS_SIDE_VIEW,
    ANGLE_TOO_EXTREME,
    LIQUID_NOT_VISIBLE,
    MEASURED,
}

/** Full output of one pipeline pass over a single frame. */
data class PipelineResult(
    val state: PipelineState,
    val candidate: CupCandidate? = null,
    val boundary: CupBoundary? = null,
    val perspective: PerspectiveEstimate? = null,
    val liquid: LiquidLevelResult? = null,
    /** Normalized liquid height, 0 (empty, at bottomY) .. 1 (full, at rimY). Null if unknown. */
    val heightFraction: Double? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    /** Human-readable trace of intermediate measurements, for the debug/test screen. */
    val debugLog: List<String> = emptyList(),
)
