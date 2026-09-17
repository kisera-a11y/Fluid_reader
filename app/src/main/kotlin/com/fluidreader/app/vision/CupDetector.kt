package com.fluidreader.app.vision

import android.graphics.Bitmap
import android.graphics.RectF
import com.fluidreader.app.vision.model.CupCandidate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/**
 * Coarse "is there a cup-shaped thing in frame, and roughly where" stage.
 *
 * We deliberately do NOT rely on ML Kit's object *classification* (it only recognizes broad
 * categories like "food", "fashion good", "home good" and a logo may not be visible anyway).
 * Instead we use its class-agnostic prominent-object detector purely to get a candidate
 * bounding box for a single foreground object, then confirm/refine it with our own geometry
 * checks in [CupBoundaryDetector] (taper ratio, aspect ratio, wall shape). This is fully
 * on-device: the base detector model ships inside the `object-detection` AAR, no download or
 * network access required.
 */
class CupDetector {

    private val detector: ObjectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
        ObjectDetection.getClient(options)
    }

    /**
     * Runs detection on [bitmap] (already upright - caller applies [rotationDegrees] via the
     * bitmap itself or passes it through InputImage rotation) and returns the best cup
     * candidate, if any, via [onResult] on ML Kit's callback thread.
     */
    fun detect(bitmap: Bitmap, rotationDegrees: Int, onResult: (CupCandidate?) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { detected ->
                onResult(pickBestCandidate(detected.map { it.boundingBox }, bitmap.width, bitmap.height))
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    private fun pickBestCandidate(boxes: List<android.graphics.Rect>, frameWidth: Int, frameHeight: Int): CupCandidate? {
        if (boxes.isEmpty()) return null

        val frameArea = (frameWidth * frameHeight).toDouble()
        val frameCx = frameWidth / 2.0
        val frameCy = frameHeight / 2.0

        // Score every candidate by size (bigger = more likely the intended subject), how
        // upright/cup-like its aspect ratio is, and how close to the frame center it sits.
        // This keeps the pipeline working even if ML Kit returns several objects at once.
        var best: android.graphics.Rect? = null
        var bestScore = -1.0
        for (box in boxes) {
            val w = box.width().toFloat()
            val h = box.height().toFloat()
            if (w <= 0f || h <= 0f) continue
            val aspect = h / w
            // A cup silhouette is taller than wide, but not extremely so.
            if (aspect < 0.8 || aspect > 4.0) continue

            val areaScore = (box.width() * box.height()) / frameArea
            val cx = box.centerX().toDouble()
            val cy = box.centerY().toDouble()
            val distFromCenter = kotlin.math.hypot(cx - frameCx, cy - frameCy) / kotlin.math.hypot(frameCx, frameCy)
            val centerScore = 1.0 - distFromCenter.coerceIn(0.0, 1.0)

            val score = areaScore * 0.7 + centerScore * 0.3
            if (score > bestScore) {
                bestScore = score
                best = box
            }
        }

        val chosen = best ?: return null
        val areaFraction = (chosen.width() * chosen.height()) / frameArea
        // Detector "confidence" proxy: reasonably large + reasonably centered.
        val detectorScore = (areaFraction.coerceIn(0.0, 0.6) / 0.6) * 0.6 + (bestScore.coerceIn(0.0, 1.0)) * 0.4

        return CupCandidate(
            boundingBox = RectF(chosen),
            detectorScore = detectorScore.coerceIn(0.0, 1.0),
        )
    }

    fun close() {
        detector.close()
    }
}
