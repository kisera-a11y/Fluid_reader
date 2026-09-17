package com.fluidreader.core.measurement

import com.fluidreader.core.calibration.CalibrationCurve
import com.fluidreader.core.cup.CupProfile
import com.fluidreader.core.units.UnitConverter
import kotlin.math.PI

/**
 * Turns a liquid height inside a [CupProfile] into a volume in fluid ounces.
 *
 * The cup is modeled as a frustum (truncated cone): the radius grows linearly from
 * [CupProfile.bottomDiameter]/2 at the base to [CupProfile.topDiameter]/2 at the rim. The
 * volume from the base up to height h is the standard frustum-of-a-cone integral:
 *
 *   r(h) = r_bottom + (r_top - r_bottom) * h / H
 *   V(h) = (pi/3) * h * (r_bottom^2 + r_bottom*r(h) + r(h)^2)
 *
 * Because a real disposable cup is not a perfect cone (rounded base, rim lip, wall taper
 * that isn't perfectly linear), the raw geometric volume at full height is normalized so it
 * equals the cup's rated [CupProfile.totalVolumeOz] exactly at 100% of [CupProfile.usableInteriorHeight].
 * That normalization is a single constant scale factor, so it does not change the *shape* of
 * the volume-vs-height curve (still strictly monotonic), only its calibration against the
 * printed capacity.
 *
 * If the profile carries measured [CupProfile.calibrationPoints], those take over completely
 * via a monotonic interpolated [CalibrationCurve] instead of the pure geometry - "trust the
 * ruler over the math" once real measurements exist.
 */
object VolumeCalculator {

    /** Interior radius (mm) at height [heightMm] above the base, per the frustum model. */
    fun radiusAtHeightMm(profile: CupProfile, heightMm: Double): Double {
        val h = heightMm.coerceIn(0.0, profile.usableInteriorHeight)
        val rBottom = profile.bottomDiameter / 2.0
        val rTop = profile.topDiameter / 2.0
        val fraction = h / profile.usableInteriorHeight
        return rBottom + (rTop - rBottom) * fraction
    }

    /** Raw (un-normalized) frustum volume in mm^3 from the base up to [heightMm]. */
    fun geometricVolumeMm3(profile: CupProfile, heightMm: Double): Double {
        val h = heightMm.coerceIn(0.0, profile.usableInteriorHeight)
        val rBottom = profile.bottomDiameter / 2.0
        val rTop = radiusAtHeightMm(profile, h)
        return (PI / 3.0) * h * (rBottom * rBottom + rBottom * rTop + rTop * rTop)
    }

    /**
     * Scale factor that makes the raw geometric volume at 100% height equal
     * [CupProfile.totalVolumeOz]. Public so debug tooling can display it.
     */
    fun normalizationFactor(profile: CupProfile): Double {
        val fullGeometricMl = geometricVolumeMm3(profile, profile.usableInteriorHeight) / 1000.0
        val fullGeometricOz = UnitConverter.mlToOz(fullGeometricMl)
        if (fullGeometricOz <= 0.0) return 1.0
        return profile.totalVolumeOz / fullGeometricOz
    }

    /** Normalized geometric-model volume (fl oz) for a liquid column of height [heightMm]. */
    fun geometricVolumeOz(profile: CupProfile, heightMm: Double): Double {
        val ml = geometricVolumeMm3(profile, heightMm) / 1000.0
        return UnitConverter.mlToOz(ml) * normalizationFactor(profile)
    }

    /**
     * Best-available volume (fl oz) for liquid height [heightMm]: the calibration curve when
     * the profile has real measurements, otherwise the normalized frustum geometry.
     */
    fun volumeOz(profile: CupProfile, heightMm: Double): Double {
        val h = heightMm.coerceIn(0.0, profile.usableInteriorHeight)
        if (profile.calibrationPoints.isNotEmpty()) {
            val curve = CalibrationCurve.build(
                points = profile.calibrationPoints,
                usableInteriorHeightMm = profile.usableInteriorHeight,
                totalVolumeOz = profile.totalVolumeOz,
            )
            return curve.volumeOzAt(h)
        }
        return geometricVolumeOz(profile, h)
    }

    /**
     * Convenience overload for the camera pipeline, which naturally works in normalized
     * height fraction (0 = empty, 1 = full to the usable rim line) rather than millimeters.
     */
    fun volumeOzAtFraction(profile: CupProfile, heightFraction: Double): Double {
        val fraction = heightFraction.coerceIn(0.0, 1.0)
        return volumeOz(profile, fraction * profile.usableInteriorHeight)
    }
}
