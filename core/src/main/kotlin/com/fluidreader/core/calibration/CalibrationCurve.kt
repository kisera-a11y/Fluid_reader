package com.fluidreader.core.calibration

/**
 * A monotonic piecewise-linear lookup table built from user-entered [CalibrationPoint]s.
 *
 * The curve always anchors at (0 mm, 0 oz) - an empty cup - even if the caller didn't supply
 * that point explicitly. Points are sorted by height and, if the user's measurements were
 * noisy enough to imply the volume going *down* as height goes up, an isotonic (running-max)
 * correction is applied so the resulting curve is guaranteed non-decreasing, matching the
 * physical reality of a cup that only gets fuller as liquid rises.
 */
class CalibrationCurve private constructor(
    private val heights: DoubleArray,
    private val volumes: DoubleArray,
) {

    /** Highest calibrated height, in mm. Above this the curve is extrapolated flat/linear. */
    val maxCalibratedHeightMm: Double get() = heights.last()

    /** Volume in fluid ounces for the given liquid height (mm), clamped to calibrated range. */
    fun volumeOzAt(heightMm: Double): Double {
        val h = heightMm.coerceIn(0.0, heights.last())
        if (h <= heights.first()) return volumes.first()

        var lo = 0
        var hi = heights.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (heights[mid] <= h) lo = mid else hi = mid
        }
        val h0 = heights[lo]
        val h1 = heights[hi]
        val v0 = volumes[lo]
        val v1 = volumes[hi]
        if (h1 == h0) return v0
        val t = (h - h0) / (h1 - h0)
        return v0 + t * (v1 - v0)
    }

    companion object {
        /**
         * Builds a curve from raw points plus the cup's known full-height capacity, which is
         * always used as the top anchor so the curve tops out at the profile's rated volume.
         */
        fun build(
            points: List<CalibrationPoint>,
            usableInteriorHeightMm: Double,
            totalVolumeOz: Double,
        ): CalibrationCurve {
            val merged = LinkedHashMap<Double, Double>()
            merged[0.0] = 0.0
            for (p in points) merged[p.heightMm] = p.volumeOz
            merged[usableInteriorHeightMm] = totalVolumeOz

            val sortedHeights = merged.keys.sorted()
            val h = DoubleArray(sortedHeights.size)
            val v = DoubleArray(sortedHeights.size)
            var runningMax = 0.0
            for ((i, height) in sortedHeights.withIndex()) {
                h[i] = height
                runningMax = maxOf(runningMax, merged.getValue(height))
                v[i] = runningMax
            }
            return CalibrationCurve(h, v)
        }
    }
}
