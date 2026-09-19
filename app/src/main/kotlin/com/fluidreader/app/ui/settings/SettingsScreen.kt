package com.fluidreader.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fluidreader.app.R
import com.fluidreader.app.settings.AppSettings
import com.fluidreader.app.settings.SettingsStore
import com.fluidreader.app.ui.theme.AquaPrimary
import com.fluidreader.app.ui.theme.Ink0
import com.fluidreader.app.ui.theme.Ink2
import com.fluidreader.app.ui.theme.TextSecondary
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink0),
            )
        },
        containerColor = Ink0,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard(title = stringResource(R.string.settings_cup_profile)) {
                CupProfileRegistry.defaultProfiles.forEachIndexed { index, profile ->
                    if (index > 0) HorizontalDivider(color = Ink2)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { settingsStore.setCupProfileId(profile.id) } }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.cupProfileId == profile.id,
                            onClick = { scope.launch { settingsStore.setCupProfileId(profile.id) } },
                            colors = RadioButtonDefaults.colors(selectedColor = AquaPrimary),
                        )
                        Text("${profile.name} (${profile.totalVolumeOz.toInt()} oz)")
                    }
                }
            }

            SettingsCard(title = stringResource(R.string.settings_units)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VolumeUnit.entries.forEach { unit ->
                        val selected = settings.units == unit
                        UnitChip(
                            label = if (unit == VolumeUnit.FLUID_OUNCES) "Fluid ounces" else "Milliliters",
                            selected = selected,
                            onClick = { scope.launch { settingsStore.setUnits(unit) } },
                        )
                    }
                }
            }

            SettingsCard(title = stringResource(R.string.settings_confidence_threshold)) {
                Text(
                    "Minimum confidence required before showing a reading: ${(settings.confidenceThreshold * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Slider(
                    value = settings.confidenceThreshold,
                    onValueChange = { v -> scope.launch { settingsStore.setConfidenceThreshold(v) } },
                    valueRange = 0f..0.9f,
                    colors = SliderDefaults.colors(thumbColor = AquaPrimary, activeTrackColor = AquaPrimary),
                )
            }

            SettingsCard {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_show_overlay),
                    checked = settings.showOverlay,
                    onCheckedChange = { v -> scope.launch { settingsStore.setShowOverlay(v) } },
                )
                HorizontalDivider(color = Ink2)
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_debug_mode) + " (developer options)",
                    checked = settings.developerOptionsEnabled,
                    onCheckedChange = { v -> scope.launch { settingsStore.setDeveloperOptionsEnabled(v) } },
                )
            }

            if (settings.developerOptionsEnabled) {
                SettingsCard {
                    NavigationRow(stringResource(R.string.settings_calibration), onClick = onOpenCalibration)
                    HorizontalDivider(color = Ink2)
                    NavigationRow(stringResource(R.string.debug_title), onClick = onOpenDebug)
                }
            }

            SettingsCard {
                NavigationRow(stringResource(R.string.settings_about), onClick = onOpenAbout)
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Ink2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = AquaPrimary),
        )
    }
}

@Composable
private fun NavigationRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun UnitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val containerColor = if (selected) AquaPrimary else Ink0
    val contentColor = if (selected) Color.Black else TextSecondary
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
