package com.fluidreader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fluidreader.app.ui.nav.FluidReaderNavGraph
import com.fluidreader.app.ui.theme.FluidReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluidReaderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FluidReaderNavGraph()
                }
            }
        }
    }
}
