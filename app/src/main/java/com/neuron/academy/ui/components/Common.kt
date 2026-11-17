package com.neuron.academy.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuron.academy.data.db.PlayerEntity
import com.neuron.academy.ui.theme.GameColors
import com.neuron.gamification.LevelSystem
import com.neuron.gamification.ShopCatalog

fun skinEmoji(skinId: String): String = ShopCatalog.byId(skinId)?.emoji ?: "🤖"

/** Робот-помощник «Нейрон» с лёгким покачиванием. */
@Composable
fun NeuronAvatar(skin: String, size: Dp = 64.dp, animated: Boolean = true) {
    val t = rememberInfiniteTransition(label = "bob")
    val dy by t.animateFloat(0f, if (animated) -6f else 0f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "dy")
    Box(
        Modifier
            .size(size)
            .graphicsLayer { translationY = dy }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(skinEmoji(skin), fontSize = (size.value * 0.55f).sp)
    }
}

@Composable
fun NeuronBubble(text: String, skin: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        NeuronAvatar(skin, 52.dp)
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Нейрон", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(text, style = MaterialTheme.typography.bodyMedium)
                action?.invoke()
            }
        }
    }
}

/** Верхняя панель с уровнем, XP, кристаллами и серией. */
@Composable
fun PlayerStatsBar(player: PlayerEntity, modifier: Modifier = Modifier) {
    val info = LevelSystem.info(player.xp)
    val progress by animateFloatAsState(info.progress, tween(800), label = "xp")
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(GameColors.Xp),
                    contentAlignment = Alignment.Center,
                ) { Text("${info.level}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${info.rank.emoji} ${info.rank.title}", style = MaterialTheme.typography.titleMedium)
                    Text("${player.xp} XP · до ур. ${info.level + 1}: ${info.xpToNext}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatChip("💎", player.crystals.toString(), GameColors.Crystal)
                Spacer(Modifier.width(6.dp))
                StatChip("🔥", player.streak.toString(), GameColors.Fire)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = GameColors.Xp,
                trackColor = GameColors.Xp.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun StatChip(emoji: String, value: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 14.sp)
        Spacer(Modifier.width(3.dp))
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
    }
}

@Composable
fun ProgressRing(fraction: Float, color: Color, size: Dp = 56.dp, stroke: Dp = 6.dp, content: @Composable () -> Unit = {}) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(s, cap = StrokeCap.Round), topLeft = androidx.compose.ui.geometry.Offset(s / 2, s / 2), size = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s))
            drawArc(color, -90f, 360f * anim, false, style = Stroke(s, cap = StrokeCap.Round), topLeft = androidx.compose.ui.geometry.Offset(s / 2, s / 2), size = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s))
        }
        content()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(vertical = 8.dp))
}

@Composable
fun InfoCard(title: String, emoji: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 18.sp) }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun LoadingBlock(text: String = "Нейрон думает…") {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorBlock(message: String, onOpenSettings: (() -> Unit)? = null) {
    Surface(color = GameColors.Error.copy(alpha = 0.1f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().border(1.dp, GameColors.Error.copy(alpha = 0.3f), RoundedCornerShape(14.dp))) {
        Column(Modifier.padding(12.dp)) {
            Text("⚠️ $message", style = MaterialTheme.typography.bodyMedium)
            if (onOpenSettings != null) TextButton(onClick = onOpenSettings) { Text("Открыть настройки ключей") }
        }
    }
}

@Composable
fun EmptyHint(emoji: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.height(8.dp))
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
