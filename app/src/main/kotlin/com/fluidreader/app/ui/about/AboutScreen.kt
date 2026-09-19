package com.fluidreader.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fluidreader.app.BuildConfig
import com.fluidreader.app.R
import com.fluidreader.app.ui.theme.AquaContainer
import com.fluidreader.app.ui.theme.AquaPrimary
import com.fluidreader.app.ui.theme.Ink0
import com.fluidreader.app.ui.theme.Ink2
import com.fluidreader.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(colors = CardDefaults.cardColors(containerColor = AquaContainer), modifier = Modifier.size(48.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = AquaPrimary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            Text(
                "Fluid Reader estimates how many fluid ounces of liquid are in a standard " +
                    "clear 16 oz Solo-style cup by pointing your camera at it from the side. " +
                    "It uses on-device computer vision to find the cup's rim, base, and the " +
                    "liquid surface, then calculates volume using the cup's tapered (frustum) " +
                    "geometry rather than a naive percentage-of-height guess.",
                style = MaterialTheme.typography.bodyMedium,
            )

            AboutSection(
                title = "Accuracy",
                body = "Expect roughly ±0.5 oz under good lighting with a clear side view, and " +
                    "up to ±1 oz (or an explicit \"unavailable\" message) in difficult " +
                    "lighting, heavy reflections, or an off-angle view. Transparent cups are " +
                    "genuinely hard for computer vision - this app is a helpful estimate, not " +
                    "a lab instrument. Use the manual adjustment mode any time the automatic " +
                    "reading looks wrong.",
            )

            AboutSection(
                title = "Privacy",
                body = "Everything runs on-device. Camera frames are analyzed locally and are " +
                    "never uploaded anywhere. The Debug/Test screen's gallery photos are only " +
                    "read locally to test the detection pipeline.",
            )
        }
    }
}

@Composable
private fun AboutSection(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Ink2), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AquaPrimary)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
