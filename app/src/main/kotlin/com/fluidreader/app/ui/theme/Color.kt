package com.fluidreader.app.ui.theme

import androidx.compose.ui.graphics.Color

// Core palette - a "liquid" cyan/teal accent against near-black camera-app surfaces.
// Every screen in the app draws from this single set of tokens rather than ad-hoc colors,
// so light/dark treatment, contrast, and mood stay consistent everywhere.

val AquaPrimary = Color(0xFF22D3EE) // main accent: cup outline, buttons, links
val AquaPrimaryDim = Color(0xFF0E7490) // pressed/dim state, secondary chrome
val AquaContainer = Color(0xFF113C47) // low-emphasis fills (chips, selected rows)

val GoodGreen = Color(0xFF34D399) // HIGH confidence, success states
val WarnAmber = Color(0xFFFBBF24) // MEDIUM confidence, caution
val BadRed = Color(0xFFF87171) // LOW confidence, errors

// Manual-adjust drag lines: three distinct hues (none reused from the confidence colors above)
// so the rim/base/liquid lines can't be confused with each other at a glance.
val ManualRim = Color(0xFF60A5FA) // TOP line
val ManualBase = Color(0xFFC084FC) // BOTTOM line
val ManualLiquid = Color(0xFFFACC15) // FILL line

// Kept as aliases for any older call sites still referencing the original names.
val CupBlue = AquaPrimary
val CupBlueDim = AquaPrimaryDim

val Ink0 = Color(0xFF0A0D0F) // app background - near black
val Ink1 = Color(0xFF13181B) // base surface (screens, scaffold)
val Ink2 = Color(0xFF1B2226) // raised surface (cards)
val Ink3 = Color(0xFF262F34) // hairlines, dividers, unselected chrome

val TextPrimary = Color(0xFFF3F6F7)
val TextSecondary = Color(0xFFA9B4B8)
val TextMuted = Color(0xFF6E7A7F)

/** Semi-transparent overlay panels drawn directly on top of the live camera preview. */
val SurfaceOverlay = Color(0xE6101619)
val SurfaceOverlaySoft = Color(0x99101619)
