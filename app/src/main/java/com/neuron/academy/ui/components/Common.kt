package com.neuron.academy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.ui.theme.GameColors
import com.neuron.academy.ui.theme.Unbounded
import com.neuron.gamification.LevelSystem
import com.neuron.gamification.ShopCatalog

fun skinEmoji(skinId: String): String = ShopCatalog.byId(skinId)?.emoji ?: "🤖"

/** Робот-помощник «Нейрон». */
@Composable
fun NeuronAvatar(skin: String, size: Dp = 64.dp, animated: Boolean = true, mood: MascotMood = MascotMood.NORMAL) {
    NeuronMascot(skin, size, mood, animated)
}

/** Базовая плоская панель: тёплая подложка + тонкая линия вместо теней и градиентов. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    border: Color = MaterialTheme.colorScheme.outline,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = BorderStroke(1.dp, border)) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = BorderStroke(1.dp, border)) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

/** Маленькая надпись над блоком (как рубрика в журнале). */
@Composable
fun Eyebrow(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

@Composable
fun NeuronBubble(text: String, skin: String, modifier: Modifier = Modifier, mood: MascotMood = MascotMood.NORMAL, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NeuronMascot(skin, 56.dp, mood)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            action?.invoke()
        }
    }
}

/** Панель игрока: уровень в кольце прогресса, звание, кристаллы и серия. */
@Composable
fun PlayerStatsBar(player: PlayerEntity, modifier: Modifier = Modifier) {
    val info = LevelSystem.info(player.xp)
    Panel(modifier, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(info.progress, MaterialTheme.colorScheme.primary, 54.dp, 5.dp) {
                Text("${info.level}", style = MaterialTheme.typography.titleLarge.copy(fontFamily = Unbounded))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(info.rank.title, style = MaterialTheme.typography.titleMedium)
                Text("${player.xp} XP · ещё ${info.xpToNext} до ${info.level + 1} ур.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatChip(Icons.Rounded.Diamond, player.crystals.toString(), GameColors.Crystal)
            Spacer(Modifier.width(6.dp))
            StatChip(Icons.Rounded.LocalFireDepartment, player.streak.toString(), GameColors.Fire)
        }
    }
}

@Composable
fun StatChip(icon: ImageVector, value: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun ProgressRing(fraction: Float, color: Color, size: Dp = 56.dp, stroke: Dp = 6.dp, content: @Composable () -> Unit = {}) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "ring")
    val track = MaterialTheme.colorScheme.outline
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val tl = androidx.compose.ui.geometry.Offset(s / 2, s / 2)
            val sz = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, style = Stroke(s), topLeft = tl, size = sz)
            drawArc(color, -90f, 360f * anim, false, style = Stroke(s, cap = StrokeCap.Round), topLeft = tl, size = sz)
        }
        content()
    }
}

/** Тонкая полоса прогресса. */
@Composable
fun ThinProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "bar")
    val track = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = size.height / 2
        drawLine(track, androidx.compose.ui.geometry.Offset(r, r), androidx.compose.ui.geometry.Offset(size.width - r, r), strokeWidth = size.height, cap = StrokeCap.Round)
        if (anim > 0f) drawLine(color, androidx.compose.ui.geometry.Offset(r, r), androidx.compose.ui.geometry.Offset(r + (size.width - 2 * r) * anim, r), strokeWidth = size.height, cap = StrokeCap.Round)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = 10.dp, bottom = 2.dp))
}

/** Заголовок экрана в стиле журнала: крупно, слева. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun InfoCard(title: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, content: @Composable () -> Unit) {
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Spacer(Modifier.width(8.dp))
            Eyebrow(title)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun LoadingBlock(text: String = "Нейрон думает…", skin: String = "skin_classic") {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        NeuronMascot(skin, 36.dp, MascotMood.THINKING)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorBlock(message: String, onOpenSettings: (() -> Unit)? = null) {
    Panel(color = MaterialTheme.colorScheme.errorContainer, border = MaterialTheme.colorScheme.error.copy(alpha = 0.3f), padding = PaddingValues(12.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        if (onOpenSettings != null) TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(0.dp)) { Text("Настройки ИИ") }
    }
}

@Composable
fun EmptyHint(text: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NeuronMascot("skin_classic", 72.dp, MascotMood.THINKING)
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Иконка в мягкой плашке. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3.2f)).background(color.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

@Suppress("unused")
private val keepLightbulb = Icons.Rounded.Lightbulb
