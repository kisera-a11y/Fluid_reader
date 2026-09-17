package com.fluidreader.app.ui.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/** Loads a gallery image as an upright [Bitmap], applying its EXIF rotation if present. */
object GalleryImageLoader {

    fun loadUprightBitmap(context: Context, uri: Uri): Bitmap? {
        val rotationDegrees = context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0

        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
