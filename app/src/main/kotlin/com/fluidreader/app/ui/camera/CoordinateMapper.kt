package com.fluidreader.app.ui.camera

import androidx.compose.ui.geometry.Offset

/**
 * Maps a point from the analyzed camera frame's pixel space into the on-screen overlay's
 * coordinate space, so the overlay drawn in Compose lines up with what
 * [androidx.camera.view.PreviewView] is actually showing.
 *
 * Frames coming out of `FrameConverter.toLumaFrame(ImageProxy)` are already rotated upright
 * (see that function's doc), matching what `PreviewView` displays - so the only transform left
 * here is [androidx.camera.view.PreviewView.ScaleType.FILL_CENTER]-style uniform scale +
 * center-crop, no rotation.
 */
class CoordinateMapper(
    private val frameWidth: Int,
    private val frameHeight: Int,
    viewWidth: Float,
    viewHeight: Float,
) {
    private val scale: Float = if (frameWidth > 0 && frameHeight > 0) {
        maxOf(viewWidth / frameWidth, viewHeight / frameHeight)
    } else {
        1f
    }
    private val offsetX: Float = (viewWidth - frameWidth * scale) / 2f
    private val offsetY: Float = (viewHeight - frameHeight * scale) / 2f

    fun map(x: Int, y: Int): Offset = Offset(x * scale + offsetX, y * scale + offsetY)

    /** Sub-pixel variant, for drawing smoothly-animated (non-integer) overlay coordinates. */
    fun map(x: Float, y: Float): Offset = Offset(x * scale + offsetX, y * scale + offsetY)

    /** Inverse of [map]'s Y axis: a screen Y coordinate -> the corresponding frame row. */
    fun screenYToFrameY(screenY: Float): Int =
        (((screenY - offsetY) / scale).toInt()).coerceIn(0, (frameHeight - 1).coerceAtLeast(0))
}
