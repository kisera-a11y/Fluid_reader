package com.fluidreader.app.vision

import android.graphics.RectF
import com.fluidreader.app.vision.model.CupBoundary
import com.fluidreader.core.cup.CupProfile
import kotlin.math.abs

/**
 * Refines a coarse ML Kit bounding box into a precise cup outline: the rim row, the base row,
 * and a left/right wall x-coordinate for every row in between. Pure horizontal-edge-energy /
 * vertical-edge scanning - no native dependency, cheap enough to run every analyzed frame.
 */
object CupBoundaryDetector {

    private const val ROI_MARGIN_FRACTION = 0.12
    private const val RIM_SEARCH_FRACTION = 0.28
    private const val WALL_EDGE_THRESHOLD = 22

    fun refine(frame: LumaFrame, roughBox: RectF, expectedProfile: CupProfile): CupBoundary? {
        val marginX = (roughBox.width() * ROI_MARGIN_FRACTION).toInt()
        val marginY = (roughBox.height() * ROI_MARGIN_FRACTION).toInt()
        val roiLeft = (roughBox.left.toInt() - marginX).coerceIn(0, frame.width - 2)
        val roiRight = (roughBox.right.toInt() + marginX).coerceIn(roiLeft + 1, frame.width - 1)
        val roiTop = (roughBox.top.toInt() - marginY).coerceIn(0, frame.height - 2)
        val roiBottom = (roughBox.bottom.toInt() + marginY).coerceIn(roiTop + 1, frame.height - 1)

        val roiHeight = roiBottom - roiTop
        if (roiHeight < 20) return null

        // A cup's rim and base show up as rows with unusually strong horizontal edge energy,
        // searched independently near the top and bottom of the ROI (so a busy background
        // elsewhere in the box doesn't win).
        val midXStart = roiLeft + (roiRight - roiLeft) * 0.15
        val midXEnd = roiLeft + (roiRight - roiLeft) * 0.85

        val rimSearchEnd = roiTop + (roiHeight * RIM_SEARCH_FRACTION).toInt()
        var rimY = roiTop
        var rimEnergy = -1.0
        for (y in roiTop until rimSearchEnd) {
            val e = EdgeDetector.rowHorizontalEdgeEnergy(frame, y, midXStart.toInt(), midXEnd.toInt())
            if (e > rimEnergy) {
                rimEnergy = e
                rimY = y
            }
        }

        val bottomSearchStart = roiBottom - (roiHeight * RIM_SEARCH_FRACTION).toInt()
        var bottomY = roiBottom
        var bottomEnergy = -1.0
        for (y in bottomSearchStart until roiBottom) {
            val e = EdgeDetector.rowHorizontalEdgeEnergy(frame, y, midXStart.toInt(), midXEnd.toInt())
            if (e > bottomEnergy) {
                bottomEnergy = e
                bottomY = y
            }
        }

        if (bottomY - rimY < 12) return null

        val rowCount = bottomY - rimY + 1
        val leftEdge = IntArray(rowCount)
        val rightEdge = IntArray(rowCount)
        val roiCenterX = (roiLeft + roiRight) / 2

        for (i in 0 until rowCount) {
            val y = rimY + i
            leftEdge[i] = findWallEdge(frame, y, fromX = roiLeft, toX = roiCenterX, stepIn = 1)
            rightEdge[i] = findWallEdge(frame, y, fromX = roiRight, toX = roiCenterX, stepIn = -1)
        }

        // Average the first/last few rows to reduce single-row noise when measuring the taper.
        val edgeSampleRows = minOf(4, rowCount / 4).coerceAtLeast(1)
        val topWidth = averageWidth(leftEdge, rightEdge, 0, edgeSampleRows)
        val bottomWidth = averageWidth(leftEdge, rightEdge, rowCount - edgeSampleRows, rowCount)

        val measuredRatio = if (topWidth > 0) bottomWidth / topWidth else 0.0
        val expectedRatio = expectedProfile.taperRatio
        val shapeMatchScore = (1.0 - (abs(measuredRatio - expectedRatio) / expectedRatio)).coerceIn(0.0, 1.0)

        val edgeQuality = (((rimEnergy + bottomEnergy) / 2.0) / 40.0).coerceIn(0.0, 1.0)

        return CupBoundary(
            rimY = rimY,
            bottomY = bottomY,
            leftEdge = leftEdge,
            rightEdge = rightEdge,
            shapeMatchScore = shapeMatchScore,
            edgeQuality = edgeQuality,
            topLeftX = averageX(leftEdge, 0, edgeSampleRows),
            topRightX = averageX(rightEdge, 0, edgeSampleRows),
            bottomLeftX = averageX(leftEdge, rowCount - edgeSampleRows, rowCount),
            bottomRightX = averageX(rightEdge, rowCount - edgeSampleRows, rowCount),
        )
    }

    private fun averageWidth(left: IntArray, right: IntArray, from: Int, to: Int): Double {
        var sum = 0.0
        var count = 0
        for (i in from until to) {
            sum += (right[i] - left[i])
            count++
        }
        return if (count == 0) 0.0 else sum / count
    }

    private fun averageX(edge: IntArray, from: Int, to: Int): Double {
        var sum = 0.0
        var count = 0
        for (i in from until to) {
            sum += edge[i]
            count++
        }
        return if (count == 0) 0.0 else sum / count
    }

    /**
     * Scans from [fromX] toward [toX] (step direction given by the sign of [stepIn], which
     * must be +1 or -1) for the *strongest* vertical edge in range - the cup wall against its
     * background - rather than just the first pixel to cross the threshold. A "first crossing"
     * scan is vulnerable to a single noisy/reflective pixel encountered early in the scan
     * winning over the true (stronger) wall edge further in; taking the max is more robust to
     * that kind of spurious trigger. Falls back to [fromX] if nothing in range clears the
     * threshold, so callers always get a usable (if less precise) boundary rather than a crash.
     */
    private fun findWallEdge(frame: LumaFrame, y: Int, fromX: Int, toX: Int, stepIn: Int): Int {
        var x = fromX
        var bestX = fromX
        var bestMagnitude = 0
        while (if (stepIn > 0) x < toX else x > toX) {
            val gx = abs(EdgeDetector.sobelGx(frame, x, y))
            if (gx > bestMagnitude) {
                bestMagnitude = gx
                bestX = x
            }
            x += stepIn
        }
        return if (bestMagnitude > WALL_EDGE_THRESHOLD) bestX else fromX
    }
}
