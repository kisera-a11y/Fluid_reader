package com.fluidreader.app.vision

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Bridges camera/gallery input into the two representations the vision pipeline needs:
 *  - a [LumaFrame] (raw grayscale byte buffer) for our own fast edge-based analysis
 *  - a grayscale [Bitmap] for ML Kit's `InputImage.fromBitmap`
 *
 * [toLumaFrame] for a camera [ImageProxy] always returns an UPRIGHT buffer: it bakes in
 * `imageInfo.rotationDegrees` immediately, rather than leaving frames in the raw sensor
 * orientation. Every later stage (rim/wall/liquid row-scanning in [CupBoundaryDetector] and
 * [LiquidLevelDetector], the perspective symmetry check, and the on-screen overlay mapping in
 * `ui/camera/CoordinateMapper`) assumes "row = a roughly horizontal line in the real, upright
 * scene", which is only true once rotation has already been applied - most phones mount the
 * back sensor in landscape, so a raw un-rotated portrait frame would otherwise have the cup's
 * rim running along a *column*, not a row.
 */
object FrameConverter {

    // Only kicks in when a frame is actually dark - a well-lit frame's mean luma never gets
    // near this, so normal-light behavior is completely untouched.
    private const val LOW_LIGHT_MEAN_THRESHOLD = 70.0
    private const val LOW_LIGHT_TARGET_MEAN = 120.0
    private const val LOW_LIGHT_MAX_GAIN = 3.0

    /**
     * Extracts the Y (luma) plane of a YUV_420_888 [ImageProxy] into an upright [LumaFrame],
     * rotating pixel data by [ImageProxy.getImageInfo]'s `rotationDegrees` (0/90/180/270) in
     * the same pass so downstream code never has to reason about sensor orientation.
     */
    fun toLumaFrame(imageProxy: ImageProxy): LumaFrame {
        val plane = imageProxy.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val src = ByteArray(buffer.remaining())
        buffer.get(src)

        val rotation = ((imageProxy.imageInfo.rotationDegrees % 360) + 360) % 360
        val oldW = imageProxy.width
        val oldH = imageProxy.height
        val oldRowStride = plane.rowStride

        val width: Int
        val height: Int
        val data: ByteArray

        if (rotation == 0) {
            // Still repack to a tightly-packed buffer so rowStride == width everywhere downstream.
            if (oldRowStride == oldW) {
                width = oldW
                height = oldH
                data = src
            } else {
                val packed = ByteArray(oldW * oldH)
                for (y in 0 until oldH) {
                    System.arraycopy(src, y * oldRowStride, packed, y * oldW, oldW)
                }
                width = oldW
                height = oldH
                data = packed
            }
        } else {
            val newW: Int
            val newH: Int
            if (rotation == 90 || rotation == 270) {
                newW = oldH
                newH = oldW
            } else {
                newW = oldW
                newH = oldH
            }
            val out = ByteArray(newW * newH)

            // See the class doc: output(ox, oy) = input(ix, iy) per the standard 90/180/270
            // clockwise-rotation pixel mapping.
            for (oy in 0 until newH) {
                val outRowOffset = oy * newW
                for (ox in 0 until newW) {
                    val ix: Int
                    val iy: Int
                    when (rotation) {
                        90 -> {
                            ix = oy
                            iy = oldH - 1 - ox
                        }
                        180 -> {
                            ix = oldW - 1 - ox
                            iy = oldH - 1 - oy
                        }
                        else -> { // 270
                            ix = oldW - 1 - oy
                            iy = ox
                        }
                    }
                    out[outRowOffset + ox] = src[iy * oldRowStride + ix]
                }
            }
            width = newW
            height = newH
            data = out
        }

        return LumaFrame(data = enhanceLowLight(data), width = width, height = height, rowStride = width)
    }

    /**
     * Boosts brightness/contrast, but only when the frame is genuinely dark (mean luma below
     * [LOW_LIGHT_MEAN_THRESHOLD]) - a well-lit frame's mean sits well above that, so this is a
     * no-op and returns the input untouched. Cheap multiplicative gain toward
     * [LOW_LIGHT_TARGET_MEAN], capped at [LOW_LIGHT_MAX_GAIN] so it doesn't blow dim highlights
     * out to solid white or amplify sensor noise into a useless mess in near-total darkness -
     * just gives a genuinely dim scene (a dark room, low bar lighting) enough contrast for the
     * rim/wall/liquid edge detection to have something to work with. This only changes the
     * buffer the vision pipeline analyzes, not the live camera preview the user actually sees.
     */
    private fun enhanceLowLight(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        // Sample every 7th byte for a cheap mean estimate rather than scanning the whole frame twice.
        var sum = 0L
        var count = 0
        var i = 0
        while (i < data.size) {
            sum += data[i].toInt() and 0xFF
            count++
            i += 7
        }
        val mean = sum.toDouble() / count
        if (mean <= 0.0 || mean >= LOW_LIGHT_MEAN_THRESHOLD) return data

        val gain = (LOW_LIGHT_TARGET_MEAN / mean).coerceAtMost(LOW_LIGHT_MAX_GAIN)
        val out = ByteArray(data.size)
        for (j in data.indices) {
            out[j] = (((data[j].toInt() and 0xFF) * gain).toInt().coerceIn(0, 255)).toByte()
        }
        return out
    }

    /** Builds a grayscale-as-ARGB_8888 [Bitmap] from a [LumaFrame], for ML Kit's InputImage. */
    fun toGrayscaleBitmap(frame: LumaFrame): Bitmap {
        val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(frame.width * frame.height)
        for (y in 0 until frame.height) {
            val rowOffset = y * frame.rowStride
            val outRow = y * frame.width
            for (x in 0 until frame.width) {
                val luma = frame.data[rowOffset + x].toInt() and 0xFF
                pixels[outRow + x] = Color.rgb(luma, luma, luma)
            }
        }
        bitmap.setPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
        return bitmap
    }

    /** Builds a [LumaFrame] from an arbitrary already-decoded, already-upright [Bitmap] (debug/test mode). */
    fun toLumaFrame(bitmap: Bitmap): LumaFrame {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val data = ByteArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Standard luma weighting.
            val luma = ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255)
            data[i] = luma.toByte()
        }
        return LumaFrame(data = enhanceLowLight(data), width = width, height = height, rowStride = width)
    }
}
