package com.fluidreader.core

import com.fluidreader.core.measurement.TemporalSmoother
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemporalSmootherTest {

    @Test
    fun `single outlier does not swing the smoothed value as much as the raw jump`() {
        val smoother = TemporalSmoother(windowSize = 5, emaAlpha = 0.4)
        val steadyValue = 8.0
        repeat(6) { smoother.addSample(steadyValue) }

        val afterOutlier = smoother.addSample(15.0) // a stray reflection spike
        assertTrue(
            abs(afterOutlier - steadyValue) < abs(15.0 - steadyValue),
            "outlier should be heavily damped, got $afterOutlier",
        )
    }

    @Test
    fun `converges toward a new steady value over successive frames`() {
        val smoother = TemporalSmoother(windowSize = 5, emaAlpha = 0.5)
        repeat(6) { smoother.addSample(8.0) }
        var last = 8.0
        repeat(10) { last = smoother.addSample(10.0) }
        assertTrue(abs(last - 10.0) < 0.3, "expected convergence near 10.0, got $last")
    }

    @Test
    fun `reset clears history`() {
        val smoother = TemporalSmoother()
        smoother.addSample(5.0)
        smoother.reset()
        assertNull(smoother.current())
    }
}
