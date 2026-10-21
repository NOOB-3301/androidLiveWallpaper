package com.haiku.wall.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Feibai's tokens, plus a derived night-ink scheme for dark mode. */
@Immutable
data class HaikuColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceSolid: Color,
    val text: Color,
    val muted: Color,
    val hairline: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val pine: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
)

val LightHaikuColors = HaikuColors(
    isDark = false,
    bg = Color(0xFFEDEBE3),
    surface = Color(0xFFF7F5EE),
    surfaceSolid = Color(0xFFF7F5EE),
    text = Color(0xFF16130F),
    muted = Color(0xFF6B6459),
    hairline = Color(0x1C16130F),
    accent = Color(0xFFB4372A),
    accentSoft = Color(0x21B4372A),
    onAccent = Color(0xFFF7F5EE),
    pine = Color(0xFF3C6152),
    success = Color(0xFF3C6152),
    warning = Color(0xFF9A6A2E),
    error = Color(0xFF9E2B1E),
    info = Color(0xFF3A5566),
)

val DarkHaikuColors = HaikuColors(
    isDark = true,
    bg = Color(0xFF14120F),
    surface = Color(0xFF1D1A15),
    surfaceSolid = Color(0xFF221E18),
    text = Color(0xFFEDE7DA),
    muted = Color(0xFFA79E90),
    hairline = Color(0x33EDE7DA),
    accent = Color(0xFFDE5C45),
    accentSoft = Color(0x33DE5C45),
    onAccent = Color(0xFF14120F),
    pine = Color(0xFF7FAE95),
    success = Color(0xFF7FAE95),
    warning = Color(0xFFD9A45B),
    error = Color(0xFFE06A55),
    info = Color(0xFF8FB0C4),
)

val LocalHaikuColors = staticCompositionLocalOf { LightHaikuColors }
