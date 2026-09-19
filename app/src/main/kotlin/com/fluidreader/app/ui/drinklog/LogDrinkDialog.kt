package com.fluidreader.app.ui.drinklog

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fluidreader.app.ui.theme.AquaPrimary
import com.fluidreader.app.ui.theme.Ink0
import com.fluidreader.app.ui.theme.Ink2
import com.fluidreader.app.ui.theme.TextSecondary
import com.fluidreader.core.units.VolumeUnit

/**
 * Prompts for a drink name (typed or spoken via the mic button) and logs [currentVolumeOz]
 * against it. Tapping a suggestion chip from [existingNames] fills the field instantly, so a
 * repeat pour of something already in tonight's log doesn't need retyping (and can't drift
 * into a near-duplicate name from a typo).
 */
@Composable
fun LogDrinkDialog(
    currentVolumeOz: Double,
    units: VolumeUnit,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) name = spoken
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text("Log this drink") },
        text = {
            Column {
                Text(
                    "Current reading: ${units.format(currentVolumeOz)}",
                    color = AquaPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "This amount will be added to the name below.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Drink name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AquaPrimary, focusedLabelColor = AquaPrimary),
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the drink name")
                            }
                            val available = intent.resolveActivity(context.packageManager) != null
                            if (available) {
                                speechLauncher.launch(intent)
                            } else {
                                Toast.makeText(context, "Voice input isn't available on this device", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.Mic, contentDescription = "Say the drink name", tint = AquaPrimary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (existingNames.isNotEmpty()) {
                    Text(
                        "Tonight so far",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        existingNames.forEach { existingName ->
                            SuggestionChip(
                                onClick = { name = existingName },
                                label = { Text(existingName) },
                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Ink0, labelColor = TextSecondary),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary),
            ) {
                Text("Log it", color = Ink0)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
