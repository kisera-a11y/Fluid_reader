package com.fluidreader.app.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fluidreader.app.R
import com.fluidreader.app.camera.CameraController
import com.fluidreader.app.camera.FrameAnalyzer
import com.fluidreader.app.drinklog.DrinkLogViewModel
import com.fluidreader.app.measurement.DisplayState
import com.fluidreader.app.measurement.MeasurementUiState
import com.fluidreader.app.measurement.MeasurementViewModel
import com.fluidreader.app.ui.drinklog.LogDrinkDialog
import com.fluidreader.app.ui.theme.AquaPrimary
import com.fluidreader.app.ui.theme.BadRed
import com.fluidreader.app.ui.theme.ExtraShapes
import com.fluidreader.app.ui.theme.GoodGreen
import com.fluidreader.app.ui.theme.Ink0
import com.fluidreader.app.ui.theme.Ink3
import com.fluidreader.app.ui.theme.SurfaceOverlay
import com.fluidreader.app.ui.theme.TextPrimary
import com.fluidreader.app.ui.theme.TextSecondary
import com.fluidreader.app.ui.theme.WarnAmber
import com.fluidreader.core.measurement.ConfidenceLevel

@Composable
fun CameraScreen(
    onOpenSettings: () -> Unit,
    onOpenDrinkLog: () -> Unit,
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
            CameraContent(viewModel = viewModel, onOpenSettings = onOpenSettings, onOpenDrinkLog = onOpenDrinkLog)
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
        Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = AquaPrimary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Camera access is needed to measure liquid in a cup.",
            color = TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary)) {
            Text("Grant camera permission", color = Color.Black)
        }
    }
}

@Composable
private fun CameraContent(
    viewModel: MeasurementViewModel,
    onOpenSettings: () -> Unit,
    onOpenDrinkLog: () -> Unit,
    drinkLogViewModel: DrinkLogViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val drinkLogEntries by drinkLogViewModel.entries.collectAsState()
    var showLogDialog by remember { mutableStateOf(false) }
    // Frozen at the moment "Log drink" is tapped - the camera keeps re-measuring in the
    // background the whole time the dialog is open (while you type or speak a name), so reading
    // uiState.volumeOz live from inside onConfirm would log whatever the newest reading happens
    // to be instead of the amount actually shown when you decided to log it.
    var pendingLogVolumeOz by remember { mutableStateOf(0.0) }
    // Top edge of the bottom panel, in the same pixel space as the overlay Canvas below (both
    // are direct, full-size children of the same Box). Manual-adjust lines are kept from ever
    // being placed at or under this - otherwise, framed close to the cup, a line's default
    // position can end up entirely hidden behind the opaque panel with no way to touch it to
    // drag it back into view.
    var bottomPanelTopPx by remember { mutableStateOf(Float.MAX_VALUE) }
    val manualDragCeilingMarginPx = with(LocalDensity.current) { 12.dp.toPx() }

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
                manualDragCeilingPx = if (bottomPanelTopPx < Float.MAX_VALUE) {
                    bottomPanelTopPx - manualDragCeilingMarginPx
                } else {
                    Float.MAX_VALUE
                },
                onManualDrag = { line, y -> viewModel.setManualLine(line, y) },
            )
        }

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = SurfaceOverlay, shape = ExtraShapes.Pill) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = AquaPrimary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.app_name),
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Row {
                IconButton(
                    onClick = onOpenDrinkLog,
                    modifier = Modifier.background(SurfaceOverlay, ExtraShapes.Pill),
                ) {
                    Icon(Icons.Filled.LocalBar, contentDescription = stringResource(R.string.action_drink_log), tint = TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.background(SurfaceOverlay, ExtraShapes.Pill),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings), tint = TextPrimary)
                }
            }
        }

        // Guidance banner. The last non-null guidance state is cached so the exit animation
        // fades the previous message away instead of snapping straight to blank content.
        val guidanceRes = uiState.displayState.primaryGuidanceRes()
        var lastGuidanceState by remember { mutableStateOf(uiState.displayState) }
        if (guidanceRes != null) lastGuidanceState = uiState.displayState

        AnimatedVisibility(
            visible = guidanceRes != null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 2 },
        ) {
            val shownRes = lastGuidanceState.primaryGuidanceRes()
            if (shownRes != null) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 64.dp)
                        .background(SurfaceOverlay, RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(shownRes), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    lastGuidanceState.secondaryGuidanceRes()?.let {
                        Text(stringResource(it), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        BottomPanel(
            uiState = uiState,
            onToggleManual = {
                if (uiState.manualOverrideEnabled) viewModel.exitManualOverride() else viewModel.enterManualOverride()
            },
            onLogDrink = {
                pendingLogVolumeOz = uiState.volumeOz
                showLogDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coordinates -> bottomPanelTopPx = coordinates.positionInParent().y },
        )
    }

    if (showLogDialog) {
        LogDrinkDialog(
            currentVolumeOz = pendingLogVolumeOz,
            units = uiState.units,
            existingNames = drinkLogEntries.map { it.name },
            onDismiss = { showLogDialog = false },
            onConfirm = { name ->
                drinkLogViewModel.logPour(name, pendingLogVolumeOz)
                showLogDialog = false
                Toast.makeText(context, "Logged ${uiState.units.format(pendingLogVolumeOz)} → $name", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun BottomPanel(
    uiState: MeasurementUiState,
    onToggleManual: () -> Unit,
    onLogDrink: () -> Unit,
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
            .background(SurfaceOverlay, ExtraShapes.BottomPanel)
            // The background above extends full-bleed to the bottom of the screen (this
            // padding is inside it), but the actual content - critically, the manual-adjust
            // button - is pushed up above the system navigation bar / gesture handle so it's
            // always reachable, on both 3-button and gesture-nav devices.
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Grabber handle - reinforces the "bottom sheet" affordance.
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(Ink3, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.height(16.dp))

        if (blocked) {
            Text(
                stringResource(R.string.guidance_unavailable),
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        } else {
            AnimatedContent(
                targetState = uiState.displayVolumeText,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "volume",
            ) { volumeText ->
                Text(
                    "≈ $volumeText",
                    color = AquaPrimary,
                    style = MaterialTheme.typography.displaySmall,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${stringResource(R.string.label_range)}: ${uiState.displayRangeText}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            ConfidenceBadge(uiState.confidenceLevel, dimmed = uiState.displayState == DisplayState.STABILIZING)
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (uiState.manualOverrideEnabled) {
                Button(
                    onClick = onToggleManual,
                    shape = ExtraShapes.Pill,
                    colors = ButtonDefaults.buttonColors(containerColor = GoodGreen),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_done), color = Color.Black)
                }
                // Logging shouldn't require leaving manual mode first: exiting hands control
                // back to the live auto-tracker, which immediately starts re-measuring and can
                // overwrite this exact reading before there's a chance to tap Log - so the
                // manually-set amount has to be logged directly from here instead.
                Button(
                    onClick = onLogDrink,
                    shape = ExtraShapes.Pill,
                    colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary),
                ) {
                    Icon(Icons.Filled.LocalBar, contentDescription = null, tint = Ink0, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_log_drink), color = Ink0)
                }
            } else {
                OutlinedButton(
                    onClick = onToggleManual,
                    shape = ExtraShapes.Pill,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AquaPrimary),
                    border = BorderStroke(1.dp, AquaPrimary),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_manual_adjust))
                }

                if (!blocked) {
                    Button(
                        onClick = onLogDrink,
                        shape = ExtraShapes.Pill,
                        colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary),
                    ) {
                        Icon(Icons.Filled.LocalBar, contentDescription = null, tint = Ink0, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_log_drink), color = Ink0)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(level: ConfidenceLevel, dimmed: Boolean = false) {
    val targetColor = when (level) {
        ConfidenceLevel.HIGH -> GoodGreen
        ConfidenceLevel.MEDIUM -> WarnAmber
        ConfidenceLevel.LOW -> BadRed
    }
    val color by animateColorAsState(targetColor, tween(300), label = "confidenceColor")
    val emphasis by animateFloatAsState(if (dimmed) 0f else 1f, tween(300), label = "confidenceEmphasis")

    Surface(
        color = color.copy(alpha = 0.16f + 0.68f * emphasis),
        shape = ExtraShapes.Pill,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                stringResource(level.labelRes()),
                color = color,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
