package com.fluidreader.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CupBlue = Color(0xFF4FC3F7)
val CupBlueDim = Color(0xFF29627E)
val WarnAmber = Color(0xFFFFB300)
val GoodGreen = Color(0xFF66BB6A)
val BadRed = Color(0xFFEF5350)
val SurfaceDark = Color(0xFF121212)
val SurfaceOverlay = Color(0xCC1B1B1B)

private val FluidReaderColorScheme = darkColorScheme(
    primary = CupBlue,
    onPrimary = Color.Black,
    secondary = WarnAmber,
    background = SurfaceDark,
    surface = SurfaceDark,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun FluidReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FluidReaderColorScheme,
        content = content,
    )
}
