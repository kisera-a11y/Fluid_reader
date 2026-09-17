package com.fluidreader.app.ui.debug

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fluidreader.app.R
import com.fluidreader.app.vision.FrameConverter
import com.fluidreader.app.vision.VisionPipeline
import com.fluidreader.app.vision.model.PipelineResult
import com.fluidreader.app.vision.model.PipelineState
import com.fluidreader.core.cup.CupProfileRegistry
import com.fluidreader.core.measurement.ConfidenceInputs
import com.fluidreader.core.measurement.ConfidenceModel
import com.fluidreader.core.measurement.VolumeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline testing tool: load a reference photo of the target cup and run the exact same
 * detection pipeline used live by the camera screen against it, showing every intermediate
 * measurement so the CV heuristics can be tuned without needing to stand in front of a cup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visionPipeline = remember { VisionPipeline() }
    val cupProfile = remember { CupProfileRegistry.SOLO_16OZ }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<PipelineResult?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isProcessing = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { GalleryImageLoader.loadUprightBitmap(context, uri) }
            bitmap = loaded
            if (loaded != null) {
                val lumaFrame = withContext(Dispatchers.Default) { FrameConverter.toLumaFrame(loaded) }
                visionPipeline.process(lumaFrame, loaded, 0, cupProfile) { r ->
                    result = r
                    isProcessing = false
                }
            } else {
                isProcessing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Button(onClick = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Text(stringResource(R.string.debug_load_image))
            }

            if (isProcessing) {
                Text("Processing…", modifier = Modifier.padding(top = 12.dp))
            }

            bitmap?.let { bmp ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat()),
                ) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
                    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())) {
                        val scale = size.width / bmp.width
                        val r = result ?: return@Canvas

                        r.candidate?.let { c ->
                            drawRect(
                                color = Color.Yellow,
                                topLeft = androidx.compose.ui.geometry.Offset(c.boundingBox.left * scale, c.boundingBox.top * scale),
                                size = androidx.compose.ui.geometry.Size(c.boundingBox.width() * scale, c.boundingBox.height() * scale),
                                style = Stroke(width = 3f),
                            )
                        }
                        r.boundary?.let { b ->
                            drawLine(
                                Color.Cyan,
                                androidx.compose.ui.geometry.Offset(b.leftAt(b.rimY) * scale, b.rimY * scale),
                                androidx.compose.ui.geometry.Offset(b.rightAt(b.rimY) * scale, b.rimY * scale),
                                strokeWidth = 4f,
                                cap = StrokeCap.Round,
                            )
                            drawLine(
                                Color.Cyan,
                                androidx.compose.ui.geometry.Offset(b.leftAt(b.bottomY) * scale, b.bottomY * scale),
                                androidx.compose.ui.geometry.Offset(b.rightAt(b.bottomY) * scale, b.bottomY * scale),
                                strokeWidth = 4f,
                                cap = StrokeCap.Round,
                            )
                            drawLine(
                                Color.Magenta,
                                androidx.compose.ui.geometry.Offset(b.leftAt(b.rimY) * scale, b.rimY * scale),
                                androidx.compose.ui.geometry.Offset(b.leftAt(b.bottomY) * scale, b.bottomY * scale),
                                strokeWidth = 2f,
                            )
                            drawLine(
                                Color.Magenta,
                                androidx.compose.ui.geometry.Offset(b.rightAt(b.rimY) * scale, b.rimY * scale),
                                androidx.compose.ui.geometry.Offset(b.rightAt(b.bottomY) * scale, b.bottomY * scale),
                                strokeWidth = 2f,
                            )
                        }
                        r.liquid?.let { liquid ->
                            val b = r.boundary
                            if (b != null && liquid.detected) {
                                drawLine(
                                    Color.Green,
                                    androidx.compose.ui.geometry.Offset(b.leftAt(liquid.surfaceY) * scale, liquid.surfaceY * scale),
                                    androidx.compose.ui.geometry.Offset(b.rightAt(liquid.surfaceY) * scale, liquid.surfaceY * scale),
                                    strokeWidth = 5f,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                    }
                }
            }

            result?.let { r -> DebugInfoPanel(r, cupProfile) }
        }
    }
}

@Composable
private fun DebugInfoPanel(result: PipelineResult, cupProfile: com.fluidreader.core.cup.CupProfile) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    Text("Pipeline state: ${result.state}", style = MaterialTheme.typography.titleMedium)

    result.candidate?.let {
        Text("Cup detector score: ${"%.2f".format(it.detectorScore)}")
        Text("Bounding box: ${it.boundingBox}")
    }
    result.boundary?.let {
        Text("Rim Y: ${it.rimY}  Bottom Y: ${it.bottomY}")
        Text("Shape match score: ${"%.2f".format(it.shapeMatchScore)}")
        Text("Edge quality: ${"%.2f".format(it.edgeQuality)}")
    }
    result.perspective?.let {
        Text("Perspective angle quality: ${"%.2f".format(it.angleQuality)}")
        Text("Top width: ${"%.1f".format(it.topWidthPx)}px  Bottom width: ${"%.1f".format(it.bottomWidthPx)}px")
        Text("Too extreme: ${it.tooExtreme}")
    }
    result.liquid?.let {
        Text("Liquid detected: ${it.detected}  y=${it.surfaceY}")
        Text("Liquid visibility score: ${"%.2f".format(it.visibilityScore)}")
    }
    result.heightFraction?.let { fraction ->
        Text("Normalized height fraction: ${"%.3f".format(fraction)}")
        val volume = VolumeCalculator.volumeOzAtFraction(cupProfile, fraction)
        Text("Calculated volume: ${"%.2f".format(volume)} oz", style = MaterialTheme.typography.titleMedium)

        val confidence = ConfidenceModel.evaluate(
            ConfidenceInputs(
                cupDetectionScore = result.candidate?.detectorScore ?: 0.0,
                boundaryQuality = ((result.boundary?.edgeQuality ?: 0.0) + (result.boundary?.shapeMatchScore ?: 0.0)) / 2.0,
                angleQuality = result.perspective?.angleQuality ?: 0.0,
                liquidLineVisibility = result.liquid?.visibilityScore ?: 0.0,
                temporalStability = 0.5, // no frame history available for a single static image
            ),
            estimatedVolumeOz = volume,
        )
        Text("Confidence score: ${"%.2f".format(confidence.score)} (${confidence.level})")
        val range = ConfidenceModel.rangeOz(confidence, volume)
        Text("Range: ${"%.1f".format(range.start)} - ${"%.1f".format(range.endInclusive)} oz")
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    Text("Debug log", style = MaterialTheme.typography.titleMedium)
    result.debugLog.forEach { line ->
        Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
