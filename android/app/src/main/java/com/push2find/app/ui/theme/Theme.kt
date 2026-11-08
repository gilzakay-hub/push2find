package com.push2find.app.ui.theme

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

// Brand
val Signal = Color(0xFFFF5B1F)      // the device button
val SignalDeep = Color(0xFFE2470F)
val Connected = Color(0xFF17A35B)
val Searching = Color(0xFFEFA21B)
val Muted = Color(0xFF8D929B)
val Danger = Color(0xFFE5484D)

private val Ink = Color(0xFF121417)
private val Paper = Color(0xFFF4F2EE)

private val LightColors = lightColorScheme(
    primary = Ink, onPrimary = Color.White,
    secondary = Signal, onSecondary = Color.White,
    tertiary = Signal,
    background = Paper, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFEAE7E1), onSurfaceVariant = Color(0xFF666B74),
    outline = Color(0xFFD9D5CE), outlineVariant = Color(0xFFEAE7E1),
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF4F2EE), onPrimary = Ink,
    secondary = Signal, onSecondary = Color.White,
    tertiary = Signal,
    background = Color(0xFF0C0D10), onBackground = Color(0xFFF4F2EE),
    surface = Color(0xFF16181C), onSurface = Color(0xFFF4F2EE),
    surfaceVariant = Color(0xFF22252A), onSurfaceVariant = Color(0xFF9EA3AC),
    outline = Color(0xFF2E3137), outlineVariant = Color(0xFF22252A),
    error = Color(0xFFFF6369),
)

private val P2FTypography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    // Used for small uppercase overlines ("BATTERY", "DEVICE")
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
)

@Composable
fun Push2FindTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = P2FTypography,
        content = content,
    )
}
