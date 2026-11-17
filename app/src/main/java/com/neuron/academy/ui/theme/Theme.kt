package com.neuron.academy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuron.academy.R
import com.neuron.academy.content.Subject
import com.neuron.academy.data.ThemeMode

/* Шрифты: Unbounded — для заголовков и цифр, Onest — для текста. Оба с кириллицей (OFL). */
val Unbounded = FontFamily(
    Font(R.font.unbounded_500, FontWeight.Medium),
    Font(R.font.unbounded_700, FontWeight.Bold),
)
val Onest = FontFamily(
    Font(R.font.onest_400, FontWeight.Normal),
    Font(R.font.onest_500, FontWeight.Medium),
    Font(R.font.onest_600, FontWeight.SemiBold),
    Font(R.font.onest_700, FontWeight.Bold),
)

/**
 * Палитра «бумага и чернила»: тёплый светлый фон, почти чёрный текст, один акцент.
 * Тёмная тема — графит, а не фиолетовая ночь.
 */
private data class Accent(val light: Long, val dark: Long, val container: Long, val containerDark: Long)

private val accents = mapOf(
    "theme_default" to Accent(0xFF2F49D1, 0xFF9DB0FF, 0xFFE3E7FB, 0xFF232A4D),
    "theme_space" to Accent(0xFF5B47C9, 0xFFB8AAFF, 0xFFE9E4FB, 0xFF2C2550),
    "theme_ocean" to Accent(0xFF0B7A75, 0xFF6FD6CE, 0xFFDDF1EF, 0xFF15393A),
    "theme_forest" to Accent(0xFF2E6B3F, 0xFF8FCB9C, 0xFFE2EFE3, 0xFF1D3524),
    "theme_sunset" to Accent(0xFFC2410C, 0xFFFFA477, 0xFFFBE6DA, 0xFF472516),
)

object Ink {
    val Paper = Color(0xFFF6F3EC)
    val PaperRaised = Color(0xFFFFFDF8)
    val PaperSunken = Color(0xFFEDE8DD)
    val Line = Color(0xFFDDD6C8)
    val Text = Color(0xFF1C1B18)
    val TextMuted = Color(0xFF6B675E)

    val Night = Color(0xFF121315)
    val NightRaised = Color(0xFF1A1B1E)
    val NightSunken = Color(0xFF222428)
    val NightLine = Color(0xFF2E3035)
    val NightText = Color(0xFFEDEAE3)
    val NightTextMuted = Color(0xFF9C988F)
}

fun appColorScheme(themeId: String, dark: Boolean): ColorScheme {
    val a = accents[themeId] ?: accents.getValue("theme_default")
    return if (dark) darkColorScheme(
        primary = Color(a.dark),
        onPrimary = Ink.Night,
        primaryContainer = Color(a.containerDark),
        onPrimaryContainer = Ink.NightText,
        secondary = Color(0xFFE8C468),
        onSecondary = Ink.Night,
        secondaryContainer = Color(0xFF3A3322),
        onSecondaryContainer = Color(0xFFF3E2B5),
        tertiary = Color(0xFF7FD1A8),
        background = Ink.Night,
        onBackground = Ink.NightText,
        surface = Ink.Night,
        onSurface = Ink.NightText,
        surfaceVariant = Ink.NightSunken,
        onSurfaceVariant = Ink.NightTextMuted,
        surfaceContainerLowest = Ink.Night,
        surfaceContainerLow = Ink.NightRaised,
        surfaceContainer = Ink.NightRaised,
        surfaceContainerHigh = Ink.NightRaised,
        surfaceContainerHighest = Ink.NightSunken,
        outline = Ink.NightLine,
        outlineVariant = Ink.NightLine,
        inverseSurface = Ink.NightText,
        inverseOnSurface = Ink.Night,
        error = Color(0xFFFF8A80),
        errorContainer = Color(0xFF4A1F1C),
        onErrorContainer = Color(0xFFFFDAD5),
    ) else lightColorScheme(
        primary = Color(a.light),
        onPrimary = Color.White,
        primaryContainer = Color(a.container),
        onPrimaryContainer = Ink.Text,
        secondary = Color(0xFF9A6B00),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF6E7BF),
        onSecondaryContainer = Color(0xFF3B2A00),
        tertiary = Color(0xFF1F7A4D),
        background = Ink.Paper,
        onBackground = Ink.Text,
        surface = Ink.Paper,
        onSurface = Ink.Text,
        surfaceVariant = Ink.PaperSunken,
        onSurfaceVariant = Ink.TextMuted,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Ink.PaperRaised,
        surfaceContainer = Ink.PaperRaised,
        surfaceContainerHigh = Ink.PaperRaised,
        surfaceContainerHighest = Ink.PaperSunken,
        outline = Ink.Line,
        outlineVariant = Ink.Line,
        inverseSurface = Ink.Text,
        inverseOnSurface = Ink.Paper,
        error = Color(0xFFC62828),
        errorContainer = Color(0xFFFBE3DF),
        onErrorContainer = Color(0xFF5F1410),
    )
}

private fun display(size: Int, weight: FontWeight = FontWeight.Bold, line: Int = (size * 1.18).toInt(), spacing: Double = -0.02) =
    TextStyle(fontFamily = Unbounded, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = (size * spacing).sp)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = (size * 1.45).toInt(), spacing: Double = 0.0) =
    TextStyle(fontFamily = Onest, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

private val AppTypography = Typography(
    displayLarge = display(52),
    displayMedium = display(42),
    displaySmall = display(34),
    headlineLarge = display(30),
    headlineMedium = display(25),
    headlineSmall = display(21, FontWeight.Bold),
    titleLarge = display(17, FontWeight.Medium, 22),
    titleMedium = body(16, FontWeight.SemiBold, 22),
    titleSmall = body(14, FontWeight.SemiBold, 20),
    bodyLarge = body(16),
    bodyMedium = body(15),
    bodySmall = body(13, line = 18),
    labelLarge = body(14, FontWeight.SemiBold, 18, 0.1),
    labelMedium = body(12, FontWeight.SemiBold, 16, 0.2),
    labelSmall = body(11, FontWeight.Medium, 14, 0.3),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NeuronTheme(mode: ThemeMode, themeId: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = appColorScheme(themeId, dark), typography = AppTypography, shapes = AppShapes, content = content)
}

/** Сигнальные цвета игры — приглушённые, без «неона». */
object GameColors {
    val Xp = Color(0xFF5B47C9)
    val Crystal = Color(0xFF1E88B8)
    val Fire = Color(0xFFE0561B)
    val Success = Color(0xFF1F8A55)
    val Error = Color(0xFFD03B32)
    val Gold = Color(0xFFC9930C)
    val Locked = Color(0xFF9A958B)
}

/** Цвет предмета с учётом темы. */
fun Subject.tint(dark: Boolean): Color = when (this) {
    Subject.ALGEBRA -> if (dark) Color(0xFF8FA6FF) else Color(0xFF3550C8)
    Subject.PHYSICS -> if (dark) Color(0xFFFF9C86) else Color(0xFFD2452F)
    Subject.CHEMISTRY -> if (dark) Color(0xFF6FD3B0) else Color(0xFF138A66)
}
