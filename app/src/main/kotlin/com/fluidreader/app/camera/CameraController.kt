package com.fluidreader.app.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.guava.await
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Thin wrapper around CameraX setup: binds a [Preview] (shown in a [PreviewView]) and an
 * [ImageAnalysis] use case (feeding [FrameAnalyzer]) to the given [LifecycleOwner], and tears
 * everything down cleanly - including the dedicated analysis thread - when the screen goes
 * away, so the camera and its background thread are never left running while backgrounded.
 */
class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    suspend fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer,
    ) {
        val provider = ProcessCameraProvider.getInstance(context).await()
        cameraProvider = provider
        provider.unbindAll()

        val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }

        camera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis,
        )
    }

    /** Unbinds all CameraX use cases; safe to call repeatedly. */
    fun stop() {
        cameraProvider?.unbindAll()
        camera = null
    }

    /** Fully releases the analysis executor thread. Call once, when the owning screen is gone. */
    fun release() {
        stop()
        analysisExecutor.shutdown()
    }
}
