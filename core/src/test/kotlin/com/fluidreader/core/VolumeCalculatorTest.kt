package com.fluidreader.core

import com.fluidreader.core.calibration.CalibrationPoint
import com.fluidreader.core.cup.CupProfile
import com.fluidreader.core.cup.CupProfileRegistry
import com.fluidreader.core.measurement.VolumeCalculator
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VolumeCalculatorTest {

    private val solo16 = CupProfileRegistry.SOLO_16OZ

    @Test
    fun `full cup is approximately the rated capacity`() {
        val oz = VolumeCalculator.volumeOzAtFraction(solo16, 1.0)
        assertEquals(solo16.totalVolumeOz, oz, 0.01)
    }

    @Test
    fun `empty cup is zero`() {
        val oz = VolumeCalculator.volumeOzAtFraction(solo16, 0.0)
        assertEquals(0.0, oz, 1e-9)
    }

    @Test
    fun `half height liquid is NOT half the rated volume`() {
        val halfHeightOz = VolumeCalculator.volumeOzAtFraction(solo16, 0.5)
        val naiveHalf = solo16.totalVolumeOz / 2.0
        // Because the cup tapers (narrower at the base), the bottom half of the height holds
        // less than half the total volume - this must NOT equal the naive percentage-of-height
        // guess the spec explicitly warns against.
        assertTrue(
            abs(halfHeightOz - naiveHalf) > 0.3,
            "expected half-height volume ($halfHeightOz) to diverge meaningfully from a naive " +
                "50% guess ($naiveHalf) given the frustum taper",
        )
        assertTrue(halfHeightOz < naiveHalf, "tapered cup's lower half should hold less than half the volume")
    }

    @Test
    fun `low liquid gives a small but nonzero appropriate value`() {
        val lowOz = VolumeCalculator.volumeOzAtFraction(solo16, 0.1)
        assertTrue(lowOz > 0.0)
        assertTrue(lowOz < solo16.totalVolumeOz * 0.1, "10% height should hold less than 10% of volume (narrower base)")
    }

    @Test
    fun `volume is monotonically non-decreasing across the full height range`() {
        var previous = -1.0
        var step = 0.0
        while (step <= 1.0001) {
            val oz = VolumeCalculator.volumeOzAtFraction(solo16, step)
            assertTrue(oz >= previous - 1e-9, "volume decreased at fraction=$step: $oz < $previous")
            previous = oz
            step += 0.01
        }
    }

    @Test
    fun `radius grows linearly from bottom to top`() {
        val rBottom = VolumeCalculator.radiusAtHeightMm(solo16, 0.0)
        val rTop = VolumeCalculator.radiusAtHeightMm(solo16, solo16.usableInteriorHeight)
        assertEquals(solo16.bottomDiameter / 2.0, rBottom, 1e-9)
        assertEquals(solo16.topDiameter / 2.0, rTop, 1e-9)

        val rMid = VolumeCalculator.radiusAtHeightMm(solo16, solo16.usableInteriorHeight / 2.0)
        assertEquals((rBottom + rTop) / 2.0, rMid, 1e-9)
    }

    @Test
    fun `calibration points override the geometric model and stay monotonic`() {
        val calibrated = solo16.copy(
            calibrationPoints = listOf(
                CalibrationPoint(heightMm = 25.0, volumeOz = 1.5),
                CalibrationPoint(heightMm = 50.0, volumeOz = 4.0),
                CalibrationPoint(heightMm = 75.0, volumeOz = 7.5),
            ),
        )

        assertEquals(1.5, VolumeCalculator.volumeOz(calibrated, 25.0), 1e-9)
        assertEquals(4.0, VolumeCalculator.volumeOz(calibrated, 50.0), 1e-9)
        assertEquals(7.5, VolumeCalculator.volumeOz(calibrated, 75.0), 1e-9)

        // interpolated point between two calibration measurements
        val between = VolumeCalculator.volumeOz(calibrated, 37.5)
        assertTrue(between in 1.5..4.0)

        // still reaches full rated capacity at 100% height
        assertEquals(solo16.totalVolumeOz, VolumeCalculator.volumeOz(calibrated, solo16.usableInteriorHeight), 1e-9)

        var previous = -1.0
        var h = 0.0
        while (h <= solo16.usableInteriorHeight) {
            val oz = VolumeCalculator.volumeOz(calibrated, h)
            assertTrue(oz >= previous - 1e-9)
            previous = oz
            h += 1.0
        }
    }

    @Test
    fun `out of range fractions are clamped`() {
        assertEquals(0.0, VolumeCalculator.volumeOzAtFraction(solo16, -0.5), 1e-9)
        assertEquals(solo16.totalVolumeOz, VolumeCalculator.volumeOzAtFraction(solo16, 1.5), 0.01)
    }

    @Test
    fun `a narrower-based cup profile holds proportionally less near the bottom`() {
        val extremeTaper = CupProfile(
            id = "test",
            name = "test",
            totalVolumeOz = 16.0,
            height = 130.0,
            topDiameter = 100.0,
            bottomDiameter = 20.0,
            usableInteriorHeight = 130.0,
        )
        val lowFraction = VolumeCalculator.volumeOzAtFraction(extremeTaper, 0.25)
        assertTrue(lowFraction < extremeTaper.totalVolumeOz * 0.25)
    }
}
