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

        if (rotation == 0) {
            // Still repack to a tightly-packed buffer so rowStride == width everywhere downstream.
            if (oldRowStride == oldW) {
                return LumaFrame(data = src, width = oldW, height = oldH, rowStride = oldW)
            }
            val packed = ByteArray(oldW * oldH)
            for (y in 0 until oldH) {
                System.arraycopy(src, y * oldRowStride, packed, y * oldW, oldW)
            }
            return LumaFrame(data = packed, width = oldW, height = oldH, rowStride = oldW)
        }

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
        return LumaFrame(data = out, width = newW, height = newH, rowStride = newW)
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
        return LumaFrame(data = data, width = width, height = height, rowStride = width)
    }
}
