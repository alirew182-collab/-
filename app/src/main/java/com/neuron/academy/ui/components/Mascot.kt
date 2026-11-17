package com.neuron.academy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class MascotMood { NORMAL, HAPPY, THINKING }

/** Внешний вид скина: основной цвет и аксессуар. */
private data class SkinLook(val body: Color, val accessory: String)

private fun look(skin: String): SkinLook = when (skin) {
    "skin_alien" -> SkinLook(Color(0xFF3E9B5A), "alien")
    "skin_fox" -> SkinLook(Color(0xFFE07A2E), "fox")
    "skin_astronaut" -> SkinLook(Color(0xFFE4E0D6), "astronaut")
    "skin_wizard" -> SkinLook(Color(0xFF6A4BC4), "wizard")
    "skin_dragon" -> SkinLook(Color(0xFF1F8A6D), "dragon")
    else -> SkinLook(Color(0xFF3550C8), "none")
}

/**
 * Робот-помощник «Нейрон», нарисованный векторно: голова-экран, моргающие глаза,
 * мигающая антенна. Скины меняют цвет корпуса и аксессуар.
 */
@Composable
fun NeuronMascot(
    skin: String,
    size: Dp = 64.dp,
    mood: MascotMood = MascotMood.NORMAL,
    animated: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val l = look(skin)
    val t = rememberInfiniteTransition(label = "mascot")
    val blink by t.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 4200
            1f at 0; 1f at 3900; 0.1f at 4000; 1f at 4120
        }),
        label = "blink",
    )
    val bob by t.animateFloat(0f, if (animated) 1f else 0f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "bob")
    val glow by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "glow")

    Canvas(modifier.size(size).graphicsLayer { translationY = -bob * size.toPx() * 0.04f }) {
        val s = this.size.minDimension
        drawMascot(s, l, mood, if (animated) blink else 1f, glow)
    }
}

private fun DrawScope.drawMascot(s: Float, l: SkinLook, mood: MascotMood, blink: Float, glow: Float) {
    val screen = Color(0xFF15171C)
    val eye = Color(0xFF8CF0E0)
    val body = l.body
    val shade = Color(body.red * 0.78f, body.green * 0.78f, body.blue * 0.78f)

    // аксессуары за головой
    when (l.accessory) {
        "fox" -> {
            listOf(0.2f, 0.8f).forEach { x ->
                val p = Path().apply {
                    moveTo(s * x - s * 0.13f, s * 0.32f); lineTo(s * x, s * 0.06f); lineTo(s * x + s * 0.13f, s * 0.32f); close()
                }
                drawPath(p, shade)
                val q = Path().apply {
                    moveTo(s * x - s * 0.06f, s * 0.3f); lineTo(s * x, s * 0.16f); lineTo(s * x + s * 0.06f, s * 0.3f); close()
                }
                drawPath(q, Color(0xFFFFE2C7))
            }
        }
        "dragon" -> listOf(0.24f, 0.76f).forEach { x ->
            val dir = if (x < 0.5f) -1 else 1
            val p = Path().apply {
                moveTo(s * x - s * 0.06f, s * 0.3f); quadraticBezierTo(s * (x + dir * 0.02f), s * 0.12f, s * (x + dir * 0.12f), s * 0.04f)
                quadraticBezierTo(s * (x + dir * 0.06f), s * 0.18f, s * x + s * 0.06f, s * 0.3f); close()
            }
            drawPath(p, Color(0xFFF2E6C9))
        }
        "alien" -> listOf(0.3f, 0.7f).forEach { x ->
            drawLine(shade, Offset(s * x, s * 0.28f), Offset(s * (x + (x - 0.5f) * 0.4f), s * 0.07f), strokeWidth = s * 0.035f, cap = StrokeCap.Round)
            drawCircle(Color(0xFFB8F27C).copy(alpha = glow), s * 0.05f, Offset(s * (x + (x - 0.5f) * 0.4f), s * 0.07f))
        }
        else -> {
            // антенна
            drawLine(shade, Offset(s * 0.5f, s * 0.26f), Offset(s * 0.5f, s * 0.1f), strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            drawCircle(Color(0xFFF2B705).copy(alpha = glow), s * 0.055f, Offset(s * 0.5f, s * 0.08f))
        }
    }

    // «уши»-болты
    drawRoundRect(shade, Offset(s * 0.05f, s * 0.48f), Size(s * 0.1f, s * 0.2f), CornerRadius(s * 0.04f))
    drawRoundRect(shade, Offset(s * 0.85f, s * 0.48f), Size(s * 0.1f, s * 0.2f), CornerRadius(s * 0.04f))

    // голова
    drawRoundRect(body, Offset(s * 0.12f, s * 0.24f), Size(s * 0.76f, s * 0.66f), CornerRadius(s * 0.2f))
    drawRoundRect(shade.copy(alpha = 0.35f), Offset(s * 0.12f, s * 0.78f), Size(s * 0.76f, s * 0.12f), CornerRadius(s * 0.2f))

    // экран-лицо
    drawRoundRect(screen, Offset(s * 0.2f, s * 0.34f), Size(s * 0.6f, s * 0.42f), CornerRadius(s * 0.13f))

    // глаза
    val eyeY = s * 0.52f
    val eyeW = s * 0.09f
    val eyeH = s * 0.14f * blink
    listOf(0.37f, 0.63f).forEach { x ->
        when (mood) {
            MascotMood.HAPPY -> {
                val p = Path().apply {
                    moveTo(s * x - eyeW, eyeY + s * 0.03f)
                    quadraticBezierTo(s * x, eyeY - s * 0.07f, s * x + eyeW, eyeY + s * 0.03f)
                }
                drawPath(p, eye, style = Stroke(s * 0.04f, cap = StrokeCap.Round))
            }
            MascotMood.THINKING -> drawRoundRect(eye, Offset(s * x - eyeW / 2, eyeY - s * 0.02f), Size(eyeW, s * 0.04f), CornerRadius(s * 0.02f))
            MascotMood.NORMAL -> drawRoundRect(eye, Offset(s * x - eyeW / 2, eyeY - eyeH / 2), Size(eyeW, eyeH.coerceAtLeast(s * 0.012f)), CornerRadius(eyeW / 2))
        }
    }
    // рот
    val mouth = Path().apply {
        moveTo(s * 0.44f, s * 0.66f)
        quadraticBezierTo(s * 0.5f, if (mood == MascotMood.HAPPY) s * 0.72f else s * 0.69f, s * 0.56f, s * 0.66f)
    }
    drawPath(mouth, eye.copy(alpha = 0.8f), style = Stroke(s * 0.025f, cap = StrokeCap.Round))

    // аксессуары поверх головы
    when (l.accessory) {
        "wizard" -> {
            val hat = Path().apply {
                moveTo(s * 0.2f, s * 0.3f); lineTo(s * 0.56f, s * -0.02f); lineTo(s * 0.8f, s * 0.3f); close()
            }
            drawPath(hat, Color(0xFF2C2357))
            drawRoundRect(Color(0xFF2C2357), Offset(s * 0.12f, s * 0.26f), Size(s * 0.76f, s * 0.07f), CornerRadius(s * 0.035f))
            drawCircle(Color(0xFFF2B705), s * 0.035f, Offset(s * 0.5f, s * 0.17f))
        }
        "astronaut" -> {
            drawCircle(Color(0xFFBFE3FF).copy(alpha = 0.22f), s * 0.5f, Offset(s * 0.5f, s * 0.56f))
            drawCircle(Color.White.copy(alpha = 0.7f), s * 0.5f, Offset(s * 0.5f, s * 0.56f), style = Stroke(s * 0.025f))
            drawLine(Color.White.copy(alpha = 0.6f), Offset(s * 0.24f, s * 0.36f), Offset(s * 0.32f, s * 0.28f), strokeWidth = s * 0.03f, cap = StrokeCap.Round)
        }
        "dragon" -> listOf(0.38f, 0.5f, 0.62f).forEach { x ->
            val p = Path().apply { moveTo(s * x - s * 0.04f, s * 0.25f); lineTo(s * x, s * 0.17f); lineTo(s * x + s * 0.04f, s * 0.25f); close() }
            drawPath(p, Color(0xFFF2E6C9))
        }
        else -> {}
    }
    // блик
    drawLine(Color.White.copy(alpha = 0.18f), Offset(s * 0.26f, s * 0.4f), Offset(s * 0.34f, s * 0.37f), strokeWidth = s * 0.025f, cap = StrokeCap.Round)
}
