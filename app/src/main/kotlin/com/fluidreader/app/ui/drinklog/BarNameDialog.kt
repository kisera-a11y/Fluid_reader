package com.fluidreader.app.ui.drinklog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fluidreader.app.ui.theme.AquaPrimary
import com.fluidreader.app.ui.theme.Ink0
import com.fluidreader.app.ui.theme.Ink2
import com.fluidreader.app.ui.theme.TextSecondary

/**
 * One-time prompt shown right after the first pour of a new log, asking which bar tonight's
 * list is for. Entirely optional - both [onSkip] (the dismiss button, tapping outside, or the
 * back button) and [onSave] with a blank field resolve the prompt the same way, so it never
 * reappears for the rest of this log just because it was left blank.
 */
@Composable
fun BarNameDialog(
    onSkip: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onSkip,
        containerColor = Ink2,
        title = { Text("Which bar?") },
        text = {
            Column {
                Text(
                    "Optional - name the bar tonight's list is for.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bar name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AquaPrimary, focusedLabelColor = AquaPrimary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary),
            ) { Text("Save", color = Ink0) }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip") }
        },
    )
}
