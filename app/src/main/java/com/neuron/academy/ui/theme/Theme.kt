package com.neuron.academy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.neuron.academy.data.ThemeMode

private data class Palette(val primary: Long, val secondary: Long, val tertiary: Long, val darkBg: Long, val lightBg: Long)

private val palettes = mapOf(
    "theme_default" to Palette(0xFF6C4DF6, 0xFF26C6DA, 0xFFFFB300, 0xFF140F2E, 0xFFF7F5FF),
    "theme_space" to Palette(0xFF3D5AFE, 0xFF7C4DFF, 0xFFFFD54F, 0xFF0A0F2C, 0xFFF1F4FF),
    "theme_ocean" to Palette(0xFF00897B, 0xFF29B6F6, 0xFFFF8A65, 0xFF06201F, 0xFFF0FBFA),
    "theme_forest" to Palette(0xFF2E7D32, 0xFF9CCC65, 0xFFFFCA28, 0xFF0D1F0F, 0xFFF3FAF1),
    "theme_sunset" to Palette(0xFFF4511E, 0xFFEC407A, 0xFFFFCA28, 0xFF2A1210, 0xFFFFF6F2),
)

private fun scheme(themeId: String, dark: Boolean): ColorScheme {
    val p = palettes[themeId] ?: palettes.getValue("theme_default")
    return if (dark) darkColorScheme(
        primary = Color(p.primary).copy(alpha = 1f).let { lighten(it, 0.25f) },
        onPrimary = Color.White,
        primaryContainer = Color(p.primary).copy(alpha = 0.35f).compositeOn(Color(p.darkBg)),
        onPrimaryContainer = Color.White,
        secondary = lighten(Color(p.secondary), 0.15f),
        tertiary = Color(p.tertiary),
        background = Color(p.darkBg),
        surface = Color(p.darkBg),
        surfaceVariant = lighten(Color(p.darkBg), 0.08f),
        surfaceContainer = lighten(Color(p.darkBg), 0.06f),
        surfaceContainerHigh = lighten(Color(p.darkBg), 0.1f),
    ) else lightColorScheme(
        primary = Color(p.primary),
        onPrimary = Color.White,
        primaryContainer = Color(p.primary).copy(alpha = 0.15f).compositeOn(Color.White),
        onPrimaryContainer = Color(p.primary).darken(0.4f),
        secondary = Color(p.secondary).darken(0.15f),
        tertiary = Color(p.tertiary).darken(0.1f),
        background = Color(p.lightBg),
        surface = Color(p.lightBg),
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color.White,
    )
}

private fun lighten(c: Color, f: Float) = Color(c.red + (1 - c.red) * f, c.green + (1 - c.green) * f, c.blue + (1 - c.blue) * f, c.alpha)
private fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
private fun Color.compositeOn(bg: Color) = Color(
    red * alpha + bg.red * (1 - alpha), green * alpha + bg.green * (1 - alpha), blue * alpha + bg.blue * (1 - alpha), 1f,
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun NeuronTheme(mode: ThemeMode, themeId: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = scheme(themeId, dark), typography = AppTypography, content = content)
}

object GameColors {
    val Xp = Color(0xFF7C4DFF)
    val Crystal = Color(0xFF29B6F6)
    val Fire = Color(0xFFFF7043)
    val Success = Color(0xFF43A047)
    val Error = Color(0xFFE53935)
    val Gold = Color(0xFFFFB300)
    val Locked = Color(0xFF9E9E9E)
}
