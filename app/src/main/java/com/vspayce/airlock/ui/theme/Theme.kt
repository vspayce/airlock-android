package com.vspayce.airlock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AirlockBackground = Color(0xFF121722)
val AirlockTeal = Color(0xFF62D6D6)
val AirlockBurn = Color(0xFFFF8A5B)

private val AirlockColors = darkColorScheme(
    primary = AirlockTeal,
    onPrimary = Color(0xFF002E2E),
    primaryContainer = Color(0xFF1C4A4C),
    onPrimaryContainer = Color(0xFFB9F2F2),
    secondary = AirlockTeal,
    onSecondary = Color(0xFF002E2E),
    background = AirlockBackground,
    onBackground = Color(0xFFE2E7EE),
    surface = AirlockBackground,
    onSurface = Color(0xFFE2E7EE),
    surfaceVariant = Color(0xFF1E2636),
    onSurfaceVariant = Color(0xFF9AA6B8),
    surfaceContainer = Color(0xFF1A2130),
    surfaceContainerHigh = Color(0xFF222A3A),
    surfaceContainerHighest = Color(0xFF283142),
    outline = Color(0xFF3A4558),
    error = Color(0xFFFF8A80),
)

@Composable
fun AirlockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AirlockColors, content = content)
}
