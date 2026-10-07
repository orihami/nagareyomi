package com.orihami.nagareyomi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Calm "paper" palette for long reading sessions; no dynamic color so the
// reading surface looks the same on every device.
private val LightColors = lightColorScheme(
    primary = Color(0xFF3E6A73),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E6E8),
    onPrimaryContainer = Color(0xFF1B3A40),
    secondary = Color(0xFF7D7262),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F3EC),
    onBackground = Color(0xFF2A2926),
    surface = Color(0xFFF6F3EC),
    onSurface = Color(0xFF2A2926),
    surfaceVariant = Color(0xFFEAE5DA),
    onSurfaceVariant = Color(0xFF5C5850),
    surfaceContainer = Color(0xFFEFEBE2),
    surfaceContainerHigh = Color(0xFFEAE5DA),
    surfaceContainerLow = Color(0xFFF2EEE6),
    outline = Color(0xFFB9B1A2),
    outlineVariant = Color(0xFFD9D2C4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93C2C9),
    onPrimary = Color(0xFF0F2E33),
    primaryContainer = Color(0xFF2B4A50),
    onPrimaryContainer = Color(0xFFD6E6E8),
    secondary = Color(0xFFC2B6A3),
    onSecondary = Color(0xFF2E2A23),
    background = Color(0xFF171819),
    onBackground = Color(0xFFE3DFD7),
    surface = Color(0xFF171819),
    onSurface = Color(0xFFE3DFD7),
    surfaceVariant = Color(0xFF2A2C2E),
    onSurfaceVariant = Color(0xFFB4AFA6),
    surfaceContainer = Color(0xFF202223),
    surfaceContainerHigh = Color(0xFF2A2C2E),
    surfaceContainerLow = Color(0xFF1C1D1E),
    outline = Color(0xFF6B675F),
    outlineVariant = Color(0xFF3A3B3C),
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 30.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun NagareTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
