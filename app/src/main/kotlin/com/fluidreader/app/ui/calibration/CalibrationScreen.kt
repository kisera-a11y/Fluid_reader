package com.fluidreader.app.ui.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fluidreader.app.R
import com.fluidreader.app.calibration.CalibrationData
import com.fluidreader.app.calibration.CalibrationStore
import com.fluidreader.app.settings.AppSettings
import com.fluidreader.app.settings.SettingsStore
import com.fluidreader.core.calibration.CalibrationPoint
import com.fluidreader.core.cup.CupProfileRegistry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val calibrationStore = remember { CalibrationStore(context) }
    val scope = rememberCoroutineScope()

    val settings by settingsStore.settings.collectAsState(initial = AppSettings())
    val profileId = settings.cupProfileId
    val baseProfile = remember(profileId) { CupProfileRegistry.byId(profileId) }
    val calibration by calibrationStore.observe(profileId).collectAsState(initial = CalibrationData())

    var heightText by remember(profileId) { mutableStateOf("") }
    var topDiameterText by remember(profileId) { mutableStateOf("") }
    var bottomDiameterText by remember(profileId) { mutableStateOf("") }
    var initialized by remember(profileId) { mutableStateOf(false) }
    if (!initialized) {
        heightText = (calibration.usableInteriorHeightOverrideMm ?: baseProfile.usableInteriorHeight).toString()
        topDiameterText = (calibration.topDiameterOverrideMm ?: baseProfile.topDiameter).toString()
        bottomDiameterText = (calibration.bottomDiameterOverrideMm ?: baseProfile.bottomDiameter).toString()
        initialized = true
    }

    var newPointHeight by remember { mutableStateOf("") }
    var newPointVolume by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calibration_title)) },
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
            Text(baseProfile.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Measure your exact cup with calipers/a ruler and enter the numbers below. " +
                    "These override the built-in approximate dimensions and feed directly into " +
                    "the frustum volume model.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            NumberField(stringResource(R.string.calibration_total_height), heightText) { heightText = it }
            NumberField(stringResource(R.string.calibration_top_diameter), topDiameterText) { topDiameterText = it }
            NumberField(stringResource(R.string.calibration_bottom_diameter), bottomDiameterText) { bottomDiameterText = it }

            Button(
                onClick = {
                    scope.launch {
                        calibrationStore.updateDimensions(
                            profileId = profileId,
                            heightMm = heightText.toDoubleOrNull(),
                            topDiameterMm = topDiameterText.toDoubleOrNull(),
                            bottomDiameterMm = bottomDiameterText.toDoubleOrNull(),
                            usableInteriorHeightMm = heightText.toDoubleOrNull(),
                        )
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Save dimensions")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Text("Measured calibration points", style = MaterialTheme.typography.titleMedium)
            Text(
                "For extra accuracy: fill the cup to a known height, pour it out into a " +
                    "measuring cup, and add the pair below. Add several points across the " +
                    "cup's range for the best curve.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            calibration.points.sortedBy { it.heightMm }.forEach { point ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${point.heightMm} mm -> ${point.volumeOz} oz")
                    IconButton(onClick = { scope.launch { calibrationStore.removePoint(profileId, point.heightMm) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove")
                    }
                }
            }

            NumberField(
                stringResource(R.string.calibration_height_mm),
                newPointHeight,
                modifier = Modifier.padding(top = 8.dp),
            ) { newPointHeight = it }
            NumberField(stringResource(R.string.calibration_volume_oz), newPointVolume) { newPointVolume = it }
            Button(
                onClick = {
                    val h = newPointHeight.toDoubleOrNull()
                    val v = newPointVolume.toDoubleOrNull()
                    if (h != null && v != null) {
                        scope.launch {
                            calibrationStore.addPoint(profileId, CalibrationPoint(heightMm = h, volumeOz = v))
                            newPointHeight = ""
                            newPointVolume = ""
                        }
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.calibration_add_point))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            OutlinedButton(onClick = { scope.launch { calibrationStore.resetToDefaults(profileId) } }) {
                Text(stringResource(R.string.calibration_reset))
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
