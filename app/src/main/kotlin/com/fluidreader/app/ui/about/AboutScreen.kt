package com.fluidreader.app.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fluidreader.app.R
import com.fluidreader.app.BuildConfig

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
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)

            Text(
                "\nFluid Reader estimates how many fluid ounces of liquid are in a standard " +
                    "clear 16 oz Solo-style cup by pointing your camera at it from the side. " +
                    "It uses on-device computer vision to find the cup's rim, base, and the " +
                    "liquid surface, then calculates volume using the cup's tapered (frustum) " +
                    "geometry rather than a naive percentage-of-height guess.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                "\nAccuracy",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Expect roughly ±0.5 oz under good lighting with a clear side view, and " +
                    "up to ±1 oz (or an explicit \"unavailable\" message) in difficult " +
                    "lighting, heavy reflections, or an off-angle view. Transparent cups are " +
                    "genuinely hard for computer vision - this app is a helpful estimate, not " +
                    "a lab instrument. Use the manual adjustment mode any time the automatic " +
                    "reading looks wrong.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                "\nPrivacy",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Everything runs on-device. Camera frames are analyzed locally and are never " +
                    "uploaded anywhere. The Debug/Test screen's gallery photos are only read " +
                    "locally to test the detection pipeline.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
