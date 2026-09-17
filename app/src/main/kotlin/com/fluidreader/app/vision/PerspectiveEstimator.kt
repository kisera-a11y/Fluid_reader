package com.fluidreader.app.vision

import com.fluidreader.app.vision.model.CupBoundary
import com.fluidreader.app.vision.model.PerspectiveEstimate
import kotlin.math.sqrt

/**
 * Estimates how close the current viewing angle is to a perpendicular side view, using only
 * the wall outline already extracted by [CupBoundaryDetector] - no extra CV pass needed.
 *
 * The key signal is how much the cup's horizontal center (midpoint between the left and right
 * wall) drifts as you move from rim to base. For a true side-on view of a symmetric frustum,
 * both walls taper symmetrically toward the same vertical centerline. A skewed/angled view
 * (looking down at the rim, or off to one side) makes the walls taper asymmetrically, so the
 * midpoint drifts sideways as height changes - the more it drifts, the worse the angle.
 */
object PerspectiveEstimator {

    private const val TOO_EXTREME_THRESHOLD = 0.35

    fun estimate(boundary: CupBoundary): PerspectiveEstimate {
        val rowCount = boundary.leftEdge.size
        if (rowCount < 4) {
            return PerspectiveEstimate(angleQuality = 0.0, topWidthPx = 0.0, bottomWidthPx = 0.0, tooExtreme = true)
        }

        val centers = DoubleArray(rowCount) { i ->
            (boundary.leftEdge[i] + boundary.rightEdge[i]) / 2.0
        }
        val meanCenter = centers.average()
        val avgWidth = (0 until rowCount).sumOf { (boundary.rightEdge[it] - boundary.leftEdge[it]).toDouble() } / rowCount

        val variance = centers.sumOf { (it - meanCenter) * (it - meanCenter) } / rowCount
        val centerDriftPx = sqrt(variance)
        val normalizedDrift = if (avgWidth > 0) (centerDriftPx / avgWidth).coerceIn(0.0, 1.0) else 1.0

        val angleQuality = (1.0 - normalizedDrift).coerceIn(0.0, 1.0)

        val edgeSampleRows = minOf(4, rowCount / 4).coerceAtLeast(1)
        val topWidth = (0 until edgeSampleRows).map { boundary.rightEdge[it] - boundary.leftEdge[it] }.average()
        val bottomWidth = (rowCount - edgeSampleRows until rowCount)
            .map { boundary.rightEdge[it] - boundary.leftEdge[it] }.average()

        return PerspectiveEstimate(
            angleQuality = angleQuality,
            topWidthPx = topWidth,
            bottomWidthPx = bottomWidth,
            tooExtreme = angleQuality < TOO_EXTREME_THRESHOLD,
        )
    }
}
