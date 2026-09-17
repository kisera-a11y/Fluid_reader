package com.fluidreader.app.vision

import android.graphics.Bitmap
import com.fluidreader.app.vision.model.PipelineResult
import com.fluidreader.app.vision.model.PipelineState
import com.fluidreader.core.cup.CupProfile

/**
 * Orchestrates one full pass of the pipeline described in the design doc:
 *
 *   frame -> cup detection -> bounding region -> perspective/orientation
 *         -> rim detection -> wall/profile detection -> liquid surface detection
 *         -> height normalization (-> geometry + volume + confidence happen one layer up,
 *            in MeasurementViewModel, since those need cross-frame smoothing/state).
 *
 * One [VisionPipeline] instance owns the (relatively expensive) ML Kit detector, so it should
 * be created once and reused for the lifetime of the camera session.
 */
class VisionPipeline {

    private val cupDetector = CupDetector()

    fun process(
        lumaFrame: LumaFrame,
        mlKitBitmap: Bitmap,
        rotationDegrees: Int,
        cupProfile: CupProfile,
        onResult: (PipelineResult) -> Unit,
    ) {
        val log = mutableListOf<String>()
        cupDetector.detect(mlKitBitmap, rotationDegrees) { candidate ->
            if (candidate == null) {
                log += "cup detector: no candidate object found"
                onResult(
                    PipelineResult(
                        state = PipelineState.NO_CUP,
                        frameWidth = lumaFrame.width,
                        frameHeight = lumaFrame.height,
                        debugLog = log,
                    ),
                )
                return@detect
            }
            log += "cup detector: box=${candidate.boundingBox} score=${"%.2f".format(candidate.detectorScore)}"

            val frameArea = (lumaFrame.width * lumaFrame.height).toDouble()
            val boxArea = candidate.boundingBox.width() * candidate.boundingBox.height()
            val areaFraction = boxArea / frameArea
            if (areaFraction < MIN_AREA_FRACTION) {
                log += "rejected: candidate too small (${"%.3f".format(areaFraction)} of frame)"
                onResult(
                    PipelineResult(
                        state = PipelineState.NEEDS_CENTERING,
                        candidate = candidate,
                        frameWidth = lumaFrame.width,
                        frameHeight = lumaFrame.height,
                        debugLog = log,
                    ),
                )
                return@detect
            }

            val aspect = candidate.boundingBox.height() / candidate.boundingBox.width()
            if (aspect < MIN_ASPECT_FOR_SIDE_VIEW) {
                log += "rejected: aspect ratio $aspect too squat for a side view"
                onResult(
                    PipelineResult(
                        state = PipelineState.NEEDS_SIDE_VIEW,
                        candidate = candidate,
                        frameWidth = lumaFrame.width,
                        frameHeight = lumaFrame.height,
                        debugLog = log,
                    ),
                )
                return@detect
            }

            val boundary = CupBoundaryDetector.refine(lumaFrame, candidate.boundingBox, cupProfile)
            if (boundary == null || boundary.shapeMatchScore < MIN_SHAPE_MATCH) {
                log += "rejected: boundary refine failed or shape mismatch " +
                    "(shapeMatchScore=${boundary?.shapeMatchScore})"
                onResult(
                    PipelineResult(
                        state = PipelineState.NO_CUP,
                        candidate = candidate,
                        boundary = boundary,
                        frameWidth = lumaFrame.width,
                        frameHeight = lumaFrame.height,
                        debugLog = log,
                    ),
                )
                return@detect
            }
            log += "boundary: rim=${boundary.rimY} bottom=${boundary.bottomY} " +
                "shapeMatch=${"%.2f".format(boundary.shapeMatchScore)} edgeQuality=${"%.2f".format(boundary.edgeQuality)}"

            val perspective = PerspectiveEstimator.estimate(boundary)
            log += "perspective: angleQuality=${"%.2f".format(perspective.angleQuality)} " +
                "topW=${"%.1f".format(perspective.topWidthPx)} bottomW=${"%.1f".format(perspective.bottomWidthPx)}"
            if (perspective.tooExtreme) {
                log += "rejected: perspective angle too extreme"
                onResult(
                    PipelineResult(
                        state = PipelineState.ANGLE_TOO_EXTREME,
                        candidate = candidate,
                        boundary = boundary,
                        perspective = perspective,
                        frameWidth = lumaFrame.width,
                        frameHeight = lumaFrame.height,
                        debugLog = log,
                    ),
                )
                return@detect
            }

            val liquid = LiquidLevelDetector.detect(lumaFrame, boundary)
            log += "liquid: detected=${liquid.detected} y=${liquid.surfaceY} " +
                "visibility=${"%.2f".format(liquid.visibilityScore)}"
            if (!liquid.detected) {
                onResult(
                    PipelineResult(
                        state = PipelineState.LIQUID_NOT_VISIBLE,
                        candidate = candidate,
                        boundary = boundary,
                        perspective = perspective,
                        liquid = liquid,
                        frameWidth = lumaFrame.width,
                        frameHeight = lumaFrame.height,
                        debugLog = log,
                    ),
                )
                return@detect
            }

            val heightFraction = ((boundary.bottomY - liquid.surfaceY).toDouble() /
                (boundary.bottomY - boundary.rimY).toDouble()).coerceIn(0.0, 1.0)
            log += "heightFraction=${"%.3f".format(heightFraction)}"

            onResult(
                PipelineResult(
                    state = PipelineState.MEASURED,
                    candidate = candidate,
                    boundary = boundary,
                    perspective = perspective,
                    liquid = liquid,
                    heightFraction = heightFraction,
                    frameWidth = lumaFrame.width,
                    frameHeight = lumaFrame.height,
                    debugLog = log,
                ),
            )
        }
    }

    fun close() {
        cupDetector.close()
    }

    companion object {
        private const val MIN_AREA_FRACTION = 0.04
        private const val MIN_ASPECT_FOR_SIDE_VIEW = 1.15
        private const val MIN_SHAPE_MATCH = 0.45
    }
}
