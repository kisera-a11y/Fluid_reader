package com.fluidreader.app.ui.drinklog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fluidreader.app.drinklog.DrinkLogEntry
import com.fluidreader.app.drinklog.DrinkLogViewModel
import com.fluidreader.app.settings.AppSettings
import com.fluidreader.app.settings.SettingsStore
import com.fluidreader.app.ui.theme.AquaPrimary
import com.fluidreader.app.ui.theme.BadRed
import com.fluidreader.app.ui.theme.Ink0
import com.fluidreader.app.ui.theme.Ink2
import com.fluidreader.app.ui.theme.TextSecondary
import com.fluidreader.core.units.VolumeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrinkLogScreen(
    onBack: () -> Unit,
    viewModel: DrinkLogViewModel = viewModel(),
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val settings by settingsStore.settings.collectAsState(initial = AppSettings())
    val units = settings.units

    val entries by viewModel.entries.collectAsState()
    val grandTotal by viewModel.grandTotalOz.collectAsState()

    var pendingDeleteName by remember { mutableStateOf<String?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drink log") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { confirmClearAll = true }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear all", tint = BadRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink0),
            )
        },
        containerColor = Ink0,
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.LocalBar, contentDescription = null, tint = TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
                Text(
                    "Nothing logged yet. Measure a drink, then tap \"Log drink\" to start tracking tonight.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Total tonight", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text(units.format(grandTotal), style = MaterialTheme.typography.displaySmall, color = AquaPrimary)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(entries, key = { it.name }) { entry ->
                        DrinkLogRow(
                            entry = entry,
                            units = units,
                            onDelete = { pendingDeleteName = entry.name },
                        )
                    }
                }
            }
        }
    }

    pendingDeleteName?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDeleteName = null },
            containerColor = Ink2,
            title = { Text("Remove \"$name\"?") },
            text = { Text("This deletes its total from tonight's log. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntry(name)
                    pendingDeleteName = null
                }) { Text("Remove", color = BadRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteName = null }) { Text("Cancel") }
            },
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            containerColor = Ink2,
            title = { Text("Clear entire log?") },
            text = { Text("This deletes all ${entries.size} entries from tonight's log. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    confirmClearAll = false
                }) { Text("Clear all", color = BadRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DrinkLogRow(entry: DrinkLogEntry, units: VolumeUnit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Ink2),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (entry.pourCount == 1) "1 pour" else "${entry.pourCount} pours",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(units.format(entry.totalOz), style = MaterialTheme.typography.titleMedium, color = AquaPrimary)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove ${entry.name}", tint = TextSecondary)
                }
            }
        }
    }
}
