package com.fluidreader.app.vision

import com.fluidreader.app.vision.model.CupBoundary
import com.fluidreader.app.vision.model.LiquidLevelResult
import kotlin.math.abs

/**
 * Looks for the liquid surface inside an already-detected cup outline.
 *
 * Transparent cups don't give a single reliable cue, so this combines several per-row signals
 * that a meniscus/liquid boundary tends to produce, any one of which might be weak or absent
 * depending on the drink:
 *  - a horizontal edge (the meniscus itself, or a reflection/highlight band along it)
 *  - a step change in mean brightness (liquid vs. empty air space, or vs. background seen
 *    through an empty cup)
 *  - a step change in local texture/variance (bubbles, carbonation, refraction distortion of
 *    whatever is behind the cup, or condensation - all increase pixel-to-pixel variance right
 *    at the boundary compared to the smoother regions above/below it)
 *
 * The row whose combined score stands out most from the rest of the interior's score profile
 * is taken as the liquid line; how much it stands out becomes the visibility/confidence score.
 */
object LiquidLevelDetector {

    private const val TOP_MARGIN_FRACTION = 0.08
    private const val BOTTOM_MARGIN_FRACTION = 0.06
    private const val ROW_STEP = 2
    private const val DELTA_ROWS = 3
    private const val MIN_VISIBILITY_TO_ACCEPT = 0.18

    fun detect(frame: LumaFrame, boundary: CupBoundary): LiquidLevelResult {
        val totalHeight = boundary.bottomY - boundary.rimY
        val top = boundary.rimY + (totalHeight * TOP_MARGIN_FRACTION).toInt()
        val bottom = boundary.bottomY - (totalHeight * BOTTOM_MARGIN_FRACTION).toInt()
        if (bottom - top < 6) {
            return LiquidLevelResult(detected = false, surfaceY = boundary.bottomY, visibilityScore = 0.0)
        }

        val rows = (top..bottom step ROW_STEP).toList()
        val meanLuma = DoubleArray(rows.size)
        val stdDev = DoubleArray(rows.size)
        val edgeEnergy = DoubleArray(rows.size)

        for ((i, y) in rows.withIndex()) {
            val width = boundary.widthAt(y)
            val inset = (width * 0.10).toInt()
            val xStart = boundary.leftAt(y) + inset
            val xEnd = boundary.rightAt(y) - inset
            if (xEnd <= xStart) continue
            meanLuma[i] = EdgeDetector.rowMeanLuma(frame, y, xStart, xEnd)
            stdDev[i] = EdgeDetector.rowLumaStdDev(frame, y, xStart, xEnd)
            edgeEnergy[i] = EdgeDetector.rowHorizontalEdgeEnergy(frame, y, xStart, xEnd)
        }

        val scores = DoubleArray(rows.size)
        val maxEdge = edgeEnergy.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val maxMeanDelta = (1 until rows.size).maxOfOrNull { i ->
            val j = (i - DELTA_ROWS).coerceAtLeast(0)
            abs(meanLuma[i] - meanLuma[j])
        }?.coerceAtLeast(1.0) ?: 1.0
        val maxStdDelta = (1 until rows.size).maxOfOrNull { i ->
            val j = (i - DELTA_ROWS).coerceAtLeast(0)
            abs(stdDev[i] - stdDev[j])
        }?.coerceAtLeast(1.0) ?: 1.0

        for (i in rows.indices) {
            val j = (i - DELTA_ROWS).coerceAtLeast(0)
            val meanDelta = abs(meanLuma[i] - meanLuma[j]) / maxMeanDelta
            val stdDelta = abs(stdDev[i] - stdDev[j]) / maxStdDelta
            val edgeNorm = edgeEnergy[i] / maxEdge
            scores[i] = edgeNorm * 0.45 + meanDelta * 0.30 + stdDelta * 0.25
        }

        // Ignore the first/last couple of sampled rows so a residual rim/base edge inside the
        // margin can't masquerade as the liquid line.
        val searchStart = minOf(2, scores.size - 1)
        val searchEnd = maxOf(scores.size - 2, searchStart + 1)
        var bestIndex = searchStart
        var bestScore = -1.0
        for (i in searchStart until searchEnd) {
            if (scores[i] > bestScore) {
                bestScore = scores[i]
                bestIndex = i
            }
        }

        val relevantScores = scores.slice(searchStart until searchEnd)
        val meanScore = relevantScores.average()
        val stdScore = kotlin.math.sqrt(relevantScores.sumOf { (it - meanScore) * (it - meanScore) } / relevantScores.size)
        val contrast = if (stdScore > 1e-6) (bestScore - meanScore) / stdScore else 0.0
        val visibility = (contrast / 3.0).coerceIn(0.0, 1.0)

        val surfaceY = rows[bestIndex]
        return LiquidLevelResult(
            detected = visibility >= MIN_VISIBILITY_TO_ACCEPT,
            surfaceY = surfaceY,
            visibilityScore = visibility,
        )
    }
}
