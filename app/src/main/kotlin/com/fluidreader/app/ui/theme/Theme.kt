package com.fluidreader.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FluidReaderColorScheme = darkColorScheme(
    primary = AquaPrimary,
    onPrimary = Ink0,
    primaryContainer = AquaContainer,
    onPrimaryContainer = AquaPrimary,
    secondary = WarnAmber,
    onSecondary = Ink0,
    tertiary = GoodGreen,
    onTertiary = Ink0,
    error = BadRed,
    onError = Ink0,
    background = Ink0,
    onBackground = TextPrimary,
    surface = Ink1,
    onSurface = TextPrimary,
    surfaceVariant = Ink2,
    onSurfaceVariant = TextSecondary,
    outline = Ink3,
    outlineVariant = Ink3,
)

@Composable
fun FluidReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FluidReaderColorScheme,
        typography = FluidReaderTypography,
        shapes = FluidReaderShapes,
        content = content,
    )
}
