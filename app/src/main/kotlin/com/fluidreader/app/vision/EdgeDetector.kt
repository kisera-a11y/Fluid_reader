package com.fluidreader.app.vision

import kotlin.math.abs

/**
 * Lightweight grayscale gradient operations used throughout the vision pipeline.
 *
 * Everything here works directly on a single-channel luma buffer (the Y plane of a
 * YUV_420_888 camera frame, or a grayscale-converted bitmap in debug/test mode) so it stays
 * fast enough to run several times a second on a phone without any native/OpenCV dependency.
 */
class LumaFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val rowStride: Int,
) {
    /** Luma value 0..255 at (x, y), clamped to the frame bounds. */
    fun at(x: Int, y: Int): Int {
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        return data[cy * rowStride + cx].toInt() and 0xFF
    }
}

object EdgeDetector {

    /** Horizontal gradient (Sobel Gx) - large for vertical edges such as cup walls. */
    fun sobelGx(frame: LumaFrame, x: Int, y: Int): Int {
        return (frame.at(x + 1, y - 1) + 2 * frame.at(x + 1, y) + frame.at(x + 1, y + 1)) -
            (frame.at(x - 1, y - 1) + 2 * frame.at(x - 1, y) + frame.at(x - 1, y + 1))
    }

    /** Vertical gradient (Sobel Gy) - large for horizontal edges such as a rim or liquid line. */
    fun sobelGy(frame: LumaFrame, x: Int, y: Int): Int {
        return (frame.at(x - 1, y + 1) + 2 * frame.at(x, y + 1) + frame.at(x + 1, y + 1)) -
            (frame.at(x - 1, y - 1) + 2 * frame.at(x, y - 1) + frame.at(x + 1, y - 1))
    }

    /** Cheap gradient magnitude approximation (|Gx| + |Gy|), fast enough for per-pixel scans. */
    fun gradientMagnitude(frame: LumaFrame, x: Int, y: Int): Int =
        abs(sobelGx(frame, x, y)) + abs(sobelGy(frame, x, y))

    /**
     * Sum of |Gy| across [xStart, xEnd) at row [y] - a proxy for "how strong a horizontal
     * edge runs through this row", used to locate the rim, the base, and the liquid line.
     * [stepX] subsamples columns for speed on wide ROIs.
     */
    fun rowHorizontalEdgeEnergy(frame: LumaFrame, y: Int, xStart: Int, xEnd: Int, stepX: Int = 2): Double {
        var sum = 0.0
        var count = 0
        var x = xStart
        while (x < xEnd) {
            sum += abs(sobelGy(frame, x, y))
            count++
            x += stepX
        }
        return if (count == 0) 0.0 else sum / count
    }

    /** Mean luma across [xStart, xEnd) at row [y]. */
    fun rowMeanLuma(frame: LumaFrame, y: Int, xStart: Int, xEnd: Int, stepX: Int = 2): Double {
        var sum = 0.0
        var count = 0
        var x = xStart
        while (x < xEnd) {
            sum += frame.at(x, y)
            count++
            x += stepX
        }
        return if (count == 0) 0.0 else sum / count
    }

    /** Luma standard deviation across [xStart, xEnd) at row [y] - a texture proxy. */
    fun rowLumaStdDev(frame: LumaFrame, y: Int, xStart: Int, xEnd: Int, stepX: Int = 2): Double {
        val mean = rowMeanLuma(frame, y, xStart, xEnd, stepX)
        var sumSq = 0.0
        var count = 0
        var x = xStart
        while (x < xEnd) {
            val d = frame.at(x, y) - mean
            sumSq += d * d
            count++
            x += stepX
        }
        return if (count == 0) 0.0 else kotlin.math.sqrt(sumSq / count)
    }
}
