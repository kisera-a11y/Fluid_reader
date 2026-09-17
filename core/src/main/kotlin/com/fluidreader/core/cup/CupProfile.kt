package com.fluidreader.core.cup

import com.fluidreader.core.calibration.CalibrationPoint

/**
 * Describes one physical cup model as a truncated cone (frustum) plus optional real-world
 * calibration measurements that override the pure-geometry estimate.
 *
 * All linear dimensions are in millimeters, matching what a person would read off a ruler
 * or calipers in the Calibration screen.
 */
data class CupProfile(
    val id: String,
    val name: String,
    /** Rated capacity of the cup, e.g. 16.0 for a "16 oz" Solo-style cup. */
    val totalVolumeOz: Double,
    /** Full exterior height of the cup. */
    val height: Double,
    /** Interior diameter at the rim. */
    val topDiameter: Double,
    /** Interior diameter at the base. */
    val bottomDiameter: Double,
    /**
     * How tall a liquid column can actually get before it would spill at the rim. Usually a
     * little less than [height] because of the base thickness / rim lip. This is the height
     * that maps to 100% = [totalVolumeOz].
     */
    val usableInteriorHeight: Double,
    /**
     * Optional measured points (height in mm -> known volume in fl oz) that replace the pure
     * frustum math with a calibration curve. Empty by default, meaning "trust the geometry".
     */
    val calibrationPoints: List<CalibrationPoint> = emptyList(),
) {
    init {
        require(totalVolumeOz > 0.0) { "totalVolumeOz must be > 0" }
        require(height > 0.0) { "height must be > 0" }
        require(topDiameter > 0.0) { "topDiameter must be > 0" }
        require(bottomDiameter > 0.0) { "bottomDiameter must be > 0" }
        require(usableInteriorHeight > 0.0 && usableInteriorHeight <= height) {
            "usableInteriorHeight must be within (0, height]"
        }
    }

    /** Ratio of bottom to top diameter - a shape fingerprint used to confirm cup identity. */
    val taperRatio: Double get() = bottomDiameter / topDiameter

    /** Overall aspect ratio (height / top diameter) - another shape fingerprint. */
    val aspectRatio: Double get() = height / topDiameter
}

object CupProfileRegistry {

    /**
     * Measurements for a standard clear 16 oz Solo-style plastic cup. Interior diameters are
     * approximate manufacturer specs (top ~3.70in / 94mm, bottom ~2.38in / 60mm, height
     * ~5.38in / 136.5mm); [usableInteriorHeight] is slightly reduced to account for the base
     * thickness. These defaults are deliberately conservative starting points - use the
     * Calibration screen to refine them for your exact cup.
     */
    val SOLO_16OZ = CupProfile(
        id = "solo_16oz",
        name = "16 oz Solo-style cup (clear)",
        totalVolumeOz = 16.0,
        height = 136.5,
        topDiameter = 94.0,
        bottomDiameter = 60.3,
        usableInteriorHeight = 133.0,
    )

    val defaultProfiles: List<CupProfile> = listOf(SOLO_16OZ)

    fun byId(id: String): CupProfile = defaultProfiles.firstOrNull { it.id == id } ?: SOLO_16OZ
}
