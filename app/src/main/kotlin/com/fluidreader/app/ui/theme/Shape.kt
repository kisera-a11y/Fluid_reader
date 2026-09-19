package com.fluidreader.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val FluidReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Extra radii used outside the Material `Shapes` scale (pills, the bottom sheet panel). */
object ExtraShapes {
    val Pill = RoundedCornerShape(50)
    val BottomPanel = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
