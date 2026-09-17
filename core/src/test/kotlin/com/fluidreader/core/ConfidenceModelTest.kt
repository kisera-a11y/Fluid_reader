package com.fluidreader.core

import com.fluidreader.core.measurement.ConfidenceInputs
import com.fluidreader.core.measurement.ConfidenceLevel
import com.fluidreader.core.measurement.ConfidenceModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfidenceModelTest {

    private val good = ConfidenceInputs(
        cupDetectionScore = 0.95,
        boundaryQuality = 0.9,
        angleQuality = 0.95,
        liquidLineVisibility = 0.9,
        temporalStability = 0.9,
    )

    private val poor = ConfidenceInputs(
        cupDetectionScore = 0.3,
        boundaryQuality = 0.25,
        angleQuality = 0.2,
        liquidLineVisibility = 0.2,
        temporalStability = 0.3,
    )

    @Test
    fun `good signals produce high confidence with a tight range`() {
        val result = ConfidenceModel.evaluate(good, estimatedVolumeOz = 8.3)
        assertEquals(ConfidenceLevel.HIGH, result.level)
        assertTrue(result.uncertaintyOz <= 0.6, "expected a tight range, got +/-${result.uncertaintyOz}")
    }

    @Test
    fun `poor signals produce low confidence with a wide range`() {
        val result = ConfidenceModel.evaluate(poor, estimatedVolumeOz = 8.3)
        assertEquals(ConfidenceLevel.LOW, result.level)
        assertTrue(result.uncertaintyOz >= 1.0, "expected a wide range, got +/-${result.uncertaintyOz}")
    }

    @Test
    fun `range is centered on the estimate and never goes negative`() {
        val result = ConfidenceModel.evaluate(poor, estimatedVolumeOz = 0.2)
        val range = ConfidenceModel.rangeOz(result, 0.2)
        assertTrue(range.start >= 0.0)
        assertTrue(range.endInclusive > 0.2)
    }

    @Test
    fun `higher overall score never produces a wider uncertainty than a lower one`() {
        val goodResult = ConfidenceModel.evaluate(good, 8.3)
        val poorResult = ConfidenceModel.evaluate(poor, 8.3)
        assertTrue(goodResult.uncertaintyOz <= poorResult.uncertaintyOz)
    }
}
