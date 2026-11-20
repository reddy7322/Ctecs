package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// PART 1.1 – PREMIUM AMOLED DEEP BLACK DARK THEME PALETTE
// ============================================================================
val DarkMainBackground = Color(0xFF000000)
val DarkSecondaryBackground = Color(0xFF080B12)
val DarkPrimarySurface = Color(0xFF0D111A)
val DarkSecondarySurface = Color(0xFF111827)
val DarkElevatedSurface = Color(0xFF171E2B)

val DarkPrimaryText = Color(0xFFFFFFFF)
val DarkSecondaryText = Color(0xFFA1AAB9)
val DarkMutedText = Color(0xFF718096)

val DarkPrimaryAccent = Color(0xFF1685FF)
val DarkSecondaryAccent = Color(0xFF20B8F0)
val DarkSuccess = Color(0xFF20C997)
val DarkWarning = Color(0xFFFFB52E)
val DarkError = Color(0xFFFF5263)

// ============================================================================
// PART 1.2 – PROFESSIONAL MINIMALIST LIGHT THEME PALETTE
// ============================================================================
val LightMainBackground = Color(0xFFFFFFFF)
val LightSecondaryBackground = Color(0xFFF7F9FC)
val LightPrimarySurface = Color(0xFFF1F4F8)
val LightSecondarySurface = Color(0xFFEAF0F7)
val LightElevatedSurface = Color(0xFFFFFFFF)

val LightPrimaryText = Color(0xFF101828)
val LightSecondaryText = Color(0xFF526174)
val LightMutedText = Color(0xFF8995A5)

val LightPrimaryAccent = Color(0xFF1677F2)
val LightSecondaryAccent = Color(0xFF159FD5)
val LightSuccess = Color(0xFF159C72)
val LightWarning = Color(0xFFD99A20)
val LightError = Color(0xFFDC4455)

// ============================================================================
// COMPATIBILITY ALIASES (Mapped cleanly to the unified AMOLED/Light tokens)
// ============================================================================
val PrimaryBackground = DarkMainBackground
val SecondaryBackground = DarkSecondaryBackground
val SurfaceDark = DarkPrimarySurface
val ElevatedSurface = DarkElevatedSurface

val PrimaryText = DarkPrimaryText
val SecondaryText = DarkSecondaryText
val BorderColor = DarkElevatedSurface

val AccentRed = DarkError
val DeepRed = LightError
val HighlightYellow = DarkWarning
val DarkYellowSurface = DarkSecondarySurface
val DisabledGray = DarkMutedText

val HighwayCyan = DarkSecondaryAccent
val RoadAmber = DarkWarning
val AlertRed = DarkError
val GPSGreen = DarkSuccess
val Slate950 = DarkMainBackground
val Slate900 = DarkPrimarySurface
val Slate800 = DarkSecondarySurface
val Slate700 = DarkElevatedSurface
val Slate600 = DarkMutedText
val Slate400 = DarkSecondaryText
