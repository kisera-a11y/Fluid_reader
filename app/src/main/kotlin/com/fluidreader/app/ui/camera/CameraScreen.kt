package com.fluidreader.app.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fluidreader.app.R
import com.fluidreader.app.camera.CameraController
import com.fluidreader.app.camera.FrameAnalyzer
import com.fluidreader.app.measurement.DisplayState
import com.fluidreader.app.measurement.MeasurementUiState
import com.fluidreader.app.measurement.MeasurementViewModel
import com.fluidreader.app.ui.theme.BadRed
import com.fluidreader.app.ui.theme.GoodGreen
import com.fluidreader.app.ui.theme.SurfaceOverlay
import com.fluidreader.app.ui.theme.WarnAmber
import com.fluidreader.core.measurement.ConfidenceLevel

@Composable
fun CameraScreen(
    onOpenSettings: () -> Unit,
    viewModel: MeasurementViewModel = viewModel(),
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            CameraContent(viewModel = viewModel, onOpenSettings = onOpenSettings)
        } else {
            PermissionRequest(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Camera access is needed to measure liquid in a cup.",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Grant camera permission") }
    }
}

@Composable
private fun CameraContent(viewModel: MeasurementViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val cameraController = remember { CameraController(context) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    LaunchedEffect(Unit) {
        val analyzer = FrameAnalyzer(
            visionPipeline = viewModel.visionPipeline,
            getCupProfile = { viewModel.currentCupProfileForAnalysis() },
            onResult = { viewModel.onPipelineResult(it) },
        )
        cameraController.start(lifecycleOwner, previewView, analyzer)
    }
    DisposableEffect(Unit) {
        onDispose { cameraController.release() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        if (uiState.showOverlay) {
            MeasurementOverlay(
                state = uiState,
                modifier = Modifier.fillMaxSize(),
                onManualDrag = { line, y -> viewModel.setManualLine(line, y) },
            )
        }

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(color = SurfaceOverlay, shape = RoundedCornerShape(12.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.background(SurfaceOverlay, RoundedCornerShape(50)),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings), tint = Color.White)
            }
        }

        // Guidance banner
        uiState.displayState.primaryGuidanceRes()?.let { guidanceRes ->
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .background(SurfaceOverlay, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(guidanceRes), color = Color.White, style = MaterialTheme.typography.titleMedium)
                uiState.displayState.secondaryGuidanceRes()?.let {
                    Text(stringResource(it), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        BottomPanel(
            uiState = uiState,
            onToggleManual = {
                if (uiState.manualOverrideEnabled) viewModel.exitManualOverride() else viewModel.enterManualOverride()
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BottomPanel(
    uiState: MeasurementUiState,
    onToggleManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocked = uiState.displayState in setOf(
        DisplayState.NO_CUP,
        DisplayState.NEEDS_CENTERING,
        DisplayState.NEEDS_SIDE_VIEW,
        DisplayState.ANGLE_TOO_EXTREME,
        DisplayState.LIQUID_NOT_VISIBLE,
        DisplayState.BELOW_CONFIDENCE_THRESHOLD,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceOverlay, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (blocked) {
            Text(
                stringResource(R.string.guidance_unavailable),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Text(
                "≈ ${uiState.displayVolumeText}",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${stringResource(R.string.label_range)}: ${uiState.displayRangeText}",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            ConfidenceBadge(uiState.confidenceLevel, dimmed = uiState.displayState == DisplayState.STABILIZING)
        }

        Spacer(Modifier.height(16.dp))

        if (uiState.manualOverrideEnabled) {
            Button(
                onClick = onToggleManual,
                colors = ButtonDefaults.buttonColors(containerColor = GoodGreen),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_done))
            }
        } else {
            OutlinedButton(onClick = onToggleManual) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_manual_adjust))
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(level: ConfidenceLevel, dimmed: Boolean = false) {
    val color = when (level) {
        ConfidenceLevel.HIGH -> GoodGreen
        ConfidenceLevel.MEDIUM -> WarnAmber
        ConfidenceLevel.LOW -> BadRed
    }
    Surface(
        color = color.copy(alpha = if (dimmed) 0.35f else 0.85f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            stringResource(level.labelRes()),
            color = Color.Black,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
