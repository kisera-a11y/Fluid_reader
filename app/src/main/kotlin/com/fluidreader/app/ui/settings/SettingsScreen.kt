package com.fluidreader.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fluidreader.app.R
import com.fluidreader.app.settings.AppSettings
import com.fluidreader.app.settings.SettingsStore
import com.fluidreader.core.cup.CupProfileRegistry
import com.fluidreader.core.units.VolumeUnit
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val settings by settingsStore.settings.collectAsState(initial = AppSettings())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
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
            SectionHeader(stringResource(R.string.settings_cup_profile))
            CupProfileRegistry.defaultProfiles.forEach { profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.cupProfileId == profile.id,
                        onClick = { scope.launch { settingsStore.setCupProfileId(profile.id) } },
                    )
                    Text("${profile.name} (${profile.totalVolumeOz.toInt()} oz)")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionHeader(stringResource(R.string.settings_units))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VolumeUnit.entries.forEach { unit ->
                    val selected = settings.units == unit
                    Button(
                        onClick = { scope.launch { settingsStore.setUnits(unit) } },
                        colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text(if (unit == VolumeUnit.FLUID_OUNCES) "Fluid ounces" else "Milliliters")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionHeader(stringResource(R.string.settings_confidence_threshold))
            Text(
                "Minimum confidence required before showing a reading: ${(settings.confidenceThreshold * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = settings.confidenceThreshold,
                onValueChange = { v -> scope.launch { settingsStore.setConfidenceThreshold(v) } },
                valueRange = 0f..0.9f,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_show_overlay))
                Switch(
                    checked = settings.showOverlay,
                    onCheckedChange = { v -> scope.launch { settingsStore.setShowOverlay(v) } },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_debug_mode) + " (developer options)")
                Switch(
                    checked = settings.developerOptionsEnabled,
                    onCheckedChange = { v -> scope.launch { settingsStore.setDeveloperOptionsEnabled(v) } },
                )
            }

            if (settings.developerOptionsEnabled) {
                NavigationRow(stringResource(R.string.settings_calibration), onClick = onOpenCalibration)
                NavigationRow(stringResource(R.string.debug_title), onClick = onOpenDebug)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            NavigationRow(stringResource(R.string.settings_about), onClick = onOpenAbout)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun NavigationRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}
