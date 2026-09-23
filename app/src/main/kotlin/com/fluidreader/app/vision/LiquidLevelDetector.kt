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
 *  - a *sustained* step change in mean brightness (liquid vs. empty air space, or vs.
 *    background seen through an empty cup)
 *  - a *sustained* step change in local texture/variance (bubbles, carbonation, refraction
 *    distortion of whatever is behind the cup, or condensation)
 *
 * Brightness and variance are scored as a windowed before/after step (average of several rows
 * immediately above a candidate vs. several rows immediately below it), not a fixed small-offset
 * delta. This is what lets it reject a thin structural feature molded into the glass itself - a
 * grip ridge, a stacking lip, a seam - that produces a real one- or two-row edge/brightness blip
 * but doesn't hold up as a lasting regime change: a few rows past it, things go back to matching
 * what was above it. A genuine liquid surface doesn't revert - everything below it stays liquid
 * all the way to the bottom of the cup - so only a real surface keeps scoring high across the
 * whole "after" window, not just the row or two right at the transition.
 *
 * The row whose combined score stands out most from the rest of the interior's score profile
 * is taken as the liquid line; how much it stands out becomes the visibility/confidence score.
 */
object LiquidLevelDetector {

    private const val TOP_MARGIN_FRACTION = 0.08
    private const val BOTTOM_MARGIN_FRACTION = 0.06
    private const val ROW_STEP = 2
    private const val STEP_WINDOW_ROWS = 4
    private const val MIN_VISIBILITY_TO_ACCEPT = 0.18

    fun detect(frame: LumaFrame, boundary: CupBoundary): LiquidLevelResult {
        val totalHeight = boundary.bottomY - boundary.rimY
        val top = boundary.rimY + (totalHeight * TOP_MARGIN_FRACTION).toInt()
        val bottom = boundary.bottomY - (totalHeight * BOTTOM_MARGIN_FRACTION).toInt()
        if (bottom - top < 6) {
            return LiquidLevelResult(detected = false, surfaceY = boundary.bottomY, visibilityScore = 0.0)
        }

        val rows = (top..bottom step ROW_STEP).toList()
        // Every candidate row needs a full STEP_WINDOW_ROWS of real sampled context on both
        // sides, or there's nothing to compare it against.
        if (rows.size < STEP_WINDOW_ROWS * 2 + 3) {
            return LiquidLevelResult(detected = false, surfaceY = boundary.bottomY, visibilityScore = 0.0)
        }

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

        val searchStart = STEP_WINDOW_ROWS
        val searchEnd = rows.size - STEP_WINDOW_ROWS

        fun stepScore(values: DoubleArray, i: Int): Double {
            var before = 0.0
            var after = 0.0
            for (k in 1..STEP_WINDOW_ROWS) {
                before += values[i - k]
                after += values[i + k - 1]
            }
            return abs(after / STEP_WINDOW_ROWS - before / STEP_WINDOW_ROWS)
        }

        val meanStep = DoubleArray(rows.size)
        val stdStep = DoubleArray(rows.size)
        for (i in searchStart until searchEnd) {
            meanStep[i] = stepScore(meanLuma, i)
            stdStep[i] = stepScore(stdDev, i)
        }

        val maxMeanStep = (searchStart until searchEnd).maxOf { meanStep[it] }.coerceAtLeast(1.0)
        val maxStdStep = (searchStart until searchEnd).maxOf { stdStep[it] }.coerceAtLeast(1.0)
        val maxEdge = (searchStart until searchEnd).maxOf { edgeEnergy[it] }.coerceAtLeast(1.0)

        val scores = DoubleArray(rows.size)
        for (i in searchStart until searchEnd) {
            val meanScore = meanStep[i] / maxMeanStep
            val stdScore = stdStep[i] / maxStdStep
            // Edge energy stays an instantaneous per-row cue (a meniscus is a thin band), but
            // now carries less weight than the two sustained cues so a one-row structural edge
            // alone can't win.
            val edgeScore = edgeEnergy[i] / maxEdge
            scores[i] = meanScore * 0.45 + stdScore * 0.30 + edgeScore * 0.25
        }

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
