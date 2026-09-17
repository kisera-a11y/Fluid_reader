package com.fluidreader.app.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.fluidreader.app.vision.FrameConverter
import com.fluidreader.app.vision.VisionPipeline
import com.fluidreader.app.vision.model.PipelineResult
import com.fluidreader.core.cup.CupProfile

/**
 * CameraX [ImageAnalysis.Analyzer] that throttles the (relatively expensive) vision pipeline
 * to roughly [targetFps] frames per second instead of running it on every preview frame -
 * keeping the preview itself smooth and battery usage reasonable, per the spec's "5-15 fps"
 * guidance. Frames that arrive faster than the throttle window, or while a previous frame is
 * still being processed asynchronously (ML Kit's callback hasn't returned yet), are dropped
 * immediately by closing the [ImageProxy] without further work.
 */
class FrameAnalyzer(
    private val visionPipeline: VisionPipeline,
    private val getCupProfile: () -> CupProfile,
    targetFps: Int = 8,
    private val onResult: (PipelineResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private val minIntervalMs = 1000L / targetFps.coerceIn(1, 30)
    private var lastProcessedAtMs = 0L
    @Volatile private var busy = false

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (busy || now - lastProcessedAtMs < minIntervalMs) {
            image.close()
            return
        }

        try {
            // toLumaFrame() already bakes imageInfo.rotationDegrees into the buffer, so the
            // frame (and the bitmap built from it) is upright - pass rotation 0 onward.
            val lumaFrame = FrameConverter.toLumaFrame(image)
            val bitmap = FrameConverter.toGrayscaleBitmap(lumaFrame)
            lastProcessedAtMs = now
            busy = true
            visionPipeline.process(lumaFrame, bitmap, 0, getCupProfile()) { result ->
                busy = false
                onResult(result)
            }
        } catch (t: Throwable) {
            busy = false
        } finally {
            image.close()
        }
    }
}
