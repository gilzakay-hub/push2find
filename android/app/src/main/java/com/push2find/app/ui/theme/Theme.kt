package com.push2find.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Signal = Color(0xFFFF5B1F)      // brand accent – the wristband button
val Connected = Color(0xFF1E9E5A)
val Searching = Color(0xFFE39B12)
val Muted = Color(0xFF8A8F98)

private val Ink = Color(0xFF14161A)
private val Paper = Color(0xFFF6F5F2)

private val LightColors = lightColorScheme(
    primary = Ink, onPrimary = Paper,
    secondary = Signal, onSecondary = Color.White,
    background = Paper, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFECEAE5), onSurfaceVariant = Color(0xFF5B606A),
    error = Color(0xFFD7263D),
)

private val DarkColors = darkColorScheme(
    primary = Paper, onPrimary = Ink,
    secondary = Signal, onSecondary = Color.White,
    background = Color(0xFF0E0F12), onBackground = Paper,
    surface = Color(0xFF17191D), onSurface = Paper,
    surfaceVariant = Color(0xFF22252B), onSurfaceVariant = Color(0xFFA4A9B2),
    error = Color(0xFFFF5A6E),
)

@Composable
fun Push2FindTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
