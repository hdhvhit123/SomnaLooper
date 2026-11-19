package com.somnalooper.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Somna {
    val Black = Color(0xFF000000)
    val Midnight = Color(0xFF0A1028)
    val MidnightHigh = Color(0xFF121A3A)
    val MidnightHigher = Color(0xFF1B2550)
    val Glow = Color(0xFF8FA8FF)
    val GlowSoft = Color(0xFF5C74D6)
    val Moon = Color(0xFFE8E4D4)
    val Muted = Color(0xFF8A90A8)
    val Teal = Color(0xFF6FD3C4)
    val Amber = Color(0xFFFFC27A)
}

private val scheme = darkColorScheme(
    primary = Somna.Glow,
    onPrimary = Color(0xFF07102E),
    primaryContainer = Somna.MidnightHigher,
    onPrimaryContainer = Somna.Moon,
    secondary = Somna.Teal,
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF123B38),
    onSecondaryContainer = Color(0xFFBDF1E8),
    tertiary = Somna.Amber,
    onTertiary = Color(0xFF2E1800),
    background = Somna.Black,
    onBackground = Somna.Moon,
    surface = Somna.Black,
    onSurface = Somna.Moon,
    surfaceVariant = Somna.MidnightHigh,
    onSurfaceVariant = Somna.Muted,
    surfaceContainerLowest = Somna.Black,
    surfaceContainerLow = Color(0xFF060A1A),
    surfaceContainer = Somna.Midnight,
    surfaceContainerHigh = Somna.MidnightHigh,
    surfaceContainerHighest = Somna.MidnightHigher,
    outline = Color(0xFF3A4370),
    outlineVariant = Color(0xFF232B52),
    error = Color(0xFFFFB4AB),
)

private val typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Light),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Light),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Light),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Normal),
    )
}

val ClockStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, fontSize = 72.sp)

@Composable
fun SomnaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
