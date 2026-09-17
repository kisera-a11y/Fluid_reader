package com.fluidreader.core.measurement

/**
 * Stabilizes a noisy per-frame stream of measurements (e.g. 8.1 -> 8.7 -> 7.9 -> 8.5 oz)
 * into a slowly-moving displayed value.
 *
 * Two stages:
 *  1. A rolling median over the last [windowSize] samples, which rejects single-frame
 *     outliers (a stray reflection, a hand passing through frame) without lag on genuine
 *     step changes.
 *  2. An exponential moving average over the median stream, which removes the remaining
 *     small frame-to-frame jitter.
 *
 * Not thread-safe; intended to be owned by a single measurement loop (e.g. a ViewModel).
 */
class TemporalSmoother(
    private val windowSize: Int = 7,
    private val emaAlpha: Double = 0.35,
) {
    init {
        require(windowSize >= 1) { "windowSize must be >= 1" }
        require(emaAlpha > 0.0 && emaAlpha <= 1.0) { "emaAlpha must be in (0, 1]" }
    }

    private val window = ArrayDeque<Double>()
    private var ema: Double? = null

    /** Feeds one new raw measurement and returns the current smoothed value. */
    fun addSample(value: Double): Double {
        window.addLast(value)
        while (window.size > windowSize) window.removeFirst()

        val median = median(window)
        val previousEma = ema
        val newEma = if (previousEma == null) median else {
            previousEma + emaAlpha * (median - previousEma)
        }
        ema = newEma
        return newEma
    }

    /** Current smoothed value without adding a new sample, or null if nothing fed yet. */
    fun current(): Double? = ema

    /** Clears all history - call this when the cup/liquid is no longer detected. */
    fun reset() {
        window.clear()
        ema = null
    }

    private fun median(values: Collection<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }
}
