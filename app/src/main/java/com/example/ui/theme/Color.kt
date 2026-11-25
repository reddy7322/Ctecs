package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// PART 1.1 – DEEP CHARCOAL DARK THEME PALETTE
// ============================================================================
val CharcoalDarkMainBackground = Color(0xFF12151C)
val CharcoalDarkSecondaryBackground = Color(0xFF171B24)
val CharcoalDarkPrimarySurface = Color(0xFF1C212C)
val CharcoalDarkSecondarySurface = Color(0xFF232937)
val CharcoalDarkElevatedSurface = Color(0xFF2B3344)

val CharcoalDarkPrimaryText = Color(0xFFF3F6FA)
val CharcoalDarkSecondaryText = Color(0xFFB0B9C8)
val CharcoalDarkMutedText = Color(0xFF7D899E)
val CharcoalDarkSeparator = Color(0xFF283040)

// ============================================================================
// PART 1.2 – TRUE BLACK AMOLED THEME PALETTE (#000000)
// ============================================================================
val AmoledMainBackground = Color(0xFF000000)
val AmoledSecondaryBackground = Color(0xFF06080D)
val AmoledPrimarySurface = Color(0xFF0B0E16)
val AmoledSecondarySurface = Color(0xFF111622)
val AmoledElevatedSurface = Color(0xFF171E2B)

val AmoledPrimaryText = Color(0xFFFFFFFF)
val AmoledSecondaryText = Color(0xFFA1AAB9)
val AmoledMutedText = Color(0xFF718096)
val AmoledSeparator = Color(0xFF161D2A)

// Backwards-compatible aliases used across legacy screens (mapped to AMOLED/Dark tokens)
val DarkMainBackground = AmoledMainBackground
val DarkSecondaryBackground = AmoledSecondaryBackground
val DarkPrimarySurface = AmoledPrimarySurface
val DarkSecondarySurface = AmoledSecondarySurface
val DarkElevatedSurface = AmoledElevatedSurface

val DarkPrimaryText = AmoledPrimaryText
val DarkSecondaryText = AmoledSecondaryText
val DarkMutedText = AmoledMutedText

val DarkPrimaryAccent = Color(0xFF1685FF)
val DarkSecondaryAccent = Color(0xFF20B8F0)
val DarkSuccess = Color(0xFF20C997)
val DarkWarning = Color(0xFFFFB52E)
val DarkError = Color(0xFFFF5263)

// ============================================================================
// PART 1.3 – PROFESSIONAL MINIMALIST LIGHT THEME PALETTE
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
// COMPATIBILITY ALIASES
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
